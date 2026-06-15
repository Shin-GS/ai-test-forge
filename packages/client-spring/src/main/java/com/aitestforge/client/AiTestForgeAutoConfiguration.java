package com.aitestforge.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(AiTestForgeProperties.class)
@ConditionalOnProperty(prefix = "ai-test-forge", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiTestForgeAutoConfiguration {
    // Bean 등록은 추후 구현
}
