package com.aitestforge.controller.auth;

import com.aitestforge.domain.auth.User;
import com.aitestforge.dto.auth.request.ChangePasswordRequest;
import com.aitestforge.dto.auth.request.LoginRequest;
import com.aitestforge.dto.auth.request.OtpLoginRequest;
import com.aitestforge.dto.auth.request.OtpVerifyRequest;
import com.aitestforge.dto.auth.request.RegisterRequest;
import com.aitestforge.dto.auth.response.LoginResponse;
import com.aitestforge.dto.auth.response.OtpSetupResponse;
import com.aitestforge.dto.auth.response.UserResponse;
import com.aitestforge.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하여 JWT 토큰을 발급받습니다. OTP 활성화 유저는 otpRequired=true를 반환합니다.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "회원가입", description = "새 계정을 생성합니다.")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @Operation(summary = "현재 사용자 조회", description = "현재 로그인한 사용자의 정보를 조회합니다.")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(authService.getCurrentUser(user));
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인한 후 새 비밀번호로 변경합니다.")
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user, request);
        return ResponseEntity.noContent().build();
    }

    // --- OTP 엔드포인트 ---

    @Operation(summary = "OTP 설정", description = "TOTP secret을 생성하고 QR 코드용 otpauth:// URI를 반환합니다.")
    @PostMapping("/otp/setup")
    public ResponseEntity<OtpSetupResponse> setupOtp(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(authService.setupOtp(user));
    }

    @Operation(summary = "OTP 검증 및 활성화", description = "사용자가 입력한 OTP 코드를 검증하고, 성공 시 OTP를 활성화합니다.")
    @PostMapping("/otp/verify")
    public ResponseEntity<Void> verifyOtp(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody OtpVerifyRequest request) {
        authService.verifyOtp(user, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "OTP 비활성화", description = "OTP 2단계 인증을 비활성화합니다.")
    @DeleteMapping("/otp")
    public ResponseEntity<Void> disableOtp(@AuthenticationPrincipal User user) {
        authService.disableOtp(user);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "OTP 로그인", description = "OTP 활성화 유저가 이메일과 OTP 코드로 최종 인증하여 토큰을 발급받습니다.")
    @PostMapping("/otp/login")
    public ResponseEntity<LoginResponse> otpLogin(@Valid @RequestBody OtpLoginRequest request) {
        return ResponseEntity.ok(authService.otpLogin(request));
    }
}
