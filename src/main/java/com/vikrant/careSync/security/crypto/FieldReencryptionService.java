package com.vikrant.careSync.security.crypto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.vikrant.careSync.util.ErrorLog;

/**
 * Rewrites every encrypted column into the current AES-256-GCM format: old AES/ECB values, and plaintext in
 * columns that were not encrypted before. Works in small batches with keyset paging; each row is updated only if
 * it still holds the value that was read, so concurrent edits by the app are never overwritten. Safe to repeat.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FieldReencryptionService {

    static final int BATCH_SIZE = 200;

    /** legacyEcb: the column held AES/ECB values; otherwise it held plaintext. Names are constants, never input. */
    record Target(String table, String idColumn, String column, boolean legacyEcb) {
    }

    static final List<Target> TARGETS = List.of(
            new Target("patients", "user_id", "first_name", true),
            new Target("patients", "user_id", "last_name", true),
            new Target("patients", "user_id", "contact_info", true),
            new Target("patients", "user_id", "illness_details", true),
            new Target("medical_histories", "id", "symptoms", true),
            new Target("medical_histories", "id", "diagnosis", true),
            new Target("medical_histories", "id", "treatment", true),
            new Target("medical_histories", "id", "medicine", true),
            new Target("medical_histories", "id", "doses", true),
            new Target("medical_histories", "id", "notes", true),
            new Target("medical_histories", "id", "subjective", true),
            new Target("medical_histories", "id", "objective", true),
            new Target("medical_histories", "id", "assessment", true),
            new Target("medical_histories", "id", "plan", true),
            new Target("medical_histories", "id", "transcript", true),
            new Target("patient_payment_preferences", "id", "upi_id", true),
            new Target("patient_payment_preferences", "id", "cardholder_name", true),
            new Target("appointment_intakes", "id", "chief_complaint", false),
            new Target("appointment_intakes", "id", "symptoms", false),
            new Target("appointment_intakes", "id", "current_medications", false),
            new Target("appointment_intakes", "id", "allergies", false),
            new Target("appointment_intakes", "id", "ai_summary", false),
            new Target("appointment_intakes", "id", "red_flags", false),
            new Target("appointment_intakes", "id", "edit_history", false),
            new Target("appointments", "id", "reason", false),
            new Target("appointments", "id", "cancellation_reason", false),
            new Target("chat_messages", "id", "content", false),
            new Target("ai_audit_logs", "id", "user_prompt", false),
            new Target("ai_audit_logs", "id", "ai_response", false));

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final FieldCipher cipher;

    @CacheEvict(cacheNames = { "PATIENT:PROFILE", "PATIENT:HISTORY", "PATIENT:COMPLETE_DATA",
            "PATIENT:PAGINATED_LIST", "PATIENT:APPOINTMENTS", "DOCTOR:APPOINTMENTS",
            "DOCTOR:APPOINTMENTS_PAGINATED" }, allEntries = true)
    public Map<String, Object> reencryptAll(boolean dryRun) {
        Map<String, Object> columns = new LinkedHashMap<>();
        long totalRewritten = 0;
        long totalFailed = 0;
        for (Target target : TARGETS) {
            ColumnResult result = process(target, dryRun);
            columns.put(target.table() + "." + target.column(), result.asMap());
            totalRewritten += dryRun ? result.needsRewrite : result.rewritten;
            totalFailed += result.failed;
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("dryRun", dryRun);
        report.put(dryRun ? "valuesToRewrite" : "valuesRewritten", totalRewritten);
        report.put("failed", totalFailed);
        report.put("columns", columns);
        log.info("Field re-encryption (dryRun={}): rewritten/toRewrite={}, failed={}", dryRun, totalRewritten,
                totalFailed);
        return report;
    }

    private ColumnResult process(Target target, boolean dryRun) {
        ColumnResult result = new ColumnResult();
        String select = "SELECT " + target.idColumn() + " AS row_id, " + target.column() + " AS val FROM "
                + target.table() + " WHERE " + target.column() + " IS NOT NULL AND " + target.idColumn()
                + " > ? ORDER BY " + target.idColumn() + " LIMIT " + BATCH_SIZE;
        String update = "UPDATE " + target.table() + " SET " + target.column() + " = ? WHERE "
                + target.idColumn() + " = ? AND " + target.column() + " = ?";

        long lastId = Long.MIN_VALUE;
        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(select, lastId);
            if (rows.isEmpty()) {
                return result;
            }
            List<Object[]> updates = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                long id = ((Number) row.get("row_id")).longValue();
                String stored = (String) row.get("val");
                lastId = id;
                Optional<String> plain = plaintextToRewrite(target, stored, result);
                plain.ifPresent(value -> updates.add(new Object[] { cipher.encrypt(value), id, stored }));
            }
            result.needsRewrite += updates.size();
            if (!dryRun && !updates.isEmpty()) {
                int[] counts = transactionTemplate.execute(status -> jdbcTemplate.batchUpdate(update, updates));
                for (int count : counts == null ? new int[0] : counts) {
                    // 0 = the row changed meanwhile (the app already wrote the current format); skipped safely
                    result.rewritten += count > 0 || count == java.sql.Statement.SUCCESS_NO_INFO ? 1 : 0;
                }
            }
        }
    }

    /** The plaintext to encrypt, or empty when the value is already current or cannot be read. */
    private Optional<String> plaintextToRewrite(Target target, String stored, ColumnResult result) {
        if (cipher.isCurrent(stored)) {
            try {
                cipher.decryptCurrent(stored);
                result.alreadyCurrent++;
                return Optional.empty();
            } catch (IllegalStateException e) {
                ErrorLog.debug(e);
                if (!target.legacyEcb()) {
                    // Plaintext that happens to start with "v2:"
                    result.plaintext++;
                    return Optional.of(stored);
                }
                result.failed++;
                return Optional.empty();
            }
        }
        if (target.legacyEcb()) {
            Optional<String> legacy = cipher.decryptLegacyEcb(stored);
            if (legacy.isPresent()) {
                result.legacyEcb++;
                return legacy;
            }
        }
        result.plaintext++;
        return Optional.of(stored);
    }

    private static final class ColumnResult {
        long alreadyCurrent;
        long legacyEcb;
        long plaintext;
        long needsRewrite;
        long rewritten;
        long failed;

        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("alreadyCurrent", alreadyCurrent);
            map.put("legacyEcb", legacyEcb);
            map.put("plaintext", plaintext);
            map.put("rewritten", rewritten);
            map.put("failed", failed);
            return map;
        }
    }
}
