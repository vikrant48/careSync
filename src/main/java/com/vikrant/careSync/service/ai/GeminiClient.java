package com.vikrant.careSync.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini generateContent: text and vision (image or PDF sent inline). The model is configurable
 * (GEMINI_MODEL) and the API key travels in the x-goog-api-key header, never in the URL, so it cannot end up in
 * logs or error messages. Every call counts against the user's AI allowance; token usage of the last call is kept
 * per thread for the audit log.
 */
@Service
@Slf4j
public class GeminiClient {

    private final String apiKey;
    private final String model;
    private final String endpoint;
    private final int maxOutputTokens;
    private final RestTemplate restTemplate;
    private final AiQuotaService quotaService;
    private final ThreadLocal<AiUsage> lastUsage = new ThreadLocal<>();

    public GeminiClient(
            @Value("${app.ai.gemini.api-key:}") String apiKey,
            @Value("${app.ai.gemini.model:gemini-2.5-flash}") String model,
            @Value("${app.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta/models}") String baseUrl,
            @Value("${app.ai.gemini.max-output-tokens:4096}") int maxOutputTokens,
            @Value("${app.ai.http.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${app.ai.http.read-timeout-ms:45000}") int readTimeoutMs,
            AiQuotaService quotaService) {
        this.apiKey = apiKey;
        this.quotaService = quotaService;
        this.model = model == null || model.isBlank() ? "gemini-2.5-flash" : model.trim();
        this.endpoint = baseUrl.replaceAll("/+$", "") + "/" + this.model + ":generateContent";
        this.maxOutputTokens = maxOutputTokens;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Model id used for calls (for audit logs) */
    public String getModel() {
        return model;
    }

    /** Forget the last call's tokens (server threads are reused across requests) */
    public void resetLastCall() {
        lastUsage.remove();
    }

    /** Tokens used by the current thread's last successful call (AiUsage.NONE when unknown) */
    public AiUsage getLastUsage() {
        AiUsage usage = lastUsage.get();
        return usage != null ? usage : AiUsage.NONE;
    }

    public String generateContent(String prompt) {
        return generateVisionContent(null, null, prompt);
    }

    /** With no file bytes this is a plain text request. */
    public String generateVisionContent(byte[] fileBytes, String mimeType, String prompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini API key is not configured.");
        }
        lastUsage.remove();
        if (quotaService != null) {
            quotaService.consume();
        }

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));
        if (fileBytes != null && fileBytes.length > 0) {
            parts.add(Map.of("inline_data", Map.of(
                    "mime_type", mimeType != null ? mimeType : "image/jpeg",
                    "data", Base64.getEncoder().encodeToString(fileBytes))));
        }
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", maxOutputTokens));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        try {
            Map<?, ?> response = restTemplate.postForObject(endpoint, new HttpEntity<>(requestBody, headers), Map.class);
            String text = firstText(response);
            if (text != null) {
                lastUsage.set(usageOf(response));
                return text;
            }
        } catch (HttpStatusCodeException e) {
            log.error("Gemini request failed (HTTP {})", e.getStatusCode().value());
            throw new IllegalStateException("Gemini request failed (HTTP " + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            // Timeouts and connection errors: only the type is reported (no URLs or request details)
            log.error("Gemini request failed: {}", e.getClass().getSimpleName());
            throw new IllegalStateException("Gemini is unavailable right now");
        }
        throw new IllegalStateException("Gemini returned no content");
    }

    private static AiUsage usageOf(Map<?, ?> response) {
        if (!(response.get("usageMetadata") instanceof Map<?, ?> usage)) {
            return AiUsage.NONE;
        }
        return new AiUsage(intOrNull(usage.get("promptTokenCount")), intOrNull(usage.get("candidatesTokenCount")),
                intOrNull(usage.get("totalTokenCount")));
    }

    private static Integer intOrNull(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    /** Text of the first candidate (all text parts joined; thinking models may split the answer) */
    private static String firstText(Map<?, ?> response) {
        if (response == null || !(response.get("candidates") instanceof List<?> candidates) || candidates.isEmpty()) {
            return null;
        }
        if (!(candidates.get(0) instanceof Map<?, ?> candidate)
                || !(candidate.get("content") instanceof Map<?, ?> content)
                || !(content.get("parts") instanceof List<?> parts)) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof Map<?, ?> p && p.get("text") instanceof String s && !Boolean.TRUE.equals(p.get("thought"))) {
                text.append(s);
            }
        }
        return text.isEmpty() ? null : text.toString();
    }
}
