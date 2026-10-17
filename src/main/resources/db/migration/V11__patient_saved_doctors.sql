-- Saved (favourite) doctors per patient: the heart on a doctor's profile and the "Saved" tab in Find & Book.
-- Applied by Flyway at startup. Rollback: db/rollback/U11__patient_saved_doctors.sql
--
-- Lock / downtime risk:
--   * CREATE TABLE and its index touch no existing table.
--   * ADD FOREIGN KEY takes a SHARE ROW EXCLUSIVE lock on "patients" and "doctors" (blocks writes to them, not
--     reads) while it checks existing rows. The new table is empty, so the check is instant. No downtime expected.
--   * Safe to run twice.
--
-- patients and doctors share their primary key with users (@MapsId), so both are keyed by user_id.

CREATE TABLE IF NOT EXISTS patient_saved_doctors (
    id         BIGSERIAL PRIMARY KEY,
    patient_id BIGINT    NOT NULL,
    doctor_id  BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL,
    -- One save per patient and doctor; also serves "list my saved doctors" (patient_id first)
    CONSTRAINT uk_saved_doctor_patient_doctor UNIQUE (patient_id, doctor_id)
);

-- Deleting a doctor removes their saves; this index keeps that lookup fast
CREATE INDEX IF NOT EXISTS idx_saved_doctors_doctor ON patient_saved_doctors (doctor_id);

ALTER TABLE patient_saved_doctors DROP CONSTRAINT IF EXISTS fk_saved_doctor_patient;
ALTER TABLE patient_saved_doctors
    ADD CONSTRAINT fk_saved_doctor_patient FOREIGN KEY (patient_id) REFERENCES patients (user_id) ON DELETE CASCADE;

ALTER TABLE patient_saved_doctors DROP CONSTRAINT IF EXISTS fk_saved_doctor_doctor;
ALTER TABLE patient_saved_doctors
    ADD CONSTRAINT fk_saved_doctor_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (user_id) ON DELETE CASCADE;
