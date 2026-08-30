package com.vikrant.careSync.service.ai;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class GroqClient {

    @Value("${app.ai.groq.api-key:}")
    private String apiKey;

    @Value("${app.ai.groq.model:llama-3.3-70b-versatile}")
    private String model;

    @Value("${app.ai.groq.url:https://api.groq.com/openai/v1/chat/completions}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    private volatile String lastUsedModel;

    public String getLastUsedModel() {
        return lastUsedModel != null ? lastUsedModel : model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String callGroq(String systemPrompt, String userMessage, boolean jsonMode) {
        return callGroq(systemPrompt, null, userMessage, jsonMode, null);
    }

    public String callGroq(String systemPrompt, List<Map<String, String>> history, String userMessage,
            boolean jsonMode) {
        return callGroq(systemPrompt, history, userMessage, jsonMode, null);
    }

    public String callGroq(String systemPrompt, List<Map<String, String>> history, String userMessage,
            boolean jsonMode, String conversationId) {
        if (!isConfigured()) {
            throw new IllegalStateException("Groq API key is not configured.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        if (conversationId != null && !conversationId.isBlank()) {
            headers.set("x-prompt-cache-key", conversationId);
            headers.set("X-Groq-Prompt-Cache-Key", conversationId);
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
            requestBody.put("user", conversationId);
        }

        if (jsonMode) {
            requestBody.put("response_format", Map.of("type", "json_object"));
        }

        List<String> candidateModels = List.of(
                model,
                "groq/compound",
                "openai/gpt-oss-20b",
                "openai/gpt-oss-120b",
                "groq/compound-mini",
                "qwen/qwen3.8-27b",
                "qwen/qwen3.6-27b",
                "allam-2-7b");

        for (String currentModel : candidateModels) {
            requestBody.put("model", currentModel);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            int maxRetries = 2;
            int retryDelay = 800;

            for (int i = 0; i < maxRetries; i++) {
                try {
                    GroqResponse response = restTemplate.postForObject(apiUrl, entity, GroqResponse.class);
                    if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                        GroqChoice choice = response.getChoices().get(0);
                        if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                            this.lastUsedModel = currentModel;
                            this.model = currentModel; // Remember working model
                            return choice.getMessage().getContent();
                        }
                    }
                } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                    log.warn("Groq model '{}' not found (404). Trying next candidate model...", currentModel);
                    break; // Skip to next candidate model
                } catch (org.springframework.web.client.HttpClientErrorException e) {
                    String respBody = e.getResponseBodyAsString();
                    if (respBody.contains("model_not_found") || respBody.contains("model_decommissioned")
                            || respBody.contains("decommissioned")) {
                        log.warn("Groq model '{}' invalid/decommissioned. Trying next candidate model...",
                                currentModel);
                        break; // Skip to next candidate model
                    }
                    log.error("Groq API client error: {}", e.getMessage());
                    throw e;
                } catch (org.springframework.web.client.HttpServerErrorException.ServiceUnavailable e) {
                    log.warn("Groq API overloaded (503). Retrying {}/{}...", i + 1, maxRetries);
                    if (i == maxRetries - 1)
                        break;
                    try {
                        Thread.sleep(retryDelay * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    if (msg.contains("model_not_found") || msg.contains("model_decommissioned")
                            || msg.contains("decommissioned")) {
                        log.warn("Groq model '{}' invalid/decommissioned. Trying next candidate...", currentModel);
                        break;
                    }
                    log.error("Groq API call failed (attempt {}/{}): {}", i + 1, maxRetries, e.getMessage());
                }
            }
        }

        throw new RuntimeException("No working Groq AI model found for current API key.");
    }

    public void streamGroq(String systemPrompt, List<Map<String, String>> history, String userMessage,
            boolean jsonMode, String conversationId, java.util.function.Consumer<String> chunkConsumer) {
        if (!isConfigured()) {
            throw new IllegalStateException("Groq API key is not configured.");
        }

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
                requestBody.put("user", conversationId);
            }
            if (jsonMode) {
                requestBody.put("response_format", Map.of("type", "json_object"));
            }

            String jsonPayload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(requestBody);

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonPayload));

            if (conversationId != null && !conversationId.isBlank()) {
                builder.header("x-prompt-cache-key", conversationId);
                builder.header("X-Groq-Prompt-Cache-Key", conversationId);
            }

            java.net.http.HttpResponse<java.io.InputStream> response = client.send(builder.build(),
                    java.net.http.HttpResponse.BodyHandlers.ofInputStream());

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equalsIgnoreCase(data)) {
                            break;
                        }
                        try {
                            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(data);
                            com.fasterxml.jackson.databind.JsonNode choices = node.get("choices");
                            if (choices != null && choices.isArray() && choices.size() > 0) {
                                com.fasterxml.jackson.databind.JsonNode delta = choices.get(0).get("delta");
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
        } catch (Exception e) {
            log.error("Error streaming from Groq API: {}", e.getMessage(), e);
            throw new RuntimeException("Streaming failed: " + e.getMessage(), e);
        }
    }

    public String getModel() {
        return this.model;
    }

    @Data
    public static class GroqResponse {
        private String model;
        private List<GroqChoice> choices;
        private GroqUsage usage;
    }

    @Data
    public static class GroqUsage {
        private Integer prompt_tokens;
        private Integer completion_tokens;
        private Integer total_tokens;
    }

    @Data
    public static class GroqChoice {
        private GroqMessage message;
    }

    @Data
    public static class GroqMessage {
        private String role;
        private String content;
    }
}
