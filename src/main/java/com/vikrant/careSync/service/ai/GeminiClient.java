package com.vikrant.careSync.service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiClient {

    @Value("${app.ai.gemini.api-key:}")
    private String apiKey;

    @Value("${app.ai.gemini.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String generateContent(String prompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini API key is not configured.");
        }

        String fullUrl = apiUrl.contains("key=") ? apiUrl : (apiUrl + "?key=" + apiKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            Map<?, ?> response = restTemplate.postForObject(fullUrl, entity, Map.class);
            if (response != null && response.containsKey("candidates")) {
                List<?> candidates = (List<?>) response.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
                    Map<?, ?> candContent = (Map<?, ?>) firstCandidate.get("content");
                    if (candContent != null && candContent.containsKey("parts")) {
                        List<?> parts = (List<?>) candContent.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
                            return (String) firstPart.get("text");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new RuntimeException("Gemini API error: " + e.getMessage(), e);
        }

        throw new RuntimeException("No response content returned from Gemini API");
    }

    public String generateVisionContent(byte[] imageBytes, String mimeType, String prompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini API key is not configured.");
        }

        String fullUrl = apiUrl.contains("key=") ? apiUrl : (apiUrl + "?key=" + apiKey);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String base64Data = Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> imagePart = Map.of("inline_data", Map.of(
                "mime_type", mimeType != null ? mimeType : "image/jpeg",
                "data", base64Data
        ));

        Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            Map<?, ?> response = restTemplate.postForObject(fullUrl, entity, Map.class);
            if (response != null && response.containsKey("candidates")) {
                List<?> candidates = (List<?>) response.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
                    Map<?, ?> candContent = (Map<?, ?>) firstCandidate.get("content");
                    if (candContent != null && candContent.containsKey("parts")) {
                        List<?> parts = (List<?>) candContent.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
                            return (String) firstPart.get("text");
                        }
                    }
                }
            }
//            JsonNode response = restTemplate.postForObject(fullUrl, entity, JsonNode.class);
//            if (response != null) {
//                String text = response.path("candidates").path(0)
//                        .path("content").path("parts").path(0)
//                        .path("text").asText(null);
//                if (text != null && !text.isBlank()) {
//                    return text;
//                }
//            }

        } catch (Exception e) {
            log.error("Gemini Vision API call failed: {}", e.getMessage());
            throw new RuntimeException("Gemini Vision API error: " + e.getMessage(), e);
        }

        throw new RuntimeException("No response content returned from Gemini Vision API");
    }
}
