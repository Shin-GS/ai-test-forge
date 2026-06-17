package com.aitestforge.domain.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "USERS")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "EMAIL", nullable = false, unique = true)
    private String email;

    @Column(name = "PASSWORD", nullable = false)
    private String password;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "OTP_SECRET")
    private String otpSecret;

    @Column(name = "OTP_ENABLED", nullable = false)
    @Builder.Default
    private boolean otpEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "ROLE", nullable = false)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Column(name = "CREATED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void enableOtp(String secret) {
        this.otpSecret = secret;
        this.otpEnabled = true;
    }

    public void disableOtp() {
        this.otpSecret = null;
        this.otpEnabled = false;
    }

    public void setOtpSecret(String otpSecret) {
        this.otpSecret = otpSecret;
    }
}
