package cafe.woden.ircclient.diagnostics;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Summarizes captured JFR snapshot files for diagnostics UI display. */
@Component
@ApplicationLayer
public class JfrSnapshotSummarizer {

  public String summarize(Path snapshot) {
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
}
