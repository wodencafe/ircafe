package cafe.woden.ircclient.bouncer;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.jmolecules.ddd.annotation.ValueObject;

/** Immutable metadata describing a bouncer backend family. */
@ValueObject
public record BouncerBackendDescriptor(
    String backendId,
    String ephemeralIdPrefix,
    String networksGroupLabel,
    Set<String> capabilityHints) {

  public BouncerBackendDescriptor {
    backendId = normalizeLower(backendId);
    if (backendId == null) {
      throw new IllegalArgumentException("backendId is required");
    }

    String prefix = normalize(ephemeralIdPrefix);
    if (prefix == null) {
      prefix = backendId + ":";
    }
    if (!prefix.endsWith(":")) {
      prefix = prefix + ":";
    }
    ephemeralIdPrefix = prefix;

    String label = normalize(networksGroupLabel);
    networksGroupLabel = label == null ? backendId + " Networks" : label;

    capabilityHints = normalizeHints(capabilityHints);
  }

  private static Set<String> normalizeHints(Set<String> hints) {
    if (hints == null || hints.isEmpty()) return Set.of();
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String hint : hints) {
      String normalized = normalizeLower(hint);
      if (normalized == null) continue;
      out.add(normalized);
    }
    return out.isEmpty() ? Set.of() : Set.copyOf(out);
  }

  private static String normalize(String value) {
    String v = Objects.toString(value, "").trim();
    return v.isEmpty() ? null : v;
  }

  private static String normalizeLower(String value) {
    String v = normalize(value);
    return v == null ? null : v.toLowerCase(Locale.ROOT);
  }
}
