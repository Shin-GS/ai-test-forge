package com.aitestforge.dto.auth;

public record LoginResponse(
        String token,
        String email,
        String name
) {
}
