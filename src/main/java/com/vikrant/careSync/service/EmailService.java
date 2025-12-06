package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.Communication;
import com.vikrant.careSync.repository.CommunicationRepository;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;


@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final CommunicationRepository communicationRepository;
    private final EmailTemplateService emailTemplateService;
    private final SendGridEmailClient sendGridEmailClient;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${mail.from.address:}")
    private String overrideFromAddress;

    @Value("${mail.api.provider:}")
    private String mailApiProvider;

    public EmailService(JavaMailSender mailSender, CommunicationRepository communicationRepository, EmailTemplateService emailTemplateService, SendGridEmailClient sendGridEmailClient) {
        this.mailSender = mailSender;
        this.communicationRepository = communicationRepository;
        this.emailTemplateService = emailTemplateService;
        this.sendGridEmailClient = sendGridEmailClient;
    }

    public void sendTemplateEmail(String to, String subject, String templatePath, java.util.Map<String, String> model) {
        String html = emailTemplateService.render(templatePath, model);
        String sender;
        if (overrideFromAddress != null && !overrideFromAddress.isBlank()) {
            sender = overrideFromAddress;
        } else if (fromAddress != null && !fromAddress.isBlank()) {
            sender = fromAddress;
        } else {
            sender = "noreply@caresync.local";
        }
        boolean sent = false;
        Exception sendError = null;

        // Try HTTP provider first when configured
        if (mailApiProvider != null && mailApiProvider.equalsIgnoreCase("sendgrid")) {
            try {
                sent = sendGridEmailClient.sendHtml(sender, to, subject, html);
            } catch (Exception e) {
                sendError = e;
                log.warn("SendGrid send failed, falling back to SMTP: {}", e.getMessage());
            }
        }

        // Fallback to SMTP if not sent via HTTP
        if (!sent) {
            try {
                var mimeMessage = mailSender.createMimeMessage();
                var helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setFrom(new InternetAddress(sender, "CareSync"));
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(html, true);
                mailSender.send(mimeMessage);
                sent = true;
                sendError = null;
            } catch (Exception e) {
                sendError = e;
                log.error("SMTP send failed: {}", e.getMessage(), e);
            }
        }

        if (sent) {
            log.info("Sent template email '{}' to {}", templatePath, to);
            communicationRepository.save(
                    Communication.builder()
                            .fromEmail(sender)
                            .toEmail(to)
                            .subject(subject)
                            .body(html)
                            .status(Communication.Status.SENT)
                            .errorMessage(null)
                            .createdAt(java.time.LocalDateTime.now())
                            .build()
            );
        } else {
            String err = sendError != null ? sendError.getMessage() : "Unknown error";
            log.error("Failed to send template email '{}': {}", templatePath, err);
            communicationRepository.save(
                    Communication.builder()
                            .fromEmail(sender)
                            .toEmail(to)
                            .subject(subject)
                            .body(html)
                            .status(Communication.Status.FAILED)
                            .errorMessage(truncate(err, 1000))
                            .createdAt(java.time.LocalDateTime.now())
                            .build()
            );
        }
    }

    public void sendOtpEmail(String to, String name, String otp) {
        String subject = "Your CareSync Password Reset OTP";
        java.util.Map<String, String> model = new java.util.HashMap<>();
        model.put("name", name);
        model.put("otp", otp);
        sendTemplateEmail(to, subject, "email/password-reset-otp.html", model);
    }

    

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }
}