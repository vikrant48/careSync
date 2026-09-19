package com.vikrant.careSync.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResendEmailClient {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailClient.class);

    @Value("${resend.api.key:}")
    private String apiKey;

    @Value("${mail.from.name:CareSync}")
    private String defaultFromName;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Sends an HTML email using Resend's HTTP API (https://api.resend.com/emails).
     *
     * @param fromEmail sender email address
     * @param toEmail   recipient email address
     * @param subject   email subject
     * @param htmlBody  HTML content
     * @return true if accepted, false otherwise
     */
    public boolean sendHtml(String fromEmail, String toEmail, String subject, String htmlBody) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Resend API key not configured; skipping HTTP send");
            return false;
        }

        try {
            String url = "https://api.resend.com/emails";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> payload = new HashMap<>();

            // Format from address or fallback to default onboarding address if not
            // specified
            String formattedFrom;
            if (fromEmail != null && !fromEmail.isBlank() && !fromEmail.contains("noreply@caresync.local")) {
                formattedFrom = defaultFromName + " <" + fromEmail + ">";
            } else {
                formattedFrom = defaultFromName + " <onboarding@resend.dev>";
            }

            payload.put("from", formattedFrom);
            payload.put("to", List.of(toEmail));
            payload.put("subject", subject);
            payload.put("html", htmlBody);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            int status = response.getStatusCodeValue();
            if (status >= 200 && status < 300) {
                log.info("Resend accepted email to {} with subject '{}'", toEmail, subject);
                return true;
            } else {
                log.error("Resend returned status {}: {}", status, response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Resend HTTP send failed: {}", e.getMessage(), e);
            return false;
        }
    }
}
