-- Proactive AI (Batch 8): saved lab report explanations, "unread" lab reports, saved after-visit summaries.
-- Applied by Flyway at startup. Rollback: db/rollback/U13__proactive_ai.sql
--
-- Lock / downtime risk:
--   * ADD COLUMN without a default only changes the catalog (brief ACCESS EXCLUSIVE lock on "documents", no rewrite).
--   * The UPDATE marks existing lab reports as already seen, so old reports don't suddenly show as unread. It
--     locks the lab report rows it touches while it runs (documents is small). No downtime expected.
--   * CREATE TABLE touches no existing table; its foreign key briefly takes a SHARE ROW EXCLUSIVE lock on
--     "appointments" (blocks writes, not reads); the new table is empty, so the check is instant.
--   * Safe to run twice.
--
-- documents.ai_explanation and ai_visit_summaries.summary hold ciphertext written by EncryptedTextConverter
-- (AES-256-GCM): they describe a patient's results and visit.

ALTER TABLE documents ADD COLUMN IF NOT EXISTS ai_explanation TEXT;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS ai_explained_at TIMESTAMP;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS patient_viewed_at TIMESTAMP;

UPDATE documents SET patient_viewed_at = upload_date
WHERE document_type = 'LAB_REPORT' AND patient_viewed_at IS NULL;

CREATE TABLE IF NOT EXISTS ai_visit_summaries (
    appointment_id BIGINT      PRIMARY KEY,
    summary        TEXT        NOT NULL,
    source_hash    VARCHAR(64) NOT NULL,
    created_at     TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP   NOT NULL
);

ALTER TABLE ai_visit_summaries DROP CONSTRAINT IF EXISTS fk_ai_visit_summaries_appointment;
ALTER TABLE ai_visit_summaries
    ADD CONSTRAINT fk_ai_visit_summaries_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id)
        ON DELETE CASCADE;
