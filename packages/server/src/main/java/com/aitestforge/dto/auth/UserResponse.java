package com.aitestforge.dto.auth;

import com.aitestforge.domain.auth.User;
import com.aitestforge.domain.auth.UserRole;

public record UserResponse(
        Long id,
        String email,
        String name,
        UserRole role
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
