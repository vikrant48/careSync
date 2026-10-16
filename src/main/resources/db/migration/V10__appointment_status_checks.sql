-- Appointment status rules list every status the app uses (Appointment.Status).
-- Older databases kept the list from when the table was first created (Hibernate never updates CHECK constraints),
-- e.g. without NO_SHOW_PATIENT, so the no-show job failed. The removed SchemaMigrationRunner used to rewrite
-- this rule on every startup; this migration does it once, the Flyway way.
--
-- Lock / downtime: NOT VALID adds the rule without scanning rows (brief lock); VALIDATE then checks existing rows
-- while still allowing reads and writes. Safe to run twice.
-- When a status is added to Appointment.Status, add a migration like this one with the new list.

ALTER TABLE appointments DROP CONSTRAINT IF EXISTS appointments_status_check;
ALTER TABLE appointments ADD CONSTRAINT appointments_status_check CHECK (status IN (
    'BOOKED', 'REQUESTED', 'CONFIRMED', 'SCHEDULED', 'INTAKE', 'INTAKE_COMPLETED', 'WAITING_ROOM',
    'READY_FOR_VISIT', 'IN_PROGRESS', 'MEDICAL_RECORD', 'REPORT_DRAFTED', 'READY_TO_COMPLETE', 'COMPLETED',
    'CANCELLED', 'REJECTED', 'CANCELLED_BY_PATIENT', 'CANCELLED_BY_DOCTOR', 'NO_SHOW', 'NO_SHOW_PATIENT',
    'NO_SHOW_DOCTOR', 'AUTO_CLOSED')) NOT VALID;
ALTER TABLE appointments VALIDATE CONSTRAINT appointments_status_check;

ALTER TABLE appointment_status_logs DROP CONSTRAINT IF EXISTS appointment_status_logs_new_status_check;
ALTER TABLE appointment_status_logs ADD CONSTRAINT appointment_status_logs_new_status_check CHECK (new_status IN (
    'BOOKED', 'REQUESTED', 'CONFIRMED', 'SCHEDULED', 'INTAKE', 'INTAKE_COMPLETED', 'WAITING_ROOM',
    'READY_FOR_VISIT', 'IN_PROGRESS', 'MEDICAL_RECORD', 'REPORT_DRAFTED', 'READY_TO_COMPLETE', 'COMPLETED',
    'CANCELLED', 'REJECTED', 'CANCELLED_BY_PATIENT', 'CANCELLED_BY_DOCTOR', 'NO_SHOW', 'NO_SHOW_PATIENT',
    'NO_SHOW_DOCTOR', 'AUTO_CLOSED')) NOT VALID;
ALTER TABLE appointment_status_logs VALIDATE CONSTRAINT appointment_status_logs_new_status_check;

ALTER TABLE appointment_status_logs DROP CONSTRAINT IF EXISTS appointment_status_logs_previous_status_check;
ALTER TABLE appointment_status_logs ADD CONSTRAINT appointment_status_logs_previous_status_check CHECK (
    previous_status IS NULL OR previous_status IN (
    'BOOKED', 'REQUESTED', 'CONFIRMED', 'SCHEDULED', 'INTAKE', 'INTAKE_COMPLETED', 'WAITING_ROOM',
    'READY_FOR_VISIT', 'IN_PROGRESS', 'MEDICAL_RECORD', 'REPORT_DRAFTED', 'READY_TO_COMPLETE', 'COMPLETED',
    'CANCELLED', 'REJECTED', 'CANCELLED_BY_PATIENT', 'CANCELLED_BY_DOCTOR', 'NO_SHOW', 'NO_SHOW_PATIENT',
    'NO_SHOW_DOCTOR', 'AUTO_CLOSED')) NOT VALID;
ALTER TABLE appointment_status_logs VALIDATE CONSTRAINT appointment_status_logs_previous_status_check;
