package com.aitestforge.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.client.RestClient;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 서브도메인 서버의 OpenAPI 스펙을 메인 서버에 등록하는 서비스.
 * ApplicationReadyEvent 시점에 docs URL에서 OpenAPI JSON을 가져와 메인 서버에 push한다.
 */
@Slf4j
public class SpecRegistrationService {

    private final AiTestForgeProperties properties;
    private final RestClient restClient;

    public SpecRegistrationService(AiTestForgeProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getServerUrl())
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        if (!isActiveProfile()) {
            log.info("AI Test Forge: 현재 프로필이 활성화 대상이 아닙니다. 등록을 건너뜁니다.");
            return;
        }

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String specJson = fetchOpenApiSpec();
                pushSpec(specJson);
                log.info("AI Test Forge: 스펙 등록 성공 - {} ({})",
                        properties.getSubdomainName(), properties.getEnvironment());
                return;
            } catch (Exception e) {
                log.warn("AI Test Forge: 스펙 등록 실패 (시도 {}/{}): {}",
                        attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    sleep(5000);
                }
            }
        }
        log.error("AI Test Forge: 스펙 등록 최종 실패 - {} ({})",
                properties.getSubdomainName(), properties.getEnvironment());
    }

    /**
     * Heartbeat 전송 (해시만 전달, 변경 시에만 전체 재전송).
     */
    public void sendHeartbeat() {
        try {
            String specJson = fetchOpenApiSpec();
            String hash = computeHash(specJson);

            pushSpec(null, hash);
            log.debug("AI Test Forge: heartbeat 전송 - {}", properties.getSubdomainName());
        } catch (Exception e) {
            log.warn("AI Test Forge: heartbeat 실패 - {}", e.getMessage());
        }
    }

    private String fetchOpenApiSpec() {
        String docsUrl = properties.getBaseUrl() + properties.getDocsUrl();
        return RestClient.create().get()
                .uri(docsUrl)
                .retrieve()
                .body(String.class);
    }

    private void pushSpec(String specJson) {
        pushSpec(specJson, specJson != null ? computeHash(specJson) : null);
    }

    private void pushSpec(String specJson, String specHash) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", properties.getSubdomainName());
        body.put("environment", properties.getEnvironment());
        body.put("baseUrl", properties.getBaseUrl());
        if (specJson != null) {
            body.put("specJson", specJson);
        }
        if (specHash != null) {
            body.put("specHash", specHash);
        }

        // 인증 프로필 메타 정보 추가
        if (properties.getAuth() != null && properties.getAuth().getProfiles() != null
                && !properties.getAuth().getProfiles().isEmpty()) {
            List<Map<String, String>> authProfiles = properties.getAuth().getProfiles().stream()
                    .map(p -> Map.of("name", p.getName(), "loginPageUrl", p.getLoginPageUrl()))
                    .toList();
            body.put("authProfiles", authProfiles);
        }

        restClient.post()
                .uri("/api/v1/specs/register")
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private boolean isActiveProfile() {
        // 프로필 검사는 AutoConfiguration에서 @ConditionalOnProperty로 이미 필터됨
        // 여기서는 항상 true 반환 (추가 검증 필요 시 확장)
        return true;
    }

    private String computeHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
