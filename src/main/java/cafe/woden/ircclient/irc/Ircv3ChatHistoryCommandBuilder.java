package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Small helper for building IRCv3 CHATHISTORY commands.
 *
 * <p>This is intentionally a tiny, mostly-pure builder so later steps (4C/4D)
 * can reuse it without coupling to UI or DB.
 */
final class Ircv3ChatHistoryCommandBuilder {

  private static final DateTimeFormatter TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
          .withZone(ZoneOffset.UTC);

  private Ircv3ChatHistoryCommandBuilder() {}

  static String buildBeforeByTimestamp(String target, Instant beforeExclusive, int limit) {
    String t = sanitizeTarget(target);
    Instant ts = Objects.requireNonNull(beforeExclusive, "beforeExclusive");
    int lim = clampLimit(limit);

    // Spec requires the selector to be either "timestamp=..." or "msgid=...".
    String selector = "timestamp=" + TS_FMT.format(ts);
    return "CHATHISTORY BEFORE " + t + " " + selector + " " + lim;
  }

  static int clampLimit(int limit) {
    // Be conservative by default to avoid flooding; callers can request bigger later.
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
}
