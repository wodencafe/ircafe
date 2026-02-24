package cafe.woden.ircclient.logging.history;

/** Result from persisting a collected remote history batch into the local chat log DB. */
public record ChatHistoryIngestResult(
    boolean enabled,
    int total,
    int inserted,
    int skipped,
    long earliestInsertedEpochMs,
    long latestInsertedEpochMs,
    String message) {
  public ChatHistoryIngestResult {
    if (total < 0) total = 0;
    if (inserted < 0) inserted = 0;
    if (skipped < 0) skipped = 0;
    if (earliestInsertedEpochMs < 0) earliestInsertedEpochMs = 0;
    if (latestInsertedEpochMs < 0) latestInsertedEpochMs = 0;
    if (message != null && message.isBlank()) message = null;
  }
}
