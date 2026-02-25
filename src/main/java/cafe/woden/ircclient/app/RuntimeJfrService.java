package cafe.woden.ircclient.app;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Service;

/** Starts a best-effort always-on JFR recording and exposes snapshot/status helpers for the UI. */
@Service
@Lazy(false)
@ApplicationLayer
@NamedInterface("diagnostics")
public class RuntimeJfrService {
  private static final Logger log = LoggerFactory.getLogger(RuntimeJfrService.class);
  private static final long DEFAULT_MAX_SIZE_BYTES = 256L * 1024L * 1024L;
  private static final Duration DEFAULT_MAX_AGE = Duration.ofHours(4);
  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneId.systemDefault());

  private final RuntimeConfigStore runtimeConfigStore;
  private Recording recording;
  private boolean unavailable;
  private String unavailableReason;
  private Path latestSnapshot;

  public RuntimeJfrService(RuntimeConfigStore runtimeConfigStore) {
    this.runtimeConfigStore = runtimeConfigStore;
  }

  @PostConstruct
  public void start() {
    ensureStarted();
  }

  @PreDestroy
  public synchronized void stop() {
    if (recording == null) return;
    try {
      recording.stop();
    } catch (Exception ignored) {
    }
    try {
      recording.close();
    } catch (Exception ignored) {
    } finally {
      recording = null;
    }
  }

  public synchronized String statusReport() {
    ensureStarted();
    if (unavailable) {
      return "JFR is unavailable on this runtime.\n\n"
          + (unavailableReason == null ? "" : unavailableReason);
    }
    if (recording == null) {
      return "JFR recording is not active.";
    }

    StringBuilder out = new StringBuilder(512);
    out.append("Always-on JFR: active\n");
    out.append("Recording name: ").append(recording.getName()).append('\n');
    out.append("State: ").append(recording.getState()).append('\n');
    out.append("To disk: ").append(recording.isToDisk()).append('\n');
    out.append("Max age: ").append(DEFAULT_MAX_AGE).append('\n');
    out.append("Max size: ").append(DEFAULT_MAX_SIZE_BYTES / (1024L * 1024L)).append(" MiB\n");
    out.append("Latest snapshot: ")
        .append(latestSnapshot != null ? latestSnapshot.toAbsolutePath() : "(none)")
        .append('\n');
    return out.toString();
  }

  public synchronized SnapshotReport captureSnapshot() {
    ensureStarted();
    if (unavailable || recording == null) {
      return new SnapshotReport(null, statusReport());
    }
    try {
      Path dir = snapshotDirectory();
      Files.createDirectories(dir);
      Path out = dir.resolve("ircafe-memory-" + TS_FMT.format(Instant.now()) + ".jfr");
      recording.dump(out);
      latestSnapshot = out;
      String summary = summarize(out);
      return new SnapshotReport(out, summary);
    } catch (Exception e) {
      log.warn("[ircafe] Failed to dump JFR snapshot", e);
      return new SnapshotReport(
          null, "Failed to capture JFR snapshot.\n\n" + Objects.toString(e.getMessage(), ""));
    }
  }

  private synchronized void ensureStarted() {
    if (recording != null || unavailable) return;
    try {
      Configuration cfg = Configuration.getConfiguration("default");
      Recording r = new Recording(cfg);
      r.setName("IRCafe Always-On Diagnostics");
      r.setToDisk(true);
      r.setMaxAge(DEFAULT_MAX_AGE);
      r.setMaxSize(DEFAULT_MAX_SIZE_BYTES);
      r.start();
      recording = r;
    } catch (Throwable t) {
      unavailable = true;
      unavailableReason = Objects.toString(t.getMessage(), t.getClass().getSimpleName());
      log.warn("[ircafe] Could not start always-on JFR recording", t);
    }
  }

  private Path snapshotDirectory() {
    Path cfg = runtimeConfigStore != null ? runtimeConfigStore.runtimeConfigPath() : null;
    Path base = cfg != null ? cfg.getParent() : null;
    if (base == null) {
      base = Path.of(System.getProperty("java.io.tmpdir"), "ircafe");
    }
    return base.resolve("diagnostics").resolve("jfr");
  }

  private String summarize(Path snapshot) {
    long totalEvents = 0L;
    long oldObjectSamples = 0L;
    long gcPauseEvents = 0L;
    double gcPauseMillis = 0d;
    Map<String, Long> eventCounts = new LinkedHashMap<>();

    try (RecordingFile rf = new RecordingFile(snapshot)) {
      while (rf.hasMoreEvents()) {
        RecordedEvent ev = rf.readEvent();
        if (ev == null) continue;
        totalEvents++;
        String name = ev.getEventType() != null ? ev.getEventType().getName() : "unknown";
        eventCounts.merge(name, 1L, Long::sum);
        if (name.endsWith("OldObjectSample")) {
          oldObjectSamples++;
        }
        if (name.endsWith("GCPhasePause")) {
          gcPauseEvents++;
          try {
            Duration d = ev.getDuration("duration");
            if (d != null) gcPauseMillis += d.toMillis();
          } catch (Exception ignored) {
          }
        }
      }
    } catch (Exception e) {
      return "Snapshot captured at: "
          + snapshot.toAbsolutePath()
          + "\n\nCould not parse snapshot: "
          + Objects.toString(e.getMessage(), "");
    }

    StringBuilder out = new StringBuilder(2048);
    out.append("Snapshot: ").append(snapshot.toAbsolutePath()).append('\n');
    out.append("Total events: ").append(totalEvents).append('\n');
    out.append("Old object samples: ").append(oldObjectSamples).append('\n');
    out.append("GC pause events: ").append(gcPauseEvents).append('\n');
    out.append("Approx GC pause total: ")
        .append(String.format(java.util.Locale.ROOT, "%.1f ms", gcPauseMillis))
        .append('\n');
    out.append('\n').append("Top event types:\n");

    eventCounts.entrySet().stream()
        .sorted(
            Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                .reversed()
                .thenComparing(Map.Entry::getKey))
        .limit(12)
        .forEach(
            e ->
                out.append("  ").append(e.getValue()).append("  ").append(e.getKey()).append('\n'));

    return out.toString();
  }

  public record SnapshotReport(Path snapshotPath, String summary) {}
}
