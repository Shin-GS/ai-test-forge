package com.aitestforge.dto.settings.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "설정 조회 응답")
public record SettingsResponse(
        @Schema(description = "Reasoning 티어 Provider", example = "openai")
        String reasoningProvider,

        @Schema(description = "Reasoning 티어 모델명", example = "gpt-4o")
        String reasoningModel,

        @Schema(description = "Fast 티어 Provider", example = "openai")
        String fastProvider,

        @Schema(description = "Fast 티어 모델명", example = "gpt-4o-mini")
        String fastModel,

        @Schema(description = "Agent Loop 최대 반복 횟수", example = "20")
        int maxIterations,

        @Schema(description = "턴당 최대 Tool Call 수", example = "5")
        int maxToolCallsPerTurn,

        @Schema(description = "Agent Loop 타임아웃 (초)", example = "120")
        int timeoutSeconds,

        @Schema(description = "다음 액션 힌트 활성화 여부", example = "false")
        boolean nextActionHintEnabled
) {}
