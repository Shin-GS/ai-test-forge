package com.aitestforge.dto.spec;

import com.aitestforge.domain.spec.SpecStatus;
import com.aitestforge.domain.spec.SubdomainSpec;

import java.time.LocalDateTime;

public record SpecResponse(
        Long id,
        String name,
        String environment,
        String baseUrl,
        SpecStatus status,
        String description,
        LocalDateTime registeredAt,
        LocalDateTime lastHeartbeatAt
) {
    public static SpecResponse from(SubdomainSpec spec) {
        return new SpecResponse(
                spec.getId(),
                spec.getName(),
                spec.getEnvironment(),
                spec.getBaseUrl(),
                spec.getStatus(),
                spec.getDescription(),
                spec.getRegisteredAt(),
                spec.getLastHeartbeatAt()
        );
    }
}
