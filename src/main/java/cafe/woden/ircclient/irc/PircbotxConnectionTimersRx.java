package cafe.woden.ircclient.irc;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerRegistry;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import org.pircbotx.PircBotX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * RxJava-driven timers for a single server connection: heartbeat monitoring and reconnect backoff.
 *
 * <p>This intentionally centralizes all scheduling so {@link PircbotxIrcClientService} and
 * {@link PircbotxBridgeListener} don't need their own executor plumbing.
 */
@Component
final class PircbotxConnectionTimersRx {
  private static final Logger log = LoggerFactory.getLogger(PircbotxConnectionTimersRx.class);

  private final ServerRegistry serverRegistry;
  private final IrcProperties.Reconnect reconnectPolicy;
  private final IrcProperties.Heartbeat heartbeatPolicy;

  // Dedicated schedulers so we keep behavior deterministic and thread names sane.
  private final ScheduledExecutorService heartbeatExec;
  private final ScheduledExecutorService reconnectExec;
  private final Scheduler heartbeatScheduler;
  private final Scheduler reconnectScheduler;

  PircbotxConnectionTimersRx(IrcProperties props, ServerRegistry serverRegistry) {
    this.serverRegistry = Objects.requireNonNull(serverRegistry, "serverRegistry");
    IrcProperties.Client c = (props != null) ? props.client() : null;
    this.reconnectPolicy = (c != null) ? c.reconnect() : null;
    this.heartbeatPolicy = (c != null) ? c.heartbeat() : null;

    this.heartbeatExec = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "ircafe-heartbeat");
      t.setDaemon(true);
      return t;
    });
    this.reconnectExec = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "ircafe-reconnect");
      t.setDaemon(true);
      return t;
    });
    this.heartbeatScheduler = Schedulers.from(heartbeatExec);
    this.reconnectScheduler = Schedulers.from(reconnectExec);
  }

  void startHeartbeat(PircbotxConnectionState c) {
    if (c == null) return;

    c.lastInboundMs.set(System.currentTimeMillis());
    c.localTimeoutEmitted.set(false);

    IrcProperties.Heartbeat hb = heartbeatPolicy;
    if (hb == null || !hb.enabled()) {
      stopHeartbeat(c);
      return;
    }

    Disposable d = Flowable
        .interval(
            hb.checkPeriodMs(),
            hb.checkPeriodMs(),
            TimeUnit.MILLISECONDS,
            heartbeatScheduler)
        .subscribe(
            tick -> checkHeartbeat(c),
            err -> log.debug("[ircafe] Heartbeat ticker error for {}", c.serverId, err)
        );

    Disposable prev = c.heartbeatDisposable.getAndSet(d);
    if (prev != null && !prev.isDisposed()) prev.dispose();
  }

  void stopHeartbeat(PircbotxConnectionState c) {
    if (c == null) return;
    Disposable prev = c.heartbeatDisposable.getAndSet(null);
    if (prev != null && !prev.isDisposed()) prev.dispose();
  }

  private void checkHeartbeat(PircbotxConnectionState c) {
    PircBotX bot = c.botRef.get();
    if (bot == null) return;

    long idleMs = System.currentTimeMillis() - c.lastInboundMs.get();
    IrcProperties.Heartbeat hb = heartbeatPolicy;
    if (hb == null || !hb.enabled()) return;

    if (idleMs > hb.timeoutMs() && c.localTimeoutEmitted.compareAndSet(false, true)) {
      // Don't emit Disconnected here (DisconnectEvent will fire). Instead, stash a reason override.
      c.disconnectReasonOverride.set(
          "Ping timeout (no inbound traffic for " + (idleMs / 1000) + "s)"
      );
      try { bot.close(); } catch (Exception ignored) {}
    }
  }

  void cancelReconnect(PircbotxConnectionState c) {
    if (c == null) return;
    Disposable prev = c.reconnectDisposable.getAndSet(null);
    if (prev != null && !prev.isDisposed()) prev.dispose();
  }

  void scheduleReconnect(
      PircbotxConnectionState c,
      String reason,
      Function<String, Completable> connectFn,
      Consumer<ServerIrcEvent> emit
  ) {
    if (c == null) return;
    IrcProperties.Reconnect p = reconnectPolicy;
    if (p == null || !p.enabled()) return;
    if (c.manualDisconnect.get()) return;

    long attempt = c.reconnectAttempts.incrementAndGet();
    if (p.maxAttempts() > 0 && attempt > p.maxAttempts()) {
      emit.accept(new ServerIrcEvent(c.serverId, new IrcEvent.Error(
          Instant.now(),
          "Reconnect aborted (max attempts reached)",
          null
      )));
      return;
    }

    long delayMs = computeBackoffDelayMs(p, attempt);
    emit.accept(new ServerIrcEvent(c.serverId, new IrcEvent.Reconnecting(
        Instant.now(),
        attempt,
        delayMs,
        Objects.toString(reason, "Disconnected")
    )));

    // Replace any existing scheduled reconnect.
    Disposable next = Completable
        .timer(delayMs, TimeUnit.MILLISECONDS, reconnectScheduler)
        .andThen(Completable.defer(() -> {
          if (c.manualDisconnect.get()) return Completable.complete();

          // If the server was removed while waiting, abort.
          if (!serverRegistry.containsId(c.serverId)) {
            emit.accept(new ServerIrcEvent(c.serverId, new IrcEvent.Error(
                Instant.now(),
                "Reconnect cancelled (server removed)",
                null
            )));
            return Completable.complete();
          }

          // Connect is idempotent per-server; it will no-op if already connected.
          return connectFn.apply(c.serverId)
              .doOnError(err -> {
                emit.accept(new ServerIrcEvent(c.serverId, new IrcEvent.Error(
                    Instant.now(),
                    "Reconnect attempt failed",
                    err
                )));
                // Backoff again.
                scheduleReconnect(c, "Reconnect attempt failed", connectFn, emit);
              })
              // We reschedule ourselves on error; swallow it here.
              .onErrorComplete();
        }))
        .subscribe(
            () -> {},
            err -> log.debug("[ircafe] Reconnect timer error for {}", c.serverId, err)
        );

    Disposable prev = c.reconnectDisposable.getAndSet(next);
    if (prev != null && !prev.isDisposed()) prev.dispose();
  }

  private static long computeBackoffDelayMs(IrcProperties.Reconnect p, long attempt) {
    long base = p.initialDelayMs();
    double mult = Math.pow(p.multiplier(), Math.max(0, attempt - 1));
    double raw = base * mult;
    long capped = (long) Math.min(raw, (double) p.maxDelayMs());

    double jitter = p.jitterPct();
    if (jitter <= 0) return capped;

    double factor = 1.0 + ThreadLocalRandom.current().nextDouble(-jitter, jitter);
    long withJitter = (long) Math.max(0, capped * factor);
    return Math.max(250, withJitter);
  }

  @PreDestroy
  void shutdown() {
    try {
      heartbeatExec.shutdownNow();
    } catch (Exception ignored) {}
    try {
      reconnectExec.shutdownNow();
    } catch (Exception ignored) {}
  }
}
