package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.logging.model.LogLine;
import java.util.List;

public record LoadOlderResult(
    
    List<LogLine> linesOldestFirst,

    LogCursor newOldestCursor,

    boolean hasMore
) {
  public LoadOlderResult {
    if (linesOldestFirst == null) linesOldestFirst = List.of();
  }
}
