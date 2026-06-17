package com.aitestforge.service.auth;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.auth.User;
import com.aitestforge.dto.auth.ChangePasswordRequest;
import com.aitestforge.dto.auth.LoginRequest;
import com.aitestforge.dto.auth.LoginResponse;
import com.aitestforge.dto.auth.OtpLoginRequest;
import com.aitestforge.dto.auth.OtpSetupResponse;
import com.aitestforge.dto.auth.OtpVerifyRequest;
import com.aitestforge.dto.auth.RegisterRequest;
import com.aitestforge.dto.auth.UserResponse;
import com.aitestforge.infra.auth.JwtTokenProvider;
import com.aitestforge.infra.auth.RateLimiter;
import com.aitestforge.repository.UserRepository;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String OTP_ISSUER = "AI Test Forge";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RateLimiter rateLimiter;

    public LoginResponse login(LoginRequest request) {
        String rateLimitKey = "login:" + request.email();
        if (rateLimiter.isRateLimited(rateLimitKey)) {
            throw new BusinessException(ErrorCode.AGENT_LOOP_CONCURRENT_LIMIT);
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // OTP 활성화 유저는 2단계 인증 필요
        if (user.isOtpEnabled()) {
            log.info("OTP required for user: {}", user.getEmail());
            return LoginResponse.requireOtp();
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        log.info("User logged in: {}", user.getEmail());
        rateLimiter.reset(rateLimitKey);

        return LoginResponse.success(token, user.getEmail(), user.getName());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .build();

        userRepository.save(user);
        log.info("User registered: {}", user.getEmail());

        return UserResponse.from(user);
    }

    public UserResponse getCurrentUser(User user) {
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        log.info("Password changed for user: {}", user.getEmail());
    }

    // --- OTP 관련 메서드 ---

    @Transactional
    public OtpSetupResponse setupOtp(User user) {
        if (user.isOtpEnabled()) {
            throw new BusinessException(ErrorCode.OTP_ALREADY_ENABLED);
        }

        SecretGenerator secretGenerator = new DefaultSecretGenerator();
        String secret = secretGenerator.generate();

        // secret을 임시 저장 (verify 전까지는 otpEnabled=false 유지)
        user.setOtpSecret(secret);

        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer(OTP_ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        String otpAuthUrl = qrData.getUri();

        log.info("OTP setup initiated for user: {}", user.getEmail());
        return new OtpSetupResponse(secret, otpAuthUrl);
    }

    @Transactional
    public void verifyOtp(User user, OtpVerifyRequest request) {
        if (user.isOtpEnabled()) {
            throw new BusinessException(ErrorCode.OTP_ALREADY_ENABLED);
        }

        if (user.getOtpSecret() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (!isValidOtpCode(user.getOtpSecret(), request.code())) {
            throw new BusinessException(ErrorCode.OTP_INVALID_CODE);
        }

        user.enableOtp(user.getOtpSecret());
        log.info("OTP enabled for user: {}", user.getEmail());
    }

    @Transactional
    public void disableOtp(User user) {
        if (!user.isOtpEnabled()) {
            throw new BusinessException(ErrorCode.OTP_NOT_ENABLED);
        }

        user.disableOtp();
        log.info("OTP disabled for user: {}", user.getEmail());
    }

    public LoginResponse otpLogin(OtpLoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));

        if (!user.isOtpEnabled()) {
            throw new BusinessException(ErrorCode.OTP_NOT_ENABLED);
        }

        if (!isValidOtpCode(user.getOtpSecret(), request.code())) {
            throw new BusinessException(ErrorCode.OTP_INVALID_CODE);
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        log.info("User logged in with OTP: {}", user.getEmail());

        return LoginResponse.success(token, user.getEmail(), user.getName());
    }

    private boolean isValidOtpCode(String secret, String code) {
        CodeVerifier verifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6),
                new SystemTimeProvider()
        );
        return verifier.isValidCode(secret, code);
    }
}
