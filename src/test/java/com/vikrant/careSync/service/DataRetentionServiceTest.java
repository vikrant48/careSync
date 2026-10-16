package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.AiAuditLog;
import com.vikrant.careSync.entity.Communication;
import com.vikrant.careSync.entity.EmailVerificationOtp;
import com.vikrant.careSync.repository.AiAuditLogRepository;
import com.vikrant.careSync.repository.CommunicationRepository;
import com.vikrant.careSync.repository.EmailVerificationOtpRepository;
import com.vikrant.careSync.security.entity.LoginAttempt;
import com.vikrant.careSync.security.entity.PasswordResetOtp;
import com.vikrant.careSync.security.entity.PasswordResetToken;
import com.vikrant.careSync.security.repository.LoginAttemptRepository;
import com.vikrant.careSync.security.repository.PasswordResetOtpRepository;
import com.vikrant.careSync.security.repository.PasswordResetTokenRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The nightly purge removes spent or old transient data and keeps everything still in use. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DataRetentionServiceTest {

    @Autowired
    private DataRetentionService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EmailVerificationOtpRepository emailOtps;
    @Autowired
    private PasswordResetOtpRepository resetOtps;
    @Autowired
    private PasswordResetTokenRepository resetTokens;
    @Autowired
    private LoginAttemptRepository loginAttempts;
    @Autowired
    private CommunicationRepository communications;
    @Autowired
    private AiAuditLogRepository aiLogs;

    private static Timestamp daysAgo(int days) {
        return Timestamp.valueOf(LocalDateTime.now().minusDays(days));
    }

    private Long emailOtp(boolean verified, int createdDaysAgo, int expiredDaysAgo) {
        Long id = emailOtps.save(EmailVerificationOtp.builder().email("p" + createdDaysAgo + verified + "@test.local")
                .otp("123456").createdAt(LocalDateTime.now()).expiryDate(LocalDateTime.now().plusMinutes(10))
                .verified(verified).used(verified).build()).getId();
        entityManager.flush();
        jdbc.update("UPDATE email_verification_otps SET created_at = ?, expiry_date = ? WHERE id = ?",
                daysAgo(createdDaysAgo), daysAgo(expiredDaysAgo), id);
        return id;
    }

    @Test
    void purgesOnlyWhatIsSpentOrOld() {
        Long verifiedLongAgo = emailOtp(true, 3, 3);
        Long expiredUnused = emailOtp(false, 3, 2);
        Long verifiedToday = emailOtp(true, 0, -1);   // registration may still use it
        Long pendingCode = emailOtp(false, 0, -1);

        resetOtps.save(PasswordResetOtp.builder().email("a@test.local").otp("1").role("PATIENT").userId(1L)
                .name("A").mobileNumber("9000000000")
                .createdAt(Instant.now().minus(5, ChronoUnit.DAYS)).expiryDate(Instant.now().minus(5, ChronoUnit.DAYS))
                .build());
        resetTokens.save(PasswordResetToken.builder().token("t-old").email("a@test.local")
                .expiryDate(Instant.now().minus(3, ChronoUnit.DAYS)).build());
        Long freshToken = resetTokens.save(PasswordResetToken.builder().token("t-new").email("a@test.local")
                .expiryDate(Instant.now().plus(10, ChronoUnit.MINUTES)).build()).getId();

        loginAttempts.save(LoginAttempt.builder().username("u").ipAddress("10.0.0.1").userAgent("x")
                .timestamp(Instant.now().minus(120, ChronoUnit.DAYS)).build());
        Long recentAttempt = loginAttempts.save(LoginAttempt.builder().username("u").ipAddress("10.0.0.1")
                .userAgent("x").timestamp(Instant.now().minus(10, ChronoUnit.DAYS)).build()).getId();

        Long oldEmail = communications.save(Communication.builder().fromEmail("a@x").toEmail("b@x").subject("s")
                .body("Your lab report is ready").status(Communication.Status.SENT).createdAt(LocalDateTime.now())
                .build()).getId();
        Long oldAi = aiLogs.save(AiAuditLog.builder().promptType("CHAT").selectedModel("m").latencyMs(5L)
                .success(true).totalTokens(42).userPrompt("I have chest pain").aiResponse("See a doctor")
                .createdAt(LocalDateTime.now()).build()).getId();
        entityManager.flush();
        jdbc.update("UPDATE communications SET created_at = ? WHERE id = ?", daysAgo(45), oldEmail);
        jdbc.update("UPDATE ai_audit_logs SET created_at = ? WHERE id = ?", daysAgo(45), oldAi);
        entityManager.clear();

        Map<String, Integer> removed = service.purge();
        entityManager.clear();

        assertEquals(2, removed.get("emailVerificationCodes"));
        assertFalse(emailOtps.existsById(verifiedLongAgo));
        assertFalse(emailOtps.existsById(expiredUnused));
        assertTrue(emailOtps.existsById(verifiedToday));
        assertTrue(emailOtps.existsById(pendingCode));

        assertEquals(1, removed.get("passwordResetCodes"));
        assertEquals(1, removed.get("passwordResetLinks"));
        assertTrue(resetTokens.existsById(freshToken));

        assertEquals(1, removed.get("loginAttempts"));
        assertTrue(loginAttempts.existsById(recentAttempt));

        assertEquals(1, removed.get("sentEmails"));
        assertFalse(communications.existsById(oldEmail));

        AiAuditLog cleared = aiLogs.findById(oldAi).orElseThrow();
        assertNull(cleared.getUserPrompt());
        assertNull(cleared.getAiResponse());
        assertEquals(42, cleared.getTotalTokens(), "usage statistics are kept");
    }
}
