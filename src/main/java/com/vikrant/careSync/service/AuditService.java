package com.vikrant.careSync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@Slf4j
public class AuditService {

    public void log(String username, String action, String entityName, String entityId, String details, String ip) {
        String logMessage = String.format(
                "[HIPAA-AUDIT] Timestamp: %s | User: %s | Action: %s | Entity: %s | ID: %s | IP: %s | Details: %s",
                LocalDateTime.now(),
                username,
                action,
                entityName,
                entityId,
                ip,
                details);

        // Printing to application logs (can be directed to a file or log aggregator)
        log.info(logMessage);
    }
}
