package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.logging.model.LogLine;
import java.util.List;

/** Result of a "load older messages" request. */
public record LoadOlderResult(
    /** Lines returned, in chronological order (oldest-first). */
    List<LogLine> linesOldestFirst,

    /** The new oldest loaded cursor for the target (after applying these lines). */
    LogCursor newOldestCursor,

    /** Whether there are still older rows available. */
    boolean hasMore
) {
  public LoadOlderResult {
    if (linesOldestFirst == null) linesOldestFirst = List.of();
  }
}
