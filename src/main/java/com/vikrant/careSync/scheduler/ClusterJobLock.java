package com.vikrant.careSync.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Lets a scheduled job run on one app instance at a time. Uses a PostgreSQL transaction-level advisory lock,
 * released automatically when the job's transaction ends, so a crashed instance can never leave it held.
 * Other databases (H2 in tests) have no such lock: there the job always runs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClusterJobLock {

    // Namespace so these locks cannot collide with other advisory locks
    private static final int LOCK_NAMESPACE = 742_017;

    private final JdbcTemplate jdbcTemplate;
    private volatile Boolean postgres;

    /** Call inside the job's transaction. False: another instance is running this job now, so skip this run. */
    public boolean tryAcquire(String jobName) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("ClusterJobLock needs an active transaction (job " + jobName + ")");
        }
        if (!isPostgres()) {
            return true;
        }
        Boolean acquired = jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock(?, ?)", Boolean.class,
                LOCK_NAMESPACE, jobName.hashCode());
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Skipping {}: running on another instance", jobName);
            return false;
        }
        return true;
    }

    private boolean isPostgres() {
        if (postgres == null) {
            postgres = Boolean.TRUE.equals(jdbcTemplate.execute((Connection connection) -> {
                DatabaseMetaData metaData = connection.getMetaData();
                return "PostgreSQL".equalsIgnoreCase(metaData.getDatabaseProductName());
            }));
        }
        return postgres;
    }
}
