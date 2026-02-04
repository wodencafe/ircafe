package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.logging.model.LogLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

public class ChatLogService implements ChatLogWriter, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(ChatLogService.class);

  // Conservative defaults.
  // TODO: Make these configurable by GUI and config.
  private static final int MAX_QUEUE = 50_000;
  private static final int BATCH_SIZE = 250;
  private static final long FLUSH_INTERVAL_MS = 750;

  private final BlockingQueue<LogLine> queue = new LinkedBlockingQueue<>(MAX_QUEUE);
  private final ScheduledExecutorService scheduler;
  private final ChatLogRepository repo;
  private final TransactionTemplate tx;
  private final LogProperties props;

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicLong dropped = new AtomicLong(0);

  private final Object flushLock = new Object();

  public ChatLogService(ChatLogRepository repo, TransactionTemplate tx, LogProperties props) {
    this.repo = Objects.requireNonNull(repo, "repo");
    this.tx = Objects.requireNonNull(tx, "tx");
    this.props = Objects.requireNonNull(props, "props");

    this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        Thread t = new Thread(r, "ircafe-chatlog-writer");
        t.setDaemon(true);
        return t;
      }
    });

    // Fixed-delay ensures we never overlap flush runs.
    scheduler.scheduleWithFixedDelay(this::flushSafely, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  @Override
  public void log(LogLine line) {
    if (line == null) return;
    if (closed.get()) return;

    // Respect master toggle defensively (even though this service is only wired when enabled).
    if (!Boolean.TRUE.equals(props.enabled())) return;

    if (!queue.offer(line)) {
      long d = dropped.incrementAndGet();
      // Don't spam logs; warn occasionally.
      if (d == 1 || d % 1000 == 0) {
        log.warn("[ircafe] Chat log queue full ({} max). Dropping lines. Dropped so far: {}", MAX_QUEUE, d);
      }
    }
  }

  private void flushSafely() {
    try {
      flushNow();
    } catch (Throwable t) {
      log.warn("[ircafe] Chat log flush failed", t);
    }
  }

  /**
   * Flush queued log lines immediately (best effort).
   *
   * <p>Safe to call from any thread.
   */
  public void flushNow() {
    synchronized (flushLock) {
      flushOnceLocked();
    }
  }

  private void flushOnceLocked() {
    if (queue.isEmpty()) return;

    // Drain up to a bounded number of batches per tick to avoid starving other work.
    int batches = 0;
    while (!queue.isEmpty() && batches < 20) {
      List<LogLine> batch = new ArrayList<>(BATCH_SIZE);
      queue.drainTo(batch, BATCH_SIZE);
      if (batch.isEmpty()) break;

      tx.executeWithoutResult(status -> repo.insertBatch(batch));
      batches++;
    }
  }

  /** Flush anything remaining and stop the background thread. */
  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;

    scheduler.shutdown();
    try {
      scheduler.awaitTermination(2, TimeUnit.SECONDS);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }

    // Final flush (best effort).
    try {
      flushNow();
    } catch (Throwable t) {
      log.warn("[ircafe] Final chat log flush failed", t);
    }
  }
}
