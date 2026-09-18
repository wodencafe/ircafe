package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.config.IrcProperties;
import com.google.common.util.concurrent.RateLimiter;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import org.pircbotx.PircBotX;
import org.pircbotx.output.OutputRaw;

/** Shares a Guava limiter across ordinary sends, with warm-up instead of idle burst credit. */
final class PircbotxPacedOutput extends OutputRaw {
  private final ReentrantLock pacingLock = new ReentrantLock(true);
  private final RateLimiter commandLimiter;

  PircbotxPacedOutput(PircBotX bot, IrcProperties.FloodProtection settings) {
    super(bot);
    // The default bursty limiter banks idle permits. IRC servers can disconnect on bursts,
    // so use a short warm-up to approach the configured rate conservatively after idle time.
    commandLimiter =
        settings.enabled()
            ? RateLimiter.create(
                1000.0 / settings.commandIntervalMs(),
                Duration.ofMillis(settings.commandIntervalMs()))
            : null;
  }

  @Override
  public void rawLine(String line, String logline) {
    if (line == null || line.isBlank())
      throw new IllegalArgumentException("Cannot send empty IRC line");
    // SASL runs on the reader thread. Keep it and transport keepalives independent of
    // channel joins and application traffic; CAP, registration and QUIT use rawLineNow.
    if (commandLimiter == null
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
        // acquire() sleeps uninterruptibly. Poll without reserving a future permit so a
        // disconnect can cancel queued auto-joins promptly, even at the slowest rate.
        while (!commandLimiter.tryAcquire()) {
          Thread.sleep(25);
        }
        // Recheck connectivity after waiting; do not write queued work to a closed connection.
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        super.rawLineNow(line, logline);
      } finally {
        pacingLock.unlock();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("IRC send interrupted before transmission", e);
    }
  }
}
