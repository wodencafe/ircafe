package cafe.woden.ircclient.app;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.util.VirtualThreads;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;

/**
 * Collects runtime JFR feed events for UI diagnostics, with runtime enable/disable + pause
 * controls.
 */
@Service
@Lazy(false)
@ApplicationLayer
@NamedInterface("diagnostics")
public class JfrRuntimeEventsService {
  private static final Logger log = LoggerFactory.getLogger(JfrRuntimeEventsService.class);
  public static final String PROP_STATE = "jfrRuntimeState";
  private static final int MAX_EVENTS = 1200;
  private static final Duration GC_WINDOW = Duration.ofMinutes(2);
  private static final double GC_ALERT_EVENTS_PER_MINUTE = 10.0d;
  private static final Duration CPU_SAMPLE_PERIOD = Duration.ofSeconds(5);

  private final RuntimeConfigStore runtimeConfigStore;
  private final Deque<RuntimeDiagnosticEvent> events = new ArrayDeque<>();
  private final Deque<Instant> gcEventsInWindow = new ArrayDeque<>();
  private final PropertyChangeSupport stateChanges = new PropertyChangeSupport(this);
  private final ScheduledExecutorService samplerExec =
      VirtualThreads.newSingleThreadScheduledExecutor("ircafe-jfr-event-sampler");

  private volatile RecordingStream recordingStream;
  private volatile boolean started;
  private volatile boolean enabled;
  private volatile boolean tableLoggingPaused;
  private volatile Instant lastCpuSampleAt;
  private volatile Double cpuJvmUserRatio;
  private volatile Double cpuJvmSystemRatio;
  private volatile Double cpuMachineTotalRatio;
  private volatile Instant lastRuntimeSampleAt;
  private volatile long runtimeUsedBytes;
  private volatile long runtimeCommittedBytes;
  private volatile long runtimeMaxBytes;
  private volatile int runtimeHeapPercent;
  private volatile Instant lastGcEventAt;

  public JfrRuntimeEventsService(RuntimeConfigStore runtimeConfigStore) {
    this.runtimeConfigStore = runtimeConfigStore;
    this.enabled = runtimeConfigStore == null || runtimeConfigStore.readApplicationJfrEnabled(true);
  }

  @PostConstruct
  public void start() {
    synchronized (this) {
      if (started) return;
      started = true;
      appendEventAtLocked(
          Instant.now(),
          "INFO",
          "jdk.RuntimeSample",
          "Periodic runtime sampling started.",
          "Sampling cadence: every 5 seconds.",
          true);
    }
    startPeriodicSampler();
    synchronized (this) {
      if (enabled) {
        startRecordingStreamLocked();
      } else {
        appendEventAtLocked(
            Instant.now(),
            "WARN",
            "jdk.RecordingStream",
            "JFR diagnostics are disabled.",
            "Enable diagnostics from Application -> JFR to start runtime event capture.",
            true);
      }
    }
    captureRuntimeSample();
    fireStateChanged();
  }

  @PreDestroy
  public void stop() {
    try {
      samplerExec.shutdownNow();
    } catch (Exception ignored) {
    }
    synchronized (this) {
      stopRecordingStreamLocked();
      started = false;
    }
    fireStateChanged();
  }

  public synchronized List<RuntimeDiagnosticEvent> recentEvents(int limit) {
    int max = Math.max(1, Math.min(2000, limit));
    if (events.isEmpty()) return List.of();
    ArrayList<RuntimeDiagnosticEvent> out = new ArrayList<>(Math.min(max, events.size()));
    Iterator<RuntimeDiagnosticEvent> it = events.descendingIterator();
    while (it.hasNext() && out.size() < max) {
      out.add(it.next());
    }
    return List.copyOf(out);
  }

  public synchronized void clearEvents() {
    if (events.isEmpty()) return;
    events.clear();
    fireStateChanged();
  }

  public synchronized boolean removeEvent(RuntimeDiagnosticEvent event) {
    if (event == null || events.isEmpty()) return false;
    boolean removed = events.removeFirstOccurrence(event);
    if (removed) {
      fireStateChanged();
    }
    return removed;
  }

  public synchronized boolean isEnabled() {
    return enabled;
  }

  public synchronized void setEnabled(boolean enabled) {
    if (this.enabled == enabled) return;
    this.enabled = enabled;
    if (runtimeConfigStore != null) {
      runtimeConfigStore.rememberApplicationJfrEnabled(enabled);
    }
    if (enabled) {
      appendEventAtLocked(
          Instant.now(),
          "INFO",
          "jdk.RecordingStream",
          "JFR diagnostics enabled.",
          "Starting runtime JFR event stream.",
          true);
      if (started) {
        startRecordingStreamLocked();
      }
      captureRuntimeSample();
    } else {
      stopRecordingStreamLocked();
      appendEventAtLocked(
          Instant.now(),
          "WARN",
          "jdk.RecordingStream",
          "JFR diagnostics disabled.",
          "Runtime JFR event stream has been stopped.",
          true);
    }
    fireStateChanged();
  }

  public synchronized boolean isTableLoggingPaused() {
    return tableLoggingPaused;
  }

  public synchronized void setTableLoggingPaused(boolean paused) {
    if (tableLoggingPaused == paused) return;
    tableLoggingPaused = paused;
    appendEventAtLocked(
        Instant.now(),
        "INFO",
        "RuntimeEvents",
        paused ? "JFR event table logging paused." : "JFR event table logging resumed.",
        paused
            ? "Events continue to update status gauges, but table rows are not appended."
            : "Table rows are now appended for incoming JFR events.",
        true);
    fireStateChanged();
  }

  public synchronized StatusSnapshot statusSnapshot() {
    Instant now = Instant.now();
    pruneGcWindowLocked(now);
    int gcCount = gcEventsInWindow.size();
    double gcPerMinute = gcCount * 60.0d / Math.max(1.0d, GC_WINDOW.toSeconds());
    boolean gcAlert = gcPerMinute >= GC_ALERT_EVENTS_PER_MINUTE;
    return new StatusSnapshot(
        enabled,
        tableLoggingPaused,
        recordingStream != null,
        lastCpuSampleAt,
        cpuJvmUserRatio,
        cpuJvmSystemRatio,
        cpuMachineTotalRatio,
        lastRuntimeSampleAt,
        runtimeUsedBytes,
        runtimeCommittedBytes,
        runtimeMaxBytes,
        runtimeHeapPercent,
        gcCount,
        gcPerMinute,
        gcAlert,
        lastGcEventAt);
  }

  public void requestImmediateRefresh() {
    captureRuntimeSample();
  }

  public void addStateListener(PropertyChangeListener listener) {
    if (listener == null) return;
    stateChanges.addPropertyChangeListener(listener);
  }

  public void removeStateListener(PropertyChangeListener listener) {
    if (listener == null) return;
    stateChanges.removePropertyChangeListener(listener);
  }

  private void startPeriodicSampler() {
    samplerExec.scheduleAtFixedRate(this::captureRuntimeSample, 1L, 5L, TimeUnit.SECONDS);
  }

  private void captureRuntimeSample() {
    try {
      Runtime rt = Runtime.getRuntime();
      long committed = Math.max(0L, rt.totalMemory());
      long free = Math.max(0L, rt.freeMemory());
      long used = Math.max(0L, committed - free);
      long max = rt.maxMemory();
      if (max == Long.MAX_VALUE || max <= 0L) max = 0L;
      int heapPercent =
          max > 0L ? Math.max(0, Math.min(100, (int) Math.round((used * 100.0d) / max))) : -1;

      synchronized (this) {
        if (!enabled) return;
        lastRuntimeSampleAt = Instant.now();
        runtimeUsedBytes = used;
        runtimeCommittedBytes = committed;
        runtimeMaxBytes = max;
        runtimeHeapPercent = heapPercent;
      }
      fireStateChanged();
    } catch (Throwable t) {
      synchronized (this) {
        appendEventAtLocked(
            Instant.now(),
            "ERROR",
            "jdk.RuntimeSample",
            "Runtime sampler failed: " + summarizeThrowable(t),
            stackTrace(t),
            true);
      }
      fireStateChanged();
    }
  }

  private synchronized void startRecordingStreamLocked() {
    if (!enabled) return;
    if (recordingStream != null) return;
    try {
      RecordingStream stream = new RecordingStream();
      stream.enable("jdk.GarbageCollection");
      stream.enable("jdk.CPULoad").withPeriod(CPU_SAMPLE_PERIOD);
      stream.onEvent("jdk.GarbageCollection", this::onGarbageCollection);
      stream.onEvent("jdk.CPULoad", this::onCpuLoad);
      stream.onError(
          err -> {
            synchronized (JfrRuntimeEventsService.this) {
              appendEventAtLocked(
                  Instant.now(),
                  "ERROR",
                  "jdk.RecordingStream",
                  "JFR recording stream error: " + summarizeThrowable(err),
                  stackTrace(err),
                  true);
            }
            fireStateChanged();
          });
      stream.onClose(
          () -> {
            synchronized (JfrRuntimeEventsService.this) {
              if (recordingStream == stream) {
                recordingStream = null;
              }
              if (enabled) {
                appendEventAtLocked(
                    Instant.now(),
                    "WARN",
                    "jdk.RecordingStream",
                    "JFR recording stream closed.",
                    "The stream is no longer receiving runtime JFR events.",
                    true);
              }
            }
            fireStateChanged();
          });
      stream.startAsync();
      recordingStream = stream;
      appendEventAtLocked(
          Instant.now(),
          "INFO",
          "jdk.RecordingStream",
          "JFR recording stream started.",
          "Enabled events: jdk.GarbageCollection, jdk.CPULoad.",
          true);
    } catch (Throwable t) {
      log.warn("[ircafe] Failed to start JFR recording stream", t);
      appendEventAtLocked(
          Instant.now(),
          "WARN",
          "jdk.RecordingStream",
          "JFR recording stream unavailable: " + summarizeThrowable(t),
          stackTrace(t),
          true);
    }
  }

  private synchronized void stopRecordingStreamLocked() {
    RecordingStream stream = recordingStream;
    recordingStream = null;
    if (stream != null) {
      try {
        stream.close();
      } catch (Exception ignored) {
      }
    }
  }

  private void onGarbageCollection(RecordedEvent event) {
    if (event == null) return;
    Instant at = safeInstant(event);
    String gcName = safeTextField(event, "name");
    String cause = safeTextField(event, "cause");
    Duration pauses = safeDurationField(event, "sumOfPauses");
    StringBuilder summary = new StringBuilder("Garbage collection");
    if (!gcName.isBlank()) summary.append(" (").append(gcName).append(')');
    if (!cause.isBlank()) summary.append(" cause=").append(cause);
    if (pauses != null) summary.append(", pauses=").append(pauses.toMillis()).append(" ms");
    summary.append('.');

    synchronized (this) {
      if (!enabled) return;
      lastGcEventAt = at;
      gcEventsInWindow.addLast(at);
      pruneGcWindowLocked(at);
      appendEventAtLocked(
          at,
          "INFO",
          event.getEventType() != null ? event.getEventType().getName() : "jdk.GarbageCollection",
          summary.toString(),
          describeEvent(event),
          false);
    }
    fireStateChanged();
  }

  private void onCpuLoad(RecordedEvent event) {
    if (event == null) return;
    synchronized (this) {
      if (!enabled) return;
      lastCpuSampleAt = safeInstant(event);
      cpuJvmUserRatio = safeDoubleField(event, "jvmUser");
      cpuJvmSystemRatio = safeDoubleField(event, "jvmSystem");
      cpuMachineTotalRatio = safeDoubleField(event, "machineTotal");
    }
    fireStateChanged();
  }

  private synchronized void pruneGcWindowLocked(Instant now) {
    Instant cutoff = now.minus(GC_WINDOW);
    while (!gcEventsInWindow.isEmpty()) {
      Instant head = gcEventsInWindow.peekFirst();
      if (head == null || !head.isBefore(cutoff)) break;
      gcEventsInWindow.removeFirst();
    }
  }

  private void appendEventAtLocked(
      Instant at, String level, String type, String summary, String details, boolean force) {
    if (!force && tableLoggingPaused) return;
    events.addLast(new RuntimeDiagnosticEvent(at, level, type, summary, details));
    while (events.size() > MAX_EVENTS) {
      events.removeFirst();
    }
  }

  private void fireStateChanged() {
    stateChanges.firePropertyChange(PROP_STATE, null, System.nanoTime());
  }

  private static String describeEvent(RecordedEvent event) {
    if (event == null) return "";
    StringBuilder out = new StringBuilder(1024);
    String type = event.getEventType() != null ? event.getEventType().getName() : "unknown";
    out.append("type=").append(type).append('\n');
    out.append("startTime=").append(event.getStartTime()).append('\n');
    out.append("endTime=").append(event.getEndTime()).append('\n');

    try {
      for (ValueDescriptor d : event.getEventType().getFields()) {
        if (d == null) continue;
        String name = Objects.toString(d.getName(), "").trim();
        if (name.isEmpty()) continue;
        Object value;
        try {
          value = event.getValue(name);
        } catch (Exception ignored) {
          value = "<unavailable>";
        }
        out.append(name).append('=').append(formatEventFieldValue(value)).append('\n');
      }
    } catch (Exception ignored) {
      // Keep detail rendering best-effort; stream events can have unexpected payload types.
    }
    return out.toString();
  }

  private static String formatEventFieldValue(Object value) {
    if (value == null) return "null";
    if (value instanceof Duration d) {
      return d.toMillis() + " ms";
    }
    String s = Objects.toString(value, "");
    if (s.length() > 600) {
      return s.substring(0, 600) + "...";
    }
    return s;
  }

  private static Instant safeInstant(RecordedEvent event) {
    try {
      Instant at = event.getStartTime();
      return at != null ? at : Instant.now();
    } catch (Exception ignored) {
      return Instant.now();
    }
  }

  private static String safeTextField(RecordedEvent event, String field) {
    try {
      Object v = event.getValue(field);
      return Objects.toString(v, "").trim();
    } catch (Exception ignored) {
      return "";
    }
  }

  private static Double safeDoubleField(RecordedEvent event, String field) {
    try {
      Object v = event.getValue(field);
      if (v instanceof Number n) return n.doubleValue();
      return null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Duration safeDurationField(RecordedEvent event, String field) {
    try {
      Object v = event.getValue(field);
      if (v instanceof Duration d) return d;
      return null;
    } catch (Exception ignored) {
      return null;
    }
  }

  public static String formatRatio(Double ratio) {
    if (ratio == null) return "n/a";
    return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0d);
  }

  private static String summarizeThrowable(Throwable t) {
    if (t == null) return "(unknown)";
    String type = t.getClass().getSimpleName();
    if (type == null || type.isBlank()) type = t.getClass().getName();
    String msg = Objects.toString(t.getMessage(), "").trim();
    return msg.isEmpty() ? type : type + ": " + msg;
  }

  private static String stackTrace(Throwable t) {
    if (t == null) return "";
    java.io.StringWriter sw = new java.io.StringWriter();
    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
    t.printStackTrace(pw);
    pw.flush();
    return sw.toString();
  }

  public record StatusSnapshot(
      boolean enabled,
      boolean tableLoggingPaused,
      boolean streamActive,
      Instant lastCpuSampleAt,
      Double cpuJvmUserRatio,
      Double cpuJvmSystemRatio,
      Double cpuMachineTotalRatio,
      Instant lastRuntimeSampleAt,
      long runtimeUsedBytes,
      long runtimeCommittedBytes,
      long runtimeMaxBytes,
      int runtimeHeapPercent,
      int gcEventsInWindow,
      double gcEventsPerMinute,
      boolean gcAlert,
      Instant lastGcEventAt) {}
}
