package com.aitestforge.client;

import com.aitestforge.client.openapi.TestForgeOperationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableConfigurationProperties(AiTestForgeProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "ai-test-forge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiTestForgeAutoConfiguration {

    @Bean
    public SpecRegistrationService specRegistrationService(AiTestForgeProperties properties) {
        return new SpecRegistrationService(properties);
    }

    @Bean
    public SpecPushScheduler specPushScheduler(SpecRegistrationService registrationService) {
        return new SpecPushScheduler(registrationService);
    }

    @Bean
    @ConditionalOnClass(name = "org.springdoc.core.customizers.OperationCustomizer")
    public TestForgeOperationCustomizer testForgeOperationCustomizer() {
        return new TestForgeOperationCustomizer();
    }
}
