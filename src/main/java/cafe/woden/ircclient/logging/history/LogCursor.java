package cafe.woden.ircclient.logging.history;

/**
 * Stable cursor for paging chat history.
 *
 * <p>We use (timestamp, id) together to avoid duplicates/gaps when multiple rows share the same
 * timestamp.
 */
public record LogCursor(long tsEpochMs, long id) {
  public LogCursor {
    if (tsEpochMs < 0) tsEpochMs = 0;
    if (id < 0) id = 0;
  }
}
