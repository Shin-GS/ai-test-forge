package com.aitestforge.dto.spec;

import com.aitestforge.domain.spec.SpecStatus;

public record SpecRegisterResponse(
        Long id,
        String name,
        String environment,
        SpecStatus status,
        String message
) {
}
