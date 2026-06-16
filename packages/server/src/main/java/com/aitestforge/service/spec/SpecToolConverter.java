package com.aitestforge.service.spec;

import com.aitestforge.domain.spec.SubdomainSpec;
import com.aitestforge.infra.ai.dto.ToolControl;
import com.aitestforge.infra.ai.dto.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI JSON 스펙에서 AI에게 전달할 ToolDefinition 목록을 추출한다.
 * 각 엔드포인트를 하나의 tool로 변환.
 * x-test-forge-* 확장 필드를 파싱하여 ToolControl 메타데이터를 포함한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpecToolConverter {

    private final ObjectMapper objectMapper;
    private final SpecControlFilter specControlFilter;

    /**
     * 여러 서브도메인 스펙에서 tool 목록을 추출한다.
     */
    public List<ToolDefinition> convertAll(List<SubdomainSpec> specs) {
        List<ToolDefinition> tools = new ArrayList<>();
        for (SubdomainSpec spec : specs) {
            tools.addAll(convert(spec));
        }
        return tools;
    }

    /**
     * 단일 서브도메인 스펙에서 tool 목록을 추출한다.
     */
    public List<ToolDefinition> convert(SubdomainSpec spec) {
        if (spec.getSpecJson() == null || spec.getSpecJson().isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(spec.getSpecJson());
            JsonNode paths = root.get("paths");
            if (paths == null || !paths.isObject()) {
                return List.of();
            }

            List<ToolDefinition> tools = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> pathIter = paths.fields();

            while (pathIter.hasNext()) {
                Map.Entry<String, JsonNode> pathEntry = pathIter.next();
                String path = pathEntry.getKey();
                JsonNode methods = pathEntry.getValue();

                Iterator<Map.Entry<String, JsonNode>> methodIter = methods.fields();
                while (methodIter.hasNext()) {
                    Map.Entry<String, JsonNode> methodEntry = methodIter.next();
                    String method = methodEntry.getKey().toUpperCase();
                    JsonNode operation = methodEntry.getValue();

                    // GET/POST/PUT/DELETE/PATCH만 처리
                    if (!isHttpMethod(method)) continue;

                    // x-test-forge-exclude: true이면 tool 목록에서 제거
                    if (specControlFilter.isExcluded(operation)) {
                        log.debug("Excluded by x-test-forge-exclude: {} {} ({})",
                                method, path, spec.getName());
                        continue;
                    }

                    // x-test-forge-* 확장 필드 파싱
                    ToolControl control = specControlFilter.parseControlFromOperation(operation);

                    String toolName = buildToolName(spec.getName(), method, path);
                    String description = buildDescription(spec, method, path, operation, control);
                    String parametersJson = buildParametersJson(operation);

                    tools.add(new ToolDefinition(toolName, description, parametersJson, control));
                }
            }

            log.debug("Converted {} tools from spec: {} ({})",
                    tools.size(), spec.getName(), spec.getEnvironment());
            return tools;
        } catch (Exception e) {
            log.warn("Failed to convert spec to tools: {} ({}): {}",
                    spec.getName(), spec.getEnvironment(), e.getMessage());
            return List.of();
        }
    }

    private boolean isHttpMethod(String method) {
        return method.equals("GET") || method.equals("POST") ||
               method.equals("PUT") || method.equals("DELETE") || method.equals("PATCH");
    }

    private String buildToolName(String subdomain, String method, String path) {
        // 예: user-service_POST_/api/members → user-service__POST__api_members
        String sanitized = path.replaceAll("[/{}-]", "_").replaceAll("_+", "_");
        if (sanitized.startsWith("_")) sanitized = sanitized.substring(1);
        return subdomain + "__" + method + "__" + sanitized;
    }

    private String buildDescription(SubdomainSpec spec, String method, String path,
                                    JsonNode operation, ToolControl control) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(spec.getName()).append("] ");
        sb.append(method).append(" ").append(path);

        JsonNode summary = operation.get("summary");
        if (summary != null && !summary.asText().isBlank()) {
            sb.append(" — ").append(summary.asText());
        }

        // x-test-forge-hint → description 끝에 AI Hint 추가
        String hint = specControlFilter.parseHint(operation);
        if (hint != null && !hint.isBlank()) {
            sb.append(" [AI Hint: ").append(hint).append("]");
        }

        // x-test-forge-block → description 끝에 BLOCKED 표기
        if (control.blocked()) {
            sb.append(" [BLOCKED");
            if (control.blockReason() != null && !control.blockReason().isBlank()) {
                sb.append(": ").append(control.blockReason());
            }
            sb.append("]");
        }

        return sb.toString();
    }

    private String buildParametersJson(JsonNode operation) {
        try {
            // requestBody의 schema를 parameters로 전달
            JsonNode requestBody = operation.path("requestBody")
                    .path("content")
                    .path("application/json")
                    .path("schema");

            if (requestBody != null && !requestBody.isMissingNode()) {
                return objectMapper.writeValueAsString(requestBody);
            }

            // parameters (query, path)
            JsonNode parameters = operation.get("parameters");
            if (parameters != null && parameters.isArray() && !parameters.isEmpty()) {
                return objectMapper.writeValueAsString(parameters);
            }

            return "{}";
        } catch (Exception e) {
            return "{}";
        }
    }
}
