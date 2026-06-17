package com.aitestforge.service.recipe;

import com.aitestforge.domain.spec.SubdomainSpec;
import com.aitestforge.infra.ai.AiService;
import com.aitestforge.infra.ai.dto.AiChatResponse;
import com.aitestforge.infra.ai.dto.ChatMessage;
import com.aitestforge.repository.SubdomainSpecRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI를 활용하여 레시피 step의 request body를 생성/채우는 컴포넌트.
 * AI_GENERATE, AI_FILL 전략을 처리한다.
 */
@Slf4j
@Component
public class AiBodyGenerator {

    private static final int MAX_RETRY = 2;
    private static final Pattern JSON_CODE_BLOCK = Pattern.compile("```(?:json)?\\s*\\n?(.*?)\\n?```", Pattern.DOTALL);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private static final String SYSTEM_PROMPT = """
            You are a test data generator. Generate a valid JSON request body for the given API schema.
            Respond with ONLY the JSON body, no explanation or markdown formatting.
            Generate realistic Korean test data when appropriate.""";

    private final AiService fastAiService;
    private final AiService reasoningAiService;
    private final SubdomainSpecRepository subdomainSpecRepository;
    private final ObjectMapper objectMapper;

    public AiBodyGenerator(
            @Qualifier("fast") AiService fastAiService,
            @Qualifier("reasoning") AiService reasoningAiService,
            SubdomainSpecRepository subdomainSpecRepository,
            ObjectMapper objectMapper) {
        this.fastAiService = fastAiService;
        this.reasoningAiService = reasoningAiService;
        this.subdomainSpecRepository = subdomainSpecRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * AI_GENERATE 전략: 스키마 기반으로 전체 body를 AI가 생성한다.
     *
     * @param subdomain   서브도메인 이름
     * @param environment 환경 (null이면 "default")
     * @param method      HTTP 메서드
     * @param path        API 경로
     * @param aiHint      AI에게 전달할 힌트 (null 허용)
     * @param fixedFields 고정 필드 (변수 치환 완료된 값) — AI 결과와 merge
     * @return 생성된 JSON body 문자열
     */
    public String generateBody(String subdomain, String environment, String method, String path,
                               String aiHint, Map<String, String> fixedFields) {
        String schema = getApiSchema(subdomain, environment, method, path);
        if (schema == null || schema.isBlank()) {
            log.warn("No schema found for {} {} (subdomain: {}, env: {})", method, path, subdomain, environment);
            return buildFixedFieldsJson(fixedFields);
        }

        String contextInfo = buildContextInfo(fixedFields);
        String userPrompt = buildGeneratePrompt(method, path, schema, contextInfo, aiHint);

        String aiBody = callAiWithRetry(userPrompt, schema, null);
        return mergeWithFixedFields(aiBody, fixedFields);
    }

    /**
     * AI_FILL 전략: 기존 body에서 null/{{auto}} 필드만 AI가 채운다.
     *
     * @param resolvedBody 1차 치환 완료된 body JSON 문자열
     * @param subdomain    서브도메인 이름
     * @param environment  환경
     * @param method       HTTP 메서드
     * @param path         API 경로
     * @param aiHint       AI에게 전달할 힌트 (null 허용)
     * @return AI가 채운 body JSON 문자열
     */
    public String fillBody(String resolvedBody, String subdomain, String environment,
                           String method, String path, String aiHint) {
        try {
            JsonNode bodyNode = objectMapper.readTree(resolvedBody);
            if (!bodyNode.isObject()) {
                return resolvedBody;
            }

            // null이거나 "{{auto}}" 값인 필드 추출
            Map<String, String> fieldsToFill = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = bodyNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (entry.getValue().isNull()
                        || "{{auto}}".equals(entry.getValue().asText())
                        || entry.getValue().asText().isBlank()) {
                    fieldsToFill.put(entry.getKey(), "");
                }
            }

            if (fieldsToFill.isEmpty()) {
                return resolvedBody;
            }

            // 채워진 필드 정보 (컨텍스트로 제공)
            Map<String, String> filledFields = new LinkedHashMap<>();
            fields = bodyNode.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (!fieldsToFill.containsKey(entry.getKey()) && !entry.getValue().isNull()) {
                    filledFields.put(entry.getKey(), entry.getValue().asText());
                }
            }

            String schema = getApiSchema(subdomain, environment, method, path);
            String userPrompt = buildFillPrompt(method, path, schema, fieldsToFill.keySet(), filledFields, aiHint);

            String aiResult = callAiWithRetry(userPrompt, schema, fieldsToFill.keySet());

            // AI 결과를 기존 body에 merge
            return mergeAiFillResult(resolvedBody, aiResult);
        } catch (Exception e) {
            log.warn("AI_FILL failed, returning resolved body as-is: {}", e.getMessage());
            return resolvedBody;
        }
    }

    /**
     * AI_PICK 전략: GET 결과 목록에서 aiHint 조건에 맞는 항목을 AI가 선택한다.
     *
     * @param responseBody GET 결과 JSON
     * @param aiHint       선택 조건 힌트
     * @return 선택된 항목 JSON 문자열
     */
    public String pickFromList(String responseBody, String aiHint) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        String userPrompt = """
                From the following list, select the item that best matches this criteria: "%s"
                Respond with ONLY the selected item as JSON, no explanation.
                
                List:
                %s""".formatted(aiHint != null ? aiHint : "the first item", truncate(responseBody, 3000));

        List<ChatMessage> messages = List.of(
                new ChatMessage("system", "You are a selector that picks items from a list based on criteria. Respond with ONLY the JSON of the selected item."),
                new ChatMessage("user", userPrompt)
        );

        try {
            AiChatResponse response = fastAiService.chat(messages, List.of());
            if (response.message() != null && !response.message().isBlank()) {
                return extractJsonFromResponse(response.message());
            }
        } catch (Exception e) {
            log.warn("AI_PICK failed: {}", e.getMessage());
        }

        // 폴백: 배열의 첫 번째 항목
        return extractFirstItem(responseBody);
    }

    // ========== private methods ==========

    private String callAiWithRetry(String userPrompt, String schema, Set<String> requiredOutputFields) {
        String lastError = null;

        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            String prompt = attempt == 0 ? userPrompt
                    : userPrompt + "\n\n[Previous attempt failed: " + lastError + "]\nPlease try again with valid JSON.";

            List<ChatMessage> messages = List.of(
                    new ChatMessage("system", SYSTEM_PROMPT),
                    new ChatMessage("user", prompt)
            );

            try {
                AiChatResponse response = fastAiService.chat(messages, List.of());
                if (response.message() == null || response.message().isBlank()) {
                    lastError = "Empty response from AI";
                    continue;
                }

                String json = extractJsonFromResponse(response.message());
                if (json == null) {
                    lastError = "Could not extract valid JSON from AI response";
                    continue;
                }

                // required 필드 검증 (schema가 있는 경우)
                if (schema != null && !schema.isBlank()) {
                    String validationError = validateRequiredFields(json, schema);
                    if (validationError != null) {
                        lastError = validationError;
                        continue;
                    }
                }

                return json;
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("AI body generation attempt {} failed: {}", attempt + 1, e.getMessage());
            }
        }

        // Fallback: reasoning 모델로 1회 재시도
        log.info("Fast AI body generation exhausted, falling back to reasoning model");
        try {
            List<ChatMessage> messages = List.of(
                    new ChatMessage("system", SYSTEM_PROMPT),
                    new ChatMessage("user", userPrompt)
            );
            AiChatResponse response = reasoningAiService.chat(messages, List.of());
            if (response.message() != null && !response.message().isBlank()) {
                String json = extractJsonFromResponse(response.message());
                if (json != null) {
                    return json;
                }
            }
        } catch (Exception e) {
            log.warn("Reasoning fallback also failed: {}", e.getMessage());
        }

        throw new AiBodyGenerationException(
                "AI body generation failed after %d attempts + reasoning fallback. Last error: %s"
                        .formatted(MAX_RETRY + 1, lastError));
    }

    private String getApiSchema(String subdomain, String environment, String method, String path) {
        String env = (environment != null && !environment.isBlank()) ? environment : "default";

        Optional<SubdomainSpec> specOpt = subdomainSpecRepository.findByNameAndEnvironment(subdomain, env);
        if (specOpt.isEmpty()) {
            log.debug("SubdomainSpec not found: {} ({})", subdomain, env);
            return null;
        }

        SubdomainSpec spec = specOpt.get();
        if (spec.getSpecJson() == null || spec.getSpecJson().isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(spec.getSpecJson());
            JsonNode paths = root.get("paths");
            if (paths == null || !paths.isObject()) return null;

            JsonNode pathNode = paths.get(path);
            if (pathNode == null || !pathNode.isObject()) return null;

            JsonNode operation = pathNode.get(method.toLowerCase());
            if (operation == null || !operation.isObject()) return null;

            // requestBody → content → application/json → schema
            JsonNode schema = operation.path("requestBody")
                    .path("content")
                    .path("application/json")
                    .path("schema");

            if (schema.isMissingNode() || schema.isEmpty()) {
                return null;
            }

            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            log.warn("Failed to extract schema for {} {} ({}): {}", method, path, subdomain, e.getMessage());
            return null;
        }
    }

    private String validateRequiredFields(String json, String schemaJson) {
        try {
            JsonNode body = objectMapper.readTree(json);
            JsonNode schema = objectMapper.readTree(schemaJson);

            JsonNode requiredNode = schema.get("required");
            if (requiredNode == null || !requiredNode.isArray()) {
                return null; // required 정의 없으면 검증 패스
            }

            List<String> missing = new ArrayList<>();
            for (JsonNode field : requiredNode) {
                String fieldName = field.asText();
                if (!body.has(fieldName) || body.get(fieldName).isNull()) {
                    missing.add(fieldName);
                }
            }

            if (missing.isEmpty()) return null;
            return "Missing required fields: " + String.join(", ", missing);
        } catch (Exception e) {
            return null; // 파싱 실패 시 검증 스킵
        }
    }

    /**
     * AI 응답에서 JSON을 추출한다.
     * 우선순위: 1) ```json``` 코드블록  2) 전체가 JSON  3) 첫 { ~ 마지막 }
     */
    String extractJsonFromResponse(String message) {
        if (message == null || message.isBlank()) return null;

        // 1. ```json ... ``` 코드블록
        Matcher matcher = JSON_CODE_BLOCK.matcher(message);
        if (matcher.find()) {
            String candidate = matcher.group(1).trim();
            if (isValidJson(candidate)) return candidate;
        }

        // 2. 전체가 JSON
        String trimmed = message.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}") && isValidJson(trimmed)) {
            return trimmed;
        }

        // 3. 첫 { ~ 마지막 }
        Matcher objectMatcher = JSON_OBJECT.matcher(message);
        if (objectMatcher.find()) {
            String candidate = objectMatcher.group().trim();
            if (isValidJson(candidate)) return candidate;
        }

        return null;
    }

    private boolean isValidJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildGeneratePrompt(String method, String path, String schema,
                                       String contextInfo, String aiHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("API: ").append(method.toUpperCase()).append(" ").append(path).append("\n");
        sb.append("Schema: ").append(schema).append("\n");

        if (contextInfo != null && !contextInfo.isBlank()) {
            sb.append("Context (use these values as-is): ").append(contextInfo).append("\n");
        }

        if (aiHint != null && !aiHint.isBlank()) {
            sb.append("Hint: ").append(aiHint).append("\n");
        }

        sb.append("\nGenerate a complete JSON body that satisfies the schema.");
        return sb.toString();
    }

    private String buildFillPrompt(String method, String path, String schema,
                                   Set<String> fieldsToFill, Map<String, String> filledFields, String aiHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("API: ").append(method.toUpperCase()).append(" ").append(path).append("\n");

        if (schema != null && !schema.isBlank()) {
            sb.append("Schema: ").append(schema).append("\n");
        }

        sb.append("Fields that need values: ").append(String.join(", ", fieldsToFill)).append("\n");

        if (!filledFields.isEmpty()) {
            sb.append("Already filled fields (for context): ").append(filledFields).append("\n");
        }

        if (aiHint != null && !aiHint.isBlank()) {
            sb.append("Hint: ").append(aiHint).append("\n");
        }

        sb.append("\nGenerate a JSON object containing ONLY the fields that need values: ")
                .append(String.join(", ", fieldsToFill));
        return sb.toString();
    }

    private String buildContextInfo(Map<String, String> fixedFields) {
        if (fixedFields == null || fixedFields.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(fixedFields);
        } catch (Exception e) {
            return fixedFields.toString();
        }
    }

    private String mergeWithFixedFields(String aiBody, Map<String, String> fixedFields) {
        if (fixedFields == null || fixedFields.isEmpty()) return aiBody;

        try {
            JsonNode aiNode = objectMapper.readTree(aiBody);
            if (!aiNode.isObject()) return aiBody;

            ObjectNode merged = (ObjectNode) aiNode;
            // 고정 필드는 AI 결과를 덮어씀
            for (Map.Entry<String, String> entry : fixedFields.entrySet()) {
                merged.put(entry.getKey(), entry.getValue());
            }
            return objectMapper.writeValueAsString(merged);
        } catch (Exception e) {
            log.warn("Failed to merge fixed fields into AI body: {}", e.getMessage());
            return aiBody;
        }
    }

    private String mergeAiFillResult(String originalBody, String aiResult) {
        try {
            ObjectNode original = (ObjectNode) objectMapper.readTree(originalBody);
            JsonNode aiNode = objectMapper.readTree(aiResult);

            if (!aiNode.isObject()) return originalBody;

            // AI가 채운 값으로 null/{{auto}} 필드를 업데이트
            Iterator<Map.Entry<String, JsonNode>> aiFields = aiNode.properties().iterator();
            while (aiFields.hasNext()) {
                Map.Entry<String, JsonNode> entry = aiFields.next();
                JsonNode originalValue = original.get(entry.getKey());
                // null이거나 {{auto}}인 필드만 업데이트
                if (originalValue == null || originalValue.isNull()
                        || "{{auto}}".equals(originalValue.asText())
                        || originalValue.asText().isBlank()) {
                    original.set(entry.getKey(), entry.getValue());
                }
            }

            return objectMapper.writeValueAsString(original);
        } catch (Exception e) {
            log.warn("Failed to merge AI fill result: {}", e.getMessage());
            return originalBody;
        }
    }

    private String buildFixedFieldsJson(Map<String, String> fixedFields) {
        if (fixedFields == null || fixedFields.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(fixedFields);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String extractFirstItem(String responseBody) {
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            if (node.isArray() && !node.isEmpty()) {
                return objectMapper.writeValueAsString(node.get(0));
            }
            // content 필드에 배열이 있는 경우 (페이지네이션 응답)
            JsonNode content = node.get("content");
            if (content != null && content.isArray() && !content.isEmpty()) {
                return objectMapper.writeValueAsString(content.get(0));
            }
            return responseBody;
        } catch (Exception e) {
            return responseBody;
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...(truncated)";
    }
}
