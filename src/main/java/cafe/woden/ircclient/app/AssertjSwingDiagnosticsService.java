package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.notifications.IrcEventNotificationRule;
import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.UiProperties;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.ui.tray.TrayNotificationService;
import cafe.woden.ircclient.util.VirtualThreads;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Optional AssertJ Swing integration + EDT responsiveness watchdog. */
@Component
public class AssertjSwingDiagnosticsService {
  private static final Logger log = LoggerFactory.getLogger(AssertjSwingDiagnosticsService.class);
  private static final long AUTO_CAPTURE_COOLDOWN_MS = 120_000L;
  private static final long JFR_CAPTURE_DURATION_MS = 8000L;
  private static final DateTimeFormatter CAPTURE_STAMP_FMT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault());

  private final ApplicationDiagnosticsService diagnostics;
  private final UiProperties uiProps;
  private final RuntimeConfigStore runtimeConfig;
  private final ObjectProvider<NotificationSoundService> soundServiceProvider;
  private final ObjectProvider<TrayNotificationService> trayNotificationServiceProvider;

  private final ScheduledExecutorService watchdogExec;

  private volatile ScheduledFuture<?> watchdogTask;
  private volatile int freezeThresholdMs;
  private volatile int watchdogPollMs;
  private volatile int fallbackViolationReportMs;
  private volatile boolean onIssuePlaySound;
  private volatile boolean onIssueShowNotification;

  private final AtomicBoolean enabled = new AtomicBoolean(false);
  private final AtomicBoolean freezeWatchdogEnabled = new AtomicBoolean(false);
  private final AtomicBoolean pingPending = new AtomicBoolean(false);
  private final AtomicBoolean freezeActive = new AtomicBoolean(false);
  private final AtomicLong lastEdtBeatAtMs = new AtomicLong(System.currentTimeMillis());
  private final AtomicLong freezeSinceAtMs = new AtomicLong(0L);
  private final AtomicLong nextFreezeProgressAtMs = new AtomicLong(0L);
  private final AtomicLong nextAutoCaptureAtMs = new AtomicLong(0L);

  public AssertjSwingDiagnosticsService(
      ApplicationDiagnosticsService diagnostics,
      UiProperties uiProps,
      RuntimeConfigStore runtimeConfig,
      ObjectProvider<NotificationSoundService> soundServiceProvider,
      ObjectProvider<TrayNotificationService> trayNotificationServiceProvider,
      @Qualifier(ExecutorConfig.ASSERTJ_WATCHDOG_SCHEDULER) ScheduledExecutorService watchdogExec) {
    this.diagnostics = diagnostics;
    this.uiProps = uiProps;
    this.runtimeConfig = runtimeConfig;
    this.soundServiceProvider = soundServiceProvider;
    this.trayNotificationServiceProvider = trayNotificationServiceProvider;
    this.watchdogExec = watchdogExec;
  }

  @PostConstruct
  void start() {
    UiProperties.AssertjSwing cfg = resolveSettings();
    if (cfg == null) {
      cfg = new UiProperties.AssertjSwing(null, null, null, null, null, null, null);
    }
    boolean on = cfg == null || Boolean.TRUE.equals(cfg.enabled());
    if (!on) {
      diagnostics.appendAssertjSwingStatus("Disabled by configuration.");
      return;
    }
    enabled.set(true);

    freezeThresholdMs = cfg.edtFreezeThresholdMs() == null ? 2500 : cfg.edtFreezeThresholdMs();
    watchdogPollMs = cfg.edtWatchdogPollMs() == null ? 500 : cfg.edtWatchdogPollMs();
    fallbackViolationReportMs =
        cfg.edtFallbackViolationReportMs() == null ? 5000 : cfg.edtFallbackViolationReportMs();
    onIssuePlaySound = cfg.onIssuePlaySound() != null && cfg.onIssuePlaySound();
    onIssueShowNotification =
        cfg.onIssueShowNotification() != null && cfg.onIssueShowNotification();
    if (watchdogPollMs > freezeThresholdMs) {
      watchdogPollMs = Math.max(100, freezeThresholdMs / 2);
    }

    ViolationDetectorMode detectorMode = installFailOnThreadViolationRepaintManager();
    if (detectorMode == ViolationDetectorMode.ASSERTJ_SWING) {
      diagnostics.appendAssertjSwingStatus("Installed FailOnThreadViolationRepaintManager.");
    } else if (detectorMode == ViolationDetectorMode.FALLBACK) {
      diagnostics.appendAssertjSwingStatus(
          "AssertJ Swing not found on classpath; built-in fallback EDT violation detector is active.");
    } else {
      diagnostics.appendAssertjSwingStatus("No EDT violation detector is active.");
    }

    diagnostics.appendAssertjSwingStatus(
        "Issue actions: sound="
            + (onIssuePlaySound ? "on" : "off")
            + ", notification="
            + (onIssueShowNotification ? "on" : "off")
            + ".");

    boolean watchdogOn =
        cfg.edtFreezeWatchdogEnabled() == null
            || Boolean.TRUE.equals(cfg.edtFreezeWatchdogEnabled());
    if (!watchdogOn) {
      diagnostics.appendAssertjSwingStatus("EDT freeze watchdog disabled by configuration.");
      return;
    }

    freezeWatchdogEnabled.set(true);
    lastEdtBeatAtMs.set(System.currentTimeMillis());
    watchdogTask =
        watchdogExec.scheduleWithFixedDelay(
            this::watchdogTick, watchdogPollMs, watchdogPollMs, TimeUnit.MILLISECONDS);
    diagnostics.appendAssertjSwingStatus(
        "EDT freeze watchdog enabled (threshold="
            + freezeThresholdMs
            + "ms, poll="
            + watchdogPollMs
            + "ms).");
  }

  @PreDestroy
  void shutdown() {
    ScheduledFuture<?> task = watchdogTask;
    if (task != null) {
      task.cancel(true);
      watchdogTask = null;
    }
  }

  private UiProperties.AssertjSwing resolveSettings() {
    UiProperties.AppDiagnostics d = uiProps != null ? uiProps.appDiagnostics() : null;
    return d != null ? d.assertjSwing() : null;
  }

  private ViolationDetectorMode installFailOnThreadViolationRepaintManager() {
    try {
      Class<?> clazz = Class.forName("org.assertj.swing.edt.FailOnThreadViolationRepaintManager");
      Method install = clazz.getMethod("install");
      install.invoke(null);
      return ViolationDetectorMode.ASSERTJ_SWING;
    } catch (ClassNotFoundException e) {
      return installFallbackViolationDetector()
          ? ViolationDetectorMode.FALLBACK
          : ViolationDetectorMode.NONE;
    } catch (Throwable t) {
      diagnostics.appendAssertjSwingStatus(
          "Failed to install FailOnThreadViolationRepaintManager: " + summarize(t));
      return installFallbackViolationDetector()
          ? ViolationDetectorMode.FALLBACK
          : ViolationDetectorMode.NONE;
    }
  }

  private boolean installFallbackViolationDetector() {
    try {
      RepaintManager cur = RepaintManager.currentManager(null);
      if (cur instanceof FallbackViolationRepaintManager) return true;
      RepaintManager.setCurrentManager(
          new FallbackViolationRepaintManager(
              diagnostics, fallbackViolationReportMs, this::onFallbackViolationDetected));
      return true;
    } catch (Throwable t) {
      diagnostics.appendAssertjSwingStatus(
          "Could not install fallback EDT violation detector: " + summarize(t));
      return false;
    }
  }

  private void watchdogTick() {
    if (!enabled.get() || !freezeWatchdogEnabled.get()) return;

    long now = System.currentTimeMillis();
    if (pingPending.compareAndSet(false, true)) {
      final long postedAt = now;
      SwingUtilities.invokeLater(() -> onEdtHeartbeat(postedAt));
    } else {
      long lag = now - lastEdtBeatAtMs.get();
      maybeReportFreezeProgress(lag);
    }
  }

  private void onEdtHeartbeat(long postedAtMs) {
    long now = System.currentTimeMillis();
    long enqueueLag = Math.max(0L, now - postedAtMs);
    lastEdtBeatAtMs.set(now);
    pingPending.set(false);

    if (enqueueLag >= freezeThresholdMs) {
      markFrozen(enqueueLag);
    }

    if (freezeActive.getAndSet(false)) {
      long since = freezeSinceAtMs.getAndSet(0L);
      nextFreezeProgressAtMs.set(0L);
      long recoveredAfterMs = since <= 0L ? enqueueLag : Math.max(0L, now - since);
      diagnostics.notifyUiStallRecovered(recoveredAfterMs);
    }
  }

  private void maybeReportFreezeProgress(long lagMs) {
    if (lagMs < freezeThresholdMs) return;
    markFrozen(lagMs);
    long now = System.currentTimeMillis();
    long nextAt = nextFreezeProgressAtMs.get();
    if (nextAt != 0L && now < nextAt) return;
    nextFreezeProgressAtMs.set(now + 10_000L);
    diagnostics.appendAssertjSwingStatus("EDT still blocked (~" + lagMs + "ms).");
  }

  private void markFrozen(long lagMs) {
    if (!freezeActive.compareAndSet(false, true)) return;
    long now = System.currentTimeMillis();
    freezeSinceAtMs.set(now);
    nextFreezeProgressAtMs.set(now + 10_000L);
    diagnostics.appendAssertjSwingError("EDT freeze detected (~" + lagMs + "ms).");
    triggerIssueActions("EDT freeze detected", "EDT lag reached ~" + lagMs + "ms.");
    scheduleAutoCapture(now, lagMs);
    log.warn("[ircafe] EDT freeze detected (~{}ms)", lagMs);
  }

  private void scheduleAutoCapture(long detectedAtMs, long lagMs) {
    if (!acquireAutoCaptureWindow(detectedAtMs)) {
      diagnostics.appendAssertjSwingStatus(
          "Auto-capture skipped (cooldown active, "
              + Math.max(0L, (nextAutoCaptureAtMs.get() - detectedAtMs) / 1000L)
              + "s remaining).");
      return;
    }
    final String stamp = CAPTURE_STAMP_FMT.format(Instant.ofEpochMilli(detectedAtMs));
    VirtualThreads.start(
        "ircafe-edt-auto-capture", () -> runAutoCapture(detectedAtMs, lagMs, stamp));
  }

  private boolean acquireAutoCaptureWindow(long nowMs) {
    while (true) {
      long nextAllowedAt = nextAutoCaptureAtMs.get();
      if (nextAllowedAt != 0L && nowMs < nextAllowedAt) return false;
      long next = nowMs + AUTO_CAPTURE_COOLDOWN_MS;
      if (nextAutoCaptureAtMs.compareAndSet(nextAllowedAt, next)) {
        return true;
      }
    }
  }

  private void runAutoCapture(long detectedAtMs, long lagMs, String stamp) {
    Path dir = resolveCaptureDirectory();
    if (dir == null) return;

    String baseName = "ui-stall-" + stamp;
    diagnostics.appendAssertjSwingStatus(
        "Starting auto-capture for UI stall (~"
            + lagMs
            + "ms) at "
            + Instant.ofEpochMilli(detectedAtMs)
            + ".");

    Path threadDumpPath = dir.resolve(baseName + ".threaddump.txt");
    ThreadInfo[] dump = captureThreadDump(threadDumpPath);
    appendEdtSnapshot(dump);

    Path jfrPath = dir.resolve(baseName + ".jfr");
    captureJfrSnapshot(jfrPath, baseName);
  }

  private Path resolveCaptureDirectory() {
    try {
      Path runtimePath = runtimeConfig != null ? runtimeConfig.runtimeConfigPath() : null;
      Path base = runtimePath != null ? runtimePath.getParent() : null;
      if (base == null) {
        String home = Objects.toString(System.getProperty("user.home"), "").trim();
        if (home.isEmpty()) {
          diagnostics.appendAssertjSwingError(
              "Could not resolve diagnostics directory: user.home is empty.");
          return null;
        }
        base = Path.of(home, ".config", "ircafe");
      }
      Path dir = base.resolve("diagnostics").resolve("ui-stalls");
      Files.createDirectories(dir);
      return dir;
    } catch (Throwable t) {
      diagnostics.appendAssertjSwingError(
          "Could not prepare diagnostics capture directory: " + summarize(t));
      return null;
    }
  }

  private ThreadInfo[] captureThreadDump(Path outPath) {
    try {
      ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
      ThreadInfo[] all = mxBean.dumpAllThreads(true, true);
      Files.writeString(
          outPath,
          formatThreadDump(all),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
      diagnostics.appendAssertjSwingStatus("Captured thread dump: " + outPath);
      return all;
    } catch (Throwable t) {
      diagnostics.appendAssertjSwingError("Failed to capture thread dump: " + summarize(t));
      return null;
    }
  }

  private static String formatThreadDump(ThreadInfo[] infos) {
    StringBuilder sb = new StringBuilder(256 * 1024);
    sb.append("IRCafe EDT auto-capture thread dump").append('\n');
    sb.append("Captured at ").append(Instant.now()).append('\n');
    int count = infos == null ? 0 : infos.length;
    sb.append("Thread count: ").append(count).append('\n').append('\n');
    if (infos == null || infos.length == 0) {
      sb.append("(no threads)");
      return sb.toString();
    }

    List<ThreadInfo> sorted = new ArrayList<>(infos.length);
    for (ThreadInfo ti : infos) {
      if (ti != null) sorted.add(ti);
    }
    sorted.sort(
        Comparator.comparing(
            t -> Objects.toString(t.getThreadName(), ""), String.CASE_INSENSITIVE_ORDER));

    for (ThreadInfo ti : sorted) {
      sb.append('"')
          .append(Objects.toString(ti.getThreadName(), "(unnamed)"))
          .append('"')
          .append(" Id=")
          .append(ti.getThreadId())
          .append(" ")
          .append(ti.getThreadState())
          .append('\n');
      if (ti.isSuspended()) sb.append("  (suspended)").append('\n');
      if (ti.isInNative()) sb.append("  (in native)").append('\n');
      if (ti.getLockName() != null)
        sb.append("  waiting on ").append(ti.getLockName()).append('\n');
      if (ti.getLockOwnerName() != null) {
        sb.append("  lock owner ")
            .append(ti.getLockOwnerName())
            .append(" Id=")
            .append(ti.getLockOwnerId())
            .append('\n');
      }

      StackTraceElement[] stack = ti.getStackTrace();
      if (stack != null) {
        for (StackTraceElement ste : stack) {
          sb.append("    at ").append(ste).append('\n');
        }
      }

      MonitorInfo[] monitors = ti.getLockedMonitors();
      if (monitors != null && monitors.length > 0) {
        for (MonitorInfo mi : monitors) {
          sb.append("    - locked ")
              .append(mi)
              .append(" at depth ")
              .append(mi.getLockedStackDepth())
              .append('\n');
        }
      }

      LockInfo[] syncs = ti.getLockedSynchronizers();
      if (syncs != null && syncs.length > 0) {
        sb.append("  locked synchronizers: ").append(syncs.length).append('\n');
        for (LockInfo li : syncs) {
          sb.append("    - ").append(li).append('\n');
        }
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  private void appendEdtSnapshot(ThreadInfo[] infos) {
    if (infos == null || infos.length == 0) return;
    for (ThreadInfo ti : infos) {
      if (ti == null) continue;
      String name = Objects.toString(ti.getThreadName(), "");
      if (!name.startsWith("AWT-EventQueue")) continue;
      diagnostics.appendAssertjSwingStatus(
          "EDT snapshot: " + name + " state=" + ti.getThreadState());
      StackTraceElement[] stack = ti.getStackTrace();
      int max = Math.min(12, stack == null ? 0 : stack.length);
      for (int i = 0; i < max; i++) {
        diagnostics.appendAssertjSwingStatus("(edt-stack) " + stack[i]);
      }
      if (stack != null && stack.length > max) {
        diagnostics.appendAssertjSwingStatus("(edt-stack) ... " + (stack.length - max) + " more");
      }
      return;
    }
    diagnostics.appendAssertjSwingStatus(
        "EDT snapshot unavailable (AWT-EventQueue thread not found).");
  }

  private void captureJfrSnapshot(Path outPath, String captureId) {
    Object recording = null;
    try {
      Class<?> cfgClass = Class.forName("jdk.jfr.Configuration");
      Class<?> recClass = Class.forName("jdk.jfr.Recording");

      Object cfg;
      try {
        cfg = cfgClass.getMethod("getConfiguration", String.class).invoke(null, "profile");
      } catch (Throwable profileErr) {
        cfg = cfgClass.getMethod("getConfiguration", String.class).invoke(null, "default");
      }

      recording = recClass.getConstructor(cfgClass).newInstance(cfg);
      recClass.getMethod("setToDisk", boolean.class).invoke(recording, true);
      recClass.getMethod("setName", String.class).invoke(recording, "ircafe-" + captureId);
      recClass.getMethod("start").invoke(recording);

      try {
        TimeUnit.MILLISECONDS.sleep(JFR_CAPTURE_DURATION_MS);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        diagnostics.appendAssertjSwingStatus("JFR capture interrupted.");
        return;
      }

      recClass.getMethod("stop").invoke(recording);
      recClass.getMethod("dump", Path.class).invoke(recording, outPath);
      diagnostics.appendAssertjSwingStatus(
          "Captured JFR snapshot (" + JFR_CAPTURE_DURATION_MS + "ms): " + outPath);
    } catch (ClassNotFoundException e) {
      diagnostics.appendAssertjSwingStatus(
          "JFR classes unavailable on this runtime; skipping JFR capture.");
    } catch (Throwable t) {
      diagnostics.appendAssertjSwingError("Failed to capture JFR snapshot: " + summarize(t));
    } finally {
      closeRecordingQuietly(recording);
    }
  }

  private static void closeRecordingQuietly(Object recording) {
    if (recording == null) return;
    try {
      recording.getClass().getMethod("close").invoke(recording);
    } catch (Throwable ignored) {
    }
  }

  private void onFallbackViolationDetected(String threadName, String sourceName) {
    triggerIssueActions(
        "EDT thread violation detected",
        "Off-EDT Swing access on thread="
            + Objects.toString(threadName, "(unknown)")
            + ", source="
            + Objects.toString(sourceName, "(unknown)")
            + ".");
  }

  private void triggerIssueActions(String title, String body) {
    if (!onIssuePlaySound && !onIssueShowNotification) return;

    if (onIssuePlaySound) {
      NotificationSoundService soundService =
          soundServiceProvider != null ? soundServiceProvider.getIfAvailable() : null;
      if (soundService != null) {
        try {
          soundService.play();
        } catch (Throwable t) {
          diagnostics.appendAssertjSwingStatus("Issue-action sound failed: " + summarize(t));
        }
      }
    }

    if (onIssueShowNotification) {
      TrayNotificationService tray =
          trayNotificationServiceProvider != null
              ? trayNotificationServiceProvider.getIfAvailable()
              : null;
      if (tray != null) {
        try {
          tray.notifyCustom(
              TargetRef.APPLICATION_SERVER_ID,
              TargetRef.APPLICATION_ASSERTJ_SWING_TARGET,
              title,
              body,
              true,
              false,
              IrcEventNotificationRule.FocusScope.ANY,
              false,
              null,
              false,
              null);
        } catch (Throwable t) {
          diagnostics.appendAssertjSwingStatus("Issue-action notification failed: " + summarize(t));
        }
      }
    }
  }

  private enum ViolationDetectorMode {
    ASSERTJ_SWING,
    FALLBACK,
    NONE
  }

  @FunctionalInterface
  private interface ViolationCallback {
    void onViolation(String threadName, String sourceName);
  }

  private static String summarize(Throwable t) {
    if (t == null) return "(null throwable)";
    Throwable root = t;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String type = root.getClass().getSimpleName();
    if (type == null || type.isBlank()) {
      type = root.getClass().getName();
    }
    String msg = Objects.toString(root.getMessage(), "").trim();
    return msg.isEmpty() ? type : (type + ": " + msg);
  }

  private static final class FallbackViolationRepaintManager extends RepaintManager {
    private final ApplicationDiagnosticsService diagnostics;
    private final long reportIntervalMs;
    private final ViolationCallback violationCallback;
    private final AtomicLong nextReportAtMs = new AtomicLong(0L);

    FallbackViolationRepaintManager(
        ApplicationDiagnosticsService diagnostics,
        long reportIntervalMs,
        ViolationCallback violationCallback) {
      this.diagnostics = diagnostics;
      this.reportIntervalMs = Math.max(250L, reportIntervalMs);
      this.violationCallback = violationCallback;
    }

    @Override
    public synchronized void addInvalidComponent(JComponent component) {
      detectViolation(component);
      super.addInvalidComponent(component);
    }

    @Override
    public void addDirtyRegion(JComponent component, int x, int y, int w, int h) {
      detectViolation(component);
      super.addDirtyRegion(component, x, y, w, h);
    }

    @Override
    public void addDirtyRegion(java.awt.Window window, int x, int y, int w, int h) {
      detectViolation(window);
      super.addDirtyRegion(window, x, y, w, h);
    }

    @SuppressWarnings("removal")
    @Override
    public void addDirtyRegion(java.applet.Applet applet, int x, int y, int w, int h) {
      detectViolation(applet);
      super.addDirtyRegion(applet, x, y, w, h);
    }

    private void detectViolation(Object source) {
      if (SwingUtilities.isEventDispatchThread()) return;
      long now = System.currentTimeMillis();
      long next = nextReportAtMs.get();
      if (next != 0L && now < next) return;
      nextReportAtMs.set(now + reportIntervalMs);

      String threadName = Thread.currentThread().getName();
      String sourceName = source == null ? "(unknown)" : source.getClass().getName();
      diagnostics.appendAssertjSwingError(
          "EDT violation (fallback detector): thread=" + threadName + ", source=" + sourceName);

      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      int max = Math.min(12, stack == null ? 0 : stack.length);
      for (int i = 0; i < max; i++) {
        diagnostics.appendAssertjSwingStatus("(stack) " + stack[i]);
      }
      if (violationCallback != null) {
        try {
          violationCallback.onViolation(threadName, sourceName);
        } catch (Throwable ignored) {
        }
      }
    }
  }
}
