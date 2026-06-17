package com.aitestforge.dto.chat.response;

import com.aitestforge.domain.chat.ChatSession;
import com.aitestforge.domain.chat.enums.SessionStatus;

import java.time.LocalDateTime;

public record SessionResponse(
        Long id,
        String title,
        SessionStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SessionResponse from(ChatSession session) {
        return new SessionResponse(
                session.getId(),
                session.getTitle(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
