package com.smartenergy.backend.llm.impl;

import com.smartenergy.backend.llm.LlmClient;
import com.smartenergy.backend.llm.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * DeepSeek LLM 客户端（OpenAI 兼容协议）。llm.provider=deepseek 且有 apiKey 时激活。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "llm", name = "provider", havingValue = "deepseek")
@RequiredArgsConstructor
public class DeepSeekLlmClient implements LlmClient {

    private final LlmProperties props;
    private final ObjectMapper objectMapper;

    @Override
    public boolean isLive() {
        return props.getApiKey() != null && !props.getApiKey().isBlank();
    }

    @Override
    public String chat(String prompt) {
        if (!isLive()) {
            throw new IllegalStateException("DeepSeek API key not configured");
        }
        try {
            String base = props.getBaseUrl() == null || props.getBaseUrl().isBlank()
                    ? "https://api.deepseek.com" : props.getBaseUrl();
            URI uri = URI.create(base + "/v1/chat/completions");
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", props.getModel() == null ? "deepseek-chat" : props.getModel(),
                    "messages", new Object[]{
                            Map.of("role", "system", "content", "你是钢铁行业设备维修专家，回答简洁。"),
                            Map.of("role", "user", "content", prompt)
                    },
                    "temperature", 0.2,
                    "max_tokens", 1500
            ));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofMillis(props.getTimeout() > 0 ? props.getTimeout() : 30000))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(5000))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.error("[LLM] DeepSeek returned status {} body {}", resp.statusCode(), resp.body());
                throw new RuntimeException("LLM call failed: status=" + resp.statusCode());
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return content.asText("");
        } catch (Exception ex) {
            log.error("[LLM] DeepSeek call failed", ex);
            throw new RuntimeException("DeepSeek call failed: " + ex.getMessage(), ex);
        }
    }
}