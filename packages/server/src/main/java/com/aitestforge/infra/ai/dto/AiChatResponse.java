package com.aitestforge.infra.ai.dto;

import java.util.List;

public record AiChatResponse(String message, List<ToolCall> toolCalls) {
}
