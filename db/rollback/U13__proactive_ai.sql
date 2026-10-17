-- Rollback of V13__proactive_ai.sql. Deletes saved lab report explanations, "seen" times and saved after-visit
-- summaries (no other data is affected; summaries are re-made on demand by an older backend).
-- Run manually, then remove version 13 from Flyway's history so it is not re-applied:
--   DELETE FROM flyway_schema_history WHERE version = '13';
-- Deploy a backend without AiVisitSummary and the new Document fields first, or startup validation
-- (ddl-auto=validate) fails.

DROP TABLE IF EXISTS ai_visit_summaries;
ALTER TABLE documents DROP COLUMN IF EXISTS patient_viewed_at;
ALTER TABLE documents DROP COLUMN IF EXISTS ai_explained_at;
ALTER TABLE documents DROP COLUMN IF EXISTS ai_explanation;
