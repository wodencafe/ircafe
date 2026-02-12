package cafe.woden.ircclient.irc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Utility parsers for ZNC-related heuristics. */
final class PircbotxZncParsers {

  private PircbotxZncParsers() {}

  static boolean seemsZncCap(String cap) {
    if (cap == null) return false;
    String c = cap.trim().toLowerCase(Locale.ROOT);
    // CAP ACK can include cap removals (prefixed with '-'); ignore those.
    if (c.startsWith("-")) c = c.substring(1);
    return c.startsWith("znc.in/");
  }

  /**
   * Heuristic detection: RPL_MYINFO (004) on ZNC typically includes a version token containing "ZNC".
   */
  static boolean seemsRpl004Znc(String rawLine) {
    if (rawLine == null) return false;
    String line = rawLine.trim();
    if (line.isEmpty()) return false;

    String[] toks = line.split("\\s+");
    int idx = -1;
    for (int i = 0; i < toks.length; i++) {
      if ("004".equals(toks[i])) {
        idx = i;
        break;
      }
    }
    if (idx < 0) return false;

    // Expect: <prefix> 004 <nick> <servername> <version> ...
    int versionIdx = idx + 3;
    if (versionIdx >= toks.length) return false;

    String version = toks[versionIdx];
    if (version == null) return false;
    String v = version.toLowerCase(Locale.ROOT);
    return v.contains("znc");
  }

  /** Parsed row for {@code *status ListNetworks}. */
  static final class ParsedListNetworksRow {
    final String name;
    final Boolean onIrc;

    ParsedListNetworksRow(String name, Boolean onIrc) {
      this.name = name;
      this.onIrc = onIrc;
    }
  }

  /**
   * Best-effort parse of a single line of {@code *status ListNetworks} output.
   *
   * <p>ZNC outputs a table-like format in most clients, but the exact columns can vary.
   * We keep this parser intentionally tolerant: extract the first column as the network name,
   * and (optionally) infer the second column as a yes/no "On IRC" flag.
   */
  static ParsedListNetworksRow parseListNetworksRow(String messageText) {
    if (messageText == null) return null;
    String s = messageText.trim();
    if (s.isEmpty()) return null;

    // Common table borders/header lines.
    if ((s.startsWith("+") && s.endsWith("+") && s.indexOf('-') >= 0)
        || s.startsWith("--")
        || s.toLowerCase(Locale.ROOT).contains("listnetworks")) {
      return null;
    }

    // Most common format: a pipe-delimited table.
    if (s.startsWith("|") && s.contains("|")) {
      String[] parts = s.split("\\|");
      List<String> cells = new ArrayList<>();
      for (String p : parts) {
        if (p == null) continue;
        String c = p.trim();
        if (!c.isEmpty()) cells.add(c);
      }
      if (cells.isEmpty()) return null;

      String first = cells.get(0);
      if (first.equalsIgnoreCase("network") || first.equalsIgnoreCase("name")) {
        return null; // header row
      }

      String name = first.trim();
      if (name.isEmpty()) return null;

      Boolean onIrc = null;
      if (cells.size() >= 2) {
        onIrc = parseYesNo(cells.get(1));
      }

      return new ParsedListNetworksRow(name, onIrc);
    }

    // Fallback: try to spot a leading token that looks like a network name.
    // e.g. "libera yes" or "libera connected".
    String[] toks = s.split("\\s+");
    if (toks.length >= 1) {
      String name = toks[0].trim();
      if (!name.isEmpty() && !name.startsWith("[") && !name.startsWith("(")) {
        Boolean onIrc = (toks.length >= 2) ? parseYesNo(toks[1]) : null;
        return new ParsedListNetworksRow(name, onIrc);
      }
    }

    return null;
  }

  static boolean looksLikeListNetworksDoneLine(String messageText) {
    if (messageText == null) return false;
    String s = messageText.trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) return false;
    // Various ZNC modules end with a friendly "done" line; keep it broad.
    return s.contains("end") && s.contains("list")
        || s.contains("done") && s.contains("list")
        || s.contains("use /znc") && s.contains("listnetworks")
        || s.contains("listnetworks") && s.contains("complete");
  }

  private static Boolean parseYesNo(String v) {
    if (v == null) return null;
    String s = v.trim().toLowerCase(Locale.ROOT);
    if (s.isEmpty()) return null;
    if (s.equals("yes") || s.equals("y") || s.equals("on") || s.equals("true") || s.equals("1")) return Boolean.TRUE;
    if (s.equals("no") || s.equals("n") || s.equals("off") || s.equals("false") || s.equals("0")) return Boolean.FALSE;
    // Sometimes columns are "connected" / "disconnected".
    if (s.contains("connect") && !s.contains("dis")) return Boolean.TRUE;
    if (s.contains("disconnect")) return Boolean.FALSE;
    return null;
  }
}
