package com.vikrant.careSync.service;

import com.vikrant.careSync.entity.AuditLog;
import com.vikrant.careSync.repository.AuditLogRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Who accessed or changed patient data, when, from where and with what result. Entries go to the audit_logs
 * table and never contain patient data: only the route, record ids, the outcome and the user.
 * Writes run on a small pool of their own so they add no latency; when the queue is full the request thread
 * writes the entry itself, so entries are never dropped.
 */
@Service
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1000), runnable -> {
                Thread thread = new Thread(runnable, "audit-writer");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.CallerRunsPolicy());

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String username, String action, String entityName, String entityId, String details, String ip) {
        AuditLog entry = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .username(username != null ? username : "anonymous")
                .action(action)
                .entityName(truncate(entityName, 255))
                .entityId(entityId != null ? truncate(entityId, 255) : "N/A")
                .details(details)
                .ipAddress(ip)
                .build();
        writer.execute(() -> save(entry));
    }

    private void save(AuditLog entry) {
        try {
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Keep the event in the application log if the database write fails
            log.error("[HIPAA-AUDIT] could not store entry: user={} action={} entity={} id={} ip={}: {}",
                    entry.getUsername(), entry.getAction(), entry.getEntityName(), entry.getEntityId(),
                    entry.getIpAddress(), e.getMessage());
        }
    }

    public Page<AuditLog> search(String username, String entityId, LocalDateTime from, LocalDateTime to,
            Pageable pageable) {
        // Fixed bounds instead of nulls: PostgreSQL cannot type a null timestamp parameter
        LocalDateTime fromTime = from != null ? from : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime toTime = to != null ? to : LocalDateTime.now().plusDays(1);
        return auditLogRepository.search(blankToNull(username), blankToNull(entityId), fromTime, toTime, pageable);
    }

    /** Lets tests and shutdown wait for queued entries. */
    public boolean awaitIdle(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (writer.getQueue().isEmpty() && writer.getActiveCount() == 0) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        writer.shutdown();
        writer.awaitTermination(10, TimeUnit.SECONDS);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
