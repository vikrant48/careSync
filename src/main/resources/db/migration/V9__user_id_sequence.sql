-- Batch 17: user ids come from a sequence instead of SELECT MAX(user_id) + 1 (two sign-ups at the same moment
-- could get the same id). Starts after the highest existing id, at 100001 at the least.
-- Lock: CREATE SEQUENCE touches no table; setval reads users once. No downtime. Safe to run twice.
-- Rollback: db/rollback/U9__user_id_sequence.sql

CREATE SEQUENCE IF NOT EXISTS user_id_seq START WITH 100001;

SELECT setval('user_id_seq', GREATEST(100001, (SELECT COALESCE(MAX(user_id), 100000) + 1 FROM users)), false);
