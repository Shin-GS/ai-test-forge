package com.aitestforge.dto.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "설정 수정 요청")
public record UpdateSettingsRequest(
        @Schema(description = "AI Provider (openai, claude, openrouter, mock)", example = "openai")
        @NotBlank
        @Pattern(regexp = "^(openai|claude|openrouter|mock)$", message = "aiProvider must be one of: openai, claude, openrouter, mock")
        String aiProvider,

        @Schema(description = "AI 모델명", example = "gpt-4o")
        @NotBlank
        String aiModel,

        @Schema(description = "Agent Loop 최대 반복 횟수 (1~100)", example = "20")
        @Min(1)
        @Max(100)
        int maxIterations,

        @Schema(description = "턴당 최대 Tool Call 수 (1~20)", example = "5")
        @Min(1)
        @Max(20)
        int maxToolCallsPerTurn,

        @Schema(description = "Agent Loop 타임아웃 초 (10~600)", example = "120")
        @Min(10)
        @Max(600)
        int timeoutSeconds,

        @Schema(description = "다음 액션 힌트 활성화 여부", example = "false")
        boolean nextActionHintEnabled
) {}
