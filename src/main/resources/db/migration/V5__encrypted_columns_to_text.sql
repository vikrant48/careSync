-- Batch 11: encrypted patient-data columns become TEXT (AES-256-GCM values are longer than the old ones,
-- and intake answers, visit reasons and chat messages are now encrypted too).
-- Applied by Flyway at startup. Rollback: 2026-10-15_batch11_encrypted_columns_to_text_rollback.sql
--
-- Lock / downtime risk:
--   * VARCHAR(n) -> TEXT is binary-compatible in PostgreSQL: no table rewrite and no index rebuild.
--     Each statement takes a brief ACCESS EXCLUSIVE lock on its table (blocks reads and writes for
--     milliseconds). Run outside peak hours; no downtime expected.
--   * Safe to run twice.
--
-- After deploying: back up the database (create a Neon branch), then call
--   POST /api/admin/security/reencrypt-fields            (dry run: counts what will change)
--   POST /api/admin/security/reencrypt-fields?dryRun=false
-- and when a second dry run shows valuesToRewrite = 0 and failed = 0, set FIELD_ENCRYPTION_ACCEPT_LEGACY=false.

ALTER TABLE patients ALTER COLUMN first_name TYPE TEXT;
ALTER TABLE patients ALTER COLUMN last_name TYPE TEXT;
ALTER TABLE patients ALTER COLUMN contact_info TYPE TEXT;
ALTER TABLE patients ALTER COLUMN illness_details TYPE TEXT;

ALTER TABLE appointment_intakes ALTER COLUMN chief_complaint TYPE TEXT;

ALTER TABLE appointments ALTER COLUMN reason TYPE TEXT;
ALTER TABLE appointments ALTER COLUMN cancellation_reason TYPE TEXT;

ALTER TABLE chat_messages ALTER COLUMN content TYPE TEXT;
