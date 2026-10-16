-- Batch 12: audit trail of patient-data access (no patient data in rows).
-- CREATE TABLE IF NOT EXISTS: no effect on other tables. Safe to run twice.
-- Rows are the legal record of access: plan retention with your compliance owner (HIPAA: 6 years).

CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGSERIAL    PRIMARY KEY,
    timestamp   TIMESTAMP    NOT NULL,
    username    VARCHAR(255) NOT NULL,
    action      VARCHAR(255) NOT NULL,
    entity_name VARCHAR(255) NOT NULL,
    entity_id   VARCHAR(255) NOT NULL,
    details     TEXT,
    ip_address  VARCHAR(255)
);
