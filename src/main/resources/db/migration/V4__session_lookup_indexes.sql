-- Batch 8: indexes for the per-request session lookup, logout and cleanup.
-- Built CONCURRENTLY (no write lock); runs outside a transaction (see .conf). Safe to run twice.
-- If a build fails it leaves an INVALID index: DROP INDEX CONCURRENTLY it and restart the app.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_session_session_id ON user_session (session_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_refresh_token_session_id ON refresh_token (session_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_session_active_login ON user_session (login_time) WHERE active = true;
