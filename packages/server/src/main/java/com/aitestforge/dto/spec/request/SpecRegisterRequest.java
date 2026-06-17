package com.aitestforge.dto.spec.request;

import com.aitestforge.dto.spec.AuthProfileDto;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record SpecRegisterRequest(
        @NotBlank String name,
        String environment,
        @NotBlank String baseUrl,
        String specJson,
        String specHash,
        List<AuthProfileDto> authProfiles
) {
    public SpecRegisterRequest {
        if (environment == null || environment.isBlank()) {
            environment = "default";
        }
    }
}
