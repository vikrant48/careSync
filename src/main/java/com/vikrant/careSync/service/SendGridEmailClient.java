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
public class SendGridEmailClient {

    private static final Logger log = LoggerFactory.getLogger(SendGridEmailClient.class);

    @Value("${sendgrid.api.key:}")
    private String apiKey;

    @Value("${mail.from.name:CareSync}")
    private String defaultFromName;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Sends an HTML email using SendGrid's HTTP API.
     *
     * @param fromEmail sender email address
     * @param toEmail   recipient email address
     * @param subject   email subject
     * @param htmlBody  HTML content
     * @return true if accepted, false otherwise
     */
    public boolean sendHtml(String fromEmail, String toEmail, String subject, String htmlBody) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("SendGrid API key not configured; skipping HTTP send");
            return false;
        }

        try {
            String url = "https://api.sendgrid.com/v3/mail/send";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> payload = new HashMap<>();

            Map<String, Object> to = new HashMap<>();
            to.put("email", toEmail);

            Map<String, Object> personalization = new HashMap<>();
            personalization.put("to", List.of(to));

            payload.put("personalizations", List.of(personalization));

            Map<String, Object> from = new HashMap<>();
            from.put("email", fromEmail);
            from.put("name", defaultFromName);
            payload.put("from", from);

            payload.put("subject", subject);

            Map<String, Object> content = new HashMap<>();
            content.put("type", "text/html");
            content.put("value", htmlBody);

            payload.put("content", List.of(content));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            int status = response.getStatusCodeValue();
            if (status >= 200 && status < 300) {
                log.info("SendGrid accepted email to {} with subject '{}'", toEmail, subject);
                return true;
            } else {
                log.error("SendGrid returned status {}: {}", status, response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("SendGrid HTTP send failed: {}", e.getMessage(), e);
            return false;
        }
    }
}