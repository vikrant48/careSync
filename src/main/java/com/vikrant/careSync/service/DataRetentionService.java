package com.vikrant.careSync.service;

import com.vikrant.careSync.repository.AiAuditLogRepository;
import com.vikrant.careSync.repository.CommunicationRepository;
import com.vikrant.careSync.repository.EmailVerificationOtpRepository;
import com.vikrant.careSync.security.repository.LoginAttemptRepository;
import com.vikrant.careSync.security.repository.PasswordResetOtpRepository;
import com.vikrant.careSync.security.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nightly removal of data that has no reason to be kept. Clinical records (appointments, medical history,
 * intakes, documents, payments) and the access audit trail are never touched here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataRetentionService {

    private final EmailVerificationOtpRepository emailVerificationOtpRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final CommunicationRepository communicationRepository;
    private final AiAuditLogRepository aiAuditLogRepository;
    private final com.vikrant.careSync.scheduler.ClusterJobLock clusterJobLock;
    private final com.vikrant.careSync.repository.AiConversationRepository aiConversationRepository;

    @Value("${app.retention.one-time-codes-days:1}")
    private long oneTimeCodesDays;

    @Value("${app.retention.login-attempts-days:90}")
    private long loginAttemptsDays;

    @Value("${app.retention.sent-emails-days:30}")
    private long sentEmailsDays;

    @Value("${app.retention.ai-chat-days:90}")
    private int aiChatDays;

    @Value("${app.retention.ai-text-days:30}")
    private long aiTextDays;

    // @Transactional here, not only on purge(): a call from inside the class skips Spring's transaction proxy,
    // and the bulk deletes need a transaction
    @Scheduled(cron = "${app.retention.cron:0 30 3 * * *}")
    @Transactional
    public void scheduledPurge() {
        if (clusterJobLock.tryAcquire("data-retention")) {
            purge();
        }
    }

    @Transactional
    public Map<String, Integer> purge() {
        Instant now = Instant.now();
        LocalDateTime localNow = LocalDateTime.now();
        Map<String, Integer> removed = new LinkedHashMap<>();
        removed.put("emailVerificationCodes",
                emailVerificationOtpRepository.deleteSpentBefore(localNow.minusDays(oneTimeCodesDays)));
        removed.put("passwordResetCodes",
                passwordResetOtpRepository.deleteSpentBefore(now.minus(Duration.ofDays(oneTimeCodesDays))));
        removed.put("passwordResetLinks",
                passwordResetTokenRepository.deleteExpiredBefore(now.minus(Duration.ofDays(oneTimeCodesDays))));
        removed.put("loginAttempts",
                loginAttemptRepository.deleteOlderThan(now.minus(Duration.ofDays(loginAttemptsDays))));
        removed.put("sentEmails", communicationRepository.deleteOlderThan(localNow.minusDays(sentEmailsDays)));
        removed.put("aiLogTextCleared", aiAuditLogRepository.clearTextOlderThan(localNow.minusDays(aiTextDays)));
        removed.put("aiChatsDeleted", aiConversationRepository.deleteNotUpdatedSince(localNow.minusDays(aiChatDays)));
        log.info("Data retention purge: {}", removed);
        return removed;
    }
}
