package com.aitestforge.dto.chat.response;

import com.aitestforge.domain.chat.ChatMessage;
import com.aitestforge.domain.chat.enums.MessageRole;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        MessageRole role,
        String content,
        String toolCallId,
        LocalDateTime createdAt
) {
    public static MessageResponse from(ChatMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getToolCallId(),
                message.getCreatedAt()
        );
    }
}
