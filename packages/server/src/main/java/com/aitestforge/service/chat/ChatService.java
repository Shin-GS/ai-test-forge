package com.aitestforge.service.chat;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.chat.ChatMessage;
import com.aitestforge.domain.chat.ChatSession;
import com.aitestforge.domain.chat.MessageRole;
import com.aitestforge.dto.chat.CreateSessionRequest;
import com.aitestforge.dto.chat.MessageResponse;
import com.aitestforge.dto.chat.SessionResponse;
import com.aitestforge.repository.ChatMessageRepository;
import com.aitestforge.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request, Long userId) {
        ChatSession session = ChatSession.builder()
                .title(request.title())
                .userId(userId)
                .build();
        sessionRepository.save(session);
        log.info("Chat session created: {} for user {}", session.getId(), userId);
        return SessionResponse.from(session);
    }

    public List<SessionResponse> getAllSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(SessionResponse::from)
                .toList();
    }

    public SessionResponse getSession(Long sessionId) {
        ChatSession session = findSessionOrThrow(sessionId);
        return SessionResponse.from(session);
    }

    public List<MessageResponse> getMessages(Long sessionId) {
        findSessionOrThrow(sessionId);
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(MessageResponse::from)
                .toList();
    }

    @Transactional
    public ChatMessage addUserMessage(Long sessionId, String content) {
        ChatSession session = findSessionOrThrow(sessionId);
        ChatMessage message = ChatMessage.builder()
                .role(MessageRole.USER)
                .content(content)
                .build();
        session.addMessage(message);
        return message;
    }

    @Transactional
    public ChatMessage addAssistantMessage(Long sessionId, String content) {
        ChatSession session = findSessionOrThrow(sessionId);
        ChatMessage message = ChatMessage.builder()
                .role(MessageRole.ASSISTANT)
                .content(content)
                .build();
        session.addMessage(message);
        return message;
    }

    @Transactional
    public ChatMessage addToolResultMessage(Long sessionId, String toolCallId, String content) {
        ChatSession session = findSessionOrThrow(sessionId);
        ChatMessage message = ChatMessage.builder()
                .role(MessageRole.TOOL)
                .toolCallId(toolCallId)
                .content(content)
                .build();
        session.addMessage(message);
        return message;
    }

    @Transactional
    public void completeSession(Long sessionId) {
        ChatSession session = findSessionOrThrow(sessionId);
        session.complete();
    }

    @Transactional
    public void waitSession(Long sessionId) {
        ChatSession session = findSessionOrThrow(sessionId);
        session.waitForUser();
        log.info("Chat session {} transitioned to WAITING", sessionId);
    }

    @Transactional
    public void resumeSession(Long sessionId) {
        ChatSession session = findSessionOrThrow(sessionId);
        session.resume();
        log.info("Chat session {} resumed from WAITING to ACTIVE", sessionId);
    }

    public boolean isWaiting(Long sessionId) {
        ChatSession session = findSessionOrThrow(sessionId);
        return session.getStatus() == com.aitestforge.domain.chat.SessionStatus.WAITING;
    }

    private ChatSession findSessionOrThrow(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
