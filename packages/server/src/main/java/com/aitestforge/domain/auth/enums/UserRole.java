package com.aitestforge.domain.auth.enums;

import com.aitestforge.domain.common.EnumColumn;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 역할
 */
@Getter
@RequiredArgsConstructor
public enum UserRole implements EnumColumn {
    ADMIN("ADMIN", "관리자"),
    USER("USER", "일반 사용자");

    private final String code;
    private final String description;
}
