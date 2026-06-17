package com.aitestforge.service.spec;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.spec.enums.SpecStatus;
import com.aitestforge.domain.spec.SubdomainSpec;
import com.aitestforge.dto.spec.request.SpecRegisterRequest;
import com.aitestforge.dto.spec.response.ApiEndpointResponse;
import com.aitestforge.dto.spec.response.SpecDetailResponse;
import com.aitestforge.dto.spec.response.SpecRegisterResponse;
import com.aitestforge.dto.spec.response.SpecResponse;
import com.aitestforge.repository.SubdomainSpecRepository;
import com.aitestforge.service.recipe.RecipeSpecValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpecService {

    private final SubdomainSpecRepository specRepository;
    private final ObjectMapper objectMapper;
    private final SpecAsyncProcessor specAsyncProcessor;
    private final RecipeSpecValidator recipeSpecValidator;

    @Value("${spec-registry.async-threshold:5242880}")
    private long asyncThreshold;

    /**
     * 서브도메인 API 스펙 등록/갱신/heartbeat 겸용.
     * - specJson이 있으면: 등록 또는 전체 갱신
     * - specJson이 없고 specHash만 있으면: heartbeat (해시 일치 시 갱신 불필요)
     * - specJson 크기가 asyncThreshold 이상이면 비동기 처리 (202 응답)
     */
    @Transactional
    public SpecRegisterResponse register(SpecRegisterRequest request) {
        Optional<SubdomainSpec> existing = specRepository.findByNameAndEnvironment(
                request.name(), request.environment());

        if (existing.isPresent()) {
            return handleExisting(existing.get(), request);
        } else {
            return handleNew(request);
        }
    }

    public List<SpecResponse> getAll() {
        return specRepository.findAll().stream()
                .map(SpecResponse::from)
                .toList();
    }

    public Optional<SpecResponse> getByNameAndEnvironment(String name, String environment) {
        return specRepository.findByNameAndEnvironment(name, environment)
                .map(SpecResponse::from);
    }

    private SpecRegisterResponse handleExisting(SubdomainSpec spec, SpecRegisterRequest request) {
        // authProfiles가 있으면 갱신
        updateAuthProfilesIfPresent(spec, request);

        // specJson이 없으면 heartbeat만
        if (request.specJson() == null || request.specJson().isBlank()) {
            // 해시 비교 — 불일치면 재전송 필요 알림
            if (request.specHash() != null && !request.specHash().equals(spec.getSpecHash())) {
                spec.heartbeat();
                log.info("Hash mismatch detected, resend needed: {} ({})", request.name(), request.environment());
                return new SpecRegisterResponse(
                        spec.getId(), spec.getName(), spec.getEnvironment(),
                        spec.getStatus(), "hash mismatch, resend spec");
            }
            spec.heartbeat();
            log.info("Heartbeat received: {} ({})", request.name(), request.environment());
            return new SpecRegisterResponse(
                    spec.getId(), spec.getName(), spec.getEnvironment(),
                    spec.getStatus(), "heartbeat accepted");
        }

        // 비동기 처리 대상인지 판단
        if (isAsyncRequired(request.specJson())) {
            spec.markRegistering();
            log.info("Async spec update started: {} ({})", request.name(), request.environment());
            final Long specId = spec.getId();
            final String specJson = request.specJson();
            final String baseUrl = request.baseUrl();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    specAsyncProcessor.processSpecAsync(specId, specJson, baseUrl);
                }
            });
            return new SpecRegisterResponse(
                    spec.getId(), spec.getName(), spec.getEnvironment(),
                    SpecStatus.REGISTERING, "async processing");
        }

        // specJson이 있으면 전체 갱신 (동기)
        String newHash = computeHash(request.specJson());
        spec.updateSpec(request.specJson(), newHash, request.baseUrl());
        log.info("Spec updated: {} ({})", request.name(), request.environment());

        // 스펙 갱신 후 관련 레시피 백그라운드 검증 트리거
        recipeSpecValidator.validateAllForSubdomain(request.name(), request.environment());

        return new SpecRegisterResponse(
                spec.getId(), spec.getName(), spec.getEnvironment(),
                spec.getStatus(), "spec updated");
    }

    private SpecRegisterResponse handleNew(SpecRegisterRequest request) {
        // 비동기 처리 대상인지 판단
        if (request.specJson() != null && isAsyncRequired(request.specJson())) {
            SubdomainSpec spec = SubdomainSpec.builder()
                    .name(request.name())
                    .environment(request.environment())
                    .baseUrl(request.baseUrl())
                    .specJson(null)
                    .specHash(null)
                    .status(SpecStatus.REGISTERING)
                    .build();

            // 인증 프로필 저장
            updateAuthProfilesIfPresent(spec, request);

            specRepository.save(spec);
            log.info("Async spec registration started: {} ({})", request.name(), request.environment());
            final Long specId = spec.getId();
            final String specJson = request.specJson();
            final String baseUrl = request.baseUrl();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    specAsyncProcessor.processSpecAsync(specId, specJson, baseUrl);
                }
            });

            return new SpecRegisterResponse(
                    spec.getId(), spec.getName(), spec.getEnvironment(),
                    spec.getStatus(), "async processing");
        }

        // 동기 처리
        String hash = request.specJson() != null ? computeHash(request.specJson()) : null;

        SubdomainSpec spec = SubdomainSpec.builder()
                .name(request.name())
                .environment(request.environment())
                .baseUrl(request.baseUrl())
                .specJson(request.specJson())
                .specHash(hash)
                .status(SpecStatus.ACTIVE)
                .build();

        // 인증 프로필 저장
        updateAuthProfilesIfPresent(spec, request);

        specRepository.save(spec);
        log.info("Spec registered: {} ({})", request.name(), request.environment());

        return new SpecRegisterResponse(
                spec.getId(), spec.getName(), spec.getEnvironment(),
                spec.getStatus(), "spec registered");
    }

    /**
     * specJson 크기가 asyncThreshold 이상인지 판단.
     */
    private boolean isAsyncRequired(String specJson) {
        return specJson.getBytes(StandardCharsets.UTF_8).length >= asyncThreshold;
    }

    private String computeHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 서브도메인 상세 조회 — OpenAPI JSON에서 API 엔드포인트 목록을 파싱하여 반환.
     */
    public SpecDetailResponse getDetail(String name, String environment) {
        SubdomainSpec spec = specRepository.findByNameAndEnvironment(name, environment)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        List<ApiEndpointResponse> endpoints = parseEndpoints(spec.getSpecJson());
        return SpecDetailResponse.from(spec, endpoints);
    }

    private List<ApiEndpointResponse> parseEndpoints(String specJson) {
        if (specJson == null || specJson.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(specJson);
            JsonNode paths = root.get("paths");
            if (paths == null || !paths.isObject()) {
                return List.of();
            }

            List<ApiEndpointResponse> endpoints = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> pathIter = paths.fields();

            while (pathIter.hasNext()) {
                Map.Entry<String, JsonNode> pathEntry = pathIter.next();
                String path = pathEntry.getKey();
                JsonNode methods = pathEntry.getValue();

                Iterator<Map.Entry<String, JsonNode>> methodIter = methods.fields();
                while (methodIter.hasNext()) {
                    Map.Entry<String, JsonNode> methodEntry = methodIter.next();
                    String method = methodEntry.getKey().toUpperCase();
                    JsonNode operation = methodEntry.getValue();

                    if (!isHttpMethod(method)) continue;

                    String summary = operation.path("summary").asText("");
                    String tag = "";
                    JsonNode tags = operation.get("tags");
                    if (tags != null && tags.isArray() && !tags.isEmpty()) {
                        tag = tags.get(0).asText("");
                    }

                    endpoints.add(new ApiEndpointResponse(method, path, summary, tag));
                }
            }

            return endpoints;
        } catch (Exception e) {
            log.warn("Failed to parse endpoints: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean isHttpMethod(String method) {
        return method.equals("GET") || method.equals("POST") ||
               method.equals("PUT") || method.equals("DELETE") || method.equals("PATCH");
    }

    /**
     * 요청에 authProfiles가 포함되어 있으면 JSON 직렬화하여 엔티티에 저장.
     */
    private void updateAuthProfilesIfPresent(SubdomainSpec spec, SpecRegisterRequest request) {
        if (request.authProfiles() != null && !request.authProfiles().isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(request.authProfiles());
                spec.updateAuthProfiles(json);
            } catch (Exception e) {
                log.warn("Failed to serialize authProfiles: {}", e.getMessage());
            }
        }
    }
}
