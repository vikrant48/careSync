package com.vikrant.careSync.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Groq chat completions (OpenAI-compatible). The model and its fallbacks come from configuration
 * (GROQ_MODEL, GROQ_FALLBACK_MODELS, GROQ_FAST_MODEL); the next model is tried when one is retired, rate limited,
 * overloaded or returns invalid JSON. Every call counts against the user's AI allowance and has connect/read
 * timeouts and an answer-length limit. Token usage of the last call is kept per thread for the audit log.
 */
@Component
@Slf4j
public class GroqClient {

    private static final int RETRIES_PER_MODEL = 2;
    private static final long RETRY_DELAY_MS = 800;

    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final List<String> candidateModels;
    private final List<String> fastCandidateModels;
    private final AiQuotaService quotaService;
    private final int maxTokens;
    private final String reasoningEffort;
    private final Duration readTimeout;
    private final RestTemplate restTemplate;
    private final HttpClient streamClient;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Model that answered the current thread's last call (for the audit log); never shared between requests */
    private final ThreadLocal<String> lastUsedModel = new ThreadLocal<>();
    private final ThreadLocal<AiUsage> lastUsage = new ThreadLocal<>();

    public GroqClient(
            @Value("${app.ai.groq.api-key:}") String apiKey,
            @Value("${app.ai.groq.url:https://api.groq.com/openai/v1/chat/completions}") String apiUrl,
            @Value("${app.ai.groq.model:openai/gpt-oss-120b}") String model,
            @Value("${app.ai.groq.fallback-models:openai/gpt-oss-120b,openai/gpt-oss-20b}") String fallbackModels,
            @Value("${app.ai.groq.fast-model:openai/gpt-oss-20b}") String fastModel,
            @Value("${app.ai.groq.max-tokens:2048}") int maxTokens,
            @Value("${app.ai.groq.reasoning-effort:low}") String reasoningEffort,
            @Value("${app.ai.http.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.ai.http.read-timeout-ms:45000}") int readTimeoutMs,
            AiQuotaService quotaService) {
        this.apiKey = apiKey;
        this.quotaService = quotaService;
        this.apiUrl = apiUrl;
        this.model = model == null || model.isBlank() ? "openai/gpt-oss-120b" : model.trim();
        this.candidateModels = candidateModels(this.model, fallbackModels);
        // Fast tasks start on the small model and fall back to the normal list
        this.fastCandidateModels = fastModel == null || fastModel.isBlank()
                ? this.candidateModels
                : candidateModels(fastModel.trim(), String.join(",", this.candidateModels));
        this.maxTokens = maxTokens;
        this.reasoningEffort = reasoningEffort;
        this.readTimeout = Duration.ofMillis(readTimeoutMs);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.streamClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
    }

    /** Configured model first, then the fallbacks, without duplicates or blanks */
    static List<String> candidateModels(String primary, String fallbacks) {
        Set<String> models = new LinkedHashSet<>();
        models.add(primary);
        if (fallbacks != null) {
            Arrays.stream(fallbacks.split(",")).map(String::trim).filter(m -> !m.isEmpty()).forEach(models::add);
        }
        return List.copyOf(models);
    }

    public String getLastUsedModel() {
        String used = lastUsedModel.get();
        return used != null ? used : model;
    }

    public String getModel() {
        return model;
    }

    /** Forget the last call's model and tokens (server threads are reused across requests) */
    public void resetLastCall() {
        lastUsedModel.remove();
        lastUsage.remove();
    }

    /** Tokens used by the current thread's last successful call (AiUsage.NONE when unknown) */
    public AiUsage getLastUsage() {
        AiUsage usage = lastUsage.get();
        return usage != null ? usage : AiUsage.NONE;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** For tests (MockRestServiceServer) */
    RestTemplate restTemplate() {
        return restTemplate;
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
        return callGroq(systemPrompt, history, userMessage, jsonMode, conversationId, AiModelTier.SMART);
    }

    public String callGroq(String systemPrompt, String userMessage, boolean jsonMode, AiModelTier tier) {
        return callGroq(systemPrompt, null, userMessage, jsonMode, null, tier);
    }

    public String callGroq(String systemPrompt, List<Map<String, String>> history, String userMessage,
            boolean jsonMode, String conversationId, AiModelTier tier) {
        if (!isConfigured()) {
            throw new IllegalStateException("Groq API key is not configured.");
        }
        lastUsedModel.remove();
        lastUsage.remove();
        if (quotaService != null) {
            quotaService.consume();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        List<Map<String, String>> messages = messages(systemPrompt, history, userMessage);

        for (String currentModel : tier == AiModelTier.FAST ? fastCandidateModels : candidateModels) {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                    requestBody(currentModel, messages, jsonMode, false), headers);

            for (int attempt = 1; attempt <= RETRIES_PER_MODEL; attempt++) {
                try {
                    GroqResponse response = restTemplate.postForObject(apiUrl, entity, GroqResponse.class);
                    String content = firstContent(response);
                    if (content != null) {
                        lastUsedModel.set(currentModel);
                        lastUsage.set(usageOf(response));
                        return content;
                    }
                    log.warn("Groq model '{}' returned an empty answer", currentModel);
                    break;
                } catch (HttpClientErrorException e) {
                    if (tryNextModel(e)) {
                        log.warn("Groq model '{}' unavailable (HTTP {}). Trying the next model...", currentModel,
                                e.getStatusCode().value());
                        break;
                    }
                    log.error("Groq request refused (HTTP {})", e.getStatusCode().value());
                    throw new IllegalStateException("AI request was refused (HTTP " + e.getStatusCode().value() + ")");
                } catch (HttpServerErrorException | ResourceAccessException e) {
                    // Overloaded, server error or timeout: retry this model once, then move on
                    log.warn("Groq model '{}' failed (attempt {}/{}): {}", currentModel, attempt, RETRIES_PER_MODEL,
                            e.getClass().getSimpleName());
                    if (attempt < RETRIES_PER_MODEL && !sleep(RETRY_DELAY_MS * attempt)) {
                        break;
                    }
                }
            }
        }
        throw new IllegalStateException("The AI service is unavailable right now. Please try again shortly.");
    }

    /**
     * 404 / retired model, 429 rate limit (each model has its own free quota) and 400 json_validate_failed
     * (that model did not produce valid JSON) move on to the next model; other refusals do not.
     */
    private static boolean tryNextModel(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        String body = e.getResponseBodyAsString();
        return status == 404 || status == 429
                || body.contains("model_not_found") || body.contains("decommissioned")
                || body.contains("json_validate_failed");
    }

    public void streamGroq(String systemPrompt, List<Map<String, String>> history, String userMessage,
            boolean jsonMode, String conversationId, Consumer<String> chunkConsumer) {
        if (!isConfigured()) {
            throw new IllegalStateException("Groq API key is not configured.");
        }
        lastUsedModel.remove();
        lastUsage.remove();
        if (quotaService != null) {
            quotaService.consume();
        }
        List<Map<String, String>> messages = messages(systemPrompt, history, userMessage);

        for (String currentModel : candidateModels) {
            try {
                String payload = mapper.writeValueAsString(requestBody(currentModel, messages, jsonMode, true));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(readTimeout)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                HttpResponse<InputStream> response = streamClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status != 200) {
                    response.body().close();
                    if (status == 404 || status == 429 || status >= 500) {
                        log.warn("Groq streaming with '{}' unavailable (HTTP {}). Trying the next model...",
                                currentModel, status);
                        continue;
                    }
                    throw new IllegalStateException("AI request was refused (HTTP " + status + ")");
                }
                readStream(response.body(), chunkConsumer);
                lastUsedModel.set(currentModel);
                return;
            } catch (IllegalStateException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("AI streaming was interrupted", e);
            } catch (Exception e) {
                log.warn("Groq streaming with '{}' failed: {}", currentModel, e.getClass().getSimpleName());
            }
        }
        throw new IllegalStateException("The AI service is unavailable right now. Please try again shortly.");
    }

    private void readStream(InputStream body, Consumer<String> chunkConsumer) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) {
                    continue;
                }
                String data = line.substring(6).trim();
                if ("[DONE]".equalsIgnoreCase(data)) {
                    break;
                }
                try {
                    JsonNode choices = mapper.readTree(data).get("choices");
                    if (choices != null && choices.isArray() && !choices.isEmpty()) {
                        JsonNode delta = choices.get(0).get("delta");
                        if (delta != null && delta.hasNonNull("content")) {
                            String chunk = delta.get("content").asText();
                            if (!chunk.isEmpty()) {
                                chunkConsumer.accept(chunk);
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Keep-alive or non-JSON line
                }
            }
        }
    }

    private static List<Map<String, String>> messages(String systemPrompt, List<Map<String, String>> history,
            String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }

    private Map<String, Object> requestBody(String currentModel, List<Map<String, String>> messages, boolean jsonMode,
            boolean stream) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", currentModel);
        body.put("messages", messages);
        body.put("temperature", 0.2);
        body.put("max_completion_tokens", maxTokens);
        if (stream) {
            body.put("stream", true);
        }
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        // Reasoning options exist only on the gpt-oss models; other models reject them
        if (currentModel.startsWith("openai/gpt-oss")) {
            if (reasoningEffort != null && !reasoningEffort.isBlank()) {
                body.put("reasoning_effort", reasoningEffort);
            }
            body.put("include_reasoning", false);
        }
        return body;
    }

    private static AiUsage usageOf(GroqResponse response) {
        GroqUsage usage = response.getUsage();
        return usage == null ? AiUsage.NONE
                : new AiUsage(usage.getPrompt_tokens(), usage.getCompletion_tokens(), usage.getTotal_tokens());
    }

    private static String firstContent(GroqResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        GroqMessage message = response.getChoices().get(0).getMessage();
        return message != null && message.getContent() != null && !message.getContent().isBlank()
                ? message.getContent()
                : null;
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
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
