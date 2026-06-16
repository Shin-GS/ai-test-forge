package com.aitestforge.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 세션별 SSE 이벤트를 버퍼에 저장하고, 재연결 시 누락된 이벤트를 재전송할 수 있게 하는 서비스.
 *
 * <p>이벤트에 고유 ID를 부여하여 Last-Event-ID 기반 재연결을 지원한다.
 * 버퍼는 최대 120초간 보관되며, 만료된 이벤트는 스케줄러에 의해 주기적으로 정리된다.</p>
 *
 * <p>향후 AgentLoopService, RecipeExecutionService에서 SSE 이벤트 발행 시 이 서비스를 사용할 예정.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SseEventBufferService {

    private static final Duration BUFFER_TTL = Duration.ofSeconds(120);
    private static final String EVENT_ID_PREFIX = "evt-";

    private final ObjectMapper objectMapper;

    // 세션별 이벤트 버퍼 — 최대 120초 보관
    private final Map<Long, Deque<SseBufferedEvent>> buffers = new ConcurrentHashMap<>();

    // 세션별 SSE emitter 관리
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 이벤트 ID 시퀀스 (세션별)
    private final Map<Long, AtomicLong> eventIdSequences = new ConcurrentHashMap<>();

    /**
     * SSE emitter를 등록한다.
     *
     * @param sessionId 세션 ID
     * @param emitter   등록할 SseEmitter
     */
    public void registerEmitter(Long sessionId, SseEmitter emitter) {
        emitters.put(sessionId, emitter);
        log.debug("SSE emitter registered for session {}", sessionId);
    }

    /**
     * SSE emitter를 제거한다.
     *
     * @param sessionId 세션 ID
     */
    public void removeEmitter(Long sessionId) {
        emitters.remove(sessionId);
        log.debug("SSE emitter removed for session {}", sessionId);
    }

    /**
     * 이벤트를 발행한다 — SSE 전송 + 버퍼 저장.
     * 이벤트에 고유 ID를 부여한다.
     *
     * @param sessionId 세션 ID
     * @param type      이벤트 타입 (message, tool_call_start, tool_call_result, done, error 등)
     * @param data      이벤트 데이터 (직렬화할 객체)
     * @return 이벤트 ID (evt-{sequence})
     */
    public String publish(Long sessionId, String type, Object data) {
        // 1. 시퀀스 생성 또는 조회
        AtomicLong sequence = eventIdSequences.computeIfAbsent(sessionId, k -> new AtomicLong(0));

        // 2. 이벤트 ID 생성
        String eventId = EVENT_ID_PREFIX + sequence.incrementAndGet();

        // 3. 데이터를 JSON으로 직렬화
        String dataJson = serialize(data);

        // 4. 버퍼에 이벤트 저장
        SseBufferedEvent bufferedEvent = new SseBufferedEvent(eventId, type, dataJson, Instant.now());
        Deque<SseBufferedEvent> buffer = buffers.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        synchronized (buffer) {
            buffer.addLast(bufferedEvent);
        }

        // 5. emitter가 있으면 SSE 전송
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .id(eventId)
                        .name(type)
                        .data(dataJson));
            } catch (IOException e) {
                log.warn("Failed to send SSE event for session {}, eventId {}: {}",
                        sessionId, eventId, e.getMessage());
                emitters.remove(sessionId);
            }
        } else {
            log.debug("No active emitter for session {}. Event {} buffered only.", sessionId, eventId);
        }

        return eventId;
    }

    /**
     * 재연결 시 lastEventId 이후의 이벤트를 반환한다.
     *
     * @param sessionId   세션 ID
     * @param lastEventId 마지막 수신한 이벤트 ID (예: "evt-42")
     * @return lastEventId 이후의 버퍼된 이벤트 목록
     */
    public List<SseBufferedEvent> getEventsAfter(Long sessionId, String lastEventId) {
        Deque<SseBufferedEvent> buffer = buffers.get(sessionId);
        if (buffer == null) {
            return List.of();
        }

        long lastSequence = parseSequence(lastEventId);

        List<SseBufferedEvent> result;
        synchronized (buffer) {
            result = buffer.stream()
                    .filter(event -> parseSequence(event.id()) > lastSequence)
                    .sorted(Comparator.comparingLong(event -> parseSequence(event.id())))
                    .toList();
        }

        log.debug("Returning {} buffered events after {} for session {}", result.size(), lastEventId, sessionId);
        return result;
    }

    /**
     * 세션의 버퍼를 정리한다 (세션 종료 시).
     *
     * @param sessionId 세션 ID
     */
    public void cleanup(Long sessionId) {
        buffers.remove(sessionId);
        emitters.remove(sessionId);
        eventIdSequences.remove(sessionId);
        log.debug("Cleaned up buffer, emitter, and sequence for session {}", sessionId);
    }

    /**
     * 120초 초과한 이벤트를 정리한다. 30초마다 실행.
     */
    @Scheduled(fixedRate = 30_000)
    public void cleanExpiredEvents() {
        Instant cutoff = Instant.now().minus(BUFFER_TTL);
        List<Long> emptySessionIds = new ArrayList<>();

        for (Map.Entry<Long, Deque<SseBufferedEvent>> entry : buffers.entrySet()) {
            Long sessionId = entry.getKey();
            Deque<SseBufferedEvent> buffer = entry.getValue();

            synchronized (buffer) {
                // 앞(가장 오래된)부터 검사하여 만료된 이벤트 제거
                while (!buffer.isEmpty() && buffer.peekFirst().createdAt().isBefore(cutoff)) {
                    buffer.pollFirst();
                }

                if (buffer.isEmpty()) {
                    emptySessionIds.add(sessionId);
                }
            }
        }

        // 비어있는 세션 정리
        for (Long sessionId : emptySessionIds) {
            buffers.remove(sessionId);
            eventIdSequences.remove(sessionId);
        }

        if (!emptySessionIds.isEmpty()) {
            log.debug("Cleaned expired event buffers for {} sessions", emptySessionIds.size());
        }
    }

    /**
     * 객체를 JSON 문자열로 직렬화한다.
     * 직렬화 실패 시 빈 JSON 객체를 반환한다.
     */
    private String serialize(Object data) {
        if (data instanceof String str) {
            return str;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize SSE event data: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 이벤트 ID에서 시퀀스 번호를 추출한다.
     * 예: "evt-42" → 42
     *
     * @param eventId 이벤트 ID
     * @return 시퀀스 번호, 파싱 실패 시 0
     */
    private long parseSequence(String eventId) {
        if (eventId == null || !eventId.startsWith(EVENT_ID_PREFIX)) {
            return 0;
        }
        try {
            return Long.parseLong(eventId.substring(EVENT_ID_PREFIX.length()));
        } catch (NumberFormatException e) {
            log.warn("Failed to parse event ID sequence: {}", eventId);
            return 0;
        }
    }

    /**
     * 버퍼에 저장되는 SSE 이벤트.
     *
     * @param id        이벤트 ID (evt-{sequence})
     * @param type      이벤트 타입 (message, tool_call_start, tool_call_result, done, error 등)
     * @param data      JSON 직렬화된 이벤트 데이터
     * @param createdAt 이벤트 생성 시각
     */
    public record SseBufferedEvent(String id, String type, String data, Instant createdAt) {}
}
