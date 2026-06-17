package com.aitestforge.domain.chat;

import com.aitestforge.domain.chat.enums.MessageRole;
import com.aitestforge.domain.chat.enums.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChatSession 엔티티 테스트")
class ChatSessionTest {

    @Nested
    @DisplayName("addMessage")
    class AddMessage {

        @Test
        @DisplayName("정상: 메시지 추가 시 session 참조가 설정된다")
        void success_sets_session_reference() {
            // given
            ChatSession session = createSession();
            ChatMessage message = ChatMessage.builder()
                    .role(MessageRole.USER).content("hello").build();

            // when
            session.addMessage(message);

            // then
            assertThat(session.getMessages()).hasSize(1);
            assertThat(message.getSession()).isEqualTo(session);
        }
    }

    @Nested
    @DisplayName("waitForUser")
    class WaitForUser {

        @Test
        @DisplayName("정상: ACTIVE → WAITING 전이")
        void success_active_to_waiting() {
            ChatSession session = createSession();
            session.waitForUser();
            assertThat(session.getStatus()).isEqualTo(SessionStatus.WAITING);
        }

        @Test
        @DisplayName("실패: COMPLETED에서 호출 시 예외")
        void fail_from_completed() {
            ChatSession session = createSession();
            session.complete();
            assertThatThrownBy(session::waitForUser)
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("실패: WAITING에서 호출 시 예외")
        void fail_from_waiting() {
            ChatSession session = createSession();
            session.waitForUser();
            assertThatThrownBy(session::waitForUser)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("resume")
    class Resume {

        @Test
        @DisplayName("정상: WAITING → ACTIVE 전이")
        void success_waiting_to_active() {
            ChatSession session = createSession();
            session.waitForUser();
            session.resume();
            assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        }

        @Test
        @DisplayName("실패: ACTIVE에서 호출 시 예외")
        void fail_from_active() {
            ChatSession session = createSession();
            assertThatThrownBy(session::resume)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("complete")
    class Complete {

        @Test
        @DisplayName("정상: ACTIVE → COMPLETED 전이")
        void success_active_to_completed() {
            ChatSession session = createSession();
            session.complete();
            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        }
    }

    // === Helper Methods ===

    private ChatSession createSession() {
        return ChatSession.builder().userId(1L).title("테스트").build();
    }
}
