package com.aitestforge.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 주기적으로 heartbeat를 메인 서버에 전송하는 스케줄러.
 * heartbeatInterval 주기로 실행되며, JSON 해시만 전송한다.
 */
@Slf4j
@RequiredArgsConstructor
public class SpecPushScheduler {

    private final SpecRegistrationService registrationService;

    @Scheduled(fixedDelayString = "${ai-test-forge.heartbeat-interval:30000}")
    public void heartbeat() {
        registrationService.sendHeartbeat();
    }
}
