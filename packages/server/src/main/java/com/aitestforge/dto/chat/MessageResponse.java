package com.aitestforge.dto.chat;

import com.aitestforge.domain.chat.ChatMessage;
import com.aitestforge.domain.chat.MessageRole;

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
