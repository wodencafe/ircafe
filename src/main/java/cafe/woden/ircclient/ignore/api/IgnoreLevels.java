package cafe.woden.ircclient.ignore.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Shared constants/helpers for irssi-style ignore levels. */
public final class IgnoreLevels {

  private IgnoreLevels() {}

  public static final Set<String> KNOWN =
      Set.of(
          "ALL",
          "MSGS",
          "PUBLIC",
          "NOTICES",
          "CTCPS",
          "ACTIONS",
          "JOINS",
          "PARTS",
          "QUITS",
          "NICKS",
          "TOPICS",
          "WALLOPS",
          "INVITES",
          "MODES",
          "DCC",
          "DCCMSGS",
          "CLIENTCRAP",
          "CLIENTNOTICE",
          "CLIENTERRORS",
          "HILIGHT",
          "NOHILIGHT",
          "CRAP");

  /** Normalize user/config-provided levels to uppercase known values; defaults to {@code ALL}. */
  public static List<String> normalizeConfigured(Collection<String> levels) {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    if (levels != null) {
      for (String raw : levels) {
        String v = normalizeLevelToken(raw);
        if (v.isEmpty()) continue;
        out.add(v);
      }
    }
    if (out.isEmpty()) out.add("ALL");
    return List.copyOf(out);
  }

  /** Normalize event levels; unknown/blank values are dropped. */
  public static List<String> normalizeEvent(Collection<String> levels) {
    if (levels == null || levels.isEmpty()) return List.of();
    ArrayList<String> out = new ArrayList<>();
    for (String raw : levels) {
      String v = normalizeLevelToken(raw);
      if (!v.isEmpty()) out.add(v);
    }
    return List.copyOf(out);
  }

  /** Returns true when configured ignore levels match the inbound event levels. */
  public static boolean matches(
      Collection<String> configuredLevels, Collection<String> eventLevels) {
    List<String> cfg = normalizeConfigured(configuredLevels);
    List<String> ev = normalizeEvent(eventLevels);
    if (ev.isEmpty()) return true;
    if (cfg.contains("ALL")) return true;
    for (String lvl : ev) {
      if (cfg.contains(lvl)) return true;
    }
    return false;
  }

  private static String normalizeLevelToken(String raw) {
    String v = Objects.toString(raw, "").trim().toUpperCase(Locale.ROOT);
    if (v.isEmpty()) return "";
    while (v.startsWith("+") || v.startsWith("-")) {
      v = v.substring(1).trim();
    }
    if (v.isEmpty()) return "";
    if ("*".equals(v)) v = "ALL";
    return KNOWN.contains(v) ? v : "";
  }
}
