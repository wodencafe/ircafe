package cafe.woden.ircclient.app;

import cafe.woden.ircclient.util.VirtualThreads;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** Collects runtime JFR feed events plus lightweight periodic runtime samples for UI display. */
@Service
@Lazy(false)
public class JfrRuntimeEventsService {
  private static final Logger log = LoggerFactory.getLogger(JfrRuntimeEventsService.class);
  private static final int MAX_EVENTS = 1200;
  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
          .withLocale(Locale.ROOT)
          .withZone(ZoneId.systemDefault());

  private final Deque<RuntimeDiagnosticEvent> events = new ArrayDeque<>();
  private final ScheduledExecutorService samplerExec =
      VirtualThreads.newSingleThreadScheduledExecutor("ircafe-jfr-event-sampler");
  private volatile RecordingStream recordingStream;
  private volatile boolean started;

  @PostConstruct
  public void start() {
    if (started) return;
    started = true;
    startPeriodicSampler();
    startRecordingStream();
  }

  @PreDestroy
  public void stop() {
    try {
      samplerExec.shutdownNow();
    } catch (Exception ignored) {
    }

    RecordingStream stream = recordingStream;
    recordingStream = null;
    if (stream != null) {
      try {
        stream.close();
      } catch (Exception ignored) {
      }
    }
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

  private void startPeriodicSampler() {
    appendEvent(
        "INFO",
        "jdk.RuntimeSample",
        "Periodic runtime sampling started.",
        "Sampling cadence: every 30 seconds.");
    samplerExec.scheduleAtFixedRate(this::captureRuntimeSample, 2L, 30L, TimeUnit.SECONDS);
  }

  private void captureRuntimeSample() {
    try {
      Runtime rt = Runtime.getRuntime();
      long used = Math.max(0L, rt.totalMemory() - rt.freeMemory());
      long max = rt.maxMemory();
      if (max == Long.MAX_VALUE || max <= 0L) max = 0L;
      long committed = Math.max(0L, rt.totalMemory());

      double ratio = max > 0L ? (used * 100.0d / max) : 0.0d;
      String summary =
          max > 0L
              ? String.format(
                  Locale.ROOT, "Heap %.1f%% used (%s / %s).", ratio, toMib(used), toMib(max))
              : "Heap usage sampled (" + toMib(used) + " used; max unknown).";

      String level = (max > 0L && ratio >= 95.0d) ? "WARN" : "INFO";
      String details =
          "used="
              + used
              + " bytes\n"
              + "committed="
              + committed
              + " bytes\n"
              + "max="
              + max
              + " bytes\n"
              + "sampledAt="
              + TS_FMT.format(Instant.now())
              + '\n';
      appendEvent(level, "jdk.RuntimeSample", summary, details);
    } catch (Throwable t) {
      appendEvent(
          "ERROR",
          "jdk.RuntimeSample",
          "Runtime sampler failed: " + summarizeThrowable(t),
          stackTrace(t));
    }
  }

  private void startRecordingStream() {
    try {
      RecordingStream stream = new RecordingStream();
      stream.enable("jdk.GarbageCollection");
      stream.enable("jdk.CPULoad").withPeriod(Duration.ofSeconds(20));
      stream.onEvent("jdk.GarbageCollection", this::onGarbageCollection);
      stream.onEvent("jdk.CPULoad", this::onCpuLoad);
      stream.onError(
          err -> {
            String summary = "JFR recording stream error: " + summarizeThrowable(err);
            appendEvent("ERROR", "jdk.RecordingStream", summary, stackTrace(err));
          });
      stream.onClose(
          () ->
              appendEvent(
                  "WARN",
                  "jdk.RecordingStream",
                  "JFR recording stream closed.",
                  "The stream is no longer receiving runtime JFR events."));
      stream.startAsync();
      recordingStream = stream;
      appendEvent(
          "INFO",
          "jdk.RecordingStream",
          "JFR recording stream started.",
          "Enabled events: jdk.GarbageCollection, jdk.CPULoad.");
    } catch (Throwable t) {
      log.warn("[ircafe] Failed to start JFR recording stream", t);
      appendEvent(
          "WARN",
          "jdk.RecordingStream",
          "JFR recording stream unavailable: " + summarizeThrowable(t),
          stackTrace(t));
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

    appendEventAt(
        at,
        "INFO",
        event.getEventType() != null ? event.getEventType().getName() : "jdk.GarbageCollection",
        summary.toString(),
        describeEvent(event));
  }

  private void onCpuLoad(RecordedEvent event) {
    if (event == null) return;
    Instant at = safeInstant(event);
    Double jvmUser = safeDoubleField(event, "jvmUser");
    Double jvmSystem = safeDoubleField(event, "jvmSystem");
    Double machineTotal = safeDoubleField(event, "machineTotal");

    String summary =
        String.format(
            Locale.ROOT,
            "CPU load sample: jvmUser=%s, jvmSystem=%s, machineTotal=%s.",
            formatRatio(jvmUser),
            formatRatio(jvmSystem),
            formatRatio(machineTotal));

    appendEventAt(
        at,
        "INFO",
        event.getEventType() != null ? event.getEventType().getName() : "jdk.CPULoad",
        summary,
        describeEvent(event));
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

  private synchronized void appendEvent(String level, String type, String summary, String details) {
    appendEventAt(Instant.now(), level, type, summary, details);
  }

  private synchronized void appendEventAt(
      Instant at, String level, String type, String summary, String details) {
    events.addLast(new RuntimeDiagnosticEvent(at, level, type, summary, details));
    while (events.size() > MAX_EVENTS) {
      events.removeFirst();
    }
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

  private static String toMib(long bytes) {
    double mib = bytes / (1024.0d * 1024.0d);
    return String.format(Locale.ROOT, "%.1f MiB", mib);
  }

  private static String formatRatio(Double ratio) {
    if (ratio == null) return "n/a";
    return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0d);
  }
}
