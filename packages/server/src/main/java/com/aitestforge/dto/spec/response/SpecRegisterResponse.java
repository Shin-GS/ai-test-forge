package com.aitestforge.dto.spec.response;

import com.aitestforge.domain.spec.enums.SpecStatus;

public record SpecRegisterResponse(
        Long id,
        String name,
        String environment,
        SpecStatus status,
        String message
) {
}
