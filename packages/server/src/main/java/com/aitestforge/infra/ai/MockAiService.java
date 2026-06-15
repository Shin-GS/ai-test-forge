package com.aitestforge.infra.ai;

import com.aitestforge.infra.ai.dto.AiChatResponse;
import com.aitestforge.infra.ai.dto.ChatMessage;
import com.aitestforge.infra.ai.dto.ToolCall;
import com.aitestforge.infra.ai.dto.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 로컬 개발용 Mock AI 서비스.
 * 실제 AI API 호출 없이 시뮬레이션 응답을 반환한다.
 *
 * 동작:
 * - tools가 비어있으면 → 텍스트 응답만 반환
 * - tools가 있고, 이전에 tool 결과가 없으면 → 첫 번째 tool 호출 시뮬레이션
 * - 이전에 tool 결과가 있으면 → 완료 텍스트 응답
 */
@Slf4j
@Service
@Profile("local")
public class MockAiService implements AiService {

    @Override
    public AiChatResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        log.debug("MockAiService called with {} messages, {} tools", messages.size(), tools.size());

        // tool 결과가 이미 있으면 (이전 턴에서 tool call을 했고 결과를 받은 상태) → 완료 응답
        boolean hasToolResult = messages.stream()
                .anyMatch(m -> "tool".equals(m.role()));

        if (hasToolResult) {
            return new AiChatResponse(
                    "✅ Mock AI: 요청하신 작업을 완료했습니다. (시뮬레이션 응답)",
                    List.of()
            );
        }

        // tools가 있고 아직 tool call을 안 했으면 → 첫 번째 tool 호출
        if (!tools.isEmpty()) {
            ToolDefinition firstTool = tools.get(0);
            ToolCall toolCall = new ToolCall(
                    UUID.randomUUID().toString(),
                    firstTool.name(),
                    "{\"mock\": true}"
            );

            log.info("MockAiService: simulating tool call → {}", firstTool.name());
            return new AiChatResponse(
                    "네, 요청을 처리하겠습니다. API를 호출합니다.",
                    List.of(toolCall)
            );
        }

        // tools도 없으면 → 단순 텍스트 응답
        return new AiChatResponse(
                "Mock AI 응답입니다. 현재 등록된 서브도메인 API가 없어서 직접 호출할 수 없지만, 요청을 이해했습니다.",
                List.of()
        );
    }
}
