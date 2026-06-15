package com.aitestforge.dto.spec;

import jakarta.validation.constraints.NotBlank;

public record SpecRegisterRequest(
        @NotBlank String name,
        String environment,
        @NotBlank String baseUrl,
        String specJson,
        String specHash
) {
    public SpecRegisterRequest {
        if (environment == null || environment.isBlank()) {
            environment = "default";
        }
    }
}
