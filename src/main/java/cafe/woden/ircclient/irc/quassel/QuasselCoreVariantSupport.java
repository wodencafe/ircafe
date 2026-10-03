package cafe.woden.ircclient.irc.quassel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Shared decoding and normalization of Quassel QVariant values. */
final class QuasselCoreVariantSupport {
  private QuasselCoreVariantSupport() {}

  static String stripLeadingColon(String raw) {
    String text = Objects.toString(raw, "").trim();
    if (text.startsWith(":")) {
      return text.substring(1).trim();
    }
    return text;
  }

  static boolean containsAnyMapKeysIgnoreCase(Map<?, ?> map, String... keys) {
    if (map == null || map.isEmpty() || keys == null || keys.length == 0) return false;
    for (String key : keys) {
      if (firstMapValueByKeyIgnoreCase(map, key) != null) return true;
    }
    return false;
  }

  static Object firstMapValueByKeyIgnoreCase(Map<?, ?> map, String... keys) {
    if (map == null || map.isEmpty() || keys == null || keys.length == 0) return null;
    for (String wanted : keys) {
      String needle = Objects.toString(wanted, "").trim().toLowerCase(Locale.ROOT);
      if (needle.isEmpty()) continue;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = Objects.toString(entry.getKey(), "").trim().toLowerCase(Locale.ROOT);
        if (needle.equals(key)) {
          return entry.getValue();
        }
      }
    }
    return null;
  }

  static int firstIntFromMapKeys(Map<?, ?> map, String... keys) {
    if (map == null || map.isEmpty()) return -1;
    for (String key : keys) {
      int parsed = tryParseInt(firstMapValueByKeyIgnoreCase(map, key));
      if (parsed >= 0) return parsed;
    }
    return -1;
  }

  static long firstLongFromMapKeys(Map<?, ?> map, String... keys) {
    if (map == null || map.isEmpty()) return -1L;
    for (String key : keys) {
      long parsed = tryParseLong(firstMapValueByKeyIgnoreCase(map, key));
      if (parsed > 0L) return parsed;
    }
    return -1L;
  }

  static String firstNonBlank(Object... values) {
    if (values == null || values.length == 0) return "";
    for (Object value : values) {
      String text = Objects.toString(value, "").trim();
      if (!text.isEmpty()) {
        return text;
      }
    }
    return "";
  }

  static int tryParseInt(Object raw) {
    if (raw instanceof Number n) {
      return n.intValue();
    }
    String token = Objects.toString(raw, "").trim();
    if (token.isEmpty()) return -1;
    try {
      return Integer.parseInt(token);
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  static long tryParseLong(Object raw) {
    if (raw instanceof Number n) {
      return n.longValue();
    }
    String token = Objects.toString(raw, "").trim();
    if (token.isEmpty()) return -1L;
    try {
      return Long.parseLong(token);
    } catch (NumberFormatException ignored) {
      return -1L;
    }
  }

  static Boolean parseBoolean(Object raw) {
    if (raw instanceof Boolean b) {
      return b;
    }
    if (raw instanceof Number n) {
      return n.intValue() != 0;
    }
    String token = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (token.isEmpty()) return null;
    return switch (token) {
      case "1", "true", "yes", "on" -> Boolean.TRUE;
      case "0", "false", "no", "off" -> Boolean.FALSE;
      default -> null;
    };
  }

  static String firstNonBlankMapValue(Map<?, ?> map, String... keys) {
    if (map == null || keys == null) return "";
    for (String key : keys) {
      String value = Objects.toString(mapValueIgnoreCase(map, key), "").trim();
      if (!value.isEmpty()) return value;
    }
    return "";
  }

  static Object mapValueIgnoreCase(Map<?, ?> map, String wantedKey) {
    if (map == null || wantedKey == null || wantedKey.isBlank()) return null;
    String wanted = wantedKey.trim();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry == null) continue;
      String key = Objects.toString(entry.getKey(), "").trim();
      if (key.equalsIgnoreCase(wanted)) return entry.getValue();
    }
    return null;
  }

  static Map<String, Object> normalizeObjectMap(Map<?, ?> map) {
    if (map == null || map.isEmpty()) return Map.of();
    LinkedHashMap<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry == null) continue;
      String key = Objects.toString(entry.getKey(), "").trim();
      if (key.isEmpty()) continue;
      out.put(key, entry.getValue());
    }
    if (out.isEmpty()) return Map.of();
    return Collections.unmodifiableMap(out);
  }

  static boolean containsCrlf(String value) {
    String v = Objects.toString(value, "");
    return v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
  }
}
