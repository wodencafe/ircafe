package cafe.woden.ircclient.ui.shell;

import static com.google.common.base.Verify.verify;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.irc.port.IrcLagProbePort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.util.VirtualThreads;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@InterfaceLayer
@Lazy
public class LagIndicatorService {
  private static final Logger log = LoggerFactory.getLogger(LagIndicatorService.class);

  private static final long INITIAL_CHECK_DELAY_SECONDS = 2L;
  private static final long CHECK_INTERVAL_SECONDS = 6L;
  private static final long PASSIVE_FALLBACK_PROBE_INTERVAL_SECONDS = 90L;
  private static final long PASSIVE_FALLBACK_PROBE_INTERVAL_MS =
      TimeUnit.SECONDS.toMillis(PASSIVE_FALLBACK_PROBE_INTERVAL_SECONDS);
  private static final long PROBE_RESULT_WAIT_MS = 750L;
  private static final long PROBE_RESULT_POLL_MS = 50L;

  private final RuntimeConfigStore runtimeConfig;
  private final StatusBar statusBar;
  private final ActiveTargetPort activeTargetPort;
  private final IrcLagProbePort lagProbePort;
  private final ScheduledExecutorService scheduler;

  private final AtomicBoolean enabled = new AtomicBoolean(true);
  private final AtomicReference<ScheduledFuture<?>> periodicCheckTask = new AtomicReference<>();
  private final AtomicReference<String> lastDiagnosticState = new AtomicReference<>("");
  private final Set<String> serversWithLagSample = ConcurrentHashMap.newKeySet();
  private final Map<String, Long> lastFallbackProbeAtMsByServer = new ConcurrentHashMap<>();

  public LagIndicatorService(
      RuntimeConfigStore runtimeConfig,
      StatusBar statusBar,
      ActiveTargetPort activeTargetPort,
      @Qualifier("ircLagProbePort") IrcLagProbePort lagProbePort) {
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    this.statusBar = Objects.requireNonNull(statusBar, "statusBar");
    this.activeTargetPort = Objects.requireNonNull(activeTargetPort, "activeTargetPort");
    this.lagProbePort = Objects.requireNonNull(lagProbePort, "lagProbePort");
    this.scheduler = VirtualThreads.newSingleThreadScheduledExecutor("ircafe-lag-indicator");
  }

  @PostConstruct
  void start() {
    boolean initialEnabled = runtimeConfig.readLagIndicatorEnabled(true);
    log.debug("[lag] startup: enabled={}", initialEnabled);
    applyEnabled(initialEnabled, false);
  }

  @PreDestroy
  void shutdown() {
    cancelTask(periodicCheckTask);
    scheduler.shutdownNow();
  }

  public void setEnabled(boolean on) {
    applyEnabled(on, false);
  }

  private synchronized void applyEnabled(boolean on, boolean persist) {
    enabled.set(on);
    if (persist) {
      runtimeConfig.rememberLagIndicatorEnabled(on);
    }

    if (!on) {
      log.debug("[lag] disabled");
      cancelTask(periodicCheckTask);
      statusBar.setLagIndicatorEnabled(false);
      return;
    }

    log.debug("[lag] enabled");
    statusBar.setLagIndicatorEnabled(true);
    statusBar.setLagIndicatorReading(null, "Measuring server lag...");
    scheduleChecksIfNeeded();
  }

  private void scheduleChecksIfNeeded() {
    ScheduledFuture<?> periodic = periodicCheckTask.get();
    if (periodic == null || periodic.isCancelled() || periodic.isDone()) {
      ScheduledFuture<?> next =
          scheduler.scheduleWithFixedDelay(
              this::checkLagSafely,
              INITIAL_CHECK_DELAY_SECONDS,
              CHECK_INTERVAL_SECONDS,
              TimeUnit.SECONDS);
      periodicCheckTask.set(next);
    }
  }

  private void checkLagSafely() {
    if (!enabled.get()) return;

    ResolvedServerContext context = resolveActiveServerContext();
    String serverId = context.serverId();
    if (serverId.isBlank()) {
      logStateChange(
          "no-active-server",
          "[lag] waiting: no active IRC server selected (activeTarget={}, fallbackTarget={})",
          context.activeTarget(),
          context.fallbackTarget());
      statusBar.setLagIndicatorReading(null, "Lag unavailable: no active IRC server selected.");
      return;
    }

    if (lagProbePort.currentNick(serverId).isEmpty()) {
      logStateChange(
          "not-connected:" + serverId,
          "[lag] waiting: server '{}' has no current nick yet (activeTarget={})",
          serverId,
          context.activeTarget() != null ? context.activeTarget() : context.fallbackTarget());
      statusBar.setLagIndicatorReading(
          null, "Lag unavailable: not connected to '" + serverId + "'.");
      return;
    }

    try {
      OptionalLong lagMs = lagProbePort.lastMeasuredLagMs(serverId);
      if (!lagProbePort.isLagProbeReady(serverId)) {
        if (lagMs.isPresent()) {
          long lag = Math.max(0L, lagMs.getAsLong());
          serversWithLagSample.add(serverId);
          logStateChange(
              "sample-ready:" + serverId + ":" + lag,
              "[lag] sample ready: '{}' lag={} ms",
              serverId,
              lag);
          statusBar.setLagIndicatorReading(
              lag, "Round-trip lag to '" + serverId + "': " + lag + " ms.");
        } else {
          logStateChange(
              "probe-not-ready:" + serverId,
              "[lag] waiting: server '{}' is not ready for lag probes yet",
              serverId);
          statusBar.setLagIndicatorReading(
              null, "Waiting for connection setup on '" + serverId + "'...");
        }
        return;
      }

      long nowMs = System.currentTimeMillis();
      boolean activeProbeBackend = lagProbePort.shouldRequestLagProbe(serverId);
      boolean requestedFallbackProbe = false;
      boolean hadSeenLagSample = serversWithLagSample.contains(serverId);

      if (activeProbeBackend) {
        logStateChange(
            "active-probe:" + serverId,
            "[lag] probing: backend for '{}' requires explicit lag probes",
            serverId);
        boolean probeSent = requestLagProbe(serverId, "active-backend");
        lagMs =
            probeSent
                ? awaitLagSampleAfterProbe(serverId)
                : lagProbePort.lastMeasuredLagMs(serverId);
      } else {
        lagMs = lagProbePort.lastMeasuredLagMs(serverId);
        if (lagMs.isEmpty() && shouldRequestPassiveFallbackProbe(serverId, nowMs)) {
          logStateChange(
              (hadSeenLagSample ? "passive-refresh:" : "passive-initial:") + serverId,
              hadSeenLagSample
                  ? "[lag] probing: '{}' had no fresh passive lag sample; sending refresh probe"
                  : "[lag] probing: '{}' has no lag sample yet; sending initial probe",
              serverId);
          String probeReason = hadSeenLagSample ? "passive-refresh" : "passive-initial";
          requestedFallbackProbe = requestLagProbe(serverId, probeReason);
          if (requestedFallbackProbe) {
            lastFallbackProbeAtMsByServer.put(serverId, nowMs);
            lagMs = awaitLagSampleAfterProbe(serverId);
          }
        } else if (lagMs.isEmpty()) {
          long lastProbeAtMs = lastFallbackProbeAtMsByServer.getOrDefault(serverId, 0L);
          long nextProbeInMs =
              Math.max(0L, PASSIVE_FALLBACK_PROBE_INTERVAL_MS - (nowMs - lastProbeAtMs));
          logStateChange(
              "passive-wait:" + serverId,
              "[lag] waiting: '{}' has no lag sample yet and fallback probe is throttled for {} ms",
              serverId,
              nextProbeInMs);
        }
      }

      if (lagMs.isPresent()) {
        long lag = Math.max(0L, lagMs.getAsLong());
        serversWithLagSample.add(serverId);
        logStateChange(
            "sample-ready:" + serverId + ":" + lag,
            "[lag] sample ready: '{}' lag={} ms",
            serverId,
            lag);
        statusBar.setLagIndicatorReading(
            lag, "Round-trip lag to '" + serverId + "': " + lag + " ms.");
      } else if (requestedFallbackProbe) {
        statusBar.setLagIndicatorReading(
            null,
            hadSeenLagSample
                ? "Refreshing lag for '" + serverId + "'..."
                : "Measuring server lag...");
      } else if (activeProbeBackend) {
        statusBar.setLagIndicatorReading(null, "Measuring server lag...");
      } else {
        statusBar.setLagIndicatorReading(
            null, "Waiting for ping/pong activity on '" + serverId + "'...");
      }
    } catch (Exception e) {
      log.warn("[lag] unavailable for '{}'", serverId, e);
      statusBar.setLagIndicatorReading(null, "Lag unavailable for '" + serverId + "'.");
    }
  }

  private ResolvedServerContext resolveActiveServerContext() {
    TargetRef active = null;
    try {
      active = activeTargetPort.getActiveTarget();
    } catch (Exception ignored) {
    }

    TargetRef fallback = null;
    if (active == null) {
      try {
        fallback = activeTargetPort.safeStatusTarget();
      } catch (Exception ignored) {
      }
    }

    TargetRef chosen = active != null ? active : fallback;
    if (chosen == null || chosen.isApplicationServer()) {
      return new ResolvedServerContext("", active, fallback);
    }
    return new ResolvedServerContext(
        Objects.toString(chosen.serverId(), "").trim(), active, fallback);
  }

  private static void cancelTask(AtomicReference<ScheduledFuture<?>> ref) {
    ScheduledFuture<?> task = ref.getAndSet(null);
    if (task != null) {
      task.cancel(true);
    }
  }

  private boolean shouldRequestPassiveFallbackProbe(String serverId, long nowMs) {
    long lastProbeAtMs = lastFallbackProbeAtMsByServer.getOrDefault(serverId, 0L);
    return nowMs - lastProbeAtMs >= PASSIVE_FALLBACK_PROBE_INTERVAL_MS;
  }

  private boolean requestLagProbe(String serverId, String reason) {
    try {
      log.debug("[lag] sending probe: server='{}' reason={}", serverId, reason);
      verify(lagProbePort.requestLagProbe(serverId).blockingAwait(2, TimeUnit.SECONDS));
      return true;
    } catch (Exception e) {
      log.warn("[lag] probe request failed: server='{}' reason={}", serverId, reason, e);
      return false;
    }
  }

  private OptionalLong awaitLagSampleAfterProbe(String serverId) {
    OptionalLong lagMs = lagProbePort.lastMeasuredLagMs(serverId);
    if (lagMs.isPresent()) return lagMs;

    long deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PROBE_RESULT_WAIT_MS);
    while (System.nanoTime() < deadlineNs) {
      try {
        Thread.sleep(PROBE_RESULT_POLL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      lagMs = lagProbePort.lastMeasuredLagMs(serverId);
      if (lagMs.isPresent()) return lagMs;
    }
    return lagMs;
  }

  private void logStateChange(String stateKey, String message, Object... args) {
    String prev = lastDiagnosticState.getAndSet(stateKey);
    if (Objects.equals(prev, stateKey)) return;
    log.debug(message, args);
  }

  private record ResolvedServerContext(
      String serverId, TargetRef activeTarget, TargetRef fallbackTarget) {}
}
