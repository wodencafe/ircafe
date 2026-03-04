-- Persisted per-channel metadata used for startup/offline UI restoration.

CREATE TABLE channel_metadata (
  server_id VARCHAR(128) NOT NULL,
  channel_key VARCHAR(256) NOT NULL,
  channel_display VARCHAR(256) NOT NULL,
  topic VARCHAR(8192) NOT NULL,
  topic_set_by VARCHAR(128),
  topic_set_at_epoch_ms BIGINT,
  updated_at_epoch_ms BIGINT NOT NULL,
  PRIMARY KEY (server_id, channel_key)
);

CREATE INDEX idx_channel_metadata_updated_at ON channel_metadata(updated_at_epoch_ms);
