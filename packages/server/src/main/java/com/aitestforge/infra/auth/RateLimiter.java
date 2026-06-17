package com.aitestforge.infra.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 인메모리 기반 Rate Limiter.
 * 로그인/OTP 시도 횟수를 제한하여 brute-force 공격을 방지한다.
 * 키별 (IP 또는 이메일) 시간 윈도우 내 최대 시도 횟수를 제한.
 */
@Slf4j
@Component
public class RateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 300; // 5분

    private final ConcurrentHashMap<String, RateEntry> entries = new ConcurrentHashMap<>();

    /**
     * 시도를 기록하고 한도 초과 여부를 반환한다.
     *
     * @param key 제한 키 (예: "login:user@email.com")
     * @return true면 한도 초과 (요청 거부해야 함)
     */
    public boolean isRateLimited(String key) {
        Instant now = Instant.now();
        RateEntry entry = entries.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new RateEntry(now);
            }
            existing.increment();
            return existing;
        });

        if (entry.getCount() > MAX_ATTEMPTS) {
            log.warn("Rate limit exceeded for key: {}", key);
            return true;
        }
        return false;
    }

    /**
     * 인증 성공 시 해당 키의 카운터를 초기화한다.
     */
    public void reset(String key) {
        entries.remove(key);
    }

    private static class RateEntry {
        private final Instant windowStart;
        private final AtomicInteger count;

        RateEntry(Instant windowStart) {
            this.windowStart = windowStart;
            this.count = new AtomicInteger(1);
        }

        boolean isExpired(Instant now) {
            return now.getEpochSecond() - windowStart.getEpochSecond() > WINDOW_SECONDS;
        }

        void increment() {
            count.incrementAndGet();
        }

        int getCount() {
            return count.get();
        }
    }
}
