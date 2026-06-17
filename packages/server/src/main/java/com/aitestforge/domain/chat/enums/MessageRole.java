package com.aitestforge.domain.chat.enums;

import com.aitestforge.domain.common.EnumColumn;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 채팅 메시지 역할
 */
@Getter
@RequiredArgsConstructor
public enum MessageRole implements EnumColumn {
    USER("USER", "사용자"),
    ASSISTANT("ASSISTANT", "AI 어시스턴트"),
    TOOL("TOOL", "도구 실행 결과");

    private final String code;
    private final String description;
}
