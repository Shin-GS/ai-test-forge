package com.aitestforge.dto.spec;

import com.aitestforge.domain.spec.SpecStatus;
import com.aitestforge.domain.spec.SubdomainSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

public record SpecDetailResponse(
        Long id,
        String name,
        String environment,
        String baseUrl,
        SpecStatus status,
        String description,
        int apiCount,
        List<ApiEndpointResponse> endpoints,
        List<AuthProfileDto> authProfiles,
        List<ExcludedApiResponse> excludedApis,
        LocalDateTime registeredAt,
        LocalDateTime lastHeartbeatAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static SpecDetailResponse from(SubdomainSpec spec, List<ApiEndpointResponse> endpoints) {
        return from(spec, endpoints, List.of());
    }

    public static SpecDetailResponse from(SubdomainSpec spec, List<ApiEndpointResponse> endpoints,
                                           List<ExcludedApiResponse> excludedApis) {
        return new SpecDetailResponse(
                spec.getId(),
                spec.getName(),
                spec.getEnvironment(),
                spec.getBaseUrl(),
                spec.getStatus(),
                spec.getDescription(),
                endpoints.size(),
                endpoints,
                parseAuthProfiles(spec.getAuthProfilesJson()),
                excludedApis,
                spec.getRegisteredAt(),
                spec.getLastHeartbeatAt()
        );
    }

    private static List<AuthProfileDto> parseAuthProfiles(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
