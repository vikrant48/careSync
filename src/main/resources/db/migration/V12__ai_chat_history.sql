-- Server-side AI chat history: patients' and doctors' AI assistant chats, available on every device.
-- Applied by Flyway at startup. Rollback: db/rollback/U12__ai_chat_history.sql
--
-- Lock / downtime risk:
--   * CREATE TABLE and their indexes touch no existing table.
--   * ADD FOREIGN KEY takes a SHARE ROW EXCLUSIVE lock on "users" (blocks writes to users, not reads) while it
--     checks existing rows. The new tables are empty, so the check is instant. No downtime expected.
--   * Safe to run twice.
--
-- title, content, card_json and emergency_json hold ciphertext written by EncryptedTextConverter (AES-256-GCM):
-- chats contain health details. Old chats are deleted by the data-retention job (RETENTION_AI_CHAT_DAYS, 90).

CREATE TABLE IF NOT EXISTS ai_conversations (
    id            BIGSERIAL   PRIMARY KEY,
    public_id     VARCHAR(64) NOT NULL,
    user_id       BIGINT      NOT NULL,
    title         TEXT,
    message_count INTEGER     NOT NULL DEFAULT 0,
    created_at    TIMESTAMP   NOT NULL,
    updated_at    TIMESTAMP   NOT NULL,
    CONSTRAINT uk_ai_conversations_public_id UNIQUE (public_id)
);

-- "My chats, newest first" and the retention job
CREATE INDEX IF NOT EXISTS idx_ai_conversations_user_updated ON ai_conversations (user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_conversations_updated ON ai_conversations (updated_at);

ALTER TABLE ai_conversations DROP CONSTRAINT IF EXISTS fk_ai_conversations_user;
ALTER TABLE ai_conversations
    ADD CONSTRAINT fk_ai_conversations_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;

CREATE TABLE IF NOT EXISTS ai_messages (
    id              BIGSERIAL   PRIMARY KEY,
    conversation_id BIGINT      NOT NULL,
    role            VARCHAR(16) NOT NULL,
    content         TEXT        NOT NULL,
    card_json       TEXT,
    emergency_json  TEXT,
    created_at      TIMESTAMP   NOT NULL,
    CONSTRAINT ck_ai_messages_role CHECK (role IN ('user', 'assistant'))
);

-- Messages of one chat in order
CREATE INDEX IF NOT EXISTS idx_ai_messages_conversation ON ai_messages (conversation_id, id);

ALTER TABLE ai_messages DROP CONSTRAINT IF EXISTS fk_ai_messages_conversation;
ALTER TABLE ai_messages
    ADD CONSTRAINT fk_ai_messages_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id)
        ON DELETE CASCADE;
