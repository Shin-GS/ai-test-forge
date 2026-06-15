package com.aitestforge.service.agent;

import com.aitestforge.domain.chat.ChatMessage;
import com.aitestforge.domain.chat.MessageRole;
import com.aitestforge.domain.spec.SpecStatus;
import com.aitestforge.domain.spec.SubdomainSpec;
import com.aitestforge.dto.chat.ToolResultRequest;
import com.aitestforge.infra.ai.AiService;
import com.aitestforge.infra.ai.dto.AiChatResponse;
import com.aitestforge.infra.ai.dto.ToolCall;
import com.aitestforge.infra.ai.dto.ToolDefinition;
import com.aitestforge.repository.ChatMessageRepository;
import com.aitestforge.repository.SubdomainSpecRepository;
import com.aitestforge.service.chat.ChatService;
import com.aitestforge.service.spec.SpecToolConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent Loop 오케스트레이션.
 *
 * 플로우:
 * 1. 사용자 메시지 수신 → AI 호출
 * 2. AI가 tool_call 반환 → SSE로 FE에 지시
 * 3. FE가 서브도메인 API 직접 호출 → 결과를 POST /tool-result로 전달
 * 4. tool-result 수신 → AI 재호출 → 반복
 * 5. AI가 텍스트만 반환 (tool call 없음) → done 이벤트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentLoopService {

    private final AiService aiService;
    private final ChatService chatService;
    private final ChatMessageRepository messageRepository;
    private final SubdomainSpecRepository specRepository;
    private final SpecToolConverter specToolConverter;

    // 세션별 SSE emitter 관리
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * SSE 연결 생성 (FE가 스트림을 구독할 때 호출).
     */
    public SseEmitter createEmitter(Long sessionId) {
        SseEmitter emitter = new SseEmitter(120_000L); // 120초 타임아웃
        emitters.put(sessionId, emitter);

        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> emitters.remove(sessionId));
        emitter.onError(e -> emitters.remove(sessionId));

        return emitter;
    }

    /**
     * 사용자 메시지를 받아 Agent Loop 첫 턴을 시작한다.
     */
    public void startLoop(Long sessionId, String userMessage) {
        chatService.addUserMessage(sessionId, userMessage);
        processNextTurn(sessionId);
    }

    /**
     * FE에서 tool-result를 수신한 후 다음 턴을 진행한다.
     */
    public void handleToolResult(Long sessionId, ToolResultRequest toolResult) {
        // tool 결과를 메시지로 저장
        chatService.addToolResultMessage(sessionId, toolResult.toolCallId(), toolResult.body());

        // FE에 tool_call_result 이벤트 전달 (진행 상태 표시용)
        sendSseEvent(sessionId, "tool_call_result", Map.of(
                "toolCallId", toolResult.toolCallId(),
                "statusCode", String.valueOf(toolResult.statusCode())
        ));

        // 다음 AI 턴 진행
        processNextTurn(sessionId);
    }

    private void processNextTurn(Long sessionId) {
        try {
            // 현재 대화 히스토리 구성
            List<com.aitestforge.infra.ai.dto.ChatMessage> history = buildHistory(sessionId);

            // 등록된 서브도메인 스펙에서 사용 가능한 tool 목록 구성
            List<SubdomainSpec> activeSpecs = specRepository.findByStatus(SpecStatus.ACTIVE);
            List<ToolDefinition> tools = specToolConverter.convertAll(activeSpecs);

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

    private void handleToolCalls(Long sessionId, AiChatResponse response) {
        // AI 응답을 assistant 메시지로 저장 (tool call 포함)
        String assistantContent = response.message() != null ? response.message() : "";
        chatService.addAssistantMessage(sessionId, assistantContent);

        // 각 tool call을 SSE로 FE에 전달
        for (ToolCall toolCall : response.toolCalls()) {
            sendSseEvent(sessionId, "tool_call_start", Map.of(
                    "toolCallId", toolCall.id(),
                    "name", toolCall.name(),
                    "arguments", toolCall.argumentsJson()
            ));
        }
        // FE가 tool-result를 POST하면 handleToolResult에서 다음 턴 진행
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
    }
}
