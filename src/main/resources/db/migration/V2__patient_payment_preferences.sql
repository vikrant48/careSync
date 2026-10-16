-- Batch 7: last used payment method per patient (safe details only).
-- Applied by Flyway at startup. Rollback: 2026-10-15_batch7_patient_payment_preferences_rollback.sql
--
-- Lock / downtime risk:
--   * CREATE TABLE touches no existing table.
--   * ADD FOREIGN KEY takes a SHARE ROW EXCLUSIVE lock on "patients" (blocks writes to patients, not reads)
--     while it checks existing rows. The new table is empty, so the check is instant. No downtime expected.
--   * Safe to run twice: it skips what already exists, including a table that ddl-auto=update created first.
--
-- upi_id and cardholder_name hold ciphertext written by EncryptionConverter, hence the wide columns.
-- The full card number, expiry date and CVV are never stored.

CREATE TABLE IF NOT EXISTS patient_payment_preferences (
    id              BIGSERIAL    PRIMARY KEY,
    patient_id      BIGINT       NOT NULL,
    payment_method  VARCHAR(20)  NOT NULL,
    upi_id          VARCHAR(512),
    card_last_four  VARCHAR(4),
    card_brand      VARCHAR(20),
    cardholder_name VARCHAR(512),
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_payment_pref_patient UNIQUE (patient_id)
);

-- patients' primary key is user_id (Patient shares its id with User through @MapsId), not id.
-- Added separately so they also land on a table that ddl-auto=update created before this script ran
ALTER TABLE patient_payment_preferences DROP CONSTRAINT IF EXISTS fk_payment_pref_patient;
ALTER TABLE patient_payment_preferences
    ADD CONSTRAINT fk_payment_pref_patient FOREIGN KEY (patient_id) REFERENCES patients (user_id) ON DELETE CASCADE;

ALTER TABLE patient_payment_preferences DROP CONSTRAINT IF EXISTS ck_payment_pref_last_four;
ALTER TABLE patient_payment_preferences
    ADD CONSTRAINT ck_payment_pref_last_four CHECK (card_last_four IS NULL OR card_last_four ~ '^[0-9]{4}$');
