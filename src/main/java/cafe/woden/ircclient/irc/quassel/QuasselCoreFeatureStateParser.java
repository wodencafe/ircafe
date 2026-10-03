package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstMapValueByKeyIgnoreCase;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.parseBoolean;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.stripLeadingColon;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.tryParseLong;

import cafe.woden.ircclient.irc.ircv3.Ircv3MultilineSupport;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Decodes capability, multiline, and monitor state independently of the transport. */
final class QuasselCoreFeatureStateParser {
  private QuasselCoreFeatureStateParser() {}

  static Set<String> extractCapabilityTokens(Map<?, ?> map, String... keys) {
    if (map == null || map.isEmpty() || keys == null || keys.length == 0) return Set.of();
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String key : keys) {
      Object value = firstMapValueByKeyIgnoreCase(map, key);
      if (value == null) continue;
      collectCapabilityTokens(value, out);
    }
    if (out.isEmpty()) return Set.of();
    return Collections.unmodifiableSet(out);
  }

  static MultilineLimitState extractMultilineLimitsFromStateMap(Map<?, ?> stateMap) {
    if (stateMap == null || stateMap.isEmpty()) return null;
    MultilineLimitCollector out = new MultilineLimitCollector();
    collectMultilineLimitsFromRaw(
        firstMapValueByKeyIgnoreCase(
            stateMap, "capsEnabled", "capsenabled", "enabledCaps", "enabledcaps"),
        out);
    collectMultilineLimitsFromRaw(
        firstMapValueByKeyIgnoreCase(stateMap, "caps", "capabilities", "availableCaps"), out);
    return out.toStateOrNull();
  }

  private static void collectMultilineLimitsFromRaw(Object raw, MultilineLimitCollector out) {
    if (raw == null || out == null) return;
    if (raw instanceof byte[] bytes) {
      collectMultilineLimitsFromRaw(
          new String(bytes, java.nio.charset.StandardCharsets.UTF_8), out);
      return;
    }
    if (raw instanceof String text) {
      for (String token : text.split("\\s+")) {
        collectMultilineLimitsFromToken(token, out);
      }
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectMultilineLimitsFromRaw(value, out);
      }
      return;
    }
    if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) return;

    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String capKey = canonicalCapabilityToken(entry.getKey());
      Object value = entry.getValue();
      if (Ircv3MultilineSupport.isMultilineCapability(capKey)) {
        collectMultilineLimitsFromToken(entry.getKey(), out);
        if (!isCapabilityExplicitlyDisabled(value)) {
          collectMultilineLimitParams(value, out);
        }
      }
      collectMultilineLimitsFromRaw(value, out);
    }
  }

  private static void collectMultilineLimitsFromToken(Object token, MultilineLimitCollector out) {
    if (out == null) return;
    String raw = Objects.toString(token, "").trim();
    if (raw.isEmpty()) return;
    String cleaned = stripLeadingColon(raw);
    if (cleaned.isEmpty()) return;
    boolean disabled = cleaned.startsWith("-");
    String cap = canonicalCapabilityToken(cleaned);
    if (!Ircv3MultilineSupport.isMultilineCapability(cap) || disabled) return;

    int eq = cleaned.indexOf('=');
    if (eq <= 0 || eq >= cleaned.length() - 1) return;
    collectMultilineLimitParams(cleaned.substring(eq + 1), out);
  }

  private static void collectMultilineLimitParams(Object raw, MultilineLimitCollector out) {
    if (raw == null || out == null) return;
    if (raw instanceof byte[] bytes) {
      collectMultilineLimitParams(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), out);
      return;
    }
    if (raw instanceof Number n) {
      long value = n.longValue();
      if (value >= 0L) out.observeLines(value);
      return;
    }
    if (raw instanceof String text) {
      String params = Objects.toString(text, "").trim();
      if (params.isEmpty()) return;
      Ircv3MultilineSupport.LimitParams parsed = Ircv3MultilineSupport.parseLimitParams(params);
      long maxBytes = parsed.maxBytes();
      long maxLines = parsed.maxLines();
      if (maxBytes >= 0L) out.observeBytes(maxBytes);
      if (maxLines >= 0L) out.observeLines(maxLines);
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectMultilineLimitParams(value, out);
      }
      return;
    }
    if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) return;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = Objects.toString(entry.getKey(), "").trim().toLowerCase(Locale.ROOT);
      Object value = entry.getValue();
      if (key.isEmpty()) {
        collectMultilineLimitParams(value, out);
        continue;
      }
      if ("max-bytes".equals(key)
          || "maxbytes".equals(key)
          || "max_bytes".equals(key)
          || "bytes".equals(key)) {
        long parsed = tryParseLong(value);
        if (parsed >= 0L) out.observeBytes(parsed);
        continue;
      }
      if ("max-lines".equals(key)
          || "maxlines".equals(key)
          || "max_lines".equals(key)
          || "lines".equals(key)) {
        long parsed = tryParseLong(value);
        if (parsed >= 0L) out.observeLines(parsed);
        continue;
      }
      collectMultilineLimitParams(value, out);
    }
  }

  static MonitorSupportState extractMonitorSupportFromStateMap(Map<?, ?> stateMap) {
    if (stateMap == null || stateMap.isEmpty()) return null;
    MonitorSupportCollector out = new MonitorSupportCollector();
    collectMonitorSupportFromRaw(stateMap, out);
    return out.toStateOrNull();
  }

  private static void collectMonitorSupportFromRaw(Object raw, MonitorSupportCollector out) {
    if (raw == null || out == null) return;
    if (raw instanceof byte[] bytes) {
      collectMonitorSupportFromRaw(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), out);
      return;
    }
    if (raw instanceof String text) {
      parseMonitorTokensFromText(text, out);
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectMonitorSupportFromRaw(value, out);
      }
      return;
    }
    if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) return;

    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String key = Objects.toString(entry.getKey(), "").trim();
      String lowerKey = key.toLowerCase(Locale.ROOT);
      Object value = entry.getValue();
      parseMonitorToken(key, out);

      if (lowerKey.contains("monitor")) {
        Boolean availability = parseBoolean(value);
        if (availability != null) {
          out.observeAvailability(availability.booleanValue());
        }
        long limit = tryParseLong(value);
        if (limit >= 0L) {
          out.observeLimit(limit);
        }
      }

      if ("monitor".equals(canonicalCapabilityToken(key))
          && !isCapabilityExplicitlyDisabled(value)) {
        out.observeAvailability(true);
      }
      collectMonitorSupportFromRaw(value, out);
    }
  }

  private static void parseMonitorTokensFromText(String raw, MonitorSupportCollector out) {
    if (out == null) return;
    String text = Objects.toString(raw, "").trim();
    if (text.isEmpty()) return;
    for (String token : text.split("[\\s,]+")) {
      parseMonitorToken(token, out);
    }
  }

  private static void parseMonitorToken(String raw, MonitorSupportCollector out) {
    if (out == null) return;
    String token = stripTrailingPunctuation(stripLeadingColon(Objects.toString(raw, "")));
    if (token.isEmpty()) return;
    String upper = token.toUpperCase(Locale.ROOT);
    if (upper.startsWith("-MONITOR")) {
      out.observeAvailability(false);
      out.observeLimit(0L);
      return;
    }
    if ("MONITOR".equals(upper)) {
      out.observeAvailability(true);
      return;
    }
    if (!upper.startsWith("MONITOR=")) return;
    int idx = token.indexOf('=');
    if (idx < 0 || idx >= token.length() - 1) {
      out.observeAvailability(true);
      return;
    }
    long parsed = tryParseLong(token.substring(idx + 1));
    if (parsed >= 0L) {
      out.observeLimit(parsed);
    } else {
      out.observeAvailability(true);
    }
  }

  private static String stripTrailingPunctuation(String raw) {
    String text = Objects.toString(raw, "").trim();
    while (!text.isEmpty()) {
      char last = text.charAt(text.length() - 1);
      if (last == ';' || last == ':' || last == '.') {
        text = text.substring(0, text.length() - 1).trim();
      } else {
        break;
      }
    }
    return text;
  }

  private static void collectCapabilityTokens(Object raw, Set<String> out) {
    if (raw == null || out == null) return;
    if (raw instanceof byte[] bytes) {
      collectCapabilityTokens(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), out);
      return;
    }
    if (raw instanceof String text) {
      String cleaned = text.replace(',', ' ');
      for (String token : cleaned.split("\\s+")) {
        String cap = canonicalCapabilityToken(token);
        if (!cap.isEmpty()) out.add(cap);
      }
      return;
    }
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectCapabilityTokens(value, out);
      }
      return;
    }
    if (raw instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String cap = canonicalCapabilityToken(entry.getKey());
        if (cap.isEmpty()) continue;
        if (!isCapabilityExplicitlyDisabled(entry.getValue())) {
          out.add(cap);
        }
      }
      return;
    }

    String cap = canonicalCapabilityToken(raw);
    if (!cap.isEmpty()) {
      out.add(cap);
    }
  }

  private static boolean isCapabilityExplicitlyDisabled(Object raw) {
    if (raw instanceof Boolean b) return !b;
    if (raw instanceof Number n) return n.intValue() == 0;
    String token = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (token.isEmpty()) return false;
    return "0".equals(token)
        || "false".equals(token)
        || "no".equals(token)
        || "off".equals(token)
        || "-".equals(token);
  }

  static String canonicalCapabilityToken(Object raw) {
    String token = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (token.isEmpty()) return "";
    if (token.startsWith(":")) token = token.substring(1).trim();
    if (token.startsWith("+")) token = token.substring(1).trim();
    if (token.startsWith("-")) token = token.substring(1).trim();
    int eq = token.indexOf('=');
    if (eq > 0) token = token.substring(0, eq).trim();
    if (token.isEmpty()) return "";
    return token;
  }

  record MonitorSupportState(boolean available, long limit) {}

  record MultilineLimitState(long maxBytes, long maxLines) {}

  private static final class MonitorSupportCollector {
    private boolean known;
    private boolean available;
    private boolean limitSeen;
    private long limit;

    void observeAvailability(boolean enabled) {
      known = true;
      available = enabled;
      if (!enabled && !limitSeen) {
        limit = 0L;
      }
    }

    void observeLimit(long maxTargets) {
      if (maxTargets < 0L) return;
      known = true;
      limitSeen = true;
      limit = maxTargets;
      available = true;
    }

    MonitorSupportState toStateOrNull() {
      if (!known) return null;
      long parsedLimit = limitSeen ? Math.max(0L, limit) : 0L;
      return new MonitorSupportState(available, parsedLimit);
    }
  }

  private static final class MultilineLimitCollector {
    private boolean bytesObserved;
    private boolean linesObserved;
    private long maxBytes;
    private long maxLines;

    void observeBytes(long value) {
      if (value < 0L) return;
      bytesObserved = true;
      maxBytes = value;
    }

    void observeLines(long value) {
      if (value < 0L) return;
      linesObserved = true;
      maxLines = value;
    }

    MultilineLimitState toStateOrNull() {
      if (!bytesObserved && !linesObserved) return null;
      long bytes = bytesObserved ? Math.max(0L, maxBytes) : 0L;
      long lines = linesObserved ? Math.max(0L, maxLines) : 0L;
      return new MultilineLimitState(bytes, lines);
    }
  }

  static MultilineLimitState multilineLimitsFromToken(Object token) {
    MultilineLimitCollector collector = new MultilineLimitCollector();
    collectMultilineLimitsFromToken(token, collector);
    return collector.toStateOrNull();
  }
}
