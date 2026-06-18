package com.aitestforge.service.settings;

import com.aitestforge.dto.settings.request.UpdateSettingsRequest;
import com.aitestforge.dto.settings.response.SettingsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        // 생성자 주입으로 기본값 설정
        settingsService = new SettingsService(
                "openai",       // reasoningProvider
                "gpt-4o",       // reasoningModel
                "openai",       // fastProvider
                "gpt-4o-mini",  // fastModel
                20,             // maxIterations
                5,              // maxToolCallsPerTurn
                120             // timeoutSeconds
        );
    }

    @Nested
    @DisplayName("getSettings")
    class GetSettings {

        @Test
        @DisplayName("정상: 기본 설정값이 올바르게 반환됨")
        void success_returns_default_settings() {
            // when
            SettingsResponse response = settingsService.getSettings();

            // then
            assertThat(response.reasoningProvider()).isEqualTo("openai");
            assertThat(response.reasoningModel()).isEqualTo("gpt-4o");
            assertThat(response.fastProvider()).isEqualTo("openai");
            assertThat(response.fastModel()).isEqualTo("gpt-4o-mini");
            assertThat(response.maxIterations()).isEqualTo(20);
            assertThat(response.maxToolCallsPerTurn()).isEqualTo(5);
            assertThat(response.timeoutSeconds()).isEqualTo(120);
            assertThat(response.nextActionHintEnabled()).isFalse();
        }

        @Test
        @DisplayName("정상: AI 모델 정보는 읽기 전용으로 유지됨")
        void success_ai_model_info_is_readonly() {
            // given — 설정 변경 후에도 AI 모델 정보는 그대로
            UpdateSettingsRequest updateRequest = new UpdateSettingsRequest(30, 10, 180, true);
            settingsService.updateSettings(updateRequest);

            // when
            SettingsResponse response = settingsService.getSettings();

            // then — AI 모델 정보는 변하지 않음
            assertThat(response.reasoningProvider()).isEqualTo("openai");
            assertThat(response.reasoningModel()).isEqualTo("gpt-4o");
            assertThat(response.fastProvider()).isEqualTo("openai");
            assertThat(response.fastModel()).isEqualTo("gpt-4o-mini");
        }
    }

    @Nested
    @DisplayName("updateSettings")
    class UpdateSettings {

        @Test
        @DisplayName("정상: Agent Loop 파라미터 변경")
        void success_update_agent_loop_parameters() {
            // given
            UpdateSettingsRequest request = new UpdateSettingsRequest(30, 10, 180, false);

            // when
            SettingsResponse response = settingsService.updateSettings(request);

            // then
            assertThat(response.maxIterations()).isEqualTo(30);
            assertThat(response.maxToolCallsPerTurn()).isEqualTo(10);
            assertThat(response.timeoutSeconds()).isEqualTo(180);
            assertThat(response.nextActionHintEnabled()).isFalse();
        }

        @Test
        @DisplayName("정상: nextActionHintEnabled 토글 ON")
        void success_toggle_next_action_hint_on() {
            // given
            UpdateSettingsRequest request = new UpdateSettingsRequest(20, 5, 120, true);

            // when
            SettingsResponse response = settingsService.updateSettings(request);

            // then
            assertThat(response.nextActionHintEnabled()).isTrue();
        }

        @Test
        @DisplayName("정상: nextActionHintEnabled 토글 OFF")
        void success_toggle_next_action_hint_off() {
            // given — 먼저 ON으로 변경
            settingsService.updateSettings(new UpdateSettingsRequest(20, 5, 120, true));

            // when — OFF로 변경
            SettingsResponse response = settingsService.updateSettings(
                    new UpdateSettingsRequest(20, 5, 120, false));

            // then
            assertThat(response.nextActionHintEnabled()).isFalse();
        }

        @Test
        @DisplayName("정상: 변경 후 getSettings에서도 동일한 값 반환")
        void success_get_settings_after_update_returns_same_values() {
            // given
            UpdateSettingsRequest request = new UpdateSettingsRequest(50, 15, 300, true);
            settingsService.updateSettings(request);

            // when
            SettingsResponse response = settingsService.getSettings();

            // then
            assertThat(response.maxIterations()).isEqualTo(50);
            assertThat(response.maxToolCallsPerTurn()).isEqualTo(15);
            assertThat(response.timeoutSeconds()).isEqualTo(300);
            assertThat(response.nextActionHintEnabled()).isTrue();
        }

        @Test
        @DisplayName("정상: 여러 번 업데이트해도 마지막 값이 유지됨")
        void success_multiple_updates_keep_last_value() {
            // given
            settingsService.updateSettings(new UpdateSettingsRequest(10, 3, 60, true));
            settingsService.updateSettings(new UpdateSettingsRequest(25, 8, 200, false));

            // when
            SettingsResponse response = settingsService.getSettings();

            // then
            assertThat(response.maxIterations()).isEqualTo(25);
            assertThat(response.maxToolCallsPerTurn()).isEqualTo(8);
            assertThat(response.timeoutSeconds()).isEqualTo(200);
            assertThat(response.nextActionHintEnabled()).isFalse();
        }
    }
}
