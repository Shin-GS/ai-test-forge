package com.aitestforge.infra.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AiRetryTemplateTest {

    private AiRetryTemplate aiRetryTemplate;

    @BeforeEach
    void setUp() {
        // maxAttempts=3, initialDelayMs=10(테스트 속도를 위해 짧게), multiplier=2.0
        aiRetryTemplate = new AiRetryTemplate(3, 10, 2.0);
    }

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("정상: 첫 시도에서 성공하면 결과 반환")
        void success_first_attempt_returns_result() {
            // when
            String result = aiRetryTemplate.execute(() -> "success", "TestProvider");

            // then
            assertThat(result).isEqualTo("success");
        }

        @Test
        @DisplayName("정상: 429 에러 후 재시도에서 성공")
        void success_retry_after_429_then_succeeds() {
            // given
            AtomicInteger attempts = new AtomicInteger(0);

            // when
            String result = aiRetryTemplate.execute(() -> {
                if (attempts.incrementAndGet() == 1) {
                    throw HttpClientErrorException.create(
                            HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                            null, null, null);
                }
                return "success after retry";
            }, "OpenAI");

            // then
            assertThat(result).isEqualTo("success after retry");
            assertThat(attempts.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("정상: 5xx 에러 후 재시도에서 성공")
        void success_retry_after_5xx_then_succeeds() {
            // given
            AtomicInteger attempts = new AtomicInteger(0);

            // when
            String result = aiRetryTemplate.execute(() -> {
                if (attempts.incrementAndGet() <= 2) {
                    throw HttpServerErrorException.create(
                            HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                            null, null, null);
                }
                return "success after 2 retries";
            }, "Claude");

            // then
            assertThat(result).isEqualTo("success after 2 retries");
            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("실패: 최대 재시도 횟수 초과 시 마지막 예외 전파")
        void fail_exceeds_max_attempts_throws_last_exception() {
            // given
            AtomicInteger attempts = new AtomicInteger(0);

            // when & then
            assertThatThrownBy(() ->
                    aiRetryTemplate.execute(() -> {
                        attempts.incrementAndGet();
                        throw HttpServerErrorException.create(
                                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                                null, null, null);
                    }, "OpenAI")
            ).isInstanceOf(HttpServerErrorException.class);

            // 3회 시도 후 예외 전파
            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("실패: 재시도 불가능한 예외(4xx, 429 제외)는 즉시 전파")
        void fail_non_retryable_exception_throws_immediately() {
            // given
            AtomicInteger attempts = new AtomicInteger(0);

            // when & then — 400 Bad Request는 재시도하지 않음
            assertThatThrownBy(() ->
                    aiRetryTemplate.execute(() -> {
                        attempts.incrementAndGet();
                        throw HttpClientErrorException.create(
                                HttpStatus.BAD_REQUEST, "Bad Request",
                                null, null, null);
                    }, "OpenAI")
            ).isInstanceOf(HttpClientErrorException.class);

            // 1회만 시도 (재시도 없음)
            assertThat(attempts.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("정상: RuntimeException 메시지에 429 포함 시 재시도")
        void success_runtime_exception_with_429_message_is_retried() {
            // given
            AtomicInteger attempts = new AtomicInteger(0);

            // when
            String result = aiRetryTemplate.execute(() -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new RuntimeException("HTTP 429 rate limit exceeded");
                }
                return "recovered";
            }, "OpenRouter");

            // then
            assertThat(result).isEqualTo("recovered");
            assertThat(attempts.get()).isEqualTo(2);
        }

        @Test
        @DisplayName("실패: 재시도 불가능한 RuntimeException은 즉시 전파")
        void fail_non_retryable_runtime_exception_throws_immediately() {
            // given
            AtomicInteger attempts = new AtomicInteger(0);

            // when & then
            assertThatThrownBy(() ->
                    aiRetryTemplate.execute(() -> {
                        attempts.incrementAndGet();
                        throw new RuntimeException("Connection refused");
                    }, "OpenAI")
            ).isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Connection refused");

            assertThat(attempts.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("정상: 502 메시지 포함 RuntimeException도 재시도 대상")
        void success_runtime_exception_with_502_message_is_retried() {
            // given
            AtomicInteger attempts = new AtomicInteger(0);

            // when
            String result = aiRetryTemplate.execute(() -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new RuntimeException("502 Bad Gateway");
                }
                return "recovered from 502";
            }, "Claude");

            // then
            assertThat(result).isEqualTo("recovered from 502");
            assertThat(attempts.get()).isEqualTo(2);
        }
    }
}
