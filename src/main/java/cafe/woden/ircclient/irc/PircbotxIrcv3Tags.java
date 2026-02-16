package cafe.woden.ircclient.irc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared IRCv3 message-tag parsing helpers.
 *
 * <p>Parses tag maps from either PircBotX events ({@code getTags()}) or raw IRC lines
 * ({@code @k=v;... :prefix COMMAND ...}), normalizing keys and preserving insertion order.
 */
final class PircbotxIrcv3Tags {

  private PircbotxIrcv3Tags() {}

  static Map<String, String> fromEvent(Object pircbotxEvent) {
    if (pircbotxEvent == null) return Map.of();

    Object tags = reflectCall(pircbotxEvent, "getTags");
    if (tags instanceof Map<?, ?> map && !map.isEmpty()) {
      Map<String, String> parsed = normalizeTagMap(map, false);
      if (!parsed.isEmpty()) return parsed;
    }

    Object raw = reflectCall(pircbotxEvent, "getRawLine");
    if (raw == null) raw = reflectCall(pircbotxEvent, "getLine");
    if (raw == null) raw = reflectCall(pircbotxEvent, "getRaw");
    return fromRawLine(raw == null ? null : String.valueOf(raw));
  }

  static Map<String, String> fromRawLine(String rawLine) {
    if (rawLine == null) return Map.of();
    String s = rawLine.trim();
    if (s.isEmpty() || s.charAt(0) != '@') return Map.of();

    int sp = s.indexOf(' ');
    if (sp <= 1) return Map.of();

    String tagSection = s.substring(1, sp);
    if (tagSection.isEmpty()) return Map.of();

    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    int idx = 0;
    while (idx < tagSection.length()) {
      int next = tagSection.indexOf(';', idx);
      if (next < 0) next = tagSection.length();
      String part = tagSection.substring(idx, next);
      idx = next + 1;

      if (part.isEmpty()) continue;
      int eq = part.indexOf('=');
      String key = (eq >= 0) ? part.substring(0, eq) : part;
      String normalizedKey = normalizeTagKey(key);
      if (normalizedKey.isEmpty()) continue;

      String value = (eq >= 0) ? part.substring(eq + 1) : "";
      out.put(normalizedKey, unescapeTagValue(value));
    }

    if (out.isEmpty()) return Map.of();
    return Collections.unmodifiableMap(out);
  }

  static String firstTagValue(Map<String, String> tags, String... keys) {
    if (tags == null || tags.isEmpty() || keys == null) return "";
    for (String key : keys) {
      String want = normalizeTagKey(key);
      if (want.isEmpty()) continue;

      for (Map.Entry<String, String> e : tags.entrySet()) {
        String got = normalizeTagKey(e.getKey());
        if (!want.equals(got)) continue;

        String value = (e.getValue() == null) ? "" : e.getValue().trim();
        if (!value.isEmpty()) return value;
      }
    }
    return "";
  }

  private static Map<String, String> normalizeTagMap(Map<?, ?> raw, boolean unescapeValues) {
    if (raw == null || raw.isEmpty()) return Map.of();

    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> e : raw.entrySet()) {
      String key = normalizeTagKey(e.getKey());
      if (key.isEmpty()) continue;

      String value = (e.getValue() == null) ? "" : String.valueOf(e.getValue());
      if (unescapeValues) value = unescapeTagValue(value);
      out.put(key, value);
    }

    if (out.isEmpty()) return Map.of();
    return Collections.unmodifiableMap(out);
  }

  private static String normalizeTagKey(Object rawKey) {
    String k = (rawKey == null) ? "" : String.valueOf(rawKey).trim();
    if (k.startsWith("@")) k = k.substring(1).trim();
    if (k.startsWith("+")) k = k.substring(1).trim();
    if (k.isEmpty()) return "";
    return k.toLowerCase(Locale.ROOT);
  }

  private static String unescapeTagValue(String raw) {
    if (raw == null || raw.isEmpty() || raw.indexOf('\\') < 0) return raw == null ? "" : raw;
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
