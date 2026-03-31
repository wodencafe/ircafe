package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.*;
import cafe.woden.ircclient.net.NetHeartbeatContext;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.pircbotx.PircBotX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * RxJava-driven timers for a single server connection: heartbeat monitoring and reconnect backoff.
 *
 * <p>This intentionally centralizes all scheduling so {@link PircbotxIrcClientService} and {@link
 * PircbotxBridgeListener} don't need their own executor plumbing.
 */
@Component
@InfrastructureLayer
final class PircbotxConnectionTimersRx {
  private static final Logger log = LoggerFactory.getLogger(PircbotxConnectionTimersRx.class);

  private final ServerCatalog serverCatalog;
  private final IrcProperties.Reconnect reconnectPolicy;
  private final IrcProperties.Heartbeat heartbeatPolicy;

  // Dedicated schedulers so we keep behavior deterministic and thread names sane.
  private final ScheduledExecutorService heartbeatExec;
  private final ScheduledExecutorService reconnectExec;
  private final Scheduler heartbeatScheduler;
  private final Scheduler reconnectScheduler;

  // Prevent scheduling (and noisy UndeliverableException logs) during JVM/app shutdown.
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

  PircbotxConnectionTimersRx(
      IrcProperties props,
      ServerCatalog serverCatalog,
      @Qualifier(ExecutorConfig.PIRCBOTX_HEARTBEAT_SCHEDULER)
          ScheduledExecutorService heartbeatExec,
      @Qualifier(ExecutorConfig.PIRCBOTX_RECONNECT_SCHEDULER)
          ScheduledExecutorService reconnectExec) {
    this.serverCatalog = Objects.requireNonNull(serverCatalog, "serverCatalog");
    IrcProperties.Client c = (props != null) ? props.client() : null;
    this.reconnectPolicy = (c != null) ? c.reconnect() : null;
    this.heartbeatPolicy = (c != null) ? c.heartbeat() : null;

    this.heartbeatExec = Objects.requireNonNull(heartbeatExec, "heartbeatExec");
    this.reconnectExec = Objects.requireNonNull(reconnectExec, "reconnectExec");
    this.heartbeatScheduler = Schedulers.from(heartbeatExec);
    this.reconnectScheduler = Schedulers.from(reconnectExec);
  }

  void startHeartbeat(PircbotxConnectionState c) {
    startHeartbeatInternal(c, true);
  }

  /**
   * Rebuilds the active heartbeat ticker using the latest settings (e.g. after Preferences Apply).
   *
   * <p>Unlike {@link #startHeartbeat(PircbotxConnectionState)}, this does <b>not</b> reset the
   * inbound-idle clock (last inbound timestamp). That avoids masking a connection that is already
   * stuck/silent at the moment the user tweaks heartbeat settings.
   */
  void rescheduleHeartbeat(PircbotxConnectionState c) {
    startHeartbeatInternal(c, false);
  }

  private void startHeartbeatInternal(PircbotxConnectionState c, boolean resetIdleClock) {
    if (c == null) return;
    if (shuttingDown.get() || heartbeatExec.isShutdown() || heartbeatExec.isTerminated()) return;

    c.ensureHeartbeatClock(System.currentTimeMillis(), resetIdleClock);

    IrcProperties.Heartbeat hb = NetHeartbeatContext.settings();
    if (hb == null) hb = heartbeatPolicy;
    if (hb == null || !hb.enabled()) {
      stopHeartbeat(c);
      return;
    }

    Disposable d =
        Flowable.interval(
                hb.checkPeriodMs(), hb.checkPeriodMs(), TimeUnit.MILLISECONDS, heartbeatScheduler)
            .subscribe(
                tick -> checkHeartbeat(c),
                err -> log.debug("[ircafe] Heartbeat ticker error for {}", c.serverId(), err));

    Disposable prev = c.replaceHeartbeatDisposable(d);
    if (prev != null && !prev.isDisposed()) prev.dispose();
  }

  void stopHeartbeat(PircbotxConnectionState c) {
    if (c == null) return;
    Disposable prev = c.clearHeartbeatDisposable();
    if (prev != null && !prev.isDisposed()) prev.dispose();
  }

  private void checkHeartbeat(PircbotxConnectionState c) {
    PircBotX bot = c.currentBot();
    if (bot == null) return;

    long idleMs = c.idleMsAt(System.currentTimeMillis());
    IrcProperties.Heartbeat hb = NetHeartbeatContext.settings();
    if (hb == null) hb = heartbeatPolicy;
    if (hb == null || !hb.enabled()) return;

    if (idleMs > hb.timeoutMs()
        && c.markLocalTimeout("Ping timeout (no inbound traffic for " + (idleMs / 1000) + "s)")) {
      try {
        bot.close();
      } catch (Exception ignored) {
      }
    }
  }

  void cancelReconnect(PircbotxConnectionState c) {
    if (c == null) return;
    Disposable prev = c.clearReconnectDisposable();
    if (prev != null && !prev.isDisposed()) prev.dispose();
  }

  void scheduleReconnect(
      PircbotxConnectionState c,
      String reason,
      Function<String, Completable> connectFn,
      Consumer<ServerIrcEvent> emit) {
    if (c == null) return;
    if (shuttingDown.get() || reconnectExec.isShutdown() || reconnectExec.isTerminated()) return;
    IrcProperties.Reconnect p = reconnectPolicy;
    if (p == null || !p.enabled()) return;
    if (c.manualDisconnectRequested()) return;

    long attempt = c.nextReconnectAttempt();
    if (p.maxAttempts() > 0 && attempt > p.maxAttempts()) {
      emit.accept(
          new ServerIrcEvent(
              c.serverId(),
              new IrcEvent.Error(Instant.now(), "Reconnect aborted (max attempts reached)", null)));
      return;
    }

    long delayMs = computeBackoffDelayMs(p, attempt);
    emit.accept(
        new ServerIrcEvent(
            c.serverId(),
            new IrcEvent.Reconnecting(
                Instant.now(), attempt, delayMs, Objects.toString(reason, "Disconnected"))));

    // Replace any existing scheduled reconnect.
    final Disposable next;
    try {
      next =
          Completable.timer(delayMs, TimeUnit.MILLISECONDS, reconnectScheduler)
              .subscribe(
                  () -> runReconnectAttempt(c, connectFn, emit),
                  err ->
                      log.debug("[ircafe] Reconnect scheduling failed for {}", c.serverId(), err));
    } catch (RejectedExecutionException rejected) {
      // Common during shutdown: executor already terminated.
      log.debug("[ircafe] Reconnect scheduling rejected for {} (likely shutdown)", c.serverId());
      return;
    }

    Disposable prev = c.replaceReconnectDisposable(next);
    if (prev != null && !prev.isDisposed()) prev.dispose();
  }

  private void runReconnectAttempt(
      PircbotxConnectionState c,
      Function<String, Completable> connectFn,
      Consumer<ServerIrcEvent> emit) {
    if (shuttingDown.get() || c.manualDisconnectRequested()) return;

    // If the server was removed while waiting, abort.
    if (!serverCatalog.containsId(c.serverId())) {
      emit.accept(
          new ServerIrcEvent(
              c.serverId(),
              new IrcEvent.Error(Instant.now(), "Reconnect cancelled (server removed)", null)));
      return;
    }

    // Connect is idempotent per-server; it will no-op if already connected.
    var unused =
        connectFn
            .apply(c.serverId())
            .subscribe(
                () -> {},
                err -> {
                  emit.accept(
                      new ServerIrcEvent(
                          c.serverId(),
                          new IrcEvent.Error(Instant.now(), "Reconnect attempt failed", err)));
                  // Backoff again.
                  scheduleReconnect(c, "Reconnect attempt failed", connectFn, emit);
                });
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
    shuttingDown.set(true);
  }
}
