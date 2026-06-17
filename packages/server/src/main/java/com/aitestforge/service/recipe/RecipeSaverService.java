package com.aitestforge.service.recipe;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.chat.ChatMessage;
import com.aitestforge.domain.chat.ChatSession;
import com.aitestforge.domain.chat.enums.MessageRole;
import com.aitestforge.domain.recipe.Recipe;
import com.aitestforge.domain.recipe.enums.RecipeVisibility;
import com.aitestforge.dto.recipe.request.GenerateRecipeFromSessionRequest;
import com.aitestforge.dto.recipe.response.RecipeResponse;
import com.aitestforge.repository.ChatSessionRepository;
import com.aitestforge.repository.RecipeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 채팅 세션의 tool_call 이력에서 레시피를 자동 생성하는 서비스.
 * "방금 한 거 레시피로 저장해줘" 요청을 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeSaverService {

    private final ChatSessionRepository chatSessionRepository;
    private final RecipeRepository recipeRepository;
    private final ObjectMapper objectMapper;

    // 변수 타입 감지용 패턴
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\d{2,4}-\\d{3,4}-\\d{4}$");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * 세션의 tool_call 이력에서 레시피를 생성한다.
     *
     * @param request 생성 요청 (sessionId, stepRange, name, description, tags)
     * @param userId  레시피 owner
     * @return 생성된 레시피 응답
     */
    @Transactional
    public RecipeResponse generateFromSession(GenerateRecipeFromSessionRequest request, Long userId) {
        // 1. ChatSession 조회
        ChatSession session = chatSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 2. tool_call 정보 추출 (ASSISTANT 메시지에서 tool call, TOOL 메시지에서 결과)
        List<ToolCallPair> toolCallPairs = extractToolCallPairs(session.getMessages());

        // 3. stepRange 적용
        if (request.stepRange() != null && request.stepRange().length == 2) {
            int start = request.stepRange()[0];
            int end = Math.min(request.stepRange()[1], toolCallPairs.size() - 1);
            if (start >= 0 && start <= end && start < toolCallPairs.size()) {
                toolCallPairs = toolCallPairs.subList(start, end + 1);
            }
        }

        // 4. 이력 최소 검증
        if (toolCallPairs.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        log.info("Generating recipe from session {} with {} tool call(s)", request.sessionId(), toolCallPairs.size());

        // 5~8. step 구조 생성 + 참조 분석 + 변수 치환
        List<StepData> steps = buildSteps(toolCallPairs);

        // 9. Recipe 엔티티 생성 및 저장
        String stepsJson = serializeSteps(steps);
        String variablesJson = serializeVariables(steps);

        Recipe recipe = Recipe.builder()
                .userId(userId)
                .name(request.name())
                .description(request.description())
                .tags(request.tags() != null ? request.tags() : List.of())
                .stepsJson(stepsJson)
                .visibility(RecipeVisibility.PUBLIC)
                .variablesJson(variablesJson)
                .build();

        recipeRepository.save(recipe);
        log.info("Recipe created from session: {} (steps: {})", recipe.getName(), steps.size());

        return RecipeResponse.from(recipe);
    }

    // ──────────────────────────────────────────────────────────────
    // 내부 로직
    // ──────────────────────────────────────────────────────────────

    /**
     * 세션 메시지에서 ASSISTANT tool_call과 대응하는 TOOL 결과를 매칭하여 추출.
     */
    private List<ToolCallPair> extractToolCallPairs(List<ChatMessage> messages) {
        // ASSISTANT 메시지에서 tool_call 정보 파싱
        // TOOL 메시지에서 toolCallId로 결과를 매칭
        List<ToolCallPair> pairs = new ArrayList<>();

        // toolCallId → TOOL 결과 메시지 매핑
        Map<String, ChatMessage> toolResultMap = new HashMap<>();
        for (ChatMessage msg : messages) {
            if (msg.getRole() == MessageRole.TOOL && msg.getToolCallId() != null) {
                toolResultMap.put(msg.getToolCallId(), msg);
            }
        }

        // ASSISTANT 메시지에서 tool_call 추출
        for (ChatMessage msg : messages) {
            if (msg.getRole() == MessageRole.ASSISTANT && msg.getContent() != null) {
                List<ParsedToolCall> toolCalls = parseToolCallsFromAssistant(msg.getContent());
                for (ParsedToolCall tc : toolCalls) {
                    ChatMessage resultMsg = toolResultMap.get(tc.id());
                    String resultContent = resultMsg != null ? resultMsg.getContent() : null;
                    pairs.add(new ToolCallPair(tc, resultContent));
                }
            }
        }

        return pairs;
    }

    /**
     * ASSISTANT 메시지 content에서 tool_call 정보를 파싱한다.
     * content가 JSON 형식이면 toolCalls 배열을 파싱.
     */
    private List<ParsedToolCall> parseToolCallsFromAssistant(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<ParsedToolCall> result = new ArrayList<>();

        try {
            JsonNode node = objectMapper.readTree(content);

            // 1) { "toolCalls": [...] } 형태
            if (node.has("toolCalls") && node.get("toolCalls").isArray()) {
                for (JsonNode tc : node.get("toolCalls")) {
                    ParsedToolCall parsed = parseToolCallNode(tc);
                    if (parsed != null) result.add(parsed);
                }
                return result;
            }

            // 2) { "tool_calls": [...] } 형태 (OpenAI 응답 구조)
            if (node.has("tool_calls") && node.get("tool_calls").isArray()) {
                for (JsonNode tc : node.get("tool_calls")) {
                    ParsedToolCall parsed = parseToolCallNode(tc);
                    if (parsed != null) result.add(parsed);
                }
                return result;
            }

            // 3) 단일 tool_call 객체 형태
            if (node.has("id") && (node.has("function") || node.has("name"))) {
                ParsedToolCall parsed = parseToolCallNode(node);
                if (parsed != null) result.add(parsed);
                return result;
            }
        } catch (JsonProcessingException e) {
            // JSON이 아닌 일반 텍스트 → tool_call 없음
            log.debug("Assistant content is not JSON, skipping tool call parse");
        }

        return result;
    }

    /**
     * 단일 tool_call JSON 노드를 ParsedToolCall로 변환.
     */
    private ParsedToolCall parseToolCallNode(JsonNode tc) {
        String id = getTextOrNull(tc, "id");
        String functionName;
        String arguments;

        // OpenAI 형식: { "id": "...", "function": { "name": "...", "arguments": "..." } }
        if (tc.has("function")) {
            JsonNode fn = tc.get("function");
            functionName = getTextOrNull(fn, "name");
            arguments = getTextOrNull(fn, "arguments");
        }
        // 단순 형식: { "id": "...", "name": "...", "arguments": "..." }
        else {
            functionName = getTextOrNull(tc, "name");
            arguments = getTextOrNull(tc, "arguments");
        }

        if (functionName == null || id == null) {
            return null;
        }

        return new ParsedToolCall(id, functionName, arguments);
    }

    /**
     * tool_call 쌍 목록에서 레시피 step을 생성하고 참조 분석을 수행한다.
     */
    private List<StepData> buildSteps(List<ToolCallPair> pairs) {
        List<StepData> steps = new ArrayList<>();
        // 이전 step 결과 값 → 변수명 매핑 (참조 분석용)
        Map<String, String> valueToVariableMap = new LinkedHashMap<>();

        for (int i = 0; i < pairs.size(); i++) {
            ToolCallPair pair = pairs.get(i);
            ParsedToolCall tc = pair.toolCall();

            // function name 분해: {subdomain}__{METHOD}__{path}
            FunctionNameParts parts = parseFunctionName(tc.name());

            // arguments 파싱
            Map<String, Object> bodyMap = parseArguments(tc.arguments());

            // 참조 분석: 이전 step 결과 값이 현재 body에 있는지 확인
            Map<String, String> extractMappings = new LinkedHashMap<>();
            Map<String, Object> resolvedBody = resolveBodyReferences(bodyMap, valueToVariableMap, extractMappings);

            // 변수 타입 결정 (gen 패턴 치환)
            Map<String, Object> templatedBody = applyGeneratorPatterns(resolvedBody);

            // bodyStrategy 결정
            String bodyStrategy = determineBodyStrategy(templatedBody);

            // 현재 step 결과에서 top-level 값을 변수로 등록 (다음 step 참조용)
            if (pair.resultContent() != null) {
                registerResultVariables(pair.resultContent(), i, valueToVariableMap);
            }

            steps.add(new StepData(
                    parts.subdomain(),
                    parts.method(),
                    parts.path(),
                    templatedBody,
                    bodyStrategy,
                    extractMappings,
                    pair.resultContent()
            ));
        }

        return steps;
    }

    /**
     * function name을 subdomain, method, path로 분해한다.
     * 형식: {subdomain}__{METHOD}__{path_with_underscores}
     * 예: user-service__POST__api_members → subdomain=user-service, method=POST, path=/api/members
     */
    private FunctionNameParts parseFunctionName(String functionName) {
        String[] segments = functionName.split("__", 3);

        if (segments.length == 3) {
            String subdomain = segments[0];
            String method = segments[1].toUpperCase();
            String path = "/" + segments[2].replace("_", "/");
            return new FunctionNameParts(subdomain, method, path);
        }

        // 분해 실패 시 기본값
        log.warn("Failed to parse function name: {}", functionName);
        return new FunctionNameParts("unknown", "POST", "/" + functionName);
    }

    /**
     * arguments JSON 문자열을 Map으로 파싱.
     */
    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new LinkedHashMap<>();
        }

        try {
            return objectMapper.readValue(arguments, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse tool call arguments: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * body에서 이전 step 결과값을 참조하는 필드를 찾아 {{변수명}}으로 치환한다.
     */
    private Map<String, Object> resolveBodyReferences(
            Map<String, Object> body,
            Map<String, String> valueToVariableMap,
            Map<String, String> extractMappings) {

        Map<String, Object> resolved = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String strVal && valueToVariableMap.containsKey(strVal)) {
                String variableName = valueToVariableMap.get(strVal);
                resolved.put(key, "{{" + variableName + "}}");
                // extract 매핑 기록 (변수명 → JSONPath)
                extractMappings.putIfAbsent(variableName, "$." + extractFieldNameFromVariable(variableName));
            } else if (value instanceof Number numVal && valueToVariableMap.containsKey(numVal.toString())) {
                String variableName = valueToVariableMap.get(numVal.toString());
                resolved.put(key, "{{" + variableName + "}}");
                extractMappings.putIfAbsent(variableName, "$." + extractFieldNameFromVariable(variableName));
            } else {
                resolved.put(key, value);
            }
        }

        return resolved;
    }

    /**
     * 리터럴 값에서 gen 패턴을 감지하여 {{gen:*}} 형식으로 치환한다.
     */
    private Map<String, Object> applyGeneratorPatterns(Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String strVal) {
                // 이미 {{...}} 참조인 경우 그대로 유지
                if (strVal.startsWith("{{") && strVal.endsWith("}}")) {
                    result.put(key, strVal);
                } else if (EMAIL_PATTERN.matcher(strVal).matches()) {
                    result.put(key, "{{gen:email}}");
                } else if (PHONE_PATTERN.matcher(strVal).matches()) {
                    result.put(key, "{{gen:phone}}");
                } else if (UUID_PATTERN.matcher(strVal).matches()) {
                    result.put(key, "{{gen:uuid}}");
                } else {
                    result.put(key, strVal);
                }
            } else {
                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * bodyStrategy 결정:
     * - 필드 3개 이하 & 모두 리터럴 → FIXED
     * - 그 외 → FIXED (향후 ai-fill 추천 가능)
     */
    private String determineBodyStrategy(Map<String, Object> body) {
        if (body.size() <= 3) {
            boolean allSimple = body.values().stream().allMatch(v ->
                    v instanceof String || v instanceof Number || v instanceof Boolean);
            if (allSimple) return "fixed";
        }
        // 복잡한 구조여도 일단 fixed로 저장 (향후 ai-fill 추천)
        return "fixed";
    }

    /**
     * tool_call 결과에서 top-level 필드 값을 변수로 등록한다.
     * 다음 step에서 이 값이 사용되면 참조로 치환할 수 있도록.
     */
    private void registerResultVariables(String resultContent, int stepIndex, Map<String, String> valueToVariableMap) {
        try {
            JsonNode node = objectMapper.readTree(resultContent);
            if (node.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    String fieldName = field.getKey();
                    JsonNode fieldValue = field.getValue();

                    // 스칼라 값만 등록 (배열, 객체 제외)
                    if (fieldValue.isValueNode() && !fieldValue.isNull()) {
                        String strValue = fieldValue.isTextual() ? fieldValue.asText() : fieldValue.toString();
                        String variableName = "step" + stepIndex + "_" + fieldName;
                        valueToVariableMap.put(strValue, variableName);
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.debug("Failed to parse tool result for variable registration: {}", e.getMessage());
        }
    }

    /**
     * 변수명에서 원본 필드명을 추출한다.
     * 예: "step0_memberId" → "memberId"
     */
    private String extractFieldNameFromVariable(String variableName) {
        int underscoreIndex = variableName.indexOf('_');
        if (underscoreIndex >= 0 && underscoreIndex < variableName.length() - 1) {
            return variableName.substring(underscoreIndex + 1);
        }
        return variableName;
    }

    /**
     * steps를 레시피 JSON 형식으로 직렬화한다.
     */
    private String serializeSteps(List<StepData> steps) {
        try {
            ArrayNode stepsArray = objectMapper.createArrayNode();

            for (StepData step : steps) {
                ObjectNode stepNode = objectMapper.createObjectNode();
                stepNode.put("subdomain", step.subdomain());
                stepNode.put("method", step.method());
                stepNode.put("path", step.path());
                stepNode.put("bodyStrategy", step.bodyStrategy());

                // body 직렬화 — JSON 객체로 저장 (문자열이 아님)
                JsonNode bodyNode = objectMapper.valueToTree(step.body());
                stepNode.set("body", bodyNode);

                // extract 매핑
                if (!step.extractMappings().isEmpty()) {
                    ObjectNode extractNode = objectMapper.createObjectNode();
                    step.extractMappings().forEach(extractNode::put);
                    stepNode.set("extract", extractNode);
                }

                stepsArray.add(stepNode);
            }

            return objectMapper.writeValueAsString(stepsArray);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize recipe steps", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * steps에서 사용된 변수 목록을 JSON으로 직렬화한다.
     */
    private String serializeVariables(List<StepData> steps) {
        try {
            Set<String> variables = new LinkedHashSet<>();

            for (StepData step : steps) {
                // body에서 {{...}} 패턴 추출
                for (Object value : step.body().values()) {
                    if (value instanceof String strVal
                            && strVal.startsWith("{{") && strVal.endsWith("}}")) {
                        variables.add(strVal.substring(2, strVal.length() - 2));
                    }
                }
            }

            if (variables.isEmpty()) {
                return null;
            }

            // 변수 목록을 JSON 배열로 저장
            ObjectNode variablesNode = objectMapper.createObjectNode();
            for (String varName : variables) {
                if (varName.startsWith("gen:")) {
                    variablesNode.put(varName, "generated");
                } else {
                    variablesNode.put(varName, "extract");
                }
            }

            return objectMapper.writeValueAsString(variablesNode);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize variables", e);
            return null;
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    // ──────────────────────────────────────────────────────────────
    // 내부 레코드 (데이터 홀더)
    // ──────────────────────────────────────────────────────────────

    private record ParsedToolCall(String id, String name, String arguments) {}

    private record ToolCallPair(ParsedToolCall toolCall, String resultContent) {}

    private record FunctionNameParts(String subdomain, String method, String path) {}

    private record StepData(
            String subdomain,
            String method,
            String path,
            Map<String, Object> body,
            String bodyStrategy,
            Map<String, String> extractMappings,
            String resultContent
    ) {}
}
