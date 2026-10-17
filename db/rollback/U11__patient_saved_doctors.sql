-- Rollback of V11__patient_saved_doctors.sql. Deletes every patient's saved doctors (no other data is affected).
-- Run manually, then remove version 11 from Flyway's history so it is not re-applied:
--   DELETE FROM flyway_schema_history WHERE version = '11';
-- Also deploy a backend without PatientSavedDoctor first, or startup validation (ddl-auto=validate) fails.

DROP TABLE IF EXISTS patient_saved_doctors;
