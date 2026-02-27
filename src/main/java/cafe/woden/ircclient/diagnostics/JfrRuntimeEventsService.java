package cafe.woden.ircclient.diagnostics;

import cafe.woden.ircclient.config.ExecutorConfig;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.util.VirtualThreads;
import com.sun.management.HotSpotDiagnosticMXBean;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Collects runtime JFR feed events for UI diagnostics, with runtime enable/disable + pause
 * controls.
 */
@Service
@Lazy(false)
@ApplicationLayer
public class JfrRuntimeEventsService {
  private static final Logger log = LoggerFactory.getLogger(JfrRuntimeEventsService.class);
  public static final String PROP_STATE = "jfrRuntimeState";
  private static final int MAX_EVENTS = 1200;
  private static final Duration GC_WINDOW = Duration.ofMinutes(2);
  private static final double GC_ALERT_EVENTS_PER_MINUTE = 10.0d;
  private static final Duration CPU_SAMPLE_PERIOD = Duration.ofSeconds(5);
  private static final Duration EXPORT_JFR_CAPTURE_DURATION = Duration.ofSeconds(1);
  private static final String HOTSPOT_DIAGNOSTIC_BEAN = "com.sun.management:type=HotSpotDiagnostic";
  private static final String DIAGNOSTIC_COMMAND_BEAN = "com.sun.management:type=DiagnosticCommand";
  private static final Pattern HISTOGRAM_ROW =
      Pattern.compile("^\\s*\\d+:\\s*(\\d+)\\s+(\\d+)\\s+(.+)$");
  private static final DateTimeFormatter EXPORT_TS_FMT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
          .withLocale(Locale.ROOT)
          .withZone(ZoneId.systemDefault());

  private final RuntimeConfigStore runtimeConfigStore;
  private final Deque<RuntimeDiagnosticEvent> events = new ArrayDeque<>();
  private final Deque<Instant> gcEventsInWindow = new ArrayDeque<>();
  private final PropertyChangeSupport stateChanges = new PropertyChangeSupport(this);
  private final ScheduledExecutorService samplerExec;

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
    this(
        runtimeConfigStore,
        VirtualThreads.newSingleThreadScheduledExecutor("ircafe-jfr-event-sampler"));
  }

  @Autowired
  public JfrRuntimeEventsService(
      RuntimeConfigStore runtimeConfigStore,
      @Qualifier(ExecutorConfig.JFR_RUNTIME_EVENTS_SAMPLER_SCHEDULER)
          ScheduledExecutorService samplerExec) {
    this.runtimeConfigStore = runtimeConfigStore;
    this.samplerExec = Objects.requireNonNull(samplerExec, "samplerExec");
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

  public MemoryDiagnosticsExportReport captureMemoryDiagnosticsBundle() {
    return captureMemoryDiagnosticsBundle(true);
  }

  public MemoryDiagnosticsExportReport captureMemoryDiagnosticsBundle(boolean includeHeapDump) {
    Instant startedAt = Instant.now();
    String baseName = "ircafe-memory-bundle-" + EXPORT_TS_FMT.format(startedAt);

    Path exportDir = diagnosticsExportDirectory();
    Path stagingDir = exportDir.resolve(baseName);
    Path bundleZipPath = exportDir.resolve(baseName + ".zip");

    StringBuilder summary = new StringBuilder(1024);
    summary
        .append("Memory diagnostics bundle")
        .append('\n')
        .append("Created: ")
        .append(startedAt)
        .append('\n');
    if (!includeHeapDump) {
      summary.append("Mode: light (heap dump skipped)\n");
    }

    try {
      Files.createDirectories(exportDir);
      Files.createDirectories(stagingDir);

      String runtimeSummary = runtimeSummaryText();
      writeTextFile(stagingDir.resolve("runtime-summary.txt"), runtimeSummary);

      String histogram = captureClassHistogramText();
      writeTextFile(stagingDir.resolve("class-histogram.txt"), histogram);

      String findings = automatedMemoryFindings(histogram);
      writeTextFile(stagingDir.resolve("automated-memory-findings.txt"), findings);

      String jfrSummary = captureOneShotJfrSnapshot(stagingDir.resolve("runtime-snapshot.jfr"));
      writeTextFile(stagingDir.resolve("jfr-summary.txt"), jfrSummary);

      if (includeHeapDump) {
        String heapDumpSummary = dumpLiveHeap(stagingDir.resolve("heap-live.hprof"));
        writeTextFile(stagingDir.resolve("heap-dump-summary.txt"), heapDumpSummary);
      }

      zipDirectory(stagingDir, bundleZipPath);
      deleteDirectoryQuietly(stagingDir);

      summary
          .append("Bundle: ")
          .append(bundleZipPath.toAbsolutePath())
          .append('\n')
          .append(
              "Contains: runtime-summary.txt, class-histogram.txt, automated-memory-findings.txt, jfr-summary.txt, ")
          .append("runtime-snapshot.jfr")
          .append(includeHeapDump ? ", heap-live.hprof, heap-dump-summary.txt" : "")
          .append('\n');

      appendMemoryExportEvent(
          "INFO",
          "Exported memory diagnostics bundle.",
          summary.toString().trim() + '\n');
      return new MemoryDiagnosticsExportReport(bundleZipPath, summary.toString().trim(), true);
    } catch (Throwable t) {
      String err =
          summary
              .append("Export failed: ")
              .append(summarizeThrowable(t))
              .append('\n')
              .append("Staging directory: ")
              .append(stagingDir.toAbsolutePath())
              .toString()
              .trim();
      appendMemoryExportEvent("ERROR", "Memory diagnostics export failed.", err + '\n' + stackTrace(t));
      return new MemoryDiagnosticsExportReport(null, err, false);
    }
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

  private void appendMemoryExportEvent(String level, String summary, String details) {
    synchronized (this) {
      appendEventAtLocked(
          Instant.now(), level, "MemoryDiagnostics", Objects.toString(summary, ""), details, true);
    }
    fireStateChanged();
  }

  private String runtimeSummaryText() {
    StringBuilder out = new StringBuilder(8 * 1024);
    RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
    ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
    List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();

    out.append("Timestamp: ").append(Instant.now()).append('\n');
    out.append("PID@Host: ").append(Objects.toString(runtime.getName(), "")).append('\n');
    out.append("Uptime: ").append(runtime.getUptime()).append(" ms\n");
    out.append("JVM: ").append(Objects.toString(runtime.getVmName(), "")).append('\n');
    out.append("JVM Version: ").append(Objects.toString(runtime.getVmVersion(), "")).append('\n');
    out.append("JVM Vendor: ").append(Objects.toString(runtime.getVmVendor(), "")).append('\n');
    out.append("Start Time: ").append(Instant.ofEpochMilli(runtime.getStartTime())).append('\n');
    out.append("Input Args: ").append(runtime.getInputArguments()).append('\n');
    out.append('\n');

    out.append("Heap: ").append(formatMemoryUsage(memory.getHeapMemoryUsage())).append('\n');
    out.append("Non-Heap: ").append(formatMemoryUsage(memory.getNonHeapMemoryUsage())).append('\n');
    out.append('\n');

    out.append("Memory Pools:\n");
    for (MemoryPoolMXBean pool : pools) {
      if (pool == null) continue;
      out.append("- ").append(Objects.toString(pool.getName(), "(unnamed)")).append('\n');
      out.append("  Type: ").append(Objects.toString(pool.getType(), "")).append('\n');
      out.append("  Usage: ").append(formatMemoryUsage(pool.getUsage())).append('\n');
      out.append("  Peak: ").append(formatMemoryUsage(pool.getPeakUsage())).append('\n');
    }
    out.append('\n');

    out.append("Garbage Collectors:\n");
    for (GarbageCollectorMXBean gc : gcs) {
      if (gc == null) continue;
      out.append("- ").append(Objects.toString(gc.getName(), "(unnamed)")).append('\n');
      out.append("  Collections: ").append(gc.getCollectionCount()).append('\n');
      out.append("  Collection Time: ").append(gc.getCollectionTime()).append(" ms\n");
    }
    out.append('\n');

    out.append("Class Loading:\n");
    out.append("- Loaded: ").append(classes.getLoadedClassCount()).append('\n');
    out.append("- Total Loaded: ").append(classes.getTotalLoadedClassCount()).append('\n');
    out.append("- Unloaded: ").append(classes.getUnloadedClassCount()).append('\n');
    out.append('\n');

    out.append("Threads:\n");
    out.append("- Live: ").append(threads.getThreadCount()).append('\n');
    out.append("- Peak: ").append(threads.getPeakThreadCount()).append('\n');
    out.append("- Daemon: ").append(threads.getDaemonThreadCount()).append('\n');
    out.append("- Total Started: ").append(threads.getTotalStartedThreadCount()).append('\n');
    return out.toString();
  }

  private static String formatMemoryUsage(MemoryUsage usage) {
    if (usage == null) return "(n/a)";
    return "init="
        + formatBytes(usage.getInit())
        + ", used="
        + formatBytes(usage.getUsed())
        + ", committed="
        + formatBytes(usage.getCommitted())
        + ", max="
        + formatBytes(usage.getMax());
  }

  private static String formatBytes(long bytes) {
    if (bytes < 0L) return "n/a";
    if (bytes < 1024L) return bytes + " B";
    double kib = bytes / 1024.0d;
    if (kib < 1024.0d) return String.format(Locale.ROOT, "%.1f KiB", kib);
    double mib = kib / 1024.0d;
    if (mib < 1024.0d) return String.format(Locale.ROOT, "%.1f MiB", mib);
    double gib = mib / 1024.0d;
    return String.format(Locale.ROOT, "%.2f GiB", gib);
  }

  private String captureClassHistogramText() {
    try {
      MBeanServer server = ManagementFactory.getPlatformMBeanServer();
      ObjectName name = new ObjectName(DIAGNOSTIC_COMMAND_BEAN);
      if (!server.isRegistered(name)) {
        return "DiagnosticCommand MBean is unavailable on this runtime.";
      }
      Object out = invokeDiagnosticCommand(server, name, "gcClassHistogram", new String[] {"-all=true"});
      if (out == null) {
        out = invokeDiagnosticCommand(server, name, "gcClassHistogram", new String[0]);
      }
      return Objects.toString(out, "Diagnostic command returned no class histogram output.");
    } catch (Throwable t) {
      return "Failed to capture class histogram: " + summarizeThrowable(t) + '\n' + stackTrace(t);
    }
  }

  private String automatedMemoryFindings(String histogramText) {
    List<HistogramEntry> entries = parseHistogramEntries(histogramText);
    if (entries.isEmpty()) {
      return "Automated memory findings unavailable: class histogram could not be parsed.";
    }

    long parsedTotalBytes = entries.stream().mapToLong(HistogramEntry::bytes).sum();
    long swingDocBytes =
        sumBytesMatching(
            entries,
            className ->
                className.startsWith("javax.swing.text.")
                    || className.contains("GapContent")
                    || className.contains("DefaultStyledDocument"));
    long imageBytes =
        sumBytesMatching(
            entries,
            className ->
                className.startsWith("java.awt.image.")
                    || className.startsWith("sun.awt.image.")
                    || "javax.swing.ImageIcon".equals(className));
    long byteArrayBytes = sumBytesMatching(entries, className -> "[B".equals(className));
    long textBytes =
        sumBytesMatching(
            entries, className -> "java.lang.String".equals(className) || "[C".equals(className));
    long ircafeBytes =
        sumBytesMatching(entries, className -> className.startsWith("cafe.woden.ircclient."));

    StringBuilder out = new StringBuilder(8 * 1024);
    out.append("Automated Memory Findings\n");
    out.append("Timestamp: ").append(Instant.now()).append('\n');
    out.append("Parsed histogram classes: ").append(entries.size()).append('\n');
    out.append("Parsed histogram bytes: ").append(formatBytes(parsedTotalBytes)).append('\n');
    out.append('\n');

    out.append("Top classes by bytes:\n");
    int topN = Math.min(20, entries.size());
    for (int i = 0; i < topN; i++) {
      HistogramEntry e = entries.get(i);
      out.append(
          String.format(
              Locale.ROOT,
              "%2d. %s | %s | %d instances%n",
              i + 1,
              e.className(),
              formatBytes(e.bytes()),
              e.instances()));
    }
    out.append('\n');

    out.append("Category totals:\n");
    appendCategoryLine(out, "Swing transcript/text classes", swingDocBytes, parsedTotalBytes);
    appendCategoryLine(out, "Image classes", imageBytes, parsedTotalBytes);
    appendCategoryLine(out, "byte[]", byteArrayBytes, parsedTotalBytes);
    appendCategoryLine(out, "String/char[]", textBytes, parsedTotalBytes);
    appendCategoryLine(out, "IRCafe classes", ircafeBytes, parsedTotalBytes);
    out.append('\n');

    out.append("Heuristic hints:\n");
    boolean hinted = false;
    if (swingDocBytes > parsedTotalBytes / 5L) {
      out.append(
          "- Swing transcript/document objects are a large share; long-lived open targets can accumulate memory.\n");
      hinted = true;
    }
    if (imageBytes + byteArrayBytes > parsedTotalBytes / 4L) {
      out.append(
          "- Image payloads/decodes are a large share; inline image/link-preview activity is likely contributing.\n");
      hinted = true;
    }
    if (textBytes > parsedTotalBytes / 4L) {
      out.append(
          "- String/char[] usage is high; transcript text volume appears significant in the live heap.\n");
      hinted = true;
    }
    if (!hinted) {
      out.append(
          "- No bucket crossed the heuristic thresholds; inspect the top-class list for dominant types.\n");
    }
    out.append('\n');

    out.append("Top IRCafe classes by bytes:\n");
    int[] listed = {0};
    entries.stream()
        .filter(e -> e.className().startsWith("cafe.woden.ircclient."))
        .limit(12)
        .forEach(
            e -> {
              listed[0]++;
              out.append(
                  String.format(
                      Locale.ROOT,
                      "- %s | %s | %d instances%n",
                      e.className(),
                      formatBytes(e.bytes()),
                      e.instances()));
            });
    if (listed[0] == 0) {
      out.append("- No IRCafe classes were present in the top parsed histogram entries.\n");
    }
    return out.toString();
  }

  private static void appendCategoryLine(
      StringBuilder out, String label, long bytes, long parsedTotalBytes) {
    double pct =
        parsedTotalBytes > 0L ? Math.max(0.0d, (bytes * 100.0d) / parsedTotalBytes) : 0.0d;
    out.append("- ")
        .append(label)
        .append(": ")
        .append(formatBytes(bytes))
        .append(" (")
        .append(String.format(Locale.ROOT, "%.1f%%", pct))
        .append(")\n");
  }

  private static long sumBytesMatching(List<HistogramEntry> entries, Predicate<String> predicate) {
    if (entries == null || entries.isEmpty() || predicate == null) return 0L;
    long total = 0L;
    for (HistogramEntry entry : entries) {
      if (entry == null) continue;
      String className = entry.className();
      if (predicate.test(className)) {
        total += Math.max(0L, entry.bytes());
      }
    }
    return total;
  }

  private static List<HistogramEntry> parseHistogramEntries(String histogramText) {
    String raw = Objects.toString(histogramText, "");
    if (raw.isBlank()) return List.of();

    List<HistogramEntry> entries = new ArrayList<>();
    for (String line : raw.split("\\R")) {
      Matcher matcher = HISTOGRAM_ROW.matcher(line);
      if (!matcher.matches()) continue;
      long instances = parseLongSafe(matcher.group(1));
      long bytes = parseLongSafe(matcher.group(2));
      if (instances < 0L || bytes < 0L) continue;
      String className = normalizeHistogramClassName(matcher.group(3));
      if (className.isBlank()) continue;
      entries.add(new HistogramEntry(className, instances, bytes));
    }

    entries.sort(Comparator.comparingLong(HistogramEntry::bytes).reversed());
    return entries;
  }

  private static long parseLongSafe(String raw) {
    try {
      return Long.parseLong(Objects.toString(raw, "").trim());
    } catch (Exception ignored) {
      return -1L;
    }
  }

  private static String normalizeHistogramClassName(String raw) {
    String className = Objects.toString(raw, "").trim();
    if (className.isEmpty()) return "";
    if (className.startsWith("class ")) {
      className = className.substring(6).trim();
    }
    int moduleSuffixIdx = className.indexOf(" (");
    if (moduleSuffixIdx > 0) {
      className = className.substring(0, moduleSuffixIdx).trim();
    }
    return className;
  }

  private static Object invokeDiagnosticCommand(
      MBeanServer server, ObjectName objectName, String operation, String[] args) {
    try {
      return server.invoke(
          objectName,
          operation,
          new Object[] {args == null ? new String[0] : args},
          new String[] {"[Ljava.lang.String;"});
    } catch (Exception ignored) {
      return null;
    }
  }

  private String captureOneShotJfrSnapshot(Path outPath) {
    if (outPath == null) return "JFR snapshot output path is unavailable.";

    try {
      if (outPath.getParent() != null) {
        Files.createDirectories(outPath.getParent());
      }
      Configuration config;
      try {
        config = Configuration.getConfiguration("profile");
      } catch (Throwable ignored) {
        config = Configuration.getConfiguration("default");
      }

      try (Recording recording = new Recording(config)) {
        recording.setToDisk(true);
        recording.setName("IRCafe Memory Diagnostics Snapshot");
        recording.start();
        try {
          Thread.sleep(Math.max(200L, EXPORT_JFR_CAPTURE_DURATION.toMillis()));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
        recording.stop();
        recording.dump(outPath);
      }
      return "Captured JFR snapshot: " + outPath.toAbsolutePath();
    } catch (Throwable t) {
      return "Failed to capture JFR snapshot: " + summarizeThrowable(t);
    }
  }

  private String dumpLiveHeap(Path outPath) {
    if (outPath == null) return "Heap dump output path is unavailable.";
    try {
      if (outPath.getParent() != null) {
        Files.createDirectories(outPath.getParent());
      }
      Files.deleteIfExists(outPath);
      MBeanServer server = ManagementFactory.getPlatformMBeanServer();
      HotSpotDiagnosticMXBean mxBean =
          ManagementFactory.newPlatformMXBeanProxy(
              server, HOTSPOT_DIAGNOSTIC_BEAN, HotSpotDiagnosticMXBean.class);
      mxBean.dumpHeap(outPath.toAbsolutePath().toString(), true);
      return "Captured live heap dump: " + outPath.toAbsolutePath();
    } catch (Throwable t) {
      return "Failed to capture live heap dump: " + summarizeThrowable(t);
    }
  }

  private Path diagnosticsExportDirectory() {
    Path runtimePath = runtimeConfigStore != null ? runtimeConfigStore.runtimeConfigPath() : null;
    Path base = runtimePath != null ? runtimePath.getParent() : null;
    if (base == null) {
      String userHome = Objects.toString(System.getProperty("user.home"), "").trim();
      if (!userHome.isEmpty()) {
        base = Path.of(userHome, ".config", "ircafe");
      }
    }
    if (base == null) {
      base = Path.of(System.getProperty("java.io.tmpdir"), "ircafe");
    }
    return base.resolve("diagnostics").resolve("memory");
  }

  private static void writeTextFile(Path path, String text) throws IOException {
    if (path == null) return;
    if (path.getParent() != null) {
      Files.createDirectories(path.getParent());
    }
    Files.writeString(
        path,
        Objects.toString(text, ""),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE);
  }

  private static void zipDirectory(Path sourceDir, Path zipPath) throws IOException {
    if (sourceDir == null || zipPath == null) return;
    if (zipPath.getParent() != null) {
      Files.createDirectories(zipPath.getParent());
    }

    try (ZipOutputStream zos =
            new ZipOutputStream(
                Files.newOutputStream(
                    zipPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE));
        Stream<Path> walk = Files.walk(sourceDir)) {
      List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
      for (Path file : files) {
        String entryName = sourceDir.relativize(file).toString().replace('\\', '/');
        if (entryName.isBlank()) continue;
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zos);
        zos.closeEntry();
      }
    }
  }

  private static void deleteDirectoryQuietly(Path dir) {
    if (dir == null || !Files.exists(dir)) return;
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
              });
    } catch (IOException ignored) {
    }
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

  private record HistogramEntry(String className, long instances, long bytes) {}

  public record MemoryDiagnosticsExportReport(Path bundlePath, String summary, boolean success) {}
}
