package com.aitestforge.service.recipe;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.recipe.Recipe;
import com.aitestforge.dto.recipe.request.ExecuteRecipeRequest;
import com.aitestforge.repository.RecipeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 레시피 실행 서비스.
 * AI Agent Loop를 거치지 않고 저장된 단계를 순차적으로 FE에 지시한다.
 * FE가 각 step의 API를 직접 호출하고, 결과를 다시 POST해주면 다음 step으로 진행.
 *
 * AI-Assisted 모드: bodyStrategy에 따라 AI가 body를 생성/채울 수 있다.
 * selectStrategy가 AI_PICK이면 GET 결과에서 AI가 항목을 선택한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeExecutionService {

    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final RecipeVariableResolver recipeVariableResolver;
    private final AiBodyGenerator aiBodyGenerator;
    private final ObjectMapper objectMapper;

    // 세션별 실행 상태 관리
    private final Map<Long, RecipeExecutionState> executionStates = new ConcurrentHashMap<>();
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 레시피 실행을 시작한다. SSE emitter를 생성하고 첫 번째 step을 FE에 전달.
     */
    public SseEmitter startExecution(Long recipeId, Long sessionId, ExecuteRecipeRequest request) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        List<JsonNode> steps = parseSteps(recipe.getStepsJson());
        if (steps.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 실행 상태 초기화
        RecipeExecutionState state = new RecipeExecutionState(
                recipeId, sessionId, steps, request.variables(), new HashMap<>()
        );
        executionStates.put(sessionId, state);

        // SSE emitter 생성
        SseEmitter emitter = new SseEmitter(120_000L);
        emitters.put(sessionId, emitter);
        emitter.onCompletion(() -> cleanup(sessionId));
        emitter.onTimeout(() -> cleanup(sessionId));
        emitter.onError(e -> cleanup(sessionId));

        // 사용 횟수 증가
        recipeService.incrementUsage(recipeId);

        // 첫 번째 step 전달
        sendNextStep(sessionId);

        return emitter;
    }

    /**
     * FE에서 step 실행 결과를 수신하면 다음 step으로 진행.
     */
    public void handleStepResult(Long sessionId, String toolCallId, String resultBody) {
        RecipeExecutionState state = executionStates.get(sessionId);
        if (state == null) return;

        JsonNode currentStep = state.currentStep();

        // 결과를 stepOutputs에 저장 (다음 step에서 참조 가능)
        state.stepOutputs().put("Step" + state.currentStepIndex(), resultBody);

        // selectStrategy == AI_PICK 처리: GET 결과에서 AI가 항목 선택
        SelectStrategy selectStrategy = parseSelectStrategy(currentStep);
        if (selectStrategy == SelectStrategy.AI_PICK) {
            handleAiPick(state, currentStep, resultBody);
        }

        // step에 extract 정의가 있으면 JSONPath로 변수 추출하여 stepOutputs에 merge
        JsonNode extractNode = currentStep.get("extract");
        if (extractNode != null && extractNode.isObject()) {
            Map<String, String> extracts = new LinkedHashMap<>();
            extractNode.properties().forEach(field ->
                    extracts.put(field.getKey(), field.getValue().asText())
            );
            Map<String, String> extracted = recipeVariableResolver.extractVariables(resultBody, extracts);
            state.stepOutputs().putAll(extracted);
        }

        sendSseEvent(sessionId, "tool_call_result", Map.of(
                "toolCallId", toolCallId,
                "statusCode", "200"
        ));

        // 다음 step으로 진행
        state.advanceStep();

        // step_progress 이벤트 전송
        sendStepProgress(sessionId, state, "completed");

        if (state.isCompleted()) {
            // 모든 step 완료
            sendSseEvent(sessionId, "done", Map.of());
            completeEmitter(sessionId);
        } else {
            sendNextStep(sessionId);
        }
    }

    private void sendNextStep(Long sessionId) {
        RecipeExecutionState state = executionStates.get(sessionId);
        if (state == null) return;

        JsonNode step = state.currentStep();
        String subdomain = step.path("subdomain").asText("");
        String environment = step.path("environment").asText("default");
        // design.md 기준: method, path 분리 필드 사용. 하위 호환으로 "api" 필드도 지원.
        String method = step.path("method").asText("");
        String path = step.path("path").asText("");
        if (method.isEmpty() || path.isEmpty()) {
            // 하위 호환: "api" 필드 ("POST /api/members" 형태)
            String api = step.path("api").asText("");
            String[] apiParts = api.split(" ", 2);
            method = apiParts.length > 0 ? apiParts[0] : "GET";
            path = apiParts.length > 1 ? apiParts[1] : "";
        }

        // step_progress 이벤트 전송
        sendStepProgress(sessionId, state, "running");

        // bodyStrategy에 따라 분기
        BodyStrategy bodyStrategy = parseBodyStrategy(step);
        String resolvedParams;

        try {
            resolvedParams = resolveBodyByStrategy(bodyStrategy, step, subdomain, environment, method, path, state);
        } catch (AiBodyGenerationException e) {
            log.error("AI body generation failed for step {}: {}", state.currentStepIndex(), e.getMessage());
            sendStepProgress(sessionId, state, "failed");
            sendSseEvent(sessionId, "error", Map.of(
                    "stepIndex", String.valueOf(state.currentStepIndex()),
                    "message", "AI body generation failed: " + e.getMessage()
            ));
            completeEmitter(sessionId);
            return;
        }

        String toolCallId = UUID.randomUUID().toString();

        sendSseEvent(sessionId, "tool_call_start", Map.of(
                "toolCallId", toolCallId,
                "name", subdomain + "_" + method + "_" + path,
                "subdomain", subdomain,
                "method", method,
                "path", path,
                "arguments", resolvedParams
        ));
    }

    /**
     * bodyStrategy에 따라 적절한 body 생성 로직을 실행한다.
     */
    private String resolveBodyByStrategy(BodyStrategy strategy, JsonNode step,
                                         String subdomain, String environment,
                                         String method, String path,
                                         RecipeExecutionState state) {
        // context 구성: userVariables + stepOutputs (extract 결과 포함)
        Map<String, String> context = buildContext(state);
        String aiHint = step.path("aiHint").asText(null);

        return switch (strategy) {
            case FIXED, GEN -> resolveFixed(step, context);
            case AI_GENERATE -> resolveAiGenerate(step, subdomain, environment, method, path, aiHint, context);
            case AI_FILL -> resolveAiFill(step, subdomain, environment, method, path, aiHint, context);
        };
    }

    /**
     * FIXED/GEN: 기존 로직 — RecipeVariableResolver로 변수 치환.
     */
    private String resolveFixed(JsonNode step, Map<String, String> context) {
        // design.md 기준: "body" 필드 사용. 하위 호환으로 "params"도 지원.
        JsonNode bodyNode = step.get("body");
        if (bodyNode == null) {
            bodyNode = step.get("params");
        }
        String bodyStr = "{}";
        if (bodyNode != null) {
            try {
                bodyStr = objectMapper.writeValueAsString(bodyNode);
            } catch (Exception e) {
                bodyStr = "{}";
            }
        }
        return recipeVariableResolver.resolveBody(bodyStr, context);
    }

    /**
     * AI_GENERATE: AI가 스키마 기반으로 전체 body를 생성하고, 고정 필드와 merge.
     */
    private String resolveAiGenerate(JsonNode step, String subdomain, String environment,
                                     String method, String path, String aiHint,
                                     Map<String, String> context) {
        // step의 params에서 고정 필드 추출 (변수 치환 후)
        Map<String, String> fixedFields = extractFixedFields(step, context);

        return aiBodyGenerator.generateBody(subdomain, environment, method, path, aiHint, fixedFields);
    }

    /**
     * AI_FILL: 1차 치환 후 null/{{auto}} 필드만 AI가 채움.
     */
    private String resolveAiFill(JsonNode step, String subdomain, String environment,
                                 String method, String path, String aiHint,
                                 Map<String, String> context) {
        // 1차: recipeVariableResolver로 치환 (gen/변수참조 처리)
        JsonNode bodyNode = step.get("body");
        if (bodyNode == null) {
            bodyNode = step.get("params");
        }
        String bodyStr = "{}";
        if (bodyNode != null) {
            try {
                bodyStr = objectMapper.writeValueAsString(bodyNode);
            } catch (Exception e) {
                bodyStr = "{}";
            }
        }

        // {{auto}}는 치환 대상이 아니므로 그대로 남음
        String partiallyResolved = resolveBodySkippingAuto(bodyStr, context);

        // 2차: AI가 null/{{auto}} 필드를 채움
        return aiBodyGenerator.fillBody(partiallyResolved, subdomain, environment, method, path, aiHint);
    }

    /**
     * {{auto}} 패턴을 건너뛰고 나머지만 치환한다.
     * {{auto}}는 AI_FILL에서 AI가 채울 placeholder이므로 유지해야 한다.
     */
    private String resolveBodySkippingAuto(String bodyJson, Map<String, String> context) {
        // auto를 임시 context에 등록하여 치환 시 그대로 남기도록 처리
        Map<String, String> contextWithAuto = new HashMap<>(context);
        contextWithAuto.put("auto", "{{auto}}");

        try {
            return recipeVariableResolver.resolveBody(bodyJson, contextWithAuto);
        } catch (Exception e) {
            // auto 참조를 실패하면 그대로 반환
            log.debug("resolveBodySkippingAuto partial failure, returning raw: {}", e.getMessage());
            return bodyJson;
        }
    }

    /**
     * step의 body에서 고정 필드를 추출한다.
     * {{변수}} 패턴이 아닌 순수 값 + 변수 치환이 가능한 값을 추출.
     */
    private Map<String, String> extractFixedFields(JsonNode step, Map<String, String> context) {
        Map<String, String> fixedFields = new LinkedHashMap<>();
        JsonNode bodyNode = step.get("body");
        if (bodyNode == null) {
            bodyNode = step.get("params");
        }
        if (bodyNode == null || !bodyNode.isObject()) return fixedFields;

        Iterator<Map.Entry<String, JsonNode>> fields = bodyNode.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String value = entry.getValue().asText("");

            // {{auto}}이거나 비어있으면 AI에게 위임 → 고정 필드에서 제외
            if ("{{auto}}".equals(value) || value.isBlank()) continue;

            // 변수 참조인 경우 치환 시도
            if (value.contains("{{") && value.contains("}}")) {
                try {
                    String resolved = recipeVariableResolver.resolveBody(
                            "\"" + value + "\"", context);
                    // 따옴표 제거
                    resolved = resolved.replaceAll("^\"|\"$", "");
                    fixedFields.put(entry.getKey(), resolved);
                } catch (Exception e) {
                    // 치환 실패 시 스킵 (AI가 생성하도록)
                    log.debug("Variable resolution failed for field '{}': {}", entry.getKey(), e.getMessage());
                }
            } else {
                fixedFields.put(entry.getKey(), value);
            }
        }
        return fixedFields;
    }

    /**
     * AI_PICK 처리: GET 결과에서 AI가 aiHint 조건에 맞는 항목을 선택하여 extract 변수에 저장.
     */
    private void handleAiPick(RecipeExecutionState state, JsonNode step, String resultBody) {
        String aiHint = step.path("aiHint").asText(null);
        String picked = aiBodyGenerator.pickFromList(resultBody, aiHint);

        if (picked != null) {
            // 선택된 항목을 "aiPicked" 변수로 저장 (다음 step에서 참조 가능)
            state.stepOutputs().put("aiPicked", picked);

            // 선택된 항목에서 id 필드가 있으면 추가 저장
            try {
                JsonNode pickedNode = objectMapper.readTree(picked);
                if (pickedNode.has("id")) {
                    state.stepOutputs().put("aiPickedId", pickedNode.get("id").asText());
                }
            } catch (Exception e) {
                log.debug("Could not extract id from AI picked item: {}", e.getMessage());
            }
        }
    }

    private Map<String, String> buildContext(RecipeExecutionState state) {
        Map<String, String> context = new HashMap<>(state.stepOutputs());
        if (state.userVariables() != null) {
            context.putAll(state.userVariables());
        }
        return context;
    }

    private BodyStrategy parseBodyStrategy(JsonNode step) {
        String value = step.path("bodyStrategy").asText(null);
        if (value == null || value.isBlank()) return BodyStrategy.FIXED;
        try {
            return BodyStrategy.valueOf(value.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown bodyStrategy '{}', falling back to FIXED", value);
            return BodyStrategy.FIXED;
        }
    }

    private SelectStrategy parseSelectStrategy(JsonNode step) {
        String value = step.path("selectStrategy").asText(null);
        if (value == null || value.isBlank()) return null;
        try {
            return SelectStrategy.valueOf(value.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown selectStrategy '{}', ignoring", value);
            return null;
        }
    }

    private void sendStepProgress(Long sessionId, RecipeExecutionState state, String status) {
        sendSseEvent(sessionId, "step_progress", Map.of(
                "stepIndex", String.valueOf(state.currentStepIndex()),
                "totalSteps", String.valueOf(state.steps().size()),
                "status", status
        ));
    }

    private List<JsonNode> parseSteps(String stepsJson) {
        try {
            JsonNode node = objectMapper.readTree(stepsJson);
            if (node.isArray()) {
                List<JsonNode> steps = new ArrayList<>();
                node.forEach(steps::add);
                return steps;
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private void sendSseEvent(Long sessionId, String eventType, Map<String, String> data) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event().name(eventType).data(data));
        } catch (IOException e) {
            log.warn("Failed to send recipe SSE event: {}", e.getMessage());
            cleanup(sessionId);
        }
    }

    private void completeEmitter(Long sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            emitter.complete();
        }
        executionStates.remove(sessionId);
    }

    private void cleanup(Long sessionId) {
        emitters.remove(sessionId);
        executionStates.remove(sessionId);
    }

    /**
     * 레시피 실행 상태를 추적하는 내부 레코드.
     */
    private record RecipeExecutionState(
            Long recipeId,
            Long sessionId,
            List<JsonNode> steps,
            Map<String, String> userVariables,
            Map<String, String> stepOutputs
    ) {
        private static final Map<Long, Integer> currentIndexes = new ConcurrentHashMap<>();

        int currentStepIndex() {
            return currentIndexes.getOrDefault(sessionId, 0);
        }

        JsonNode currentStep() {
            return steps.get(currentStepIndex());
        }

        void advanceStep() {
            currentIndexes.put(sessionId, currentStepIndex() + 1);
        }

        boolean isCompleted() {
            return currentStepIndex() >= steps.size();
        }
    }
}
