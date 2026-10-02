package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.util.VirtualThreads;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputRaw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shares a two-command token bucket across ordinary sends, refilling at the configured interval.
 */
final class PircbotxPacedOutput extends OutputRaw implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(PircbotxPacedOutput.class);
  private static final int BURST_CAPACITY = 2;
  private static final int JOIN_QUERY_CAPACITY = 1024;
  private final ThreadLocal<String> joinQueryChannel = new ThreadLocal<>();
  private final ThreadPoolExecutor joinQueries =
      new ThreadPoolExecutor(
          1,
          1,
          0,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(JOIN_QUERY_CAPACITY),
          VirtualThreads.namedFactory("ircafe-join-queries"));
  private final AtomicLong droppedJoinQueries = new AtomicLong();
  private final ReentrantLock pacingLock = new ReentrantLock(true);
  private final long commandIntervalNanos;
  private final long maxCreditNanos;
  private final LongSupplier nanoTime;
  private final Sleeper sleeper;
  private final PircbotxWhoRequestCoordinator whoRequests;
  // Guarded by pacingLock. One command costs commandIntervalNanos of credit.
  private long creditNanos;
  private long lastRefillNanos;

  PircbotxPacedOutput(PircBotX bot, IrcProperties.FloodProtection settings) {
    this(bot, settings, System::nanoTime, TimeUnit.NANOSECONDS::sleep);
  }

  PircbotxPacedOutput(
      PircBotX bot,
      IrcProperties.FloodProtection settings,
      LongSupplier nanoTime,
      Sleeper sleeper) {
    super(bot);
    commandIntervalNanos =
        settings.enabled() ? TimeUnit.MILLISECONDS.toNanos(settings.commandIntervalMs()) : 0;
    maxCreditNanos = BURST_CAPACITY * commandIntervalNanos;
    creditNanos = maxCreditNanos;
    this.nanoTime = nanoTime;
    this.sleeper = sleeper;
    lastRefillNanos = nanoTime.getAsLong();
    whoRequests =
        new PircbotxWhoRequestCoordinator(
            bot.getBotId(), bot::isConnected, line -> pacedLine(line, line));
  }

  @Override
  public void rawLine(String line, String logline) {
    if (line == null || line.isBlank())
      throw new IllegalArgumentException("Cannot send empty IRC line");
    String channel = joinQueryChannel.get();
    if (channel != null && line.equals("WHO " + channel)) {
      whoRequests.enqueue(line);
      return;
    }
    if (channel != null && line.equals("MODE " + channel)) {
      enqueueJoinQuery(line, logline);
      return;
    }
    if (isWho(line)) {
      whoRequests.send(
          () -> {
            pacedLine(line, logline);
            return true;
          });
      return;
    }
    pacedLine(line, logline);
  }

  void enqueueAutomaticWho(String line) {
    whoRequests.enqueue(line);
  }

  void observeWhoRateLimit() {
    whoRequests.rateLimited();
  }

  private static boolean isWho(String line) {
    String command = line.stripLeading();
    if (command.startsWith("@")) {
      int endTags = command.indexOf(' ');
      if (endTags < 0) return false;
      command = command.substring(endTags + 1).stripLeading();
    }
    return command.equalsIgnoreCase("WHO")
        || (command.length() > 3
            && command.regionMatches(true, 0, "WHO", 0, 3)
            && Character.isWhitespace(command.charAt(3)));
  }

  private void pacedLine(String line, String logline) {
    // SASL runs on the reader thread. Keep it and transport keepalives independent of
    // channel joins and application traffic; CAP, registration and QUIT use rawLineNow.
    if (commandIntervalNanos == 0
        || line.startsWith("PONG ")
        || line.startsWith("PING ")
        || line.startsWith("AUTHENTICATE ")) {
      // rawLineNow also bypasses OutputRaw's built-in limiter when pacing is disabled.
      super.rawLineNow(line, logline);
      return;
    }
    try {
      pacingLock.lockInterruptibly();
      try {
        // Wait interruptibly so disconnect can cancel queued work even at the slowest rate.
        // Credit rebuilds gradually and is capped, even after long idle periods or oversleep.
        while (true) {
          long now = nanoTime.getAsLong();
          long elapsed = now - lastRefillNanos;
          creditNanos = Math.min(maxCreditNanos, creditNanos + Math.min(elapsed, maxCreditNanos));
          lastRefillNanos = now;
          long remaining = commandIntervalNanos - creditNanos;
          if (remaining <= 0) break;
          sleeper.sleep(remaining);
        }
        // Recheck connectivity after waiting; do not write queued work to a closed connection.
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        super.rawLineNow(line, logline);
        creditNanos -= commandIntervalNanos;
      } finally {
        pacingLock.unlock();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("IRC send interrupted before transmission", e);
    }
  }

  void deferJoinQueries(String channel, IoAction action) throws IOException {
    String previous = joinQueryChannel.get();
    joinQueryChannel.set(channel);
    try {
      action.run();
    } finally {
      if (previous == null) joinQueryChannel.remove();
      else joinQueryChannel.set(previous);
    }
  }

  private void enqueueJoinQuery(String line, String logline) {
    try {
      joinQueries.execute(
          () -> {
            if (!bot.isConnected() || Thread.currentThread().isInterrupted()) return;
            try {
              // This worker has no reader context: queries use the same bucket as ordinary sends.
              rawLine(line, logline);
            } catch (RuntimeException e) {
              if (!Thread.currentThread().isInterrupted() && bot.isConnected()) {
                log.warn(
                    "[bot={}] Automatic join query failed: command={}", bot.getBotId(), line, e);
              }
            }
          });
    } catch (RejectedExecutionException e) {
      if (joinQueries.isShutdown()) return;
      long dropped = droppedJoinQueries.incrementAndGet();
      // Report overload at powers of two without flooding diagnostics on a large attach burst.
      if ((dropped & (dropped - 1)) == 0) {
        log.warn(
            "[bot={}] Automatic join query queue full: capacity={}, dropped={}",
            bot.getBotId(),
            JOIN_QUERY_CAPACITY,
            dropped);
      }
    }
  }

  @Override
  public void close() {
    whoRequests.close();
    joinQueries.shutdownNow();
  }

  @FunctionalInterface
  interface IoAction {
    void run() throws IOException;
  }

  @FunctionalInterface
  interface Sleeper {
    void sleep(long nanos) throws InterruptedException;
  }
}
