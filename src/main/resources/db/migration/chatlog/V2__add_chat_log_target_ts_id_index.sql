-- Improve deterministic paging performance for "load older messages".

CREATE INDEX idx_chat_log_target_ts_id ON chat_log(server_id, target, ts_epoch_ms, id);
