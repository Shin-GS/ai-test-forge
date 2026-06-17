package com.aitestforge.service.auth;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.auth.User;
import com.aitestforge.domain.auth.enums.UserRole;
import com.aitestforge.dto.auth.request.ChangePasswordRequest;
import com.aitestforge.dto.auth.request.LoginRequest;
import com.aitestforge.dto.auth.response.LoginResponse;
import com.aitestforge.dto.auth.request.RegisterRequest;
import com.aitestforge.dto.auth.response.UserResponse;
import com.aitestforge.infra.auth.JwtTokenProvider;
import com.aitestforge.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("정상: 올바른 이메일과 비밀번호로 로그인하면 토큰을 반환한다")
        void success_returns_token() {
            // given
            LoginRequest request = new LoginRequest("test@example.com", "password123");
            User user = createUser(1L, "test@example.com", "encodedPw", "테스트유저");

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("password123", "encodedPw")).willReturn(true);
            given(jwtTokenProvider.generateToken(1L, "test@example.com")).willReturn("jwt-token");

            // when
            LoginResponse response = authService.login(request);

            // then
            assertThat(response.token()).isEqualTo("jwt-token");
            assertThat(response.email()).isEqualTo("test@example.com");
            assertThat(response.name()).isEqualTo("테스트유저");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 이메일이면 BusinessException 발생")
        void fail_email_not_found() {
            // given
            LoginRequest request = new LoginRequest("notfound@example.com", "password123");
            given(userRepository.findByEmail("notfound@example.com")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("실패: 비밀번호가 일치하지 않으면 BusinessException 발생")
        void fail_password_mismatch() {
            // given
            LoginRequest request = new LoginRequest("test@example.com", "wrongPassword");
            User user = createUser(1L, "test@example.com", "encodedPw", "테스트유저");

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrongPassword", "encodedPw")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("정상: 새 이메일로 회원가입하면 UserResponse를 반환한다")
        void success_returns_user_response() {
            // given
            RegisterRequest request = new RegisterRequest("new@example.com", "password123", "새유저");
            User savedUser = createUser(1L, "new@example.com", "encodedPw", "새유저");

            given(userRepository.existsByEmail("new@example.com")).willReturn(false);
            given(passwordEncoder.encode("password123")).willReturn("encodedPw");
            given(userRepository.save(any(User.class))).willReturn(savedUser);

            // when
            UserResponse response = authService.register(request);

            // then
            assertThat(response.email()).isEqualTo("new@example.com");
            assertThat(response.name()).isEqualTo("새유저");
            then(userRepository).should().save(any(User.class));
        }

        @Test
        @DisplayName("실패: 이미 존재하는 이메일이면 BusinessException 발생")
        void fail_email_already_exists() {
            // given
            RegisterRequest request = new RegisterRequest("exists@example.com", "password123", "유저");
            given(userRepository.existsByEmail("exists@example.com")).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT);

            then(userRepository).should(org.mockito.Mockito.never()).save(any());
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("정상: 현재 비밀번호가 일치하면 비밀번호를 변경한다")
        void success_changes_password() {
            // given
            User user = createUser(1L, "test@example.com", "oldEncodedPw", "테스트유저");
            ChangePasswordRequest request = new ChangePasswordRequest("oldPassword", "newPassword");

            given(passwordEncoder.matches("oldPassword", "oldEncodedPw")).willReturn(true);
            given(passwordEncoder.encode("newPassword")).willReturn("newEncodedPw");

            // when
            authService.changePassword(user, request);

            // then
            assertThat(user.getPassword()).isEqualTo("newEncodedPw");
        }

        @Test
        @DisplayName("실패: 현재 비밀번호가 일치하지 않으면 PASSWORD_MISMATCH 예외 발생")
        void fail_current_password_mismatch() {
            // given
            User user = createUser(1L, "test@example.com", "encodedPw", "테스트유저");
            ChangePasswordRequest request = new ChangePasswordRequest("wrongCurrent", "newPassword");

            given(passwordEncoder.matches("wrongCurrent", "encodedPw")).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.changePassword(user, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PASSWORD_MISMATCH);
        }
    }

    // === Helper Methods ===

    private User createUser(Long id, String email, String password, String name) {
        return User.builder()
                .id(id)
                .email(email)
                .password(password)
                .name(name)
                .role(UserRole.USER)
                .build();
    }
}
