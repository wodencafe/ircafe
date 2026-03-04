-- Persist per-channel topic panel divider height.

ALTER TABLE channel_metadata ADD COLUMN topic_panel_height_px INTEGER;
