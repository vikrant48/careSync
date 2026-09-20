package com.vikrant.careSync.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@Slf4j
@RequiredArgsConstructor
public class GrokClient {

    @Value("${app.ai.grok.api-key:${app.ai.groq.api-key:}}")
    private String apiKey;

    @Value("${app.ai.grok.model:grok-beta}")
    private String model;

    @Value("${app.ai.grok.url:https://api.x.ai/v1/chat/completions}")
    private String apiUrl;

    private final GroqClient groqClient;
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isConfigured() {
        return (apiKey != null && !apiKey.isBlank()) || (groqClient != null && groqClient.isConfigured());
    }

    public String callGrok(String systemPrompt, String userMessage, boolean jsonMode) {
        return callGrok(systemPrompt, null, userMessage, jsonMode, null);
    }

    public String callGrok(String systemPrompt, List<Map<String, String>> history, String userMessage,
            boolean jsonMode) {
        return callGrok(systemPrompt, history, userMessage, jsonMode, null);
    }

    public String callGrok(String systemPrompt, List<Map<String, String>> history, String userMessage,
            boolean jsonMode, String conversationId) {
        if (!isConfigured()) {
            throw new IllegalStateException("Neither Grok nor Groq API key is configured.");
        }

        // If xAI URL/Key is configured for xAI, call xAI directly
        if (apiUrl.contains("x.ai") && apiKey != null && !apiKey.isBlank()) {
            return executeXAiCall(systemPrompt, history, userMessage, jsonMode, conversationId);
        }

        // Fallback to GroqClient if configured
        if (groqClient != null && groqClient.isConfigured()) {
            return groqClient.callGroq(systemPrompt, history, userMessage, jsonMode, conversationId);
        }

        return executeXAiCall(systemPrompt, history, userMessage, jsonMode, conversationId);
    }

    private volatile String lastUsedModel;

    public String getLastUsedModel() {
        if (lastUsedModel != null) {
            return lastUsedModel;
        }
        if (groqClient != null && groqClient.getLastUsedModel() != null) {
            return groqClient.getLastUsedModel();
        }
        return model != null ? model : "grok-2-latest";
    }

    private String executeXAiCall(String systemPrompt, List<Map<String, String>> history, String userMessage,
            boolean jsonMode, String conversationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        if (conversationId != null && !conversationId.isBlank()) {
            headers.set("x-prompt-cache-key", conversationId);
            headers.set("X-Grok-Prompt-Cache-Key", conversationId);
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.2);

        if (conversationId != null && !conversationId.isBlank()) {
            requestBody.put("prompt_cache_key", conversationId);
            requestBody.put("user", conversationId);
        }

        if (jsonMode) {
            requestBody.put("response_format", Map.of("type", "json_object"));
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        int maxRetries = 3;
        int retryDelay = 1000;

        for (int i = 0; i < maxRetries; i++) {
            try {
                GrokResponse response = restTemplate.postForObject(apiUrl, entity, GrokResponse.class);
                if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                    GrokChoice choice = response.getChoices().get(0);
                    if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                        this.lastUsedModel = (response.getModel() != null ? response.getModel() : model);
                        return choice.getMessage().getContent();
                    }
                }
            } catch (Exception e) {
                log.warn("xAI Grok API call failed (attempt {}/{}): {}. Retrying...", i + 1, maxRetries,
                        e.getMessage());
                if (i == maxRetries - 1 && groqClient != null && groqClient.isConfigured()) {
                    log.info("Falling back to GroqClient after xAI failure");
                    String result = groqClient.callGroq(systemPrompt, history, userMessage, jsonMode, conversationId);
                    this.lastUsedModel = groqClient.getLastUsedModel();
                    return result;
                }
                try {
                    Thread.sleep(retryDelay * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        throw new RuntimeException("No valid response from xAI Grok API");
    }

    public void streamGrok(String systemPrompt, List<Map<String, String>> history, String userMessage,
            boolean jsonMode, String conversationId, Consumer<String> chunkConsumer) {
        if (!isConfigured()) {
            throw new IllegalStateException("Neither Grok nor Groq API key is configured.");
        }

        if (apiUrl.contains("x.ai") && apiKey != null && !apiKey.isBlank()) {
            try {
                List<Map<String, String>> messages = new ArrayList<>();
                if (systemPrompt != null && !systemPrompt.isBlank()) {
                    messages.add(Map.of("role", "system", "content", systemPrompt));
                }
                if (history != null && !history.isEmpty()) {
                    messages.addAll(history);
                }
                messages.add(Map.of("role", "user", "content", userMessage));

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", model);
                requestBody.put("messages", messages);
                requestBody.put("temperature", 0.2);
                requestBody.put("stream", true);

                if (conversationId != null && !conversationId.isBlank()) {
                    requestBody.put("prompt_cache_key", conversationId);
                    requestBody.put("user", conversationId);
                }
                if (jsonMode) {
                    requestBody.put("response_format", Map.of("type", "json_object"));
                }

                String jsonPayload = new ObjectMapper().writeValueAsString(requestBody);

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));

                if (conversationId != null && !conversationId.isBlank()) {
                    builder.header("x-prompt-cache-key", conversationId);
                    builder.header("X-Grok-Prompt-Cache-Key", conversationId);
                }

                HttpResponse<InputStream> response = client.send(builder.build(),
                        HttpResponse.BodyHandlers.ofInputStream());

                ObjectMapper mapper = new ObjectMapper();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if ("[DONE]".equalsIgnoreCase(data)) {
                                break;
                            }
                            try {
                                JsonNode node = mapper.readTree(data);
                                JsonNode choices = node.get("choices");
                                if (choices != null && choices.isArray() && choices.size() > 0) {
                                    JsonNode delta = choices.get(0).get("delta");
                                    if (delta != null && delta.has("content")) {
                                        String contentChunk = delta.get("content").asText();
                                        if (contentChunk != null && !contentChunk.isEmpty()) {
                                            chunkConsumer.accept(contentChunk);
                                        }
                                    }
                                }
                            } catch (Exception parseErr) {
                                // Skip non-json SSE lines
                            }
                        }
                    }
                }
                return;
            } catch (Exception e) {
                log.warn("Streaming from xAI failed: {}. Falling back to GroqClient streaming...", e.getMessage());
            }
        }

        // Fallback streaming to GroqClient
        if (groqClient != null && groqClient.isConfigured()) {
            groqClient.streamGroq(systemPrompt, history, userMessage, jsonMode, conversationId, chunkConsumer);
        } else {
            throw new RuntimeException("Streaming failed: No configured AI provider");
        }
    }

    public String getModel() {
        return (apiUrl.contains("x.ai") && apiKey != null && !apiKey.isBlank()) ? this.model
                : (groqClient != null ? groqClient.getModel() : this.model);
    }

    @Data
    public static class GrokResponse {
        private String model;
        private List<GrokChoice> choices;
        private GrokUsage usage;
    }

    @Data
    public static class GrokUsage {
        private Integer prompt_tokens;
        private Integer completion_tokens;
        private Integer total_tokens;
    }

    @Data
    public static class GrokChoice {
        private GrokMessage message;
    }

    @Data
    public static class GrokMessage {
        private String role;
        private String content;
    }
}
