package com.aitestforge.service.agent;

import com.aitestforge.infra.ai.AiService;
import com.aitestforge.infra.ai.dto.AiChatResponse;
import com.aitestforge.infra.ai.dto.ChatMessage;
import com.aitestforge.infra.ai.dto.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 2-Stage Strategy: 등록된 tool 수가 임계값 이상이면 AI에게 의도 파악을 요청하여
 * 관련 서브도메인의 tool만 필터링한다. 토큰 낭비를 방지하는 핵심 최적화.
 *
 * Stage 1: 의도 파악 — 사용자 요청 + 서브도메인 목록(그룹 포함)을 AI에게 전달 → 관련 서브도메인 판단
 * Stage 2: 필터된 실행 — Stage 1 결과로 선별된 grouped tool만 Agent Loop에 제공
 *
 * 보완 기능:
 * - 그룹 기반 필터링: 서브도메인별 그룹 정보를 Stage 1에 포함
 * - ungrouped API 처리: 기본적으로 grouped API만 포함, AI 요청 시 ungrouped 추가
 * - Threshold 스킵: tool 수가 threshold 미만이면 2-Stage 생략
 * - Stage 1 빈 결과 fallback: 2차 재시도 후에도 실패하면 전체 tool 반환
 * - 동적 추가: 실행 중 특정 서브도메인 tool을 추가하는 메서드 제공
 */
@Slf4j
@Service
public class TwoStageFilterService {

    private final AiService aiService;

    @Value("${agent-loop.two-stage-threshold:30}")
    private int twoStageThreshold;

    public TwoStageFilterService(@Qualifier("fast") AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 2-Stage 필터링을 수행한다.
     * tool 개수가 threshold 미만이면 전체를 그대로 반환한다.
     *
     * @param allTools    전체 tool 목록
     * @param userMessage 사용자 요청 메시지
     * @return 필터링된 tool 목록
     */
    public List<ToolDefinition> filterTools(List<ToolDefinition> allTools, String userMessage) {
        if (allTools.size() < twoStageThreshold) {
            log.debug("Tool count ({}) is below threshold ({}), skipping 2-Stage filter",
                    allTools.size(), twoStageThreshold);
            return allTools;
        }

        // 서브도메인 목록 추출
        Set<String> subdomains = extractSubdomains(allTools);

        // 서브도메인별 그룹 정보 수집
        Map<String, Set<String>> groupsBySubdomain = buildGroupsBySubdomain(allTools, subdomains);

        // Stage 1: 의도 파악 (1차 시도)
        List<String> relevantSubdomains = detectIntent(userMessage, allTools, subdomains, groupsBySubdomain);

        // Stage 1 빈 결과 fallback — 재시도
        if (relevantSubdomains.isEmpty()) {
            log.warn("Stage 1 returned empty result, retrying with full subdomain info");
            relevantSubdomains = retryDetectIntent(userMessage, subdomains, groupsBySubdomain);
        }

        // 2차 시도에서도 빈 결과면 전체 tool 반환 (fallback)
        if (relevantSubdomains.isEmpty()) {
            log.warn("Stage 1 retry also returned empty result, falling back to all tools");
            return allTools;
        }

        // Stage 2: 필터링 — 관련 서브도메인의 grouped tool만 선별
        List<ToolDefinition> filtered = filterBySubdomainsGroupedOnly(allTools, relevantSubdomains);

        log.info("2-Stage filter applied: {} → {} tools (relevant subdomains: {})",
                allTools.size(), filtered.size(), relevantSubdomains);

        // 필터 결과가 비어있으면 전체 반환 (AI 판단 오류 대비 폴백)
        if (filtered.isEmpty()) {
            log.warn("2-Stage filter returned empty result after subdomain filtering, falling back to all tools");
            return allTools;
        }

        return filtered;
    }

    /**
     * 추가 서브도메인의 tool을 기존 목록에 동적으로 추가한다.
     * 에이전트 루프 실행 중 AI가 "필요한 API가 현재 목록에 없다"고 응답했을 때 사용.
     *
     * @param existingTools 현재 tool 목록
     * @param allTools      전체 tool 목록
     * @param subdomainName 추가할 서브도메인 이름
     * @return 확장된 tool 목록 (기존 + 추가된 서브도메인의 tool)
     */
    public List<ToolDefinition> addSubdomainTools(List<ToolDefinition> existingTools,
                                                   List<ToolDefinition> allTools,
                                                   String subdomainName) {
        // 해당 서브도메인의 모든 tool 추출 (ungrouped 포함)
        List<ToolDefinition> subdomainTools = allTools.stream()
                .filter(tool -> extractSubdomain(tool.name()).equals(subdomainName))
                .toList();

        if (subdomainTools.isEmpty()) {
            log.warn("No tools found for subdomain: {}", subdomainName);
            return existingTools;
        }

        // 이미 존재하는 tool 이름 목록
        Set<String> existingNames = existingTools.stream()
                .map(ToolDefinition::name)
                .collect(Collectors.toSet());

        // 기존 목록에 없는 tool만 추가
        List<ToolDefinition> newTools = subdomainTools.stream()
                .filter(tool -> !existingNames.contains(tool.name()))
                .toList();

        if (newTools.isEmpty()) {
            log.debug("All tools from subdomain '{}' are already in the existing list", subdomainName);
            return existingTools;
        }

        List<ToolDefinition> merged = new ArrayList<>(existingTools);
        merged.addAll(newTools);

        log.info("Dynamically added {} tools from subdomain '{}' (total: {})",
                newTools.size(), subdomainName, merged.size());

        return List.copyOf(merged);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Stage 1: 의도 파악
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Stage 1: 사용자 의도를 파악하여 관련 서브도메인 목록을 반환한다.
     * AI에게 서브도메인 목록 + 그룹 정보 + 대표 API를 전달하여 관련 도메인을 판단하게 함.
     */
    private List<String> detectIntent(String userMessage,
                                       List<ToolDefinition> allTools,
                                       Set<String> subdomains,
                                       Map<String, Set<String>> groupsBySubdomain) {
        String subdomainSummary = buildSubdomainSummary(subdomains, allTools, groupsBySubdomain);
        String systemPrompt = buildIntentPrompt(subdomains, subdomainSummary);

        List<ChatMessage> messages = List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userMessage)
        );

        AiChatResponse response = aiService.chat(messages, List.of());
        return parseSubdomainList(response.message(), subdomains);
    }

    /**
     * Stage 1 재시도: 전체 서브도메인 이름, description, 그룹 목록을 다시 전달하여 재선택 요청.
     */
    private List<String> retryDetectIntent(String userMessage,
                                            Set<String> subdomains,
                                            Map<String, Set<String>> groupsBySubdomain) {
        String retryInfo = buildRetrySubdomainInfo(subdomains, groupsBySubdomain);
        String systemPrompt = buildRetryIntentPrompt(subdomains, retryInfo);

        List<ChatMessage> messages = List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userMessage)
        );

        AiChatResponse response = aiService.chat(messages, List.of());
        return parseSubdomainList(response.message(), subdomains);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 그룹 기반 필터링 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 서브도메인별 그룹 목록을 수집한다.
     * 각 tool의 control.groups에서 그룹명을 추출하고, 비어있는(ungrouped) API는 제외.
     */
    private Map<String, Set<String>> buildGroupsBySubdomain(List<ToolDefinition> allTools,
                                                             Set<String> subdomains) {
        Map<String, Set<String>> result = new LinkedHashMap<>();

        for (String subdomain : subdomains) {
            Set<String> groups = allTools.stream()
                    .filter(tool -> extractSubdomain(tool.name()).equals(subdomain))
                    .filter(tool -> tool.control() != null && !tool.control().groups().isEmpty())
                    .flatMap(tool -> tool.control().groups().stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (!groups.isEmpty()) {
                result.put(subdomain, groups);
            }
        }

        return result;
    }

    /**
     * 관련 서브도메인의 grouped tool만 필터링한다.
     * ungrouped API (control.groups가 빈 리스트)는 기본적으로 제외.
     */
    private List<ToolDefinition> filterBySubdomainsGroupedOnly(List<ToolDefinition> allTools,
                                                                List<String> relevantSubdomains) {
        return allTools.stream()
                .filter(tool -> relevantSubdomains.stream()
                        .anyMatch(sub -> extractSubdomain(tool.name()).equals(sub)))
                .filter(this::isGroupedTool)
                .toList();
    }

    /**
     * tool이 그룹에 속해있는지 확인.
     * control이 null이거나 groups가 빈 리스트면 ungrouped로 간주.
     */
    private boolean isGroupedTool(ToolDefinition tool) {
        return tool.control() != null && !tool.control().groups().isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 프롬프트 빌더
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 각 서브도메인의 대표 API 경로 + 그룹 정보를 요약하여 AI 판단에 도움을 준다.
     */
    private String buildSubdomainSummary(Set<String> subdomains,
                                          List<ToolDefinition> allTools,
                                          Map<String, Set<String>> groupsBySubdomain) {
        StringBuilder sb = new StringBuilder();

        for (String subdomain : subdomains) {
            sb.append("- ").append(subdomain).append(":");

            // 그룹 정보 추가
            Set<String> groups = groupsBySubdomain.get(subdomain);
            if (groups != null && !groups.isEmpty()) {
                sb.append(" [groups: ").append(String.join(", ", groups)).append("]");
            }

            // 대표 API description (최대 5개)
            List<String> descriptions = allTools.stream()
                    .filter(t -> extractSubdomain(t.name()).equals(subdomain))
                    .filter(this::isGroupedTool)
                    .map(ToolDefinition::description)
                    .limit(5)
                    .toList();

            if (!descriptions.isEmpty()) {
                sb.append("\n  APIs: ").append(String.join(", ", descriptions));
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    private String buildIntentPrompt(Set<String> subdomains, String subdomainSummary) {
        return """
                You are an intent classifier for an API orchestration system.
                Given a user request, determine which subdomains are relevant to fulfill the request.
                Consider dependencies between services (e.g., creating an application may require a member and resume first).
                
                Available subdomains, their groups, and representative APIs:
                %s
                
                Respond with ONLY a comma-separated list of relevant subdomain names.
                Do not include any explanation or additional text.
                If multiple subdomains are needed (cross-service scenario), list all of them.
                
                Available subdomain names: %s
                """.formatted(subdomainSummary, String.join(", ", subdomains));
    }

    /**
     * 재시도 시 사용할 서브도메인 정보 문자열을 구성한다.
     */
    private String buildRetrySubdomainInfo(Set<String> subdomains,
                                            Map<String, Set<String>> groupsBySubdomain) {
        StringBuilder sb = new StringBuilder();

        for (String subdomain : subdomains) {
            sb.append("- ").append(subdomain);

            Set<String> groups = groupsBySubdomain.get(subdomain);
            if (groups != null && !groups.isEmpty()) {
                sb.append(": groups=[").append(String.join(", ", groups)).append("]");
            } else {
                sb.append(": (no groups defined)");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    private String buildRetryIntentPrompt(Set<String> subdomains, String retryInfo) {
        return """
                You are an intent classifier for an API orchestration system.
                Your previous attempt did not select any subdomain. Please try again.
                
                Here is the full list of available subdomains and their groups:
                %s
                
                You MUST select at least one subdomain that is most likely relevant to the user's request.
                If unsure, select the most plausible candidates.
                
                Respond with ONLY a comma-separated list of relevant subdomain names.
                Do not include any explanation or additional text.
                
                Available subdomain names: %s
                """.formatted(retryInfo, String.join(", ", subdomains));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 유틸리티
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 전체 tool 목록에서 서브도메인 이름 세트를 추출한다.
     */
    private Set<String> extractSubdomains(List<ToolDefinition> allTools) {
        return allTools.stream()
                .map(tool -> extractSubdomain(tool.name()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * tool name에서 서브도메인 부분을 추출한다.
     * 형식: "{subdomain}__{operation}" — "__" 구분자 이전이 서브도메인.
     */
    private String extractSubdomain(String toolName) {
        int idx = toolName.indexOf("__");
        return idx > 0 ? toolName.substring(0, idx) : toolName;
    }

    /**
     * AI 응답을 파싱하여 유효한 서브도메인 이름만 추출한다.
     * 존재하지 않는 서브도메인 이름은 무시 (할루시네이션 방지).
     */
    private List<String> parseSubdomainList(String response, Set<String> validSubdomains) {
        if (response == null || response.isBlank()) {
            log.warn("Empty response from intent detection AI");
            return List.of();
        }

        return Arrays.stream(response.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .filter(validSubdomains::contains)
                .toList();
    }
}
