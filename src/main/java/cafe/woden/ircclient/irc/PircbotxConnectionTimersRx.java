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
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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

  // Prevent scheduling (and noisy UndeliverableException logs) during JVM/app shutdown.
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

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
  }

  void startHeartbeat(PircbotxConnectionState c) {
    if (c == null) return;
    if (shuttingDown.get() || heartbeatExec.isShutdown() || heartbeatExec.isTerminated()) return;

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
    if (shuttingDown.get() || reconnectExec.isShutdown() || reconnectExec.isTerminated()) return;
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
    final Disposable next;
    try {
      final ScheduledFuture<?> future = reconnectExec.schedule(() -> {
        if (shuttingDown.get() || c.manualDisconnect.get()) return;

        // If the server was removed while waiting, abort.
        if (!serverRegistry.containsId(c.serverId)) {
          emit.accept(new ServerIrcEvent(c.serverId, new IrcEvent.Error(
              Instant.now(),
              "Reconnect cancelled (server removed)",
              null
          )));
          return;
        }

        // Connect is idempotent per-server; it will no-op if already connected.
        connectFn.apply(c.serverId)
            .subscribe(
                () -> {},
                err -> {
                  emit.accept(new ServerIrcEvent(c.serverId, new IrcEvent.Error(
                      Instant.now(),
                      "Reconnect attempt failed",
                      err
                  )));
                  // Backoff again.
                  scheduleReconnect(c, "Reconnect attempt failed", connectFn, emit);
                }
            );
      }, delayMs, TimeUnit.MILLISECONDS);

      // RxJava's Disposables helper isn't present in some older RxJava 3 minor lines.
      // Wrap the ScheduledFuture into a lightweight Disposable.
      next = futureDisposable(future);
    } catch (RejectedExecutionException rejected) {
      // Common during shutdown: executor already terminated.
      log.debug("[ircafe] Reconnect scheduling rejected for {} (likely shutdown)", c.serverId);
      return;
    }

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

  private static Disposable futureDisposable(Future<?> f) {
    AtomicReference<Future<?>> ref = new AtomicReference<>(f);
    return new Disposable() {
      private final AtomicBoolean disposed = new AtomicBoolean(false);

      @Override
      public void dispose() {
        if (disposed.compareAndSet(false, true)) {
          Future<?> fx = ref.getAndSet(null);
          if (fx != null) fx.cancel(true);
        }
      }

      @Override
      public boolean isDisposed() {
        if (disposed.get()) return true;
        Future<?> fx = ref.get();
        return fx == null || fx.isCancelled() || fx.isDone();
      }
    };
  }

  @PreDestroy
  void shutdown() {
    shuttingDown.set(true);
    try {
      heartbeatExec.shutdownNow();
    } catch (Exception ignored) {}
    try {
      reconnectExec.shutdownNow();
    } catch (Exception ignored) {}
  }
}
