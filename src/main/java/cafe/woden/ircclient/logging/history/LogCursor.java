package cafe.woden.ircclient.logging.history;

public record LogCursor(long tsEpochMs, long id) {
  public LogCursor {
    if (tsEpochMs < 0) tsEpochMs = 0;
    if (id < 0) id = 0;
  }
}
