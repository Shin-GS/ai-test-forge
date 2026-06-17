package com.aitestforge.dto.auth.response;

public record OtpSetupResponse(
        String secret,
        String otpAuthUrl
) {
}
