package com.aitestforge.infra.ai;

import com.aitestforge.infra.ai.dto.AiChatResponse;
import com.aitestforge.infra.ai.dto.ChatMessage;
import com.aitestforge.infra.ai.dto.ToolDefinition;

import java.util.List;

public interface AiService {

    AiChatResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools);
}
