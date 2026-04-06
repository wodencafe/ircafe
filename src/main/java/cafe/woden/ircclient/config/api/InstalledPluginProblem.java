package cafe.woden.ircclient.config.api;

import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/** Describes a plugin discovery or loading problem observed at startup. */
@ValueObject
public record InstalledPluginProblem(String level, String summary, String details) {

  public InstalledPluginProblem {
    level = normalize(level, "ERROR").toUpperCase(java.util.Locale.ROOT);
    summary = normalize(summary, "Plugin problem");
    details = normalize(details, "");
  }

  private static String normalize(String raw, String fallback) {
    String normalized = Objects.toString(raw, "").trim();
    return normalized.isEmpty() ? fallback : normalized;
  }
}
