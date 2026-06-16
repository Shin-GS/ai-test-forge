package com.aitestforge.dto.spec;

import com.aitestforge.domain.spec.SpecStatus;
import com.aitestforge.domain.spec.SubdomainSpec;

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
        LocalDateTime registeredAt,
        LocalDateTime lastHeartbeatAt
) {
    public static SpecDetailResponse from(SubdomainSpec spec, List<ApiEndpointResponse> endpoints) {
        return new SpecDetailResponse(
                spec.getId(),
                spec.getName(),
                spec.getEnvironment(),
                spec.getBaseUrl(),
                spec.getStatus(),
                spec.getDescription(),
                endpoints.size(),
                endpoints,
                spec.getRegisteredAt(),
                spec.getLastHeartbeatAt()
        );
    }
}
