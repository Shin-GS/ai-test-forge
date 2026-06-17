package com.aitestforge.service.auth;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.auth.User;
import com.aitestforge.domain.auth.enums.UserRole;
import com.aitestforge.dto.auth.request.LoginRequest;
import com.aitestforge.dto.auth.response.LoginResponse;
import com.aitestforge.dto.auth.request.OtpVerifyRequest;
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
import static org.mockito.BDDMockito.given;

@DisplayName("AuthService OTP 테스트")
@ExtendWith(MockitoExtension.class)
class AuthServiceOtpTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("login (OTP 활성 유저)")
    class LoginWithOtp {

        @Test
        @DisplayName("정상: OTP 활성 유저 로그인 시 otpRequired=true 반환")
        void success_returns_otp_required() {
            // given
            User user = createOtpUser();
            given(userRepository.findByEmail("otp@test.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("pass", user.getPassword())).willReturn(true);

            // when
            LoginResponse response = authService.login(new LoginRequest("otp@test.com", "pass"));

            // then
            assertThat(response.otpRequired()).isTrue();
            assertThat(response.token()).isNull();
        }
    }

    @Nested
    @DisplayName("setupOtp")
    class SetupOtp {

        @Test
        @DisplayName("정상: OTP 미활성 유저는 secret + URI를 받는다")
        void success_generates_secret() {
            User user = createUser();
            var result = authService.setupOtp(user);
            assertThat(result.secret()).isNotBlank();
            assertThat(result.otpAuthUrl()).contains("otpauth://totp/");
        }

        @Test
        @DisplayName("실패: 이미 OTP 활성화면 OTP_ALREADY_ENABLED")
        void fail_already_enabled() {
            User user = createOtpUser();
            assertThatThrownBy(() -> authService.setupOtp(user))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.OTP_ALREADY_ENABLED);
        }
    }

    @Nested
    @DisplayName("verifyOtp")
    class VerifyOtp {

        @Test
        @DisplayName("실패: OTP 이미 활성화면 OTP_ALREADY_ENABLED")
        void fail_already_enabled() {
            User user = createOtpUser();
            assertThatThrownBy(() -> authService.verifyOtp(user, new OtpVerifyRequest("123456")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.OTP_ALREADY_ENABLED);
        }

        @Test
        @DisplayName("실패: secret 없으면 INVALID_INPUT")
        void fail_no_secret() {
            User user = createUser();
            assertThatThrownBy(() -> authService.verifyOtp(user, new OtpVerifyRequest("123456")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INPUT);
        }
    }

    @Nested
    @DisplayName("disableOtp")
    class DisableOtp {

        @Test
        @DisplayName("정상: OTP 활성 유저는 비활성화 성공")
        void success_disables() {
            User user = createOtpUser();
            authService.disableOtp(user);
            assertThat(user.isOtpEnabled()).isFalse();
            assertThat(user.getOtpSecret()).isNull();
        }

        @Test
        @DisplayName("실패: OTP 미활성 유저면 OTP_NOT_ENABLED")
        void fail_not_enabled() {
            User user = createUser();
            assertThatThrownBy(() -> authService.disableOtp(user))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.OTP_NOT_ENABLED);
        }
    }

    // === Helper Methods ===

    private User createUser() {
        return User.builder()
                .id(1L).email("test@test.com").password("enc").name("테스트")
                .role(UserRole.USER).build();
    }

    private User createOtpUser() {
        return User.builder()
                .id(2L).email("otp@test.com").password("enc").name("OTP유저")
                .otpSecret("JBSWY3DPEHPK3PXP").otpEnabled(true)
                .role(UserRole.USER).build();
    }
}
