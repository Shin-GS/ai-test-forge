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
 * OpenRouter API 구현체.
 * OpenAI 호환 API를 사용하므로 요청/응답 형식이 동일하며, base URL과 인증 키만 다르다.
 * OpenRouter를 통해 다양한 모델(GPT, Claude, Gemini, Llama 등)에 접근 가능.
 */
@Slf4j
@Service
@Profile("!local")
@ConditionalOnProperty(name = "ai.provider", havingValue = "openrouter")
public class OpenRouterService implements AiService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final AiRetryTemplate retryTemplate;

    public OpenRouterService(
            @Value("${ai.openrouter.api-key}") String apiKey,
            @Value("${ai.openrouter.model:openai/gpt-4o}") String model,
            ObjectMapper objectMapper,
            AiRetryTemplate retryTemplate) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.retryTemplate = retryTemplate;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl("https://openrouter.ai/api/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
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
                            .uri("/chat/completions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(requestJson)
                            .retrieve()
                            .body(String.class),
                    "OpenRouter");

            return parseResponse(responseJson);
        } catch (Exception e) {
            log.error("OpenRouter API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("OpenRouter API 호출 실패: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildRequestBody(List<ChatMessage> messages, List<ToolDefinition> tools) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);

        // messages
        ArrayNode messagesNode = body.putArray("messages");
        for (ChatMessage msg : messages) {
            ObjectNode msgNode = messagesNode.addObject();
            msgNode.put("role", msg.role());
            msgNode.put("content", msg.content());
        }

        // tools (OpenAI 호환 function calling 포맷)
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

    private AiChatResponse parseResponse(String responseJson) throws Exception {
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

        log.info("OpenRouter response (model={}): content={}, toolCalls={}", model, content != null, toolCalls.size());
        return new AiChatResponse(content != null ? content : "", toolCalls);
    }
}
