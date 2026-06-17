package com.aitestforge.config;

import com.aitestforge.infra.ai.AiService;
import com.aitestforge.infra.ai.MockAiService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * local 프로필에서 Mock AI 서비스를 reasoning/fast 양쪽에 등록.
 * 동일 인스턴스를 공유하여 불필요한 객체 생성을 방지한다.
 */
@Configuration
@Profile("local")
public class MockAiServiceConfig {

    @Bean
    public MockAiService mockAiService() {
        return new MockAiService();
    }

    @Bean
    @Qualifier("reasoning")
    public AiService reasoningAiService(MockAiService mockAiService) {
        return mockAiService;
    }

    @Bean
    @Qualifier("fast")
    public AiService fastAiService(MockAiService mockAiService) {
        return mockAiService;
    }
}
