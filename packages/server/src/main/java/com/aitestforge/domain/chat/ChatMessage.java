package com.aitestforge.domain.chat;

import com.aitestforge.domain.chat.enums.MessageRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "CHAT_MESSAGE")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SESSION_ID", nullable = false)
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false)
    private MessageRole role;

    @Lob
    @Column(name = "CONTENT", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "TOOL_CALL_ID")
    private String toolCallId;

    @Column(name = "CREATED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
