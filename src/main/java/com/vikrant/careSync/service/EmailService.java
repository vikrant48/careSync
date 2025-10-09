package com.vikrant.careSync.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${app.mail.mailtrap.token:}")
    private String mailtrapToken;

    @Value("${app.mail.sender:CareSync <noreply@caresync.local>}")
    private String sender;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendOtpEmail(String to, String name, String otp) {
        String subject = "Your CareSync Password Reset OTP";
        String text = String.format("Hello %s,\n\nYour one-time password (OTP) is: %s.\nIt will expire in 10 minutes.\n\nIf you did not request this, please ignore this email.", name, otp);

        // If Mailtrap token is configured, use its API (free tier support)
        if (mailtrapToken != null && !mailtrapToken.isEmpty()) {
            try {
                String url = "https://send.api.mailtrap.io/api/send";
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(mailtrapToken);

                Map<String, Object> payload = new HashMap<>();
                Map<String, Object> from = new HashMap<>();
                from.put("email", sender.contains("<") ? sender.substring(sender.indexOf('<') + 1, sender.indexOf('>')) : sender);
                from.put("name", sender.contains("<") ? sender.substring(0, sender.indexOf('<')).trim() : "CareSync");
                payload.put("from", from);

                Map<String, Object> toObj = new HashMap<>();
                toObj.put("email", to);
                toObj.put("name", name);
                payload.put("to", new Object[]{toObj});

                payload.put("subject", subject);
                Map<String, Object> content = new HashMap<>();
                content.put("text", text);
                payload.put("content", content);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
                restTemplate.postForEntity(url, entity, String.class);
                log.info("Sent OTP email via Mailtrap to {}", to);
                return;
            } catch (Exception e) {
                log.warn("Failed to send OTP via Mailtrap, falling back to log: {}", e.getMessage());
            }
        }

        // Fallback: log the OTP - replace with your SMTP provider later
        log.info("[DEV] OTP for {} ({}): {}", name, to, otp);
    }
}