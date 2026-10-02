package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.util.VirtualThreads;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Connection-owned WHO pacing, independent of ordinary message sends and the socket reader. */
final class PircbotxWhoRequestCoordinator implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(PircbotxWhoRequestCoordinator.class);
  private static final long INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);
  private static final long BACKOFF_NANOS = TimeUnit.SECONDS.toNanos(60);
  private static final int CAPACITY = 1024;
  private final int botId;
  private final ScheduledExecutorService executor;
  private final LongSupplier nanoTime;
  private final PircbotxPacedOutput.Sleeper sleeper;
  private final BooleanSupplier connected;
  private final Consumer<String> transmit;
  private final ReentrantLock sendLock = new ReentrantLock(true);
  // Guarded by this; never hold this monitor while waiting for pacing or writing to the socket.
  private final Map<String, String> pending = new LinkedHashMap<>();
  private final Map<String, RecentScan> recent = new LinkedHashMap<>();
  private boolean draining;
  private boolean closed;
  private boolean sent;
  private long lastSent;
  private boolean backingOff;
  private long backoffUntil;
  private long dropped;
  private String activeKey;
  private String activeLine;
  private Thread sendingThread;

  PircbotxWhoRequestCoordinator(int botId, BooleanSupplier connected, Consumer<String> transmit) {
    this(
        connected,
        transmit,
        VirtualThreads.newScheduledThreadPool(1, "ircafe-who"),
        System::nanoTime,
        TimeUnit.NANOSECONDS::sleep,
        botId);
  }

  PircbotxWhoRequestCoordinator(
      BooleanSupplier connected,
      Consumer<String> transmit,
      ScheduledExecutorService executor,
      LongSupplier nanoTime,
      PircbotxPacedOutput.Sleeper sleeper) {
    this(connected, transmit, executor, nanoTime, sleeper, 0);
  }

  private PircbotxWhoRequestCoordinator(
      BooleanSupplier connected,
      Consumer<String> transmit,
      ScheduledExecutorService executor,
      LongSupplier nanoTime,
      PircbotxPacedOutput.Sleeper sleeper,
      int botId) {
    this.botId = botId;
    this.connected = connected;
    this.transmit = transmit;
    this.executor = executor;
    this.nanoTime = nanoTime;
    this.sleeper = sleeper;
  }

  synchronized void enqueue(String line) {
    // Automatic callers supply an untagged WHO channel scan. Explicit /who uses send instead.
    String raw = line.trim();
    String[] parts = raw.split("\\s+", 3);
    if (parts.length < 2
        || !parts[0].equalsIgnoreCase("WHO")
        || raw.indexOf('\r') >= 0
        || raw.indexOf('\n') >= 0) {
      throw new IllegalArgumentException("Expected an automatic WHO scan");
    }
    if (closed) return;
    String key = parts[1].toLowerCase(Locale.ROOT);
    String previous = pending.get(key);
    // Enrichment's WHOX scan supersedes the plain join scan while both are pending.
    if (previous != null && (parts.length == 2 || previous.equals(raw))) return;
    if (key.equals(activeKey) && (parts.length == 2 || raw.equals(activeLine))) return;
    RecentScan scan = recent.get(key);
    if (scan != null
        && nanoTime.getAsLong() - scan.at() < INTERVAL_NANOS
        && (parts.length == 2 || scan.line().equals(raw))) return;
    if (previous == null && pending.size() >= CAPACITY) {
      if ((++dropped & (dropped - 1)) == 0) {
        log.warn(
            "[bot={}] Automatic WHO queue full: capacity={}, dropped={}", botId, CAPACITY, dropped);
      }
      return;
    }
    pending.put(key, raw);
    if (!draining) {
      draining = true;
      // Give serialized join listeners a chance to upgrade the baseline scan to WHOX.
      executor.schedule(this::drain, 200, TimeUnit.MILLISECONDS);
    }
  }

  void send(BooleanSupplier action) {
    try {
      sendLock.lockInterruptibly();
      try {
        synchronized (this) {
          sendingThread = Thread.currentThread();
        }
        while (true) {
          long remaining;
          synchronized (this) {
            if (closed || Thread.currentThread().isInterrupted()) {
              throw new InterruptedException();
            }
            long now = nanoTime.getAsLong();
            remaining = sent ? INTERVAL_NANOS - (now - lastSent) : 0;
            if (backingOff) remaining = Math.max(remaining, backoffUntil - now);
          }
          if (remaining <= 0) break;
          sleeper.sleep(remaining);
        }
        if (action.getAsBoolean()) {
          synchronized (this) {
            sent = true;
            lastSent = nanoTime.getAsLong();
          }
        }
      } finally {
        synchronized (this) {
          sendingThread = null;
        }
        sendLock.unlock();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("WHO send interrupted before transmission", e);
    }
  }

  synchronized void rateLimited() {
    if (closed) return;
    backingOff = true;
    backoffUntil = nanoTime.getAsLong() + BACKOFF_NANOS;
    log.warn(
        "[bot={}] WHO rate limited: cooldownSeconds=60, pendingScans={}", botId, pending.size());
  }

  private void drain() {
    try {
      while (true) {
        synchronized (this) {
          if (closed || pending.isEmpty() || !connected.getAsBoolean()) return;
        }
        send(
            () -> {
              String key;
              String line;
              synchronized (this) {
                if (closed || pending.isEmpty() || !connected.getAsBoolean()) return false;
                var entry = pending.entrySet().iterator().next();
                key = entry.getKey();
                line = entry.getValue();
                pending.remove(key);
                activeKey = key;
                activeLine = line;
              }
              try {
                transmit.accept(line);
                synchronized (this) {
                  if (!closed) {
                    recent.put(key, new RecentScan(line, nanoTime.getAsLong()));
                    if (recent.size() > CAPACITY) recent.remove(recent.keySet().iterator().next());
                  }
                }
              } finally {
                synchronized (this) {
                  activeKey = null;
                  activeLine = null;
                }
              }
              return true;
            });
      }
    } catch (RuntimeException e) {
      if (!Thread.currentThread().isInterrupted() && connected.getAsBoolean()) {
        log.warn("[bot={}] Automatic WHO scan failed", botId, e);
      }
    } finally {
      synchronized (this) {
        draining = false;
        if (!closed && !pending.isEmpty() && connected.getAsBoolean()) {
          draining = true;
          executor.schedule(this::drain, 200, TimeUnit.MILLISECONDS);
        }
      }
    }
  }

  @Override
  public synchronized void close() {
    closed = true;
    pending.clear();
    recent.clear();
    if (sendingThread != null) sendingThread.interrupt();
    executor.shutdownNow();
  }

  private record RecentScan(String line, long at) {}
}
