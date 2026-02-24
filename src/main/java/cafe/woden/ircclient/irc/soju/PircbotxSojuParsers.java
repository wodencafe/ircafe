package cafe.woden.ircclient.irc.soju;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small parsing helpers for soju bouncer extensions. */
public final class PircbotxSojuParsers {
  private PircbotxSojuParsers() {}

  /**
   * Parsed form of a {@code BOUNCER NETWORK} response.
   *
   * <p>Example line: {@code :bouncer.example BOUNCER NETWORK 123 name=libera;...}
   */
  public record ParsedBouncerNetwork(String netId, String name, Map<String, String> attrs) {}

  /**
   * Parse RPL_ISUPPORT (005) for soju's BOUNCER_NETID token.
   *
   * <p>Example token: {@code BOUNCER_NETID=123}
   *
   * @return the netid value, or null if not present
   */
  public static String parseRpl005BouncerNetId(String rawLine) {
    if (rawLine == null || rawLine.isBlank()) return null;
    String s = rawLine;

    // Normalize for case-insensitive match while preserving the original substring boundaries.
    String upper = s.toUpperCase(Locale.ROOT);
    String needle = "BOUNCER_NETID=";
    int idx = upper.indexOf(needle);
    if (idx < 0) return null;

    int start = idx + needle.length();
    if (start >= s.length()) return null;

    int end = s.indexOf(' ', start);
    String v = (end < 0) ? s.substring(start) : s.substring(start, end);
    if (v == null) return null;
    v = v.trim();
    if (v.startsWith(":")) v = v.substring(1).trim();
    return v.isEmpty() ? null : v;
  }

  /**
   * Parse soju's bouncer network list responses.
   *
   * <p>We expect lines of the form: {@code BOUNCER NETWORK <netid> <attrs>} where attrs is a
   * semicolon-delimited list of key=value. Some bouncers may send the attrs as a trailing parameter
   * (after {@code :}).
   *
   * @param rawLine normalized IRC line (prefix ok)
   * @return parsed network info, or null if the line is not a network entry
   */
  public static ParsedBouncerNetwork parseBouncerNetworkLine(String rawLine) {
    if (rawLine == null || rawLine.isBlank()) return null;
    String s = rawLine.trim();

    // Strip optional prefix (":nick!user@host ")
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 1 && sp + 1 < s.length()) {
        s = s.substring(sp + 1).trim();
      }
    }

    String trailing = null;
    int idx = s.indexOf(" :");
    if (idx >= 0) {
      trailing = s.substring(idx + 2);
      s = s.substring(0, idx).trim();
    }

    if (s.isEmpty()) return null;

    String[] parts = s.split("\\s+");
    if (parts.length < 3) return null;

    if (!"BOUNCER".equalsIgnoreCase(parts[0])) return null;
    if (!"NETWORK".equalsIgnoreCase(parts[1])) return null;

    String netId = parts[2] == null ? "" : parts[2].trim();
    if (netId.isEmpty()) return null;

    String attrsRaw = null;
    if (parts.length >= 4) {
      StringBuilder sb = new StringBuilder();
      for (int i = 3; i < parts.length; i++) {
        if (sb.length() > 0) sb.append(' ');
        sb.append(parts[i]);
      }
      attrsRaw = sb.toString().trim();
    }
    if ((attrsRaw == null || attrsRaw.isBlank()) && trailing != null && !trailing.isBlank()) {
      attrsRaw = trailing.trim();
    }
    if (attrsRaw == null) attrsRaw = "";

    Map<String, String> attrs = parseAttrs(attrsRaw);
    String name = attrs.getOrDefault("name", "").trim();
    if (name.isEmpty()) name = "net-" + netId;
    name = sanitizeNetworkName(name);

    return new ParsedBouncerNetwork(netId, name, attrs);
  }

  private static Map<String, String> parseAttrs(String attrsRaw) {
    if (attrsRaw == null || attrsRaw.isBlank()) return Map.of();
    String s = attrsRaw.trim();
    Map<String, String> out = new HashMap<>();
    for (String part : s.split(";")) {
      if (part == null) continue;
      String p = part.trim();
      if (p.isEmpty()) continue;
      int eq = p.indexOf('=');
      if (eq < 0) {
        out.putIfAbsent(p, "");
        continue;
      }
      String k = p.substring(0, eq).trim();
      String v = p.substring(eq + 1).trim();
      if (!k.isEmpty()) {
        out.put(k, v);
      }
    }
    return out.isEmpty() ? Map.of() : out;
  }

  /**
   * Soju network names may contain arbitrary characters; keep the client-side name safe for use in
   * usernames (user/<network>) by replacing non [A-Za-z0-9._-] with underscore.
   */
  public static String sanitizeNetworkName(String name) {
    if (name == null) return "";
    StringBuilder sb = new StringBuilder(name.length());
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if ((c >= 'a' && c <= 'z')
          || (c >= 'A' && c <= 'Z')
          || (c >= '0' && c <= '9')
          || c == '.'
          || c == '_'
          || c == '-') {
        sb.append(c);
      } else {
        sb.append('_');
      }
    }
    String v = sb.toString();
    return v.isBlank() ? "" : v;
  }
}
