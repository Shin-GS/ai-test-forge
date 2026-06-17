package com.aitestforge.service.chat;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.chat.ChatMessage;
import com.aitestforge.domain.chat.ChatSession;
import com.aitestforge.domain.chat.MessageRole;
import com.aitestforge.domain.chat.SessionStatus;
import com.aitestforge.dto.chat.CreateSessionRequest;
import com.aitestforge.dto.chat.SessionResponse;
import com.aitestforge.repository.ChatMessageRepository;
import com.aitestforge.repository.ChatSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@DisplayName("ChatService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @InjectMocks
    private ChatService chatService;

    @Nested
    @DisplayName("createSession")
    class CreateSession {

        @Test
        @DisplayName("정상: SessionResponse를 반환한다")
        void success_returns_session_response() {
            // given
            var request = new CreateSessionRequest("새 채팅");
            given(sessionRepository.save(any(ChatSession.class)))
                    .willAnswer(inv -> inv.getArgument(0));
            // when
            SessionResponse response = chatService.createSession(request, 1L);
            // then
            assertThat(response.title()).isEqualTo("새 채팅");
            assertThat(response.status()).isEqualTo(SessionStatus.ACTIVE);
            then(sessionRepository).should().save(any(ChatSession.class));
        }
    }

    @Nested
    @DisplayName("getSession")
    class GetSession {

        @Test
        @DisplayName("실패: 세션 미존재 시 RESOURCE_NOT_FOUND")
        void fail_session_not_found() {
            given(sessionRepository.findById(999L)).willReturn(Optional.empty());
            assertThatThrownBy(() -> chatService.getSession(999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("addUserMessage")
    class AddUserMessage {

        @Test
        @DisplayName("정상: USER 역할 메시지가 세션에 추가된다")
        void success_adds_user_message() {
            ChatSession session = createSession();
            given(sessionRepository.findById(1L)).willReturn(Optional.of(session));
            ChatMessage result = chatService.addUserMessage(1L, "안녕하세요");
            assertThat(result.getRole()).isEqualTo(MessageRole.USER);
            assertThat(result.getContent()).isEqualTo("안녕하세요");
            assertThat(session.getMessages()).contains(result);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 세션이면 예외")
        void fail_session_not_found() {
            given(sessionRepository.findById(999L)).willReturn(Optional.empty());
            assertThatThrownBy(() -> chatService.addUserMessage(999L, "test"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("waitSession")
    class WaitSession {

        @Test
        @DisplayName("정상: ACTIVE → WAITING")
        void success_active_to_waiting() {
            ChatSession session = createSession();
            given(sessionRepository.findById(1L)).willReturn(Optional.of(session));
            chatService.waitSession(1L);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.WAITING);
        }

        @Test
        @DisplayName("실패: COMPLETED → WAITING 시 예외")
        void fail_completed_to_waiting() {
            ChatSession session = createSession();
            session.complete();
            given(sessionRepository.findById(1L)).willReturn(Optional.of(session));
            assertThatThrownBy(() -> chatService.waitSession(1L))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("resumeSession")
    class ResumeSession {

        @Test
        @DisplayName("정상: WAITING → ACTIVE")
        void success_waiting_to_active() {
            ChatSession session = createSession();
            session.waitForUser();
            given(sessionRepository.findById(1L)).willReturn(Optional.of(session));
            chatService.resumeSession(1L);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("completeSession")
    class CompleteSession {

        @Test
        @DisplayName("정상: ACTIVE → COMPLETED")
        void success_active_to_completed() {
            ChatSession session = createSession();
            given(sessionRepository.findById(1L)).willReturn(Optional.of(session));
            chatService.completeSession(1L);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        }
    }

    // === Helper Methods ===

    private ChatSession createSession() {
        return ChatSession.builder().userId(1L).title("테스트").build();
    }
}
