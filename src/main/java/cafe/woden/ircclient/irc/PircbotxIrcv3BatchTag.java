package cafe.woden.ircclient.irc;

import java.util.Optional;

final class PircbotxIrcv3BatchTag {

  private PircbotxIrcv3BatchTag() {}

  static Optional<String> fromEvent(Object pircbotxEvent) {
    if (pircbotxEvent == null) return Optional.empty();

    Object tags = reflectCall(pircbotxEvent, "getTags");
    if (tags instanceof java.util.Map<?, ?> map && !map.isEmpty()) {
      Object v = map.get("batch");
      if (v == null) v = map.get("@batch");
      if (v != null) {
        String s = String.valueOf(v).trim();
        if (!s.isEmpty()) return Optional.of(s);
      }
    }

    Object raw = reflectCall(pircbotxEvent, "getRawLine");
    if (raw == null) raw = reflectCall(pircbotxEvent, "getLine");
    if (raw == null) raw = reflectCall(pircbotxEvent, "getRaw");
    if (raw != null) {
      return fromRawLine(String.valueOf(raw));
    }

    return Optional.empty();
  }

  /** Parse {@code @batch=} from a raw IRC line. */
  static Optional<String> fromRawLine(String rawLine) {
    if (rawLine == null) return Optional.empty();
    String s = rawLine.trim();
    if (s.isEmpty() || s.charAt(0) != '@') return Optional.empty();

    int sp = s.indexOf(' ');
    if (sp <= 0) return Optional.empty();

    String tagSection = s.substring(1, sp); // exclude leading '@'
    if (tagSection.isEmpty()) return Optional.empty();

    int idx = 0;
    while (idx < tagSection.length()) {
      int next = tagSection.indexOf(';', idx);
      if (next < 0) next = tagSection.length();
      String part = tagSection.substring(idx, next);
      idx = next + 1;

      if (part.isEmpty()) continue;
      int eq = part.indexOf('=');
      String key = (eq >= 0) ? part.substring(0, eq) : part;
      if (!"batch".equals(key)) continue;

      String val = (eq >= 0) ? part.substring(eq + 1) : "";
      val = unescapeTagValue(val);
      val = val == null ? "" : val.trim();
      if (val.isEmpty()) return Optional.empty();
      return Optional.of(val);
    }

    return Optional.empty();
  }

  private static String unescapeTagValue(String raw) {
    if (raw == null || raw.isEmpty() || raw.indexOf('\\') < 0) return raw;
    StringBuilder sb = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c != '\\') {
        sb.append(c);
        continue;
      }
      if (i + 1 >= raw.length()) break;
      char n = raw.charAt(++i);
      switch (n) {
        case ':' -> sb.append(';');
        case 's' -> sb.append(' ');
        case 'r' -> sb.append('\r');
        case 'n' -> sb.append('\n');
        case '\\' -> sb.append('\\');
        default -> sb.append(n);
      }
    }
    return sb.toString();
  }

  private static Object reflectCall(Object target, String method) {
    if (target == null || method == null) return null;
    try {
      java.lang.reflect.Method m = target.getClass().getMethod(method);
      return m.invoke(target);
    } catch (Exception ignored) {
      return null;
    }
  }
}
