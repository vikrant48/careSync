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

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender, CommunicationRepository communicationRepository, EmailTemplateService emailTemplateService) {
        this.mailSender = mailSender;
        this.communicationRepository = communicationRepository;
        this.emailTemplateService = emailTemplateService;
    }

    public void sendTemplateEmail(String to, String subject, String templatePath, java.util.Map<String, String> model) {
        String html = emailTemplateService.render(templatePath, model);
        String sender = (fromAddress != null && !fromAddress.isBlank()) ? fromAddress : "noreply@caresync.local";
        try {
            var mimeMessage = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(new InternetAddress(sender, "CareSync"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mimeMessage);
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
        } catch (Exception e) {
            log.error("Failed to send template email '{}': {}", templatePath, e.getMessage(), e);
            communicationRepository.save(
                    Communication.builder()
                            .fromEmail(sender)
                            .toEmail(to)
                            .subject(subject)
                            .body(html)
                            .status(Communication.Status.FAILED)
                            .errorMessage(truncate(e.getMessage(), 1000))
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