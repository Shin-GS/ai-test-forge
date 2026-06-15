package com.aitestforge.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties("ai-test-forge")
public class AiTestForgeProperties {

    private boolean enabled = true;
    private String serverUrl;
    private String subdomainName;
    private String docsUrl = "/v3/api-docs";
    private String baseUrl;
    private List<String> profiles = List.of("dev", "qa");
    private String environment = "default";
    private Duration heartbeatInterval = Duration.ofSeconds(30);
}
