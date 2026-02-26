package cafe.woden.ircclient.diagnostics;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** Starts a best-effort always-on JFR recording and exposes snapshot/status helpers for the UI. */
@Service
@Lazy(false)
@ApplicationLayer
public class RuntimeJfrService {
  private static final Logger log = LoggerFactory.getLogger(RuntimeJfrService.class);
  private static final long DEFAULT_MAX_SIZE_BYTES = 256L * 1024L * 1024L;
  private static final Duration DEFAULT_MAX_AGE = Duration.ofHours(4);
  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneId.systemDefault());

  private final RuntimeConfigStore runtimeConfigStore;
  private final JfrSnapshotSummarizer snapshotSummarizer;
  private Recording recording;
  private boolean unavailable;
  private String unavailableReason;
  private Path latestSnapshot;

  public RuntimeJfrService(
      RuntimeConfigStore runtimeConfigStore, JfrSnapshotSummarizer snapshotSummarizer) {
    this.runtimeConfigStore = runtimeConfigStore;
    this.snapshotSummarizer = snapshotSummarizer;
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
      String summary = snapshotSummarizer.summarize(out);
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

  public record SnapshotReport(Path snapshotPath, String summary) {}
}
