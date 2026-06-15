package com.aitestforge.repository;

import com.aitestforge.domain.chat.ChatSession;
import com.aitestforge.domain.chat.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByStatusOrderByUpdatedAtDesc(SessionStatus status);

    List<ChatSession> findAllByOrderByUpdatedAtDesc();
}
