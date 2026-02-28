package cafe.woden.ircclient.irc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

/** A single line returned as part of an IRCv3 {@code CHATHISTORY} batch. */
@ValueObject
public record ChatHistoryEntry(
    Instant at,
    Kind kind,
    String target,
    String from,
    String text,
    String messageId,
    Map<String, String> ircv3Tags) {

  public enum Kind {
    PRIVMSG,
    ACTION,
    NOTICE
  }

  public ChatHistoryEntry {
    if (at == null) at = Instant.now();
    if (kind == null) kind = Kind.PRIVMSG;
    if (target == null) target = "";
    if (from == null) from = "";
    if (text == null) text = "";
    messageId = normalizeMessageId(messageId);
    ircv3Tags = normalizeIrcv3Tags(ircv3Tags);
  }

  public ChatHistoryEntry(Instant at, Kind kind, String target, String from, String text) {
    this(at, kind, target, from, text, "", Map.of());
  }

  public ChatHistoryEntry(
      Instant at, Kind kind, String target, String from, String text, String messageId) {
    this(at, kind, target, from, text, messageId, Map.of());
  }

  private static String normalizeMessageId(String raw) {
    return Objects.toString(raw, "").trim();
  }

  private static Map<String, String> normalizeIrcv3Tags(Map<String, String> raw) {
    if (raw == null || raw.isEmpty()) return Map.of();
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : raw.entrySet()) {
      String key = normalizeTagKey(e.getKey());
      if (key.isEmpty()) continue;
      String val = Objects.toString(e.getValue(), "");
      out.put(key, val);
    }
    if (out.isEmpty()) return Map.of();
    return java.util.Collections.unmodifiableMap(out);
  }

  private static String normalizeTagKey(String rawKey) {
    String k = Objects.toString(rawKey, "").trim();
    if (k.startsWith("@")) k = k.substring(1).trim();
    if (k.startsWith("+")) k = k.substring(1).trim();
    if (k.isEmpty()) return "";
    return k.toLowerCase(Locale.ROOT);
  }
}
