package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputRaw;

/**
 * Shares a two-command token bucket across ordinary sends, refilling at the configured interval.
 */
final class PircbotxPacedOutput extends OutputRaw {
  private static final int BURST_CAPACITY = 2;
  private final ReentrantLock pacingLock = new ReentrantLock(true);
  private final long commandIntervalNanos;
  private final long maxCreditNanos;
  private final LongSupplier nanoTime;
  private final Sleeper sleeper;
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
  }

  @Override
  public void rawLine(String line, String logline) {
    if (line == null || line.isBlank())
      throw new IllegalArgumentException("Cannot send empty IRC line");
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

  @FunctionalInterface
  interface Sleeper {
    void sleep(long nanos) throws InterruptedException;
  }
}
