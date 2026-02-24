-- Persisted duplicate suppression for IRCv3 message ids.
-- Rows without message ids keep message_id NULL and are not deduplicated by this key.

ALTER TABLE chat_log ADD COLUMN message_id VARCHAR(512);

CREATE UNIQUE INDEX uq_chat_log_msgid
    ON chat_log(server_id, target, kind, direction, message_id);
