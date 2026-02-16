package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Small helper for building IRCv3 CHATHISTORY commands.
 *
 */
final class Ircv3ChatHistoryCommandBuilder {

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
          .withZone(ZoneOffset.UTC);

  private Ircv3ChatHistoryCommandBuilder() {}

  static String buildBeforeByTimestamp(String target, Instant beforeExclusive, int limit) {
    Instant ts = Objects.requireNonNull(beforeExclusive, "beforeExclusive");
    return buildBefore(target, timestampSelector(ts), limit);
  }

  static String buildBeforeByMessageId(String target, String messageId, int limit) {
    String msgId = sanitizeSelectorValue(messageId);
    return buildBefore(target, "msgid=" + msgId, limit);
  }

  static String buildBefore(String target, String selector, int limit) {
    String t = sanitizeTarget(target);
    String s = sanitizeSelector(selector);
    int lim = clampLimit(limit);
    return "CHATHISTORY BEFORE " + t + " " + s + " " + lim;
  }

  static String buildLatest(String target, String selectorOrWildcard, int limit) {
    String t = sanitizeTarget(target);
    String s = sanitizeSelectorOrWildcard(selectorOrWildcard);
    int lim = clampLimit(limit);
    return "CHATHISTORY LATEST " + t + " " + s + " " + lim;
  }

  static String buildAround(String target, String selector, int limit) {
    String t = sanitizeTarget(target);
    String s = sanitizeSelector(selector);
    int lim = clampLimit(limit);
    return "CHATHISTORY AROUND " + t + " " + s + " " + lim;
  }

  static String buildBetween(String target, String startSelector, String endSelector, int limit) {
    String t = sanitizeTarget(target);
    String start = sanitizeSelectorOrWildcard(startSelector);
    String end = sanitizeSelectorOrWildcard(endSelector);
    int lim = clampLimit(limit);
    return "CHATHISTORY BETWEEN " + t + " " + start + " " + end + " " + lim;
  }

  static String timestampSelector(Instant at) {
    Instant ts = Objects.requireNonNull(at, "at");
    return "timestamp=" + TS_FMT.format(ts);
  }

  static int clampLimit(int limit) {
    int lim = limit;
    if (lim <= 0) lim = 50;
    if (lim > 200) lim = 200;
    return lim;
  }

  static String sanitizeTarget(String target) {
    String t = Objects.requireNonNull(target, "target").trim();
    if (t.isEmpty()) throw new IllegalArgumentException("target is blank");
    if (t.contains("\r") || t.contains("\n"))
      throw new IllegalArgumentException("target contains CR/LF");
    if (t.contains(" "))
      throw new IllegalArgumentException("target contains spaces");
    return t;
  }

  static String sanitizeSelector(String selector) {
    String s = Objects.requireNonNull(selector, "selector").trim();
    if (s.isEmpty()) throw new IllegalArgumentException("selector is blank");
    if (s.contains("\r") || s.contains("\n"))
      throw new IllegalArgumentException("selector contains CR/LF");
    if (s.contains(" "))
      throw new IllegalArgumentException("selector contains spaces");

    int eq = s.indexOf('=');
    if (eq <= 0 || eq == s.length() - 1) {
      throw new IllegalArgumentException("selector must be key=value");
    }

    String key = s.substring(0, eq).trim().toLowerCase(Locale.ROOT);
    String value = sanitizeSelectorValue(s.substring(eq + 1));
    if (key.isEmpty()) throw new IllegalArgumentException("selector key is blank");
    return key + "=" + value;
  }

  static String sanitizeSelectorOrWildcard(String selector) {
    String s = Objects.requireNonNull(selector, "selector").trim();
    if (s.equals("*")) return "*";
    return sanitizeSelector(s);
  }

  private static String sanitizeSelectorValue(String raw) {
    String v = Objects.requireNonNull(raw, "selector value").trim();
    if (v.isEmpty()) throw new IllegalArgumentException("selector value is blank");
    if (v.contains("\r") || v.contains("\n"))
      throw new IllegalArgumentException("selector value contains CR/LF");
    if (v.contains(" "))
      throw new IllegalArgumentException("selector value contains spaces");
    return v;
  }
}
