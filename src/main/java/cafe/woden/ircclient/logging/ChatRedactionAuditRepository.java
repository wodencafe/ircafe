package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.model.LogKind;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public final class ChatRedactionAuditRepository {

  private static final int TEXT_MAX = 8192;

  private static final String INSERT_SQL =
      """
      INSERT INTO chat_redaction_audit(
        server_id,
        target,
        message_id,
        redacted_at_epoch_ms,
        redacted_by,
        original_kind,
        original_from_nick,
        original_text,
        original_epoch_ms
      ) VALUES (?,?,?,?,?,?,?,?,?)
      """;

  private static final String SELECT_LATEST_SQL =
      """
      SELECT server_id,
             target,
             message_id,
             redacted_at_epoch_ms,
             redacted_by,
             original_kind,
             original_from_nick,
             original_text,
             original_epoch_ms
        FROM chat_redaction_audit
       WHERE server_id = ?
         AND LOWER(target) = LOWER(?)
         AND message_id = ?
    ORDER BY redacted_at_epoch_ms DESC, id DESC
       LIMIT 1
      """;

  private static final String DELETE_OLDER_THAN_SQL =
      """
      DELETE FROM chat_redaction_audit
       WHERE redacted_at_epoch_ms < ?
      """;

  private static final RowMapper<ChatRedactionAuditRecord> ROW_MAPPER =
      (rs, rowNum) ->
          new ChatRedactionAuditRecord(
              rs.getString("server_id"),
              rs.getString("target"),
              rs.getString("message_id"),
              rs.getLong("redacted_at_epoch_ms"),
              rs.getString("redacted_by"),
              LogKind.valueOf(rs.getString("original_kind")),
              rs.getString("original_from_nick"),
              rs.getString("original_text"),
              readOptionalLong(rs.getLong("original_epoch_ms"), rs.wasNull()));

  private final JdbcTemplate jdbc;

  public ChatRedactionAuditRepository(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public void insert(ChatRedactionAuditRecord record) {
    if (record == null) return;
    jdbc.update(
        INSERT_SQL,
        ps -> {
          setString(ps, 1, record.serverId());
          setString(ps, 2, record.target());
          setString(ps, 3, record.messageId());
          ps.setLong(4, record.redactedAtEpochMs());
          setNullableString(ps, 5, record.redactedBy());
          setString(ps, 6, record.originalKind().name());
          setNullableString(ps, 7, record.originalFromNick());
          setString(ps, 8, truncate(record.originalText()));
          if (record.originalEpochMs() == null) {
            ps.setNull(9, java.sql.Types.BIGINT);
          } else {
            ps.setLong(9, record.originalEpochMs());
          }
        });
  }

  public Optional<ChatRedactionAuditRecord> findLatest(
      String serverId, String target, String messageId) {
    String sid = Objects.toString(serverId, "").trim();
    String tgt = Objects.toString(target, "").trim();
    String msgId = Objects.toString(messageId, "").trim();
    if (sid.isEmpty() || tgt.isEmpty() || msgId.isEmpty()) return Optional.empty();
    List<ChatRedactionAuditRecord> rows =
        jdbc.query(SELECT_LATEST_SQL, ROW_MAPPER, sid, tgt, msgId);
    return rows == null || rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
  }

  public int deleteOlderThan(long cutoffEpochMs) {
    return jdbc.update(DELETE_OLDER_THAN_SQL, cutoffEpochMs);
  }

  private static void setString(PreparedStatement ps, int index, String value) throws SQLException {
    ps.setString(index, Objects.toString(value, "").trim());
  }

  private static void setNullableString(PreparedStatement ps, int index, String value)
      throws SQLException {
    String trimmed = Objects.toString(value, "").trim();
    if (trimmed.isEmpty()) {
      ps.setNull(index, java.sql.Types.VARCHAR);
    } else {
      ps.setString(index, trimmed);
    }
  }

  private static String truncate(String text) {
    String value = Objects.toString(text, "");
    return value.length() <= TEXT_MAX ? value : value.substring(0, TEXT_MAX);
  }

  private static Long readOptionalLong(long value, boolean wasNull) {
    return wasNull ? null : value;
  }
}
