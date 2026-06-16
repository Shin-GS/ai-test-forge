package com.aitestforge.service.spec;

import com.aitestforge.domain.spec.SubdomainSpec;
import com.aitestforge.repository.SubdomainSpecRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 대형 스펙의 비동기 파싱 처리를 담당.
 * Spring AOP 프록시가 @Async를 정상 적용하도록 별도 빈으로 분리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpecAsyncProcessor {

    private final SubdomainSpecRepository specRepository;

    /**
     * 비동기 스펙 파싱 처리.
     * 파싱 완료 후 ACTIVE 상태로 전환한다.
     */
    @Async
    @Transactional
    public void processSpecAsync(Long specId, String specJson, String baseUrl) {
        try {
            log.info("Async spec processing started: specId={}", specId);
            String hash = computeHash(specJson);

            SubdomainSpec spec = specRepository.findById(specId)
                    .orElseThrow(() -> new IllegalStateException("Spec not found: " + specId));

            spec.updateSpec(specJson, hash, baseUrl);
            spec.activate();
            log.info("Async spec processing completed: specId={}, name={}", specId, spec.getName());
        } catch (Exception e) {
            log.error("Async spec processing failed: specId={}", specId, e);
            specRepository.findById(specId).ifPresent(spec -> {
                spec.markStale();
            });
        }
    }

    private String computeHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
