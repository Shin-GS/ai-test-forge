package com.aitestforge.dto.chat.request;

import jakarta.validation.constraints.NotBlank;

public record ToolResultRequest(
        @NotBlank String toolCallId,
        int statusCode,
        String body
) {
}
