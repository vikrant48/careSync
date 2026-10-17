-- Rollback of V12__ai_chat_history.sql. Deletes every saved AI chat (no other data is affected).
-- Run manually, then remove version 12 from Flyway's history so it is not re-applied:
--   DELETE FROM flyway_schema_history WHERE version = '12';
-- Deploy a backend without AiConversation / AiMessage first, or startup validation (ddl-auto=validate) fails.

DROP TABLE IF EXISTS ai_messages;
DROP TABLE IF EXISTS ai_conversations;
