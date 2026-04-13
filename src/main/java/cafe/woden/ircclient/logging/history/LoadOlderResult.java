package cafe.woden.ircclient.logging.history;

import cafe.woden.ircclient.logging.LogLine;
import java.util.List;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record LoadOlderResult(
    List<LogLine> linesOldestFirst, LogCursor newOldestCursor, boolean hasMore) {

  public LoadOlderResult {
    if (linesOldestFirst == null) linesOldestFirst = List.of();
  }
}
