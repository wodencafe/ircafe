package cafe.woden.ircclient.logging.history;

import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/** Shared chunk scheduling/timing helpers for chat history rendering on the EDT. */
final class HistoryChunking {

  private HistoryChunking() {}

  static long chunkBudgetNs(int chunkEdtBudgetMs) {
    return TimeUnit.MILLISECONDS.toNanos(Math.max(1, Math.min(33, chunkEdtBudgetMs)));
  }

  static int minLinesBeforeBudget(int maxLines, int minLinesPerChunk) {
    return Math.min(maxLines, Math.max(1, minLinesPerChunk));
  }

  static void scheduleNextChunk(int delayMs, Runnable task) {
    if (task == null) return;
    int safeDelayMs = Math.max(0, Math.min(1_000, delayMs));
    if (safeDelayMs == 0) {
      SwingUtilities.invokeLater(task);
      return;
    }
    javax.swing.Timer timer =
        new javax.swing.Timer(
            safeDelayMs,
            e -> {
              ((javax.swing.Timer) e.getSource()).stop();
              task.run();
            });
    timer.setRepeats(false);
    timer.start();
  }

  static int effectiveInterChunkDelayMs(int configuredDelayMs, long elapsedNs) {
    int safeDelayMs = Math.max(0, Math.min(1_000, configuredDelayMs));
    long elapsedMs = Math.max(0L, TimeUnit.NANOSECONDS.toMillis(Math.max(0L, elapsedNs)));
    if (elapsedMs >= safeDelayMs) return 0;
    return (int) (safeDelayMs - elapsedMs);
  }
}
