package com.aitestforge.config;

import com.aitestforge.infra.ai.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * AI 모델 티어(reasoning/fast) 별 AiService 빈을 등록하는 설정 클래스.
 * 각 티어의 provider 설정값에 따라 OpenAiService/ClaudeAiService/OpenRouterService 중 하나를 선택한다.
 */
@Slf4j
@Configuration
@Profile("!local")
public class AiConfig {

    @Value("${ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${ai.claude.api-key:}")
    private String claudeApiKey;

    @Value("${ai.openrouter.api-key:}")
    private String openrouterApiKey;

    @Value("${ai.reasoning.provider:openai}")
    private String reasoningProvider;

    @Value("${ai.reasoning.model:gpt-4o}")
    private String reasoningModel;

    @Value("${ai.fast.provider:openai}")
    private String fastProvider;

    @Value("${ai.fast.model:gpt-4o-mini}")
    private String fastModel;

    @Bean
    @Qualifier("reasoning")
    public AiService reasoningAiService(ObjectMapper objectMapper, AiRetryTemplate retryTemplate) {
        log.info("Registering reasoning AiService: provider={}, model={}", reasoningProvider, reasoningModel);
        return createAiService(reasoningProvider, reasoningModel, objectMapper, retryTemplate);
    }

    @Bean
    @Qualifier("fast")
    public AiService fastAiService(ObjectMapper objectMapper, AiRetryTemplate retryTemplate) {
        log.info("Registering fast AiService: provider={}, model={}", fastProvider, fastModel);
        return createAiService(fastProvider, fastModel, objectMapper, retryTemplate);
    }

    private AiService createAiService(String provider, String model,
                                       ObjectMapper objectMapper, AiRetryTemplate retryTemplate) {
        return switch (provider) {
            case "openai" -> new OpenAiService(openaiApiKey, model, objectMapper, retryTemplate);
            case "claude" -> new ClaudeAiService(claudeApiKey, model, objectMapper, retryTemplate);
            case "openrouter" -> new OpenRouterService(openrouterApiKey, model, objectMapper, retryTemplate);
            default -> throw new IllegalArgumentException("Unsupported AI provider: " + provider);
        };
    }
}
