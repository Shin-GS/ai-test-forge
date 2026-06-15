package com.aitestforge.service.spec;

import com.aitestforge.domain.spec.SpecStatus;
import com.aitestforge.domain.spec.SubdomainSpec;
import com.aitestforge.dto.spec.SpecRegisterRequest;
import com.aitestforge.dto.spec.SpecRegisterResponse;
import com.aitestforge.dto.spec.SpecResponse;
import com.aitestforge.repository.SubdomainSpecRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpecService {

    private final SubdomainSpecRepository specRepository;

    /**
     * 서브도메인 API 스펙 등록/갱신/heartbeat 겸용.
     * - specJson이 있으면: 등록 또는 전체 갱신
     * - specJson이 없고 specHash만 있으면: heartbeat (해시 일치 시 갱신 불필요)
     */
    @Transactional
    public SpecRegisterResponse register(SpecRegisterRequest request) {
        Optional<SubdomainSpec> existing = specRepository.findByNameAndEnvironment(
                request.name(), request.environment());

        if (existing.isPresent()) {
            return handleExisting(existing.get(), request);
        } else {
            return handleNew(request);
        }
    }

    public List<SpecResponse> getAll() {
        return specRepository.findAll().stream()
                .map(SpecResponse::from)
                .toList();
    }

    public Optional<SpecResponse> getByNameAndEnvironment(String name, String environment) {
        return specRepository.findByNameAndEnvironment(name, environment)
                .map(SpecResponse::from);
    }

    private SpecRegisterResponse handleExisting(SubdomainSpec spec, SpecRegisterRequest request) {
        // specJson이 없으면 heartbeat만
        if (request.specJson() == null || request.specJson().isBlank()) {
            // 해시 비교 — 불일치면 재전송 필요 알림
            if (request.specHash() != null && !request.specHash().equals(spec.getSpecHash())) {
                spec.heartbeat();
                log.info("Hash mismatch detected, resend needed: {} ({})", request.name(), request.environment());
                return new SpecRegisterResponse(
                        spec.getId(), spec.getName(), spec.getEnvironment(),
                        spec.getStatus(), "hash mismatch, resend spec");
            }
            spec.heartbeat();
            log.info("Heartbeat received: {} ({})", request.name(), request.environment());
            return new SpecRegisterResponse(
                    spec.getId(), spec.getName(), spec.getEnvironment(),
                    spec.getStatus(), "heartbeat accepted");
        }

        // specJson이 있으면 전체 갱신
        String newHash = computeHash(request.specJson());
        spec.updateSpec(request.specJson(), newHash, request.baseUrl());
        log.info("Spec updated: {} ({})", request.name(), request.environment());

        return new SpecRegisterResponse(
                spec.getId(), spec.getName(), spec.getEnvironment(),
                spec.getStatus(), "spec updated");
    }

    private SpecRegisterResponse handleNew(SpecRegisterRequest request) {
        String hash = request.specJson() != null ? computeHash(request.specJson()) : null;

        SubdomainSpec spec = SubdomainSpec.builder()
                .name(request.name())
                .environment(request.environment())
                .baseUrl(request.baseUrl())
                .specJson(request.specJson())
                .specHash(hash)
                .status(SpecStatus.ACTIVE)
                .build();

        specRepository.save(spec);
        log.info("Spec registered: {} ({})", request.name(), request.environment());

        return new SpecRegisterResponse(
                spec.getId(), spec.getName(), spec.getEnvironment(),
                spec.getStatus(), "spec registered");
    }

    private String computeHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
