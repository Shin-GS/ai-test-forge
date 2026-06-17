package com.aitestforge.service.agent;

import com.aitestforge.domain.chat.ChatSession;
import com.aitestforge.domain.spec.enums.SpecStatus;
import com.aitestforge.domain.spec.SubdomainSpec;
import com.aitestforge.dto.chat.request.ToolResultRequest;
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
import com.aitestforge.service.spec.SpecControlFilter;
import com.aitestforge.service.workspace.WorkspaceService;
import com.aitestforge.service.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final SpecControlFilter specControlFilter;
    private final WorkspaceService workspaceService;
    private final TwoStageFilterService twoStageFilterService;
    private final SettingsService settingsService;

    @Value("${agent-loop.max-iterations:20}")
    private int maxIterations;

    @Value("${agent-loop.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${agent-loop.max-tool-calls-per-turn:5}")
    private int maxToolCallsPerTurn;

    @Value("${agent-loop.max-concurrent:10}")
    private int maxConcurrent;

    // 전체 동시 Agent Loop 실행 수 추적
    private final AtomicInteger activeLoopCount = new AtomicInteger(0);

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
     * 동시 실행 한도 초과 시 에러 SSE 이벤트를 전송하고 즉시 종료한다.
     */
    public void startLoop(Long sessionId, String userMessage) {
        // 동시 실행 수 검사 (CAS 기반 원자적 증가)
        while (true) {
            int current = activeLoopCount.get();
            if (current >= maxConcurrent) {
                String msg = String.format(
                        "현재 동시 실행 한도(%d)를 초과하였습니다. 잠시 후 다시 시도해 주세요.", maxConcurrent);
                log.warn("Agent loop concurrent limit exceeded: {}/{}", current, maxConcurrent);
                sendSseEvent(sessionId, "error", Map.of("message", msg, "code", "E004"));
                sendSseEvent(sessionId, "done", Map.of());
                completeEmitter(sessionId);
                return;
            }
            if (activeLoopCount.compareAndSet(current, current + 1)) {
                break;
            }
        }

        try {
            chatService.addUserMessage(sessionId, userMessage);
            // 새 루프 시작 — 상태 초기화 (2-Stage 필터용 userMessage 보존)
            loopStates.put(sessionId, new LoopState(Instant.now(), 0, userMessage));
            processNextTurn(sessionId);
        } catch (Exception e) {
            log.error("Agent loop start failed for session {}: {}", sessionId, e.getMessage(), e);
            activeLoopCount.decrementAndGet();
            sendSseEvent(sessionId, "error", Map.of("message", "Agent Loop 시작 중 오류가 발생했습니다."));
            sendSseEvent(sessionId, "done", Map.of());
            completeEmitter(sessionId);
        }
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

        // 반복 실패 감지 (동일 tool call이 3회 이상 실패 시 중단)
        LoopState state = loopStates.get(sessionId);
        if (state != null && state.recordFailure(toolResult.toolCallId(), toolResult.statusCode())) {
            String msg = "동일 API 호출이 반복 실패하여 Agent Loop를 중단합니다.";
            log.warn("Repeated failure detected for session {}, toolCall {}", sessionId, toolResult.toolCallId());
            chatService.addAssistantMessage(sessionId, msg);
            sendSseEvent(sessionId, "message", Map.of("content", msg));
            sendSseEvent(sessionId, "error", Map.of("message", msg));
            sendSseEvent(sessionId, "done", Map.of());
            completeEmitter(sessionId);
            return;
        }

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
                state.incrementIteration();
            }

            // 현재 대화 히스토리 구성
            List<com.aitestforge.infra.ai.dto.ChatMessage> history = buildHistory(sessionId);

            // 워크스페이스 기반 tool 목록 필터링
            List<ToolDefinition> tools = buildToolsForSession(sessionId);

            // 2-Stage 필터: 첫 번째 턴에서만 적용 (이후 턴은 이미 필터된 상태로 진행)
            if (state != null && state.iterationCount() == 1 && state.userMessage() != null) {
                tools = twoStageFilterService.filterTools(tools, state.userMessage());
                state.setFilteredTools(tools);
            } else if (state != null && state.filteredTools() != null) {
                // 이후 턴에서는 첫 턴에서 필터된 tool 목록을 재사용
                tools = state.filteredTools();
            }

            // AI 호출
            AiChatResponse response = aiService.chat(history, tools);

            // tool call이 있으면 FE에 지시
            if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
                handleToolCalls(sessionId, response);
                return;
            }

            // 텍스트 응답만 있으면 → tool call 이력에 따라 COMPLETED 또는 WAITING 판정
            if (response.message() != null && !response.message().isBlank()) {
                chatService.addAssistantMessage(sessionId, response.message());
                sendSseEvent(sessionId, "message", Map.of("content", response.message()));

                if (state != null && state.hasExecutedToolCall()) {
                    // tool call을 수행한 적 있으면 → 작업 완료 보고
                    // 다음 액션 힌트 생성 (설정 활성화 시)
                    generateNextActionHint(sessionId, response.message());
                    sendSseEvent(sessionId, "done", Map.of());
                    chatService.completeSession(sessionId);
                    completeEmitter(sessionId);
                } else {
                    // tool call 없이 텍스트만 보냈으면 → 추가 정보 요청 (WAITING)
                    sendSseEvent(sessionId, "done", Map.of());
                    chatService.waitSession(sessionId);
                    completeEmitter(sessionId);
                }
            } else {
                log.warn("Empty AI response for session {}", sessionId);
                sendSseEvent(sessionId, "error", Map.of("message", "AI로부터 유효한 응답을 받지 못했습니다."));
                sendSseEvent(sessionId, "done", Map.of());
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

        // tool call을 수행했음을 기록
        LoopState state = loopStates.get(sessionId);
        if (state != null) {
            state.markToolCallExecuted();
        }

        List<ToolCall> toolCalls = response.toolCalls();
        if (toolCalls.size() > maxToolCallsPerTurn) {
            log.warn("Tool calls exceeded limit for session {}: {} > {}, truncating",
                    sessionId, toolCalls.size(), maxToolCallsPerTurn);
            toolCalls = toolCalls.subList(0, maxToolCallsPerTurn);
        }

        for (ToolCall toolCall : toolCalls) {
            Map<String, String> parsed = parseToolName(toolCall.name());

            // 해당 tool의 control 메타데이터 조회 (block/confirm/readonly 정보)
            LoopState loopState = loopStates.get(sessionId);
            String controlJson = "{}";
            if (loopState != null && loopState.filteredTools() != null) {
                controlJson = loopState.filteredTools().stream()
                        .filter(t -> t.name().equals(toolCall.name()))
                        .findFirst()
                        .map(t -> {
                            try {
                                var om = new com.fasterxml.jackson.databind.ObjectMapper();
                                return om.writeValueAsString(t.control());
                            } catch (Exception e) {
                                return "{}";
                            }
                        })
                        .orElse("{}");
            }

            sendSseEvent(sessionId, "tool_call_start", Map.of(
                    "toolCallId", toolCall.id(),
                    "name", toolCall.name(),
                    "subdomain", parsed.get("subdomain"),
                    "method", parsed.get("method"),
                    "path", parsed.get("path"),
                    "arguments", toolCall.argumentsJson(),
                    "control", controlJson
            ));
        }
    }

    /**
     * tool name을 파싱하여 subdomain, method, path를 분리한다.
     * 포맷: {subdomain}__{METHOD}__{sanitized_path}
     * 예: user-service__POST__api_members → subdomain=user-service, method=POST, path=/api/members
     */
    private Map<String, String> parseToolName(String toolName) {
        String[] parts = toolName.split("__", 3);
        if (parts.length < 3) {
            return Map.of("subdomain", "", "method", "", "path", "");
        }
        String subdomain = parts[0];
        String method = parts[1];
        // path 복원: api_members → /api/members
        String path = "/" + parts[2].replace("_", "/");
        return Map.of("subdomain", subdomain, "method", method, "path", path);
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

        List<ToolDefinition> tools = specToolConverter.convertAll(activeSpecs);
        return specControlFilter.applyGlobalExclude(tools);
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
        if (loopStates.remove(sessionId) != null) {
            activeLoopCount.decrementAndGet();
        }
    }

    /**
     * 다음 액션 힌트 생성.
     * nextActionHintEnabled 설정이 true이면 AI에게 추천 액션을 요청하고 SSE로 전달.
     */
    private void generateNextActionHint(Long sessionId, String completionMessage) {
        if (!settingsService.getSettings().nextActionHintEnabled()) {
            return;
        }

        try {
            String prompt = "사용자가 방금 완료한 작업 결과입니다: " + completionMessage +
                    "\n\n이 결과를 기반으로 사용자가 다음으로 할 수 있는 작업 2~3개를 짧게 제안해주세요. " +
                    "각 제안은 한 줄로, 실행 가능한 문장 형태로 작성하세요.";

            List<com.aitestforge.infra.ai.dto.ChatMessage> hintMessages = List.of(
                    new com.aitestforge.infra.ai.dto.ChatMessage("user", prompt)
            );

            AiChatResponse hintResponse = aiService.chat(hintMessages, List.of());

            if (hintResponse.message() != null && !hintResponse.message().isBlank()) {
                sendSseEvent(sessionId, "next_action_hint", Map.of("content", hintResponse.message()));
            }
        } catch (Exception e) {
            log.warn("Failed to generate next action hint for session {}: {}", sessionId, e.getMessage());
            // 힌트 생성 실패는 무시 — 핵심 플로우에 영향 없음
        }
    }

    private void cleanup(Long sessionId) {
        emitters.remove(sessionId);
        if (loopStates.remove(sessionId) != null) {
            activeLoopCount.decrementAndGet();
        }
    }

    /**
     * Agent Loop 상태를 추적하는 내부 클래스.
     */
    private static class LoopState {
        private final Instant startedAt;
        private int iterationCount;
        private boolean executedToolCall;
        private final String userMessage;
        private List<ToolDefinition> filteredTools;
        private final Map<String, Integer> failureTracker = new HashMap<>();

        LoopState(Instant startedAt, int iterationCount, String userMessage) {
            this.startedAt = startedAt;
            this.iterationCount = iterationCount;
            this.userMessage = userMessage;
            this.executedToolCall = false;
        }

        Instant startedAt() { return startedAt; }
        int iterationCount() { return iterationCount; }
        void incrementIteration() { this.iterationCount++; }

        String userMessage() { return userMessage; }

        List<ToolDefinition> filteredTools() { return filteredTools; }
        void setFilteredTools(List<ToolDefinition> tools) { this.filteredTools = tools; }

        void markToolCallExecuted() { this.executedToolCall = true; }
        boolean hasExecutedToolCall() { return this.executedToolCall; }

        /**
         * 실패를 기록하고, 동일 키로 3회 이상 실패 시 true 반환.
         */
        boolean recordFailure(String toolCallId, int statusCode) {
            if (statusCode >= 400) {
                int count = failureTracker.merge(toolCallId, 1, Integer::sum);
                return count >= 3;
            }
            return false;
        }
    }
}
