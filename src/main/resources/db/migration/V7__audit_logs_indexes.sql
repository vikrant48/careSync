-- Batch 12: admin audit search (newest first, optionally per user).
-- Built CONCURRENTLY (no write lock); runs outside a transaction (see .conf). Safe to run twice.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs (timestamp DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs_username_timestamp ON audit_logs (username, timestamp DESC);
