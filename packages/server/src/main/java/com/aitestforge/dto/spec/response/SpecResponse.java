package com.aitestforge.dto.spec.response;

import com.aitestforge.domain.spec.SubdomainSpec;
import com.aitestforge.domain.spec.enums.SpecStatus;
import com.aitestforge.dto.spec.AuthProfileDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

public record SpecResponse(
        Long id,
        String name,
        String environment,
        String baseUrl,
        SpecStatus status,
        String description,
        List<AuthProfileDto> authProfiles,
        LocalDateTime registeredAt,
        LocalDateTime lastHeartbeatAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static SpecResponse from(SubdomainSpec spec) {
        return new SpecResponse(
                spec.getId(),
                spec.getName(),
                spec.getEnvironment(),
                spec.getBaseUrl(),
                spec.getStatus(),
                spec.getDescription(),
                parseAuthProfiles(spec.getAuthProfilesJson()),
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
