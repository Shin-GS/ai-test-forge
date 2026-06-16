package com.aitestforge.service.agent;

import com.aitestforge.infra.ai.AiService;
import com.aitestforge.infra.ai.dto.AiChatResponse;
import com.aitestforge.infra.ai.dto.ChatMessage;
import com.aitestforge.infra.ai.dto.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 2-Stage Strategy: 등록된 tool 수가 임계값 이상이면 AI에게 의도 파악을 요청하여
 * 관련 서브도메인의 tool만 필터링한다. 토큰 낭비를 방지하는 핵심 최적화.
 *
 * Stage 1: 의도 파악 — 사용자 요청 + 서브도메인 목록을 AI에게 전달 → 관련 서브도메인 판단
 * Stage 2: 필터된 실행 — Stage 1 결과로 선별된 tool만 Agent Loop에 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoStageFilterService {

    private final AiService aiService;

    @Value("${agent-loop.two-stage-threshold:30}")
    private int twoStageThreshold;

    /**
     * tool 수가 임계값 이상이면 AI에게 의도 파악을 요청하여 관련 tool만 필터링.
     * 임계값 미만이면 전체 tool을 그대로 반환.
     *
     * @param userMessage 사용자의 자연어 요청
     * @param allTools    현재 워크스페이스에 등록된 전체 tool 목록
     * @return 필터링된 tool 목록 (또는 전체)
     */
    public List<ToolDefinition> filterToolsIfNeeded(String userMessage, List<ToolDefinition> allTools) {
        if (allTools.size() < twoStageThreshold) {
            return allTools;
        }

        // Stage 1: 의도 파악
        List<String> relevantSubdomains = detectIntent(userMessage, allTools);

        // Stage 2: 필터링 — 관련 서브도메인의 tool만 선별
        List<ToolDefinition> filtered = allTools.stream()
                .filter(tool -> relevantSubdomains.stream()
                        .anyMatch(sub -> tool.name().startsWith(sub + "__")))
                .toList();

        log.info("2-Stage filter applied: {} → {} tools (relevant subdomains: {})",
                allTools.size(), filtered.size(), relevantSubdomains);

        // 필터 결과가 비어있으면 전체 반환 (AI 판단 오류 대비 폴백)
        if (filtered.isEmpty()) {
            log.warn("2-Stage filter returned empty result, falling back to all tools");
            return allTools;
        }

        return filtered;
    }

    /**
     * Stage 1: 사용자 의도를 파악하여 관련 서브도메인 목록을 반환한다.
     * AI에게 서브도메인 목록과 사용자 요청을 전달하여 관련 도메인을 판단하게 함.
     */
    private List<String> detectIntent(String userMessage, List<ToolDefinition> allTools) {
        // 서브도메인 목록 추출 (tool name의 첫 번째 세그먼트, 중복 제거)
        Set<String> subdomains = allTools.stream()
                .map(tool -> {
                    String name = tool.name();
                    int idx = name.indexOf("__");
                    return idx > 0 ? name.substring(0, idx) : name;
                })
                .collect(Collectors.toSet());

        // 서브도메인별 대표 API 정보 구성 (AI가 판단하기 쉽도록 카테고리 역할)
        String subdomainSummary = buildSubdomainSummary(subdomains, allTools);

        String systemPrompt = buildIntentPrompt(subdomains, subdomainSummary);
        List<ChatMessage> messages = List.of(
                new ChatMessage("system", systemPrompt),
                new ChatMessage("user", userMessage)
        );

        AiChatResponse response = aiService.chat(messages, List.of());
        return parseSubdomainList(response.message(), subdomains);
    }

    /**
     * 각 서브도메인의 대표 API 경로를 요약하여 AI 판단에 도움을 준다.
     */
    private String buildSubdomainSummary(Set<String> subdomains, List<ToolDefinition> allTools) {
        StringBuilder sb = new StringBuilder();
        for (String subdomain : subdomains) {
            List<String> descriptions = allTools.stream()
                    .filter(t -> t.name().startsWith(subdomain + "__"))
                    .map(ToolDefinition::description)
                    .limit(5) // 서브도메인당 최대 5개만 예시로 제공 (토큰 절약)
                    .toList();

            sb.append("- ").append(subdomain).append(": ");
            sb.append(String.join(", ", descriptions));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildIntentPrompt(Set<String> subdomains, String subdomainSummary) {
        return """
                You are an intent classifier for an API orchestration system.
                Given a user request, determine which subdomains are relevant to fulfill the request.
                Consider dependencies between services (e.g., creating an application may require a member and resume first).
                
                Available subdomains and their representative APIs:
                %s
                
                Respond with ONLY a comma-separated list of relevant subdomain names.
                Do not include any explanation or additional text.
                If multiple subdomains are needed (cross-service scenario), list all of them.
                
                Available subdomain names: %s
                """.formatted(subdomainSummary, String.join(", ", subdomains));
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
