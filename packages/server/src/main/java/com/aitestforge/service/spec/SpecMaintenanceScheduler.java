package com.aitestforge.service.spec;

import com.aitestforge.domain.spec.SpecStatus;
import com.aitestforge.domain.spec.SubdomainSpec;
import com.aitestforge.repository.SubdomainSpecRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 서브도메인 스펙 상태 유지보수 스케줄러.
 * - heartbeat 미응답 시 STALE 전환
 * - TTL 초과 시 자동 삭제
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpecMaintenanceScheduler {

    private final SubdomainSpecRepository specRepository;

    @Value("${spec-registry.stale-threshold:5m}")
    private Duration staleThreshold;

    @Value("${spec-registry.auto-delete-threshold:30m}")
    private Duration autoDeleteThreshold;

    @Scheduled(fixedDelay = 60_000) // 1분마다 실행
    @Transactional
    public void maintain() {
        LocalDateTime now = LocalDateTime.now();
        markStale(now);
        autoDelete(now);
    }

    private void markStale(LocalDateTime now) {
        LocalDateTime staleTime = now.minus(staleThreshold);
        List<SubdomainSpec> activeSpecs = specRepository.findByStatus(SpecStatus.ACTIVE);

        for (SubdomainSpec spec : activeSpecs) {
            if (spec.getLastHeartbeatAt().isBefore(staleTime)) {
                spec.markStale();
                log.info("Spec marked STALE: {} ({})", spec.getName(), spec.getEnvironment());
            }
        }
    }

    private void autoDelete(LocalDateTime now) {
        if (autoDeleteThreshold.isZero()) {
            return; // 자동 삭제 비활성화
        }

        LocalDateTime deleteTime = now.minus(autoDeleteThreshold);
        List<SubdomainSpec> staleSpecs = specRepository.findByLastHeartbeatAtBefore(deleteTime);

        if (!staleSpecs.isEmpty()) {
            specRepository.deleteAll(staleSpecs);
            staleSpecs.forEach(spec ->
                    log.info("Spec auto-deleted: {} ({})", spec.getName(), spec.getEnvironment()));
        }
    }
}
