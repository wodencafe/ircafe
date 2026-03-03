package cafe.woden.ircclient.ui.shell;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.util.VirtualThreads;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class LagIndicatorService {

  private static final long INITIAL_CHECK_DELAY_SECONDS = 2L;
  private static final long CHECK_INTERVAL_SECONDS = 6L;

  private final RuntimeConfigStore runtimeConfig;
  private final StatusBar statusBar;
  private final ActiveTargetPort activeTargetPort;
  private final IrcClientService ircClientService;
  private final ScheduledExecutorService scheduler;

  private final AtomicBoolean enabled = new AtomicBoolean(true);
  private final AtomicReference<ScheduledFuture<?>> periodicCheckTask = new AtomicReference<>();

  public LagIndicatorService(
      RuntimeConfigStore runtimeConfig,
      StatusBar statusBar,
      ActiveTargetPort activeTargetPort,
      IrcClientService ircClientService) {
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
    this.statusBar = Objects.requireNonNull(statusBar, "statusBar");
    this.activeTargetPort = Objects.requireNonNull(activeTargetPort, "activeTargetPort");
    this.ircClientService = Objects.requireNonNull(ircClientService, "ircClientService");
    this.scheduler = VirtualThreads.newSingleThreadScheduledExecutor("ircafe-lag-indicator");
  }

  @PostConstruct
  void start() {
    boolean initialEnabled = runtimeConfig.readLagIndicatorEnabled(true);
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
      cancelTask(periodicCheckTask);
      statusBar.setLagIndicatorEnabled(false);
      return;
    }

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

    String serverId = resolveActiveServerId();
    if (serverId.isBlank()) {
      statusBar.setLagIndicatorReading(null, "Lag unavailable: no active IRC server selected.");
      return;
    }

    if (ircClientService.currentNick(serverId).isEmpty()) {
      statusBar.setLagIndicatorReading(
          null, "Lag unavailable: not connected to '" + serverId + "'.");
      return;
    }

    try {
      try {
        ircClientService.requestLagProbe(serverId).blockingAwait(2, TimeUnit.SECONDS);
      } catch (Exception ignored) {
      }
      OptionalLong lagMs = ircClientService.lastMeasuredLagMs(serverId);
      if (lagMs.isPresent()) {
        long lag = Math.max(0L, lagMs.getAsLong());
        statusBar.setLagIndicatorReading(
            lag, "Round-trip lag to '" + serverId + "': " + lag + " ms.");
      } else {
        statusBar.setLagIndicatorReading(
            null, "Waiting for ping/pong activity on '" + serverId + "'...");
      }
    } catch (Exception ignored) {
      statusBar.setLagIndicatorReading(null, "Lag unavailable for '" + serverId + "'.");
    }
  }

  private String resolveActiveServerId() {
    TargetRef active = null;
    try {
      active = activeTargetPort.getActiveTarget();
    } catch (Exception ignored) {
    }

    if (active == null) {
      try {
        active = activeTargetPort.safeStatusTarget();
      } catch (Exception ignored) {
      }
    }

    if (active == null || active.isApplicationServer()) return "";
    return Objects.toString(active.serverId(), "").trim();
  }

  private static void cancelTask(AtomicReference<ScheduledFuture<?>> ref) {
    ScheduledFuture<?> task = ref.getAndSet(null);
    if (task != null) {
      task.cancel(true);
    }
  }
}
