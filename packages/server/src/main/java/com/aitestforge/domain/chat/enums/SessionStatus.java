package com.aitestforge.domain.chat.enums;

import com.aitestforge.domain.common.EnumColumn;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 채팅 세션 상태
 */
@Getter
@RequiredArgsConstructor
public enum SessionStatus implements EnumColumn {
    ACTIVE("ACTIVE", "진행 중"),
    COMPLETED("COMPLETED", "완료"),
    WAITING("WAITING", "대기 중");

    private final String code;
    private final String description;
}
