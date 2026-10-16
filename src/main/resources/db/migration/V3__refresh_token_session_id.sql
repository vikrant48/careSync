-- Batch 8: refresh tokens belong to a login session.
-- Lock: ADD COLUMN without default is metadata-only (brief lock, no rewrite). Safe to run twice.

ALTER TABLE refresh_token ADD COLUMN IF NOT EXISTS session_id VARCHAR(255);
