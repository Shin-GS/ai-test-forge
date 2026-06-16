package com.aitestforge.service.spec;

import com.aitestforge.infra.ai.dto.ToolControl;
import com.aitestforge.infra.ai.dto.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAPI x-test-forge-* 확장 필드 파싱 및 글로벌 제외 규칙을 적용하는 필터.
 */
@Slf4j
@Service
public class SpecControlFilter {

    private final List<String> globalExcludeMethods;
    private final List<String> globalExcludePathPatterns;
    private final List<String> globalExcludeTags;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public SpecControlFilter(
            @Value("${spec-registry.global-exclude.methods:}") List<String> globalExcludeMethods,
            @Value("${spec-registry.global-exclude.path-patterns:}") List<String> globalExcludePathPatterns,
            @Value("${spec-registry.global-exclude.tags:}") List<String> globalExcludeTags
    ) {
        // 빈 문자열이 리스트에 하나 들어오는 경우 제거
        this.globalExcludeMethods = filterBlank(globalExcludeMethods);
        this.globalExcludePathPatterns = filterBlank(globalExcludePathPatterns);
        this.globalExcludeTags = filterBlank(globalExcludeTags);
    }

    /**
     * tool 목록에서 글로벌 규칙으로 제외해야 할 tool을 필터링한다.
     *
     * @param tools 원본 tool 목록
     * @return 필터링된 tool 목록 (제외된 것 빠짐)
     */
    public List<ToolDefinition> applyGlobalExclude(List<ToolDefinition> tools) {
        if (globalExcludeMethods.isEmpty() && globalExcludePathPatterns.isEmpty() && globalExcludeTags.isEmpty()) {
            return tools;
        }

        List<ToolDefinition> filtered = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            MethodAndPath mp = extractMethodAndPath(tool.name());
            if (mp == null) {
                // 파싱 불가 — 그대로 유지
                filtered.add(tool);
                continue;
            }

            // tag 정보: description에서 [subdomain] 패턴 뒤의 정보로는 부족, group으로 대체 처리
            List<String> toolGroups = tool.control() != null ? tool.control().groups() : List.of();

            if (!isGloballyExcluded(mp.method(), mp.path(), toolGroups)) {
                filtered.add(tool);
            } else {
                log.debug("Globally excluded tool: {} (method={}, path={})", tool.name(), mp.method(), mp.path());
            }
        }
        return filtered;
    }

    /**
     * OpenAPI JSON의 operation에서 x-test-forge-* 확장 필드를 파싱하여 ToolControl을 생성한다.
     *
     * @param operation Jackson JsonNode (operation 레벨)
     * @return 파싱된 ToolControl
     */
    public ToolControl parseControlFromOperation(JsonNode operation) {
        if (operation == null) {
            return ToolControl.none();
        }

        boolean blocked = false;
        String blockReason = null;
        String confirmMessage = null;
        boolean readonly = false;
        List<String> groups = List.of();

        // x-test-forge-block
        JsonNode blockNode = operation.get("x-test-forge-block");
        if (blockNode != null) {
            if (blockNode.isBoolean()) {
                blocked = blockNode.asBoolean();
            } else if (blockNode.isTextual()) {
                // 문자열이면 blocked=true + reason으로 사용
                blocked = true;
                blockReason = blockNode.asText();
            } else if (blockNode.isObject()) {
                blocked = true;
                JsonNode reasonNode = blockNode.get("reason");
                if (reasonNode != null) {
                    blockReason = reasonNode.asText();
                }
            } else {
                log.warn("x-test-forge-block has unexpected type: {}, ignoring", blockNode.getNodeType());
            }
        }

        // x-test-forge-confirm
        JsonNode confirmNode = operation.get("x-test-forge-confirm");
        if (confirmNode != null) {
            if (confirmNode.isObject()) {
                // { "message": "..." } 형태 (어노테이션 기반 표준 형식)
                JsonNode messageNode = confirmNode.get("message");
                confirmMessage = messageNode != null ? messageNode.asText() : "";
            } else if (confirmNode.isTextual()) {
                confirmMessage = confirmNode.asText();
            } else if (confirmNode.isBoolean() && confirmNode.asBoolean()) {
                confirmMessage = "";
            } else {
                log.warn("x-test-forge-confirm has unexpected type: {}, ignoring", confirmNode.getNodeType());
            }
        }

        // x-test-forge-readonly
        JsonNode readonlyNode = operation.get("x-test-forge-readonly");
        if (readonlyNode != null) {
            if (readonlyNode.isBoolean()) {
                readonly = readonlyNode.asBoolean();
            } else {
                log.warn("x-test-forge-readonly has unexpected type: {}, ignoring", readonlyNode.getNodeType());
            }
        }

        // x-test-forge-group
        JsonNode groupNode = operation.get("x-test-forge-group");
        if (groupNode != null) {
            if (groupNode.isArray()) {
                List<String> groupList = new ArrayList<>();
                for (JsonNode g : groupNode) {
                    if (g.isTextual()) {
                        groupList.add(g.asText());
                    }
                }
                groups = List.copyOf(groupList);
            } else if (groupNode.isTextual()) {
                groups = List.of(groupNode.asText());
            } else {
                log.warn("x-test-forge-group has unexpected type: {}, ignoring", groupNode.getNodeType());
            }
        }

        return new ToolControl(blocked, blockReason, confirmMessage, readonly, groups);
    }

    /**
     * x-test-forge-exclude 필드가 true인지 확인.
     *
     * @param operation Jackson JsonNode (operation 레벨)
     * @return true이면 이 API는 tool 목록에서 제거해야 함
     */
    public boolean isExcluded(JsonNode operation) {
        if (operation == null) return false;

        JsonNode excludeNode = operation.get("x-test-forge-exclude");
        if (excludeNode == null) return false;

        if (excludeNode.isBoolean()) {
            return excludeNode.asBoolean();
        }

        log.warn("x-test-forge-exclude has unexpected type: {}, ignoring", excludeNode.getNodeType());
        return false;
    }

    /**
     * x-test-forge-hint 필드를 추출한다.
     *
     * @param operation Jackson JsonNode (operation 레벨)
     * @return hint 문자열 또는 null
     */
    public String parseHint(JsonNode operation) {
        if (operation == null) return null;

        JsonNode hintNode = operation.get("x-test-forge-hint");
        if (hintNode == null) return null;

        if (hintNode.isTextual()) {
            return hintNode.asText();
        }

        log.warn("x-test-forge-hint has unexpected type: {}, ignoring", hintNode.getNodeType());
        return null;
    }

    /**
     * tool이 global-exclude에 의해 제외되어야 하는지 판단.
     */
    public boolean isGloballyExcluded(String method, String path, List<String> tags) {
        // method 기반 제외
        if (!globalExcludeMethods.isEmpty() && globalExcludeMethods.contains(method.toUpperCase())) {
            return true;
        }

        // path pattern 기반 제외
        for (String pattern : globalExcludePathPatterns) {
            try {
                if (pathMatcher.match(pattern, path)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Invalid path pattern '{}', skipping: {}", pattern, e.getMessage());
            }
        }

        // tag 기반 제외
        if (!globalExcludeTags.isEmpty() && tags != null) {
            for (String tag : tags) {
                if (globalExcludeTags.contains(tag)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * tool name에서 method와 path를 추출한다.
     * tool name 형식: {subdomain}__{METHOD}__{sanitized_path}
     */
    private MethodAndPath extractMethodAndPath(String toolName) {
        // 형식: subdomain__METHOD__path_segments
        int firstSep = toolName.indexOf("__");
        if (firstSep < 0) return null;

        int secondSep = toolName.indexOf("__", firstSep + 2);
        if (secondSep < 0) return null;

        String method = toolName.substring(firstSep + 2, secondSep);
        String sanitizedPath = toolName.substring(secondSep + 2);

        // sanitized path → 원래 path로 복원 (완벽하지는 않지만 패턴 매칭에 충분)
        // 원래: /api/members/{id} → api_members_{id}
        String path = "/" + sanitizedPath.replace("_", "/");

        return new MethodAndPath(method, path);
    }

    private List<String> filterBlank(List<String> input) {
        if (input == null) return List.of();
        return input.stream()
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    private record MethodAndPath(String method, String path) {}
}
