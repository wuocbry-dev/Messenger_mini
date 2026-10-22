-- Call history in the chat timeline: a finished call is recorded as one
-- message of type CALL that points at its row in `calls`.

ALTER TABLE messages DROP CHECK chk_messages_type;

ALTER TABLE messages
    ADD COLUMN call_id BIGINT UNSIGNED NULL AFTER sender_id,
    ADD CONSTRAINT chk_messages_type CHECK (type IN ('TEXT','IMAGE','FILE','SYSTEM','CALL')),
    ADD CONSTRAINT uk_messages_call UNIQUE (call_id),
    ADD CONSTRAINT fk_messages_call FOREIGN KEY (call_id) REFERENCES calls(id) ON DELETE SET NULL;

-- Backfill calls that already finished, placed in the timeline when they ended.
-- FAILED calls are abandoned sessions (e.g. a closed tab), so they are skipped.
INSERT INTO messages (conversation_id, sender_id, call_id, type, content, created_at)
SELECT c.conversation_id, c.caller_id, c.id, 'CALL', NULL, COALESCE(c.ended_at, c.started_at)
FROM calls c
WHERE c.status IN ('ENDED', 'REJECTED', 'CANCELLED', 'MISSED');
