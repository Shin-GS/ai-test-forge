package com.aitestforge.infra.ai;

import com.aitestforge.infra.ai.dto.AiChatResponse;
import com.aitestforge.infra.ai.dto.ChatMessage;
import com.aitestforge.infra.ai.dto.ToolDefinition;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("local")
public class MockAiService implements AiService {

    @Override
    public AiChatResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        return new AiChatResponse("Mock AI 응답입니다. 요청을 처리했습니다.", List.of());
    }
}
