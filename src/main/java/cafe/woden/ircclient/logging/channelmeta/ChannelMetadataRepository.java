package cafe.woden.ircclient.logging.channelmeta;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class ChannelMetadataRepository {

  private static final int CHANNEL_MAX = 256;
  private static final int TOPIC_MAX = 8192;
  private static final int TOPIC_SET_BY_MAX = 128;

  private static final String INSERT_SQL =
      """
      INSERT INTO channel_metadata(
        server_id,
        channel_key,
        channel_display,
        topic,
        topic_set_by,
        topic_set_at_epoch_ms,
        updated_at_epoch_ms
      )
      VALUES (?,?,?,?,?,?,?)
      """;

  private static final String UPDATE_SQL =
      """
      UPDATE channel_metadata
         SET channel_display = ?,
             topic = ?,
             topic_set_by = ?,
             topic_set_at_epoch_ms = ?,
             updated_at_epoch_ms = ?
       WHERE server_id = ?
         AND channel_key = ?
      """;

  private static final String DELETE_SQL =
      """
      DELETE FROM channel_metadata
       WHERE server_id = ?
         AND channel_key = ?
      """;

  private static final String SELECT_ONE_SQL =
      """
      SELECT server_id, channel_key, channel_display, topic, topic_set_by, topic_set_at_epoch_ms, updated_at_epoch_ms
        FROM channel_metadata
       WHERE server_id = ?
         AND channel_key = ?
      """;

  private static final String SELECT_ALL_SQL =
      """
      SELECT server_id, channel_key, channel_display, topic, topic_set_by, topic_set_at_epoch_ms, updated_at_epoch_ms
        FROM channel_metadata
      """;

  private static final RowMapper<ChannelMetadataRow> ROW_MAPPER =
      (rs, rowNum) ->
          new ChannelMetadataRow(
              Objects.toString(rs.getString("server_id"), "").trim(),
              Objects.toString(rs.getString("channel_key"), "").trim(),
              Objects.toString(rs.getString("channel_display"), "").trim(),
              Objects.toString(rs.getString("topic"), "").trim(),
              trimToNull(rs.getString("topic_set_by")),
              rs.getObject("topic_set_at_epoch_ms") != null
                  ? rs.getLong("topic_set_at_epoch_ms")
                  : null,
              rs.getLong("updated_at_epoch_ms"));

  private final JdbcTemplate jdbc;

  public record ChannelMetadataRow(
      String serverId,
      String channelKey,
      String channelDisplay,
      String topic,
      String topicSetBy,
      Long topicSetAtEpochMs,
      long updatedAtEpochMs) {

    public ChannelMetadataRow {
      serverId = Objects.toString(serverId, "").trim();
      channelKey = Objects.toString(channelKey, "").trim();
      channelDisplay = truncate(Objects.toString(channelDisplay, "").trim(), CHANNEL_MAX);
      topic = truncate(Objects.toString(topic, "").trim(), TOPIC_MAX);
      topicSetBy = truncate(trimToNull(topicSetBy), TOPIC_SET_BY_MAX);
      if (updatedAtEpochMs <= 0L) {
        updatedAtEpochMs = System.currentTimeMillis();
      }
    }
  }

  public ChannelMetadataRepository(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public void upsert(ChannelMetadataRow row) {
    if (row == null) return;
    if (row.serverId().isEmpty() || row.channelKey().isEmpty()) return;
    if (row.topic().isEmpty()) return;
    String channelDisplay =
        row.channelDisplay().isEmpty() ? row.channelKey() : row.channelDisplay();

    int updated =
        jdbc.update(
            UPDATE_SQL,
            channelDisplay,
            row.topic(),
            row.topicSetBy(),
            row.topicSetAtEpochMs(),
            row.updatedAtEpochMs(),
            row.serverId(),
            row.channelKey());
    if (updated > 0) return;

    try {
      jdbc.update(
          INSERT_SQL,
          row.serverId(),
          row.channelKey(),
          channelDisplay,
          row.topic(),
          row.topicSetBy(),
          row.topicSetAtEpochMs(),
          row.updatedAtEpochMs());
    } catch (DataAccessException ex) {
      if (!isDuplicateKey(ex)) throw ex;
      // Lost an insert race; apply a final update with the latest values.
      jdbc.update(
          UPDATE_SQL,
          channelDisplay,
          row.topic(),
          row.topicSetBy(),
          row.topicSetAtEpochMs(),
          row.updatedAtEpochMs(),
          row.serverId(),
          row.channelKey());
    }
  }

  public void delete(String serverId, String channelKey) {
    String sid = Objects.toString(serverId, "").trim();
    String key = Objects.toString(channelKey, "").trim();
    if (sid.isEmpty() || key.isEmpty()) return;
    jdbc.update(DELETE_SQL, sid, key);
  }

  public Optional<ChannelMetadataRow> find(String serverId, String channelKey) {
    String sid = Objects.toString(serverId, "").trim();
    String key = Objects.toString(channelKey, "").trim();
    if (sid.isEmpty() || key.isEmpty()) return Optional.empty();
    List<ChannelMetadataRow> rows = jdbc.query(SELECT_ONE_SQL, ROW_MAPPER, sid, key);
    if (rows == null || rows.isEmpty()) return Optional.empty();
    return Optional.ofNullable(rows.getFirst());
  }

  public List<ChannelMetadataRow> findAll() {
    List<ChannelMetadataRow> rows = jdbc.query(SELECT_ALL_SQL, ROW_MAPPER);
    if (rows == null || rows.isEmpty()) return List.of();
    return List.copyOf(rows);
  }

  private static String trimToNull(String value) {
    String trimmed = Objects.toString(value, "").trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String truncate(String value, int maxChars) {
    String v = Objects.toString(value, "");
    if (maxChars <= 0 || v.length() <= maxChars) return v;
    return v.substring(0, maxChars);
  }

  private static boolean isDuplicateKey(DataAccessException ex) {
    if (ex == null) return false;
    if (ex instanceof DuplicateKeyException) return true;
    Throwable cause = ex.getCause();
    while (cause != null) {
      String msg = String.valueOf(cause.getMessage());
      if (msg.contains("constraint") && msg.contains("PRIMARY")) return true;
      cause = cause.getCause();
    }
    return false;
  }
}
