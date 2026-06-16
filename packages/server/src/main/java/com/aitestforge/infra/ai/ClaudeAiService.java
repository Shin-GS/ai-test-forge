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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Anthropic Claude Messages API 구현체 (tool use).
 * Claude의 tool_use 응답 구조는 OpenAI와 다르므로 별도 파싱 필요.
 */
@Slf4j
@Service
@Profile("!local")
@ConditionalOnProperty(name = "ai.provider", havingValue = "claude")
public class ClaudeAiService implements AiService {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final AiRetryTemplate retryTemplate;

    public ClaudeAiService(
            @Value("${ai.claude.api-key}") String apiKey,
            @Value("${ai.claude.model:claude-sonnet-4-20250514}") String model,
            ObjectMapper objectMapper,
            AiRetryTemplate retryTemplate) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.retryTemplate = retryTemplate;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .requestFactory(factory)
                .build();
    }

    @Override
    public AiChatResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        try {
            ObjectNode requestBody = buildRequestBody(messages, tools);
            String requestJson = objectMapper.writeValueAsString(requestBody);

            String responseJson = retryTemplate.execute(() ->
                    restClient.post()
                            .uri("/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(requestJson)
                            .retrieve()
                            .body(String.class),
                    "Claude");

            return parseResponse(responseJson);
        } catch (Exception e) {
            log.error("Claude API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Claude API 호출 실패: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildRequestBody(List<ChatMessage> messages, List<ToolDefinition> tools) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 4096);

        // messages — Claude는 system을 별도 필드로 분리
        ArrayNode messagesNode = body.putArray("messages");
        for (ChatMessage msg : messages) {
            // system 메시지는 Claude에서 별도 처리
            if ("system".equals(msg.role())) {
                body.put("system", msg.content());
                continue;
            }
            ObjectNode msgNode = messagesNode.addObject();
            msgNode.put("role", mapRole(msg.role()));
            msgNode.put("content", msg.content());
        }

        // tools — Claude 포맷: {name, description, input_schema}
        if (!tools.isEmpty()) {
            ArrayNode toolsNode = body.putArray("tools");
            for (ToolDefinition tool : tools) {
                ObjectNode toolNode = toolsNode.addObject();
                toolNode.put("name", tool.name());
                toolNode.put("description", tool.description());

                try {
                    JsonNode inputSchema = objectMapper.readTree(tool.parametersJson());
                    toolNode.set("input_schema", inputSchema);
                } catch (Exception e) {
                    ObjectNode emptySchema = toolNode.putObject("input_schema");
                    emptySchema.put("type", "object");
                    emptySchema.putObject("properties");
                }
            }
        }

        return body;
    }

    private AiChatResponse parseResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode content = root.get("content");

        StringBuilder textBuilder = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();

        if (content != null && content.isArray()) {
            for (JsonNode block : content) {
                String type = block.path("type").asText();

                if ("text".equals(type)) {
                    textBuilder.append(block.path("text").asText());
                } else if ("tool_use".equals(type)) {
                    String id = block.path("id").asText();
                    String name = block.path("name").asText();
                    String input = objectMapper.writeValueAsString(block.get("input"));
                    toolCalls.add(new ToolCall(id, name, input));
                }
            }
        }

        String textContent = textBuilder.toString();
        log.info("Claude response: content={}, toolCalls={}", !textContent.isEmpty(), toolCalls.size());
        return new AiChatResponse(textContent, toolCalls);
    }

    private String mapRole(String role) {
        return switch (role) {
            case "user", "tool" -> "user";
            case "assistant" -> "assistant";
            default -> "user";
        };
    }
}
