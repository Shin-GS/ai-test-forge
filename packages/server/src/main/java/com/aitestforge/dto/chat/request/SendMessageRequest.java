package com.aitestforge.dto.chat.request;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
        @NotBlank String message
) {
}
