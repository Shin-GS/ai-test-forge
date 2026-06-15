package com.aitestforge.infra.ai;

import com.aitestforge.infra.ai.dto.AiChatResponse;
import com.aitestforge.infra.ai.dto.ChatMessage;
import com.aitestforge.infra.ai.dto.ToolCall;
import com.aitestforge.infra.ai.dto.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI Chat Completions API 구현체 (function calling).
 * OpenAI 직접 호출용.
 */
@Slf4j
@Service
@Profile("!local")
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiService implements AiService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public OpenAiService(
            @Value("${ai.openai.api-key}") String apiKey,
            @Value("${ai.openai.model:gpt-4o}") String model,
            ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public AiChatResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        try {
            ObjectNode requestBody = buildRequestBody(messages, tools);

            String responseJson = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .body(String.class);

            return parseResponse(responseJson);
        } catch (Exception e) {
            log.error("OpenAI API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("OpenAI API 호출 실패: " + e.getMessage(), e);
        }
    }

    protected ObjectNode buildRequestBody(List<ChatMessage> messages, List<ToolDefinition> tools) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);

        // messages
        ArrayNode messagesNode = body.putArray("messages");
        for (ChatMessage msg : messages) {
            ObjectNode msgNode = messagesNode.addObject();
            msgNode.put("role", msg.role());
            msgNode.put("content", msg.content());
        }

        // tools (function calling 포맷)
        if (!tools.isEmpty()) {
            ArrayNode toolsNode = body.putArray("tools");
            for (ToolDefinition tool : tools) {
                ObjectNode toolNode = toolsNode.addObject();
                toolNode.put("type", "function");

                ObjectNode functionNode = toolNode.putObject("function");
                functionNode.put("name", tool.name());
                functionNode.put("description", tool.description());

                try {
                    JsonNode params = objectMapper.readTree(tool.parametersJson());
                    functionNode.set("parameters", params);
                } catch (Exception e) {
                    functionNode.putObject("parameters");
                }
            }
        }

        return body;
    }

    protected AiChatResponse parseResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode choice = root.path("choices").path(0).path("message");

        String content = choice.path("content").asText(null);
        List<ToolCall> toolCalls = new ArrayList<>();

        JsonNode toolCallsNode = choice.get("tool_calls");
        if (toolCallsNode != null && toolCallsNode.isArray()) {
            for (JsonNode tc : toolCallsNode) {
                String id = tc.path("id").asText();
                String name = tc.path("function").path("name").asText();
                String arguments = tc.path("function").path("arguments").asText();
                toolCalls.add(new ToolCall(id, name, arguments));
            }
        }

        log.info("OpenAI response: content={}, toolCalls={}", content != null, toolCalls.size());
        return new AiChatResponse(content != null ? content : "", toolCalls);
    }
}
