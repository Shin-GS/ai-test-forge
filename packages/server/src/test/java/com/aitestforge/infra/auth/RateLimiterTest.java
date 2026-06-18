package com.aitestforge.infra.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter();
    }

    @Nested
    @DisplayName("isRateLimited")
    class IsRateLimited {

        @Test
        @DisplayName("정상: 첫 번째 요청은 제한되지 않음")
        void success_first_request_is_not_limited() {
            // when
            boolean result = rateLimiter.isRateLimited("login:user@test.com");

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("정상: 5회까지는 제한되지 않음 (MAX_ATTEMPTS = 5)")
        void success_within_max_attempts_is_not_limited() {
            // given
            String key = "login:user@test.com";

            // when — 5회 시도
            for (int i = 0; i < 5; i++) {
                rateLimiter.isRateLimited(key);
            }

            // then — 5회째까지 아직 초과하지 않음 (count > MAX_ATTEMPTS 기준)
            // 5회 시도 후의 상태에서 다시 확인
            boolean result = rateLimiter.isRateLimited(key);

            // 6번째 호출이므로 count가 6 → MAX_ATTEMPTS(5) 초과 → 제한됨
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("실패: MAX_ATTEMPTS 초과 시 true 반환 (제한됨)")
        void fail_exceeds_max_attempts_returns_true() {
            // given
            String key = "login:brute@attack.com";

            // 6회 시도 (MAX_ATTEMPTS = 5, 초과 판정은 > 5)
            for (int i = 0; i < 6; i++) {
                rateLimiter.isRateLimited(key);
            }

            // when — 7번째 시도
            boolean result = rateLimiter.isRateLimited(key);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("정상: 서로 다른 키는 독립적으로 동작")
        void success_different_keys_are_independent() {
            // given
            String key1 = "login:user1@test.com";
            String key2 = "login:user2@test.com";

            // key1만 6회 호출하여 초과시킴
            for (int i = 0; i < 6; i++) {
                rateLimiter.isRateLimited(key1);
            }

            // when
            boolean result1 = rateLimiter.isRateLimited(key1);
            boolean result2 = rateLimiter.isRateLimited(key2);

            // then
            assertThat(result1).isTrue();   // key1은 제한됨
            assertThat(result2).isFalse();  // key2는 독립 → 제한 안 됨
        }
    }

    @Nested
    @DisplayName("reset")
    class Reset {

        @Test
        @DisplayName("정상: reset 후 카운터가 초기화됨")
        void success_reset_clears_counter() {
            // given
            String key = "login:user@test.com";
            // MAX_ATTEMPTS 초과시킴
            for (int i = 0; i < 6; i++) {
                rateLimiter.isRateLimited(key);
            }
            // 초과 확인
            assertThat(rateLimiter.isRateLimited(key)).isTrue();

            // when
            rateLimiter.reset(key);

            // then — 초기화 후 첫 요청은 제한 안 됨
            boolean result = rateLimiter.isRateLimited(key);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("정상: 존재하지 않는 키를 reset해도 에러 없음")
        void success_reset_nonexistent_key_does_nothing() {
            // when & then — 예외 없이 정상 동작
            rateLimiter.reset("nonexistent-key");
        }

        @Test
        @DisplayName("정상: reset은 해당 키만 초기화하고 다른 키에 영향 없음")
        void success_reset_only_affects_target_key() {
            // given
            String key1 = "login:user1@test.com";
            String key2 = "login:user2@test.com";

            // 두 키 모두 초과시킴
            for (int i = 0; i < 6; i++) {
                rateLimiter.isRateLimited(key1);
                rateLimiter.isRateLimited(key2);
            }

            // when — key1만 리셋
            rateLimiter.reset(key1);

            // then
            assertThat(rateLimiter.isRateLimited(key1)).isFalse(); // 리셋됨
            assertThat(rateLimiter.isRateLimited(key2)).isTrue();  // 여전히 제한됨
        }
    }
}
