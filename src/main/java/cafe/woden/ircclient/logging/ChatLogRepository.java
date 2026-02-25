package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.model.LogLine;
import cafe.woden.ircclient.model.LogRow;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class ChatLogRepository {

  private static final int TEXT_MAX = 8192;

  private static final String INSERT_SQL =
      """
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
        meta,
        message_id
      ) VALUES (?,?,?,?,?,?,?,?,?,?,?)
      """;

  private static final String SELECT_RECENT_SQL =
      """
      SELECT server_id, target, ts_epoch_ms, direction, kind, from_nick, text,
             outgoing_local_echo, soft_ignored, meta
        FROM chat_log
       WHERE server_id = ?
         AND LOWER(target) = LOWER(?)
    ORDER BY ts_epoch_ms DESC, id DESC
       LIMIT ?
      """;

  // Same as SELECT_RECENT_SQL, but includes the identity id so callers can maintain a deterministic
  // cursor.
  private static final String SELECT_RECENT_ROWS_WITH_ID_SQL =
      """
      SELECT id, server_id, target, ts_epoch_ms, direction, kind, from_nick, text,
             outgoing_local_echo, soft_ignored, meta
        FROM chat_log
       WHERE server_id = ?
         AND LOWER(target) = LOWER(?)
    ORDER BY ts_epoch_ms DESC, id DESC
       LIMIT ?
      """;

  // Fetch rows strictly older than (beforeTs, beforeId).
  // Order is newest-first within the fetched page.
  private static final String SELECT_OLDER_ROWS_WITH_ID_SQL =
      """
      SELECT id, server_id, target, ts_epoch_ms, direction, kind, from_nick, text,
             outgoing_local_echo, soft_ignored, meta
        FROM chat_log
       WHERE server_id = ?
         AND LOWER(target) = LOWER(?)
         AND (
              ts_epoch_ms < ?
           OR (ts_epoch_ms = ? AND id < ?)
         )
    ORDER BY ts_epoch_ms DESC, id DESC
       LIMIT ?
      """;

  // Existence check for older history (used to decide whether to show a "Load older..." control).
  private static final String SELECT_HAS_OLDER_SQL =
      """
      SELECT 1
        FROM chat_log
       WHERE server_id = ?
         AND LOWER(target) = LOWER(?)
         AND (
              ts_epoch_ms < ?
           OR (ts_epoch_ms = ? AND id < ?)
         )
       LIMIT 1
      """;

  private static final String DELETE_OLDER_THAN_SQL =
      """
      DELETE FROM chat_log
       WHERE ts_epoch_ms < ?
      """;

  private static final String SELECT_MAX_TS_FOR_SERVER_SQL =
      """
      SELECT MAX(ts_epoch_ms)
        FROM chat_log
       WHERE server_id = ?
      """;

  private static final String SELECT_DISTINCT_TARGETS_SQL =
      """
      SELECT DISTINCT target
        FROM chat_log
       WHERE server_id = ?
    ORDER BY target ASC
       LIMIT ?
      """;

  // Exact-existence check used for remote history ingestion de-duplication.
  // NOTE: there is no unique constraint in the schema, so we must check before insert.
  private static final String SELECT_EXISTS_EXACT_SQL =
      """
      SELECT 1
        FROM chat_log
       WHERE server_id = ?
         AND LOWER(target) = LOWER(?)
         AND ts_epoch_ms = ?
         AND direction = ?
         AND kind = ?
         AND COALESCE(from_nick, '') = ?
         AND text = ?
       LIMIT 1
      """;

  private static final RowMapper<LogLine> ROW_MAPPER =
      (rs, rowNum) ->
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
              rs.getString("meta"));

  private static final RowMapper<LogRow> ROW_WITH_ID_MAPPER =
      (rs, rowNum) ->
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
                  rs.getString("meta")));

  private final JdbcTemplate jdbc;

  public ChatLogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(LogLine line) {
    if (line == null) return;
    try {
      insertOne(line);
    } catch (DataAccessException ex) {
      if (isDuplicateKey(ex)) return;
      throw ex;
    }
  }

  public int[] insertBatch(List<LogLine> lines) {
    if (lines == null || lines.isEmpty()) return new int[0];

    try {
      return jdbc.batchUpdate(
          INSERT_SQL,
          new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
              setInsertArgs(ps, lines.get(i));
            }

            @Override
            public int getBatchSize() {
              return lines.size();
            }
          });
    } catch (DataAccessException ex) {
      if (!isDuplicateKey(ex)) throw ex;

      // Batch execution fails as a unit on duplicate-key conflicts. Re-run row-by-row so
      // non-duplicate lines still persist while duplicate message-ids are ignored.
      int[] out = new int[lines.size()];
      for (int i = 0; i < lines.size(); i++) {
        LogLine line = lines.get(i);
        try {
          out[i] = insertOne(line);
        } catch (DataAccessException rowEx) {
          if (isDuplicateKey(rowEx)) {
            out[i] = 0;
            continue;
          }
          throw rowEx;
        }
      }
      return out;
    }
  }

  /**
   * Best-effort exact existence check for a persisted line.
   *
   * <p>Used by remote-history ingestion to suppress duplicates when the user requests the same
   * chathistory page multiple times.
   */
  public boolean existsExact(LogLine line) {
    if (line == null) return false;
    String from = line.fromNick();
    if (from == null) from = "";
    String text = truncate(line.text());
    try {
      List<Integer> rows =
          jdbc.query(
              SELECT_EXISTS_EXACT_SQL,
              (rs, rowNum) -> rs.getInt(1),
              line.serverId(),
              line.target(),
              line.tsEpochMs(),
              line.direction().name(),
              line.kind().name(),
              from,
              text);
      return rows != null && !rows.isEmpty();
    } catch (Exception ex) {
      // Fail open: treat as "not present" so ingestion can proceed.
      return false;
    }
  }

  /** Fetch the most recent {@code limit} lines for a given server+target (newest-first). */
  public List<LogLine> fetchRecent(String serverId, String target, int limit) {
    if (limit <= 0) return List.of();
    return jdbc.query(SELECT_RECENT_SQL, ROW_MAPPER, serverId, target, limit);
  }

  /** Fetch the newest persisted timestamp (epoch ms) for a server (if any). */
  public OptionalLong maxTimestampForServer(String serverId) {
    if (serverId == null || serverId.isBlank()) return OptionalLong.empty();
    try {
      Long v = jdbc.queryForObject(SELECT_MAX_TS_FOR_SERVER_SQL, Long.class, serverId);
      if (v == null) return OptionalLong.empty();
      return OptionalLong.of(v);
    } catch (Exception ex) {
      return OptionalLong.empty();
    }
  }

  /** Returns distinct targets for a server (ascending, up to {@code limit}). */
  public List<String> distinctTargets(String serverId, int limit) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty() || limit <= 0) return List.of();
    int wanted = Math.min(limit, 100_000);
    return jdbc.query(
        SELECT_DISTINCT_TARGETS_SQL,
        (rs, rowNum) -> Objects.toString(rs.getString(1), "").trim(),
        sid,
        wanted);
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
   * Fetch rows strictly older than the provided cursor ({@code beforeTs}/{@code beforeId})
   * (newest-first).
   *
   * <p>Cursor rule: rows are considered older if {@code ts_epoch_ms < beforeTs} OR {@code
   * (ts_epoch_ms == beforeTs AND id < beforeId)}.
   */
  public List<LogRow> fetchOlderRows(
      String serverId, String target, long beforeTs, long beforeId, int limit) {
    if (limit <= 0) return List.of();
    return jdbc.query(
        SELECT_OLDER_ROWS_WITH_ID_SQL,
        ROW_WITH_ID_MAPPER,
        serverId,
        target,
        beforeTs,
        beforeTs,
        beforeId,
        limit);
  }

  /**
   * Search rows for a server within an optional timestamp range (newest-first).
   *
   * <p>This intentionally keeps filtering simple and DB-portable; higher-level viewers can apply
   * additional filtering (glob/regex/metadata) in-memory.
   */
  public List<LogRow> searchRows(String serverId, Long fromEpochMs, Long toEpochMs, int limit) {
    String sid = (serverId == null) ? "" : serverId.trim();
    if (sid.isEmpty() || limit <= 0) return List.of();

    StringBuilder sql =
        new StringBuilder(
            """
        SELECT id, server_id, target, ts_epoch_ms, direction, kind, from_nick, text,
               outgoing_local_echo, soft_ignored, meta
          FROM chat_log
         WHERE server_id = ?
        """);
    ArrayList<Object> args = new ArrayList<>();
    args.add(sid);

    if (fromEpochMs != null) {
      sql.append(" AND ts_epoch_ms >= ?");
      args.add(fromEpochMs);
    }
    if (toEpochMs != null) {
      sql.append(" AND ts_epoch_ms <= ?");
      args.add(toEpochMs);
    }

    sql.append(" ORDER BY ts_epoch_ms DESC, id DESC LIMIT ?");
    args.add(limit);
    return jdbc.query(sql.toString(), ROW_WITH_ID_MAPPER, args.toArray());
  }

  public boolean hasOlderRows(String serverId, String target, long beforeTs, long beforeId) {
    // NOTE: Avoid JdbcTemplate#query overload ambiguity when using lambdas.
    // We only need existence; the query is LIMIT 1.
    List<Integer> rows =
        jdbc.query(
            SELECT_HAS_OLDER_SQL,
            (rs, rowNum) -> rs.getInt(1),
            serverId,
            target,
            beforeTs,
            beforeTs,
            beforeId);
    return rows != null && !rows.isEmpty();
  }

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
    return jdbc.update(
        "DELETE FROM chat_log WHERE server_id = ? AND LOWER(target) = LOWER(?)", serverId, target);
  }

  private static String truncate(String s) {
    if (s == null) return "";
    if (s.length() <= TEXT_MAX) return s;
    return s.substring(0, TEXT_MAX);
  }

  private int insertOne(LogLine line) {
    return jdbc.update(
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
        line.metaJson(),
        normalizeMessageId(extractMessageId(line.metaJson())));
  }

  private static void setInsertArgs(PreparedStatement ps, LogLine line) throws SQLException {
    ps.setString(1, line.serverId());
    ps.setString(2, line.target());
    ps.setLong(3, line.tsEpochMs());
    ps.setString(4, line.direction().name());
    ps.setString(5, line.kind().name());
    ps.setString(6, line.fromNick());
    ps.setString(7, truncate(line.text()));
    ps.setBoolean(8, line.outgoingLocalEcho());
    ps.setBoolean(9, line.softIgnored());
    ps.setString(10, line.metaJson());
    ps.setString(11, normalizeMessageId(extractMessageId(line.metaJson())));
  }

  private static boolean isDuplicateKey(Throwable ex) {
    Throwable cur = ex;
    while (cur != null) {
      if (cur instanceof DuplicateKeyException) return true;
      if (cur instanceof SQLException sqlEx) {
        String state = Objects.toString(sqlEx.getSQLState(), "").trim();
        if ("23505".equals(state)) return true;
      }
      cur = cur.getCause();
    }
    return false;
  }

  private static String extractMessageId(String metaJson) {
    String meta = Objects.toString(metaJson, "").trim();
    if (meta.isEmpty()) return "";
    String key = "\"messageId\"";
    int keyPos = meta.indexOf(key);
    if (keyPos < 0) return "";
    int colon = meta.indexOf(':', keyPos + key.length());
    if (colon < 0) return "";
    int firstQuote = meta.indexOf('"', colon + 1);
    if (firstQuote < 0) return "";

    StringBuilder out = new StringBuilder(32);
    boolean escaped = false;
    for (int i = firstQuote + 1; i < meta.length(); i++) {
      char c = meta.charAt(i);
      if (escaped) {
        out.append(c);
        escaped = false;
        continue;
      }
      if (c == '\\') {
        escaped = true;
        continue;
      }
      if (c == '"') {
        break;
      }
      out.append(c);
    }
    return out.toString().trim();
  }

  private static String normalizeMessageId(String raw) {
    String msgId = Objects.toString(raw, "").trim();
    return msgId.isEmpty() ? null : msgId;
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
