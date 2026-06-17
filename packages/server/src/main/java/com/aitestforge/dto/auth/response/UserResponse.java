package com.aitestforge.dto.auth.response;

import com.aitestforge.domain.auth.User;
import com.aitestforge.domain.auth.enums.UserRole;

public record UserResponse(
        Long id,
        String email,
        String name,
        UserRole role,
        boolean otpEnabled
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole(), user.isOtpEnabled());
    }
}
