package cafe.woden.ircclient.logging.viewer;

import java.util.List;

/** Search result payload for the log viewer. */
public record ChatLogViewerResult(
    List<ChatLogViewerRow> rows, int scannedRows, boolean truncated, boolean scanCapped) {
  public ChatLogViewerResult {
    rows = rows == null ? List.of() : List.copyOf(rows);
    scannedRows = Math.max(0, scannedRows);
  }
}
