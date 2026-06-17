package com.aitestforge.dto.auth.response;

public record LoginResponse(
        String token,
        String email,
        String name,
        Boolean otpRequired
) {
    // OTP 미사용 유저 로그인 성공
    public static LoginResponse success(String token, String email, String name) {
        return new LoginResponse(token, email, name, null);
    }

    // OTP 필요 (2단계 인증 대기)
    public static LoginResponse requireOtp() {
        return new LoginResponse(null, null, null, true);
    }
}
