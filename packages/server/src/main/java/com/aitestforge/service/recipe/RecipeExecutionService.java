package com.aitestforge.service.recipe;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.recipe.Recipe;
import com.aitestforge.dto.recipe.ExecuteRecipeRequest;
import com.aitestforge.repository.RecipeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 레시피 실행 서비스.
 * AI Agent Loop를 거치지 않고 저장된 단계를 순차적으로 FE에 지시한다.
 * FE가 각 step의 API를 직접 호출하고, 결과를 다시 POST해주면 다음 step으로 진행.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeExecutionService {

    private static final Pattern INPUT_VAR_PATTERN = Pattern.compile("\\{\\{input:(.+?)\\}\\}");
    private static final Pattern REF_VAR_PATTERN = Pattern.compile("\\{\\{(.+?)\\}\\}");

    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
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

        // 결과를 stepOutputs에 저장 (다음 step에서 참조 가능)
        state.stepOutputs().put("Step" + state.currentStepIndex(), resultBody);

        sendSseEvent(sessionId, "tool_call_result", Map.of(
                "toolCallId", toolCallId,
                "statusCode", "200"
        ));

        // 다음 step으로 진행
        state.advanceStep();

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
        String api = step.path("api").asText(""); // "POST /api/members"
        String[] apiParts = api.split(" ", 2);
        String method = apiParts.length > 0 ? apiParts[0] : "GET";
        String path = apiParts.length > 1 ? apiParts[1] : "";

        // 파라미터 변수 치환
        JsonNode paramsNode = step.get("params");
        String resolvedParams = resolveVariables(paramsNode, state);

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
     * 변수를 치환한다:
     * - {{auto}} → 규칙 기반 자동 생성
     * - {{input:라벨}} → 사용자 입력값
     * - {{StepN.field}} → 이전 step 결과 참조
     */
    private String resolveVariables(JsonNode paramsNode, RecipeExecutionState state) {
        if (paramsNode == null) return "{}";

        try {
            String paramsStr = objectMapper.writeValueAsString(paramsNode);

            // {{input:라벨}} 치환
            Matcher inputMatcher = INPUT_VAR_PATTERN.matcher(paramsStr);
            StringBuilder sb = new StringBuilder();
            while (inputMatcher.find()) {
                String label = inputMatcher.group(1);
                String value = state.userVariables().getOrDefault(label, "");
                inputMatcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
            inputMatcher.appendTail(sb);
            paramsStr = sb.toString();

            // {{auto}} 치환
            paramsStr = paramsStr.replace("{{auto}}", generateAutoValue());

            // {{StepN.field}} 등 참조 변수 치환
            Matcher refMatcher = REF_VAR_PATTERN.matcher(paramsStr);
            sb = new StringBuilder();
            while (refMatcher.find()) {
                String ref = refMatcher.group(1);
                String value = resolveReference(ref, state);
                refMatcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
            refMatcher.appendTail(sb);

            return sb.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private String generateAutoValue() {
        // 간단한 랜덤 값 생성 (실제로는 필드 타입에 따라 다르게 생성 가능)
        return "auto_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String resolveReference(String ref, RecipeExecutionState state) {
        // "StepN.field" 또는 이전 step output에서 값 추출
        // 현재는 저장된 step output 전체를 반환 (JSON 파싱은 추후 고도화)
        for (Map.Entry<String, String> entry : state.stepOutputs().entrySet()) {
            if (ref.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return ref;
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
