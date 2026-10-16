-- Batch 15: indexes for the hot queries (Q4, Q6, Q8, Q9, Q10, Q13).
-- Built CONCURRENTLY: no write lock on any table while they build. Runs outside a transaction (see .conf).
-- Safe to run twice. If a build fails it leaves an INVALID index: DROP INDEX CONCURRENTLY it and restart the app.
-- Rollback: db/rollback/U8__query_indexes.sql

-- Doctor schedule, "today", dashboard counts: doctor_id = ? AND appointment_date_time in a range
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_appointments_doctor_datetime ON appointments (doctor_id, appointment_date_time);
-- Patient appointment lists
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_appointments_patient_datetime ON appointments (patient_id, appointment_date_time);
-- Status history per appointment, and "changed since yesterday" on the dashboard
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_status_logs_appointment_created ON appointment_status_logs (appointment_id, created_at);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_status_logs_created ON appointment_status_logs (created_at);
-- Dashboard rating averages
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_feedbacks_doctor_created ON feedbacks (doctor_id, created_at);
-- Notification feed (newest first) and unread badge, both polled
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_recipient_time ON notifications (recipient_type, recipient_id, "timestamp" DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notifications_unread ON notifications (recipient_type, recipient_id) WHERE "read" = false;
-- Medical history by patient and visit date
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_medical_histories_patient_visit ON medical_histories (patient_id, visit_date);
-- Payment lookups by booking or by appointment (now two separate queries)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_booking ON payments (booking_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_appointment ON payments (appointment_id);
