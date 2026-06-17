package com.aitestforge.dto.auth.request;

import jakarta.validation.constraints.NotBlank;

public record OtpVerifyRequest(
        @NotBlank(message = "OTP code is required")
        String code
) {
}
