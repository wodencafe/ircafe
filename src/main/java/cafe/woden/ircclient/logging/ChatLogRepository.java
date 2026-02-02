package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.logging.model.LogDirection;
import cafe.woden.ircclient.logging.model.LogKind;
import cafe.woden.ircclient.logging.model.LogLine;
import cafe.woden.ircclient.logging.model.LogRow;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Low-level persistence operations for chat logs.
 */
public class ChatLogRepository {

  private static final int TEXT_MAX = 8192;

  private static final String INSERT_SQL = """
      INSERT INTO chat_log(
        server_id,
        target,
        ts_epoch_ms,
        direction,
        kind,
        from_nick,
        text,
        outgoing_local_echo,
        soft_ignored,
        meta
      ) VALUES (?,?,?,?,?,?,?,?,?,?)
      """;

  private static final String SELECT_RECENT_SQL = """
      SELECT server_id, target, ts_epoch_ms, direction, kind, from_nick, text,
             outgoing_local_echo, soft_ignored, meta
        FROM chat_log
       WHERE server_id = ?
         AND target = ?
    ORDER BY ts_epoch_ms DESC, id DESC
       LIMIT ?
      """;

  // Same as SELECT_RECENT_SQL, but includes the identity id so callers can maintain a deterministic cursor.
  private static final String SELECT_RECENT_ROWS_WITH_ID_SQL = """
      SELECT id, server_id, target, ts_epoch_ms, direction, kind, from_nick, text,
             outgoing_local_echo, soft_ignored, meta
        FROM chat_log
       WHERE server_id = ?
         AND target = ?
    ORDER BY ts_epoch_ms DESC, id DESC
       LIMIT ?
      """;

  // Fetch rows strictly older than (beforeTs, beforeId).
  // Order is newest-first within the fetched page.
  private static final String SELECT_OLDER_ROWS_WITH_ID_SQL = """
      SELECT id, server_id, target, ts_epoch_ms, direction, kind, from_nick, text,
             outgoing_local_echo, soft_ignored, meta
        FROM chat_log
       WHERE server_id = ?
         AND target = ?
         AND (
              ts_epoch_ms < ?
           OR (ts_epoch_ms = ? AND id < ?)
         )
    ORDER BY ts_epoch_ms DESC, id DESC
       LIMIT ?
      """;

  // Existence check for older history (used to decide whether to show a "Load older..." control).
  private static final String SELECT_HAS_OLDER_SQL = """
      SELECT 1
        FROM chat_log
       WHERE server_id = ?
         AND target = ?
         AND (
              ts_epoch_ms < ?
           OR (ts_epoch_ms = ? AND id < ?)
         )
       LIMIT 1
      """;

  private static final String DELETE_OLDER_THAN_SQL = """
      DELETE FROM chat_log
       WHERE ts_epoch_ms < ?
      """;

  private static final RowMapper<LogLine> ROW_MAPPER = (rs, rowNum) ->
      new LogLine(
          rs.getString("server_id"),
          rs.getString("target"),
          rs.getLong("ts_epoch_ms"),
          parseDirection(rs.getString("direction")),
          parseKind(rs.getString("kind")),
          rs.getString("from_nick"),
          rs.getString("text"),
          rs.getBoolean("outgoing_local_echo"),
          rs.getBoolean("soft_ignored"),
          rs.getString("meta")
      );

  private static final RowMapper<LogRow> ROW_WITH_ID_MAPPER = (rs, rowNum) ->
      new LogRow(
          rs.getLong("id"),
          new LogLine(
              rs.getString("server_id"),
              rs.getString("target"),
              rs.getLong("ts_epoch_ms"),
              parseDirection(rs.getString("direction")),
              parseKind(rs.getString("kind")),
              rs.getString("from_nick"),
              rs.getString("text"),
              rs.getBoolean("outgoing_local_echo"),
              rs.getBoolean("soft_ignored"),
              rs.getString("meta")
          )
      );

  private final JdbcTemplate jdbc;

  public ChatLogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Insert a single line. */
  public void insert(LogLine line) {
    if (line == null) return;
    jdbc.update(
        INSERT_SQL,
        line.serverId(),
        line.target(),
        line.tsEpochMs(),
        line.direction().name(),
        line.kind().name(),
        line.fromNick(),
        truncate(line.text()),
        line.outgoingLocalEcho(),
        line.softIgnored(),
        line.metaJson()
    );
  }

  /** Batch insert. */
  public int[] insertBatch(List<LogLine> lines) {
    if (lines == null || lines.isEmpty()) return new int[0];

    return jdbc.batchUpdate(
        INSERT_SQL,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            LogLine l = lines.get(i);
            ps.setString(1, l.serverId());
            ps.setString(2, l.target());
            ps.setLong(3, l.tsEpochMs());
            ps.setString(4, l.direction().name());
            ps.setString(5, l.kind().name());
            ps.setString(6, l.fromNick());
            ps.setString(7, truncate(l.text()));
            ps.setBoolean(8, l.outgoingLocalEcho());
            ps.setBoolean(9, l.softIgnored());
            ps.setString(10, l.metaJson());
          }

          @Override
          public int getBatchSize() {
            return lines.size();
          }
        });
  }

  /** Fetch the most recent {@code limit} lines for a given server+target (newest-first). */
  public List<LogLine> fetchRecent(String serverId, String target, int limit) {
    if (limit <= 0) return List.of();
    return jdbc.query(SELECT_RECENT_SQL, ROW_MAPPER, serverId, target, limit);
  }

  /**
   * Fetch the most recent {@code limit} rows including their identity id (newest-first).
   *
   * <p>This is useful when a caller needs a stable cursor for paging.
   */
  public List<LogRow> fetchRecentRows(String serverId, String target, int limit) {
    if (limit <= 0) return List.of();
    return jdbc.query(SELECT_RECENT_ROWS_WITH_ID_SQL, ROW_WITH_ID_MAPPER, serverId, target, limit);
  }

  /**
   * Fetch rows strictly older than the provided cursor ({@code beforeTs}/{@code beforeId}) (newest-first).
   *
   * <p>Cursor rule: rows are considered older if {@code ts_epoch_ms < beforeTs} OR
   * {@code (ts_epoch_ms == beforeTs AND id < beforeId)}.
   */
  public List<LogRow> fetchOlderRows(String serverId, String target, long beforeTs, long beforeId, int limit) {
    if (limit <= 0) return List.of();
    return jdbc.query(
        SELECT_OLDER_ROWS_WITH_ID_SQL,
        ROW_WITH_ID_MAPPER,
        serverId,
        target,
        beforeTs,
        beforeTs,
        beforeId,
        limit
    );
  }

  /** True if there exists at least one row older than the provided cursor. */
  public boolean hasOlderRows(String serverId, String target, long beforeTs, long beforeId) {
    // NOTE: Avoid JdbcTemplate#query overload ambiguity when using lambdas.
    // We only need existence; the query is LIMIT 1.
    List<Integer> rows = jdbc.query(
        SELECT_HAS_OLDER_SQL,
        (rs, rowNum) -> rs.getInt(1),
        serverId,
        target,
        beforeTs,
        beforeTs,
        beforeId
    );
    return rows != null && !rows.isEmpty();
  }

  /** Delete all lines older than the provided cutoff. */
  public int deleteOlderThan(long cutoffEpochMs) {
    return jdbc.update(DELETE_OLDER_THAN_SQL, cutoffEpochMs);
  }

  /**
   * Permanently delete all persisted lines for a specific server+target.
   *
   * @return number of deleted rows
   */
  public int deleteTarget(String serverId, String target) {
    if (serverId == null || serverId.isBlank()) return 0;
    if (target == null || target.isBlank()) return 0;
    return jdbc.update("DELETE FROM chat_log WHERE server_id = ? AND target = ?", serverId, target);
  }

  private static String truncate(String s) {
    if (s == null) return "";
    if (s.length() <= TEXT_MAX) return s;
    return s.substring(0, TEXT_MAX);
  }

  private static LogDirection parseDirection(String s) {
    if (s == null) return LogDirection.SYSTEM;
    try {
      return LogDirection.valueOf(s);
    } catch (Exception ignored) {
      return LogDirection.SYSTEM;
    }
  }

  private static LogKind parseKind(String s) {
    if (s == null) return LogKind.STATUS;
    try {
      return LogKind.valueOf(s);
    } catch (Exception ignored) {
      return LogKind.STATUS;
    }
  }
}
