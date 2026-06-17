package com.aitestforge.domain.spec;

import com.aitestforge.domain.spec.enums.SpecStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "SUBDOMAIN_SPEC", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"NAME", "ENVIRONMENT"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SubdomainSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "ENVIRONMENT", nullable = false)
    private String environment;

    @Column(name = "BASE_URL", nullable = false)
    private String baseUrl;

    @Lob
    @Column(name = "SPEC_JSON", columnDefinition = "LONGTEXT")
    private String specJson;

    @Column(name = "SPEC_HASH")
    private String specHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false)
    @Builder.Default
    private SpecStatus status = SpecStatus.ACTIVE;

    @Column(name = "DESCRIPTION")
    private String description;

    @Lob
    @Column(name = "AUTH_PROFILES_JSON", columnDefinition = "TEXT")
    private String authProfilesJson;

    @Column(name = "REGISTERED_AT", nullable = false)
    @Builder.Default
    private LocalDateTime registeredAt = LocalDateTime.now();

    @Column(name = "LAST_HEARTBEAT_AT", nullable = false)
    @Builder.Default
    private LocalDateTime lastHeartbeatAt = LocalDateTime.now();

    public void updateSpec(String specJson, String specHash, String baseUrl) {
        this.specJson = specJson;
        this.specHash = specHash;
        this.baseUrl = baseUrl;
        this.lastHeartbeatAt = LocalDateTime.now();
        this.status = SpecStatus.ACTIVE;
    }

    /**
     * 인증 프로필 메타 정보 갱신.
     */
    public void updateAuthProfiles(String authProfilesJson) {
        this.authProfilesJson = authProfilesJson;
    }

    public void heartbeat() {
        this.lastHeartbeatAt = LocalDateTime.now();
        if (this.status == SpecStatus.STALE) {
            this.status = SpecStatus.ACTIVE;
        }
    }

    public void markStale() {
        this.status = SpecStatus.STALE;
    }

    /**
     * 비동기 파싱 완료 후 ACTIVE 상태로 전환.
     */
    public void activate() {
        this.status = SpecStatus.ACTIVE;
        this.lastHeartbeatAt = LocalDateTime.now();
    }

    /**
     * 비동기 파싱 시작 — REGISTERING 상태로 전환.
     */
    public void markRegistering() {
        this.status = SpecStatus.REGISTERING;
    }
}
