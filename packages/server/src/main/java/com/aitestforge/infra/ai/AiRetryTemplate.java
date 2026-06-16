package com.aitestforge.infra.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.function.Supplier;

/**
 * AI API 호출 시 재시도 + rate limit 대응을 위한 공통 템플릿.
 * 429 (Rate Limit) 또는 5xx 에러 시 exponential backoff로 재시도한다.
 *
 * <p>외부 라이브러리(Resilience4j 등) 없이 순수 구현.</p>
 */
@Slf4j
@Component
public class AiRetryTemplate {

    private final int maxAttempts;
    private final long initialDelayMs;
    private final double multiplier;

    public AiRetryTemplate(
            @Value("${ai.retry.max-attempts:3}") int maxAttempts,
            @Value("${ai.retry.initial-delay-ms:1000}") long initialDelayMs,
            @Value("${ai.retry.multiplier:2.0}") double multiplier) {
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.multiplier = multiplier;
    }

    /**
     * 재시도 가능한 AI API 호출을 실행한다.
     *
     * @param action       실행할 API 호출 (Supplier)
     * @param providerName 로그용 프로바이더 이름 (예: "OpenAI", "Claude")
     * @param <T>          응답 타입
     * @return API 호출 결과
     * @throws RuntimeException 최대 재시도 횟수 초과 시 마지막 예외를 그대로 전파
     */
    public <T> T execute(Supplier<T> action, String providerName) {
        long delay = initialDelayMs;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                if (!isRetryable(e) || attempt >= maxAttempts) {
                    log.error("{} API call failed after {} attempt(s): {}",
                            providerName, attempt, e.getMessage());
                    throw e;
                }

                log.warn("{} API call failed (attempt {}/{}), retrying in {}ms: {}",
                        providerName, attempt, maxAttempts, delay, e.getMessage());
                sleep(delay);
                delay = (long) (delay * multiplier);
            }
        }

        // 논리적으로 도달 불가 (for 루프 내에서 항상 return 또는 throw)
        throw new IllegalStateException("Unreachable: retry loop exited unexpectedly");
    }

    /**
     * 재시도 대상 예외인지 판별한다.
     * - HttpClientErrorException (429 Too Many Requests)
     * - HttpServerErrorException (500, 502, 503, 504)
     * - 메시지에 상태코드가 포함된 RuntimeException (RestClient wrapping)
     */
    private boolean isRetryable(Exception e) {
        // Spring RestClient는 4xx → HttpClientErrorException, 5xx → HttpServerErrorException으로 래핑
        if (e instanceof HttpClientErrorException clientError) {
            return clientError.getStatusCode().value() == 429;
        }
        if (e instanceof HttpServerErrorException) {
            return true; // 5xx 전부 재시도
        }

        // RestClient가 아닌 래핑된 RuntimeException인 경우 메시지 기반 판별
        String msg = e.getMessage();
        if (msg == null) {
            return false;
        }
        return msg.contains("429") || msg.contains("500")
                || msg.contains("502") || msg.contains("503") || msg.contains("504");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", ie);
        }
    }
}
