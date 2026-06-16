package com.aitestforge.service.agent;

import com.aitestforge.domain.chat.ChatSession;
import com.aitestforge.domain.spec.SpecStatus;
import com.aitestforge.domain.spec.SubdomainSpec;
import com.aitestforge.dto.chat.ToolResultRequest;
import com.aitestforge.dto.workspace.WorkspaceMappingDto;
import com.aitestforge.infra.ai.AiService;
import com.aitestforge.infra.ai.dto.AiChatResponse;
import com.aitestforge.infra.ai.dto.ToolCall;
import com.aitestforge.infra.ai.dto.ToolDefinition;
import com.aitestforge.repository.ChatMessageRepository;
import com.aitestforge.repository.ChatSessionRepository;
import com.aitestforge.repository.SubdomainSpecRepository;
import com.aitestforge.service.chat.ChatService;
import com.aitestforge.service.spec.SpecToolConverter;
import com.aitestforge.service.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent Loop 오케스트레이션.
 *
 * 제약:
 * - 최대 반복 횟수 (기본 20회)
 * - 전체 타임아웃 (기본 120초)
 * - 초과 시 에러 메시지 + done 이벤트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLoopService {

    private final AiService aiService;
    private final ChatService chatService;
    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;
    private final SubdomainSpecRepository specRepository;
    private final SpecToolConverter specToolConverter;
    private final WorkspaceService workspaceService;

    @Value("${agent-loop.max-iterations:20}")
    private int maxIterations;

    @Value("${agent-loop.timeout-seconds:120}")
    private int timeoutSeconds;

    // 세션별 SSE emitter 관리
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 세션별 루프 상태 추적
    private final Map<Long, LoopState> loopStates = new ConcurrentHashMap<>();

    /**
     * SSE 연결 생성 (FE가 스트림을 구독할 때 호출).
     */
    public SseEmitter createEmitter(Long sessionId) {
        SseEmitter emitter = new SseEmitter((long) timeoutSeconds * 1000);
        emitters.put(sessionId, emitter);

        emitter.onCompletion(() -> cleanup(sessionId));
        emitter.onTimeout(() -> cleanup(sessionId));
        emitter.onError(e -> cleanup(sessionId));

        return emitter;
    }

    /**
     * 사용자 메시지를 받아 Agent Loop 첫 턴을 시작한다.
     */
    public void startLoop(Long sessionId, String userMessage) {
        chatService.addUserMessage(sessionId, userMessage);
        // 새 루프 시작 — 상태 초기화
        loopStates.put(sessionId, new LoopState(Instant.now(), 0));
        processNextTurn(sessionId);
    }

    /**
     * FE에서 tool-result를 수신한 후 다음 턴을 진행한다.
     */
    public void handleToolResult(Long sessionId, ToolResultRequest toolResult) {
        chatService.addToolResultMessage(sessionId, toolResult.toolCallId(), toolResult.body());

        sendSseEvent(sessionId, "tool_call_result", Map.of(
                "toolCallId", toolResult.toolCallId(),
                "statusCode", String.valueOf(toolResult.statusCode())
        ));

        processNextTurn(sessionId);
    }

    private void processNextTurn(Long sessionId) {
        // 제약 검사
        if (!checkConstraints(sessionId)) {
            return;
        }

        try {
            // 반복 횟수 증가
            LoopState state = loopStates.get(sessionId);
            if (state != null) {
                loopStates.put(sessionId, new LoopState(state.startedAt(), state.iterationCount() + 1));
            }

            // 현재 대화 히스토리 구성
            List<com.aitestforge.infra.ai.dto.ChatMessage> history = buildHistory(sessionId);

            // 워크스페이스 기반 tool 목록 필터링
            List<ToolDefinition> tools = buildToolsForSession(sessionId);

            // AI 호출
            AiChatResponse response = aiService.chat(history, tools);

            // tool call이 있으면 FE에 지시
            if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
                handleToolCalls(sessionId, response);
                return;
            }

            // 텍스트 응답만 있으면 완료
            if (response.message() != null && !response.message().isBlank()) {
                chatService.addAssistantMessage(sessionId, response.message());
                sendSseEvent(sessionId, "message", Map.of("content", response.message()));
                sendSseEvent(sessionId, "done", Map.of());
                chatService.completeSession(sessionId);
                completeEmitter(sessionId);
            }
        } catch (Exception e) {
            log.error("Agent loop error for session {}: {}", sessionId, e.getMessage(), e);
            sendSseEvent(sessionId, "error", Map.of("message", e.getMessage()));
            completeEmitter(sessionId);
        }
    }

    /**
     * Agent Loop 제약 검사.
     * 반복 횟수 초과 또는 타임아웃 시 에러 이벤트를 보내고 false 반환.
     */
    private boolean checkConstraints(Long sessionId) {
        LoopState state = loopStates.get(sessionId);
        if (state == null) return true;

        // 최대 반복 횟수 초과
        if (state.iterationCount() >= maxIterations) {
            String msg = String.format("Agent Loop 최대 반복 횟수(%d회)를 초과하여 중단합니다.", maxIterations);
            log.warn("Agent loop max iterations exceeded for session {}: {}", sessionId, maxIterations);
            chatService.addAssistantMessage(sessionId, msg);
            sendSseEvent(sessionId, "message", Map.of("content", msg));
            sendSseEvent(sessionId, "error", Map.of("message", msg));
            sendSseEvent(sessionId, "done", Map.of());
            completeEmitter(sessionId);
            return false;
        }

        // 타임아웃
        long elapsedSeconds = Instant.now().getEpochSecond() - state.startedAt().getEpochSecond();
        if (elapsedSeconds > timeoutSeconds) {
            String msg = String.format("Agent Loop 타임아웃(%d초)으로 중단합니다.", timeoutSeconds);
            log.warn("Agent loop timeout for session {}: {}s elapsed", sessionId, elapsedSeconds);
            chatService.addAssistantMessage(sessionId, msg);
            sendSseEvent(sessionId, "message", Map.of("content", msg));
            sendSseEvent(sessionId, "error", Map.of("message", msg));
            sendSseEvent(sessionId, "done", Map.of());
            completeEmitter(sessionId);
            return false;
        }

        return true;
    }

    private void handleToolCalls(Long sessionId, AiChatResponse response) {
        String assistantContent = response.message() != null ? response.message() : "";
        chatService.addAssistantMessage(sessionId, assistantContent);

        for (ToolCall toolCall : response.toolCalls()) {
            sendSseEvent(sessionId, "tool_call_start", Map.of(
                    "toolCallId", toolCall.id(),
                    "name", toolCall.name(),
                    "arguments", toolCall.argumentsJson()
            ));
        }
    }

    private List<ToolDefinition> buildToolsForSession(Long sessionId) {
        ChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return List.of();
        }

        Long userId = session.getUserId();
        List<WorkspaceMappingDto> mappings = workspaceService.getDefaultMappings(userId);
        List<SubdomainSpec> activeSpecs = specRepository.findByStatus(SpecStatus.ACTIVE);

        if (!mappings.isEmpty()) {
            Set<String> allowedKeys = mappings.stream()
                    .map(m -> m.subdomainName() + ":" + m.environment())
                    .collect(Collectors.toSet());

            activeSpecs = activeSpecs.stream()
                    .filter(spec -> allowedKeys.contains(spec.getName() + ":" + spec.getEnvironment()))
                    .toList();
        }

        return specToolConverter.convertAll(activeSpecs);
    }

    private List<com.aitestforge.infra.ai.dto.ChatMessage> buildHistory(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(msg -> new com.aitestforge.infra.ai.dto.ChatMessage(
                        msg.getRole().name().toLowerCase(),
                        msg.getContent()
                ))
                .toList();
    }

    private void sendSseEvent(Long sessionId, String eventType, Map<String, String> data) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(data));
        } catch (IOException e) {
            log.warn("Failed to send SSE event for session {}: {}", sessionId, e.getMessage());
            emitters.remove(sessionId);
        }
    }

    private void completeEmitter(Long sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            emitter.complete();
        }
        loopStates.remove(sessionId);
    }

    private void cleanup(Long sessionId) {
        emitters.remove(sessionId);
        loopStates.remove(sessionId);
    }

    /**
     * Agent Loop 상태를 추적하는 레코드.
     */
    private record LoopState(Instant startedAt, int iterationCount) {
    }
}
