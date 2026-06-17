package com.aitestforge.dto.auth;

public record OtpSetupResponse(
        String secret,
        String otpAuthUrl
) {
}
