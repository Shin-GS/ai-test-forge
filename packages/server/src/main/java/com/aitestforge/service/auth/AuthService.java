package com.aitestforge.service.auth;

import com.aitestforge.common.exception.BusinessException;
import com.aitestforge.common.exception.ErrorCode;
import com.aitestforge.domain.auth.User;
import com.aitestforge.dto.auth.LoginRequest;
import com.aitestforge.dto.auth.LoginResponse;
import com.aitestforge.dto.auth.RegisterRequest;
import com.aitestforge.dto.auth.UserResponse;
import com.aitestforge.infra.auth.JwtTokenProvider;
import com.aitestforge.repository.UserRepository;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        log.info("User logged in: {}", user.getEmail());

        return new LoginResponse(token, user.getEmail(), user.getName());
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
}
