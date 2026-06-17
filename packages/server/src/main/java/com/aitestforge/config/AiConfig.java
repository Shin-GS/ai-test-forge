package com.aitestforge.config;

import com.aitestforge.infra.ai.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 모델 티어(reasoning/fast) 별 AiService 빈을 등록하는 설정 클래스.
 * 각 티어의 provider 설정값에 따라 Mock/OpenAi/Claude/OpenRouter 중 하나를 선택한다.
 * provider=mock이면 MockAiService 등록 (프로필과 무관, .env에서 제어).
 */
@Slf4j
@Configuration
public class AiConfig {

    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${ai.claude.api-key:}")
    private String claudeApiKey;

    @Value("${ai.openrouter.api-key:}")
    private String openrouterApiKey;

    @Value("${ai.reasoning.provider:mock}")
    private String reasoningProvider;

    @Value("${ai.reasoning.model:gpt-4o}")
    private String reasoningModel;

    @Value("${ai.fast.provider:mock}")
    private String fastProvider;

    @Value("${ai.fast.model:gpt-4o-mini}")
    private String fastModel;

    @Bean
    @Qualifier("reasoning")
    public AiService reasoningAiService(ObjectMapper objectMapper, AiRetryTemplate retryTemplate,
                                         MockAiService mockAiService) {
        log.info("Registering reasoning AiService: provider={}, model={}", reasoningProvider, reasoningModel);
        return createAiService(reasoningProvider, reasoningModel, objectMapper, retryTemplate, mockAiService);
    }

    @Bean
    @Qualifier("fast")
    public AiService fastAiService(ObjectMapper objectMapper, AiRetryTemplate retryTemplate,
                                    MockAiService mockAiService) {
        log.info("Registering fast AiService: provider={}, model={}", fastProvider, fastModel);
        return createAiService(fastProvider, fastModel, objectMapper, retryTemplate, mockAiService);
    }

    @Bean
    public MockAiService mockAiService() {
        return new MockAiService();
    }

    private AiService createAiService(String provider, String model,
                                       ObjectMapper objectMapper, AiRetryTemplate retryTemplate,
                                       MockAiService mockAiService) {
        return switch (provider) {
            case "mock" -> mockAiService;
            case "openai" -> new OpenAiService(openaiApiKey, model, objectMapper, retryTemplate);
            case "claude" -> new ClaudeAiService(claudeApiKey, model, objectMapper, retryTemplate);
            case "openrouter" -> new OpenRouterService(openrouterApiKey, model, objectMapper, retryTemplate);
            default -> throw new IllegalArgumentException("Unsupported AI provider: " + provider);
        };
    }
}
