package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.PresenceEvent;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.logging.model.LogDirection;
import cafe.woden.ircclient.logging.model.LogKind;
import cafe.woden.ircclient.logging.model.LogLine;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Convert UI-facing transcript events into persisted {@link LogLine}s.
 *
 */
@Component
public final class LogLineFactory {

  private final Clock clock;

  public LogLineFactory() {
    this(Clock.systemUTC());
  }

  public LogLineFactory(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public LogLine chat(TargetRef target, String from, String text, boolean outgoingLocalEcho) {
    return base(target)
        .direction(outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN)
        .kind(LogKind.CHAT)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(outgoingLocalEcho)
        .softIgnored(false)
        .build();
  }

  /** Same as {@link #chat(TargetRef, String, String, boolean)} but with an explicit epoch timestamp. */
  public LogLine chatAt(TargetRef target, String from, String text, boolean outgoingLocalEcho, long tsEpochMs) {
    return baseAt(target, tsEpochMs)
        .direction(outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN)
        .kind(LogKind.CHAT)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(outgoingLocalEcho)
        .softIgnored(false)
        .build();
  }

  /**
   * Same as {@link #chatAt(TargetRef, String, String, boolean, long)} but with IRCv3 identity metadata.
   */
  public LogLine chatAt(
      TargetRef target,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    return baseAt(target, tsEpochMs)
        .direction(outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN)
        .kind(LogKind.CHAT)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(outgoingLocalEcho)
        .softIgnored(false)
        .metaJson(messageMetaJson(null, messageId, ircv3Tags))
        .build();
  }

  /**
   * Persist a resolved outbound line (pending send replaced by canonical server echo).
   *
   * <p>Includes the pending id plus message identity metadata in {@code meta}.
   */
  public LogLine resolvedOutgoingChatAt(
      TargetRef target,
      String from,
      String text,
      long tsEpochMs,
      String pendingId,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    return baseAt(target, tsEpochMs)
        .direction(LogDirection.OUT)
        .kind(LogKind.CHAT)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(true)
        .softIgnored(false)
        .metaJson(messageMetaJson(pendingId, messageId, ircv3Tags))
        .build();
  }

  public LogLine softIgnoredSpoiler(TargetRef target, String from, String text) {
    return base(target)
        .direction(LogDirection.IN)
        .kind(LogKind.SPOILER)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(false)
        .softIgnored(true)
        .build();
  }

  /** Same as {@link #softIgnoredSpoiler(TargetRef, String, String)} but with an explicit epoch timestamp. */
  public LogLine softIgnoredSpoilerAt(TargetRef target, String from, String text, long tsEpochMs) {
    return baseAt(target, tsEpochMs)
        .direction(LogDirection.IN)
        .kind(LogKind.SPOILER)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(false)
        .softIgnored(true)
        .build();
  }

  /**
   * Same as {@link #softIgnoredSpoilerAt(TargetRef, String, String, long)} but with IRCv3 identity metadata.
   */
  public LogLine softIgnoredSpoilerAt(
      TargetRef target,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    return baseAt(target, tsEpochMs)
        .direction(LogDirection.IN)
        .kind(LogKind.SPOILER)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(false)
        .softIgnored(true)
        .metaJson(messageMetaJson(null, messageId, ircv3Tags))
        .build();
  }

  public LogLine action(TargetRef target, String from, String action, boolean outgoingLocalEcho) {
    return base(target)
        .direction(outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN)
        .kind(LogKind.ACTION)
        .fromNick(normNick(from))
        .text(normText(action))
        .outgoingLocalEcho(outgoingLocalEcho)
        .softIgnored(false)
        .build();
  }

  /** Same as {@link #action(TargetRef, String, String, boolean)} but with an explicit epoch timestamp. */
  public LogLine actionAt(TargetRef target, String from, String action, boolean outgoingLocalEcho, long tsEpochMs) {
    return baseAt(target, tsEpochMs)
        .direction(outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN)
        .kind(LogKind.ACTION)
        .fromNick(normNick(from))
        .text(normText(action))
        .outgoingLocalEcho(outgoingLocalEcho)
        .softIgnored(false)
        .build();
  }

  /**
   * Same as {@link #actionAt(TargetRef, String, String, boolean, long)} but with IRCv3 identity metadata.
   */
  public LogLine actionAt(
      TargetRef target,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    return baseAt(target, tsEpochMs)
        .direction(outgoingLocalEcho ? LogDirection.OUT : LogDirection.IN)
        .kind(LogKind.ACTION)
        .fromNick(normNick(from))
        .text(normText(action))
        .outgoingLocalEcho(outgoingLocalEcho)
        .softIgnored(false)
        .metaJson(messageMetaJson(null, messageId, ircv3Tags))
        .build();
  }

  public LogLine notice(TargetRef target, String from, String text) {
    return base(target)
        .direction(LogDirection.IN)
        .kind(LogKind.NOTICE)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(false)
        .softIgnored(false)
        .build();
  }

  /** Same as {@link #notice(TargetRef, String, String)} but with an explicit epoch timestamp. */
  public LogLine noticeAt(TargetRef target, String from, String text, long tsEpochMs) {
    return baseAt(target, tsEpochMs)
        .direction(LogDirection.IN)
        .kind(LogKind.NOTICE)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(false)
        .softIgnored(false)
        .build();
  }

  /** Same as {@link #noticeAt(TargetRef, String, String, long)} but with IRCv3 identity metadata. */
  public LogLine noticeAt(
      TargetRef target,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    return baseAt(target, tsEpochMs)
        .direction(LogDirection.IN)
        .kind(LogKind.NOTICE)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(false)
        .softIgnored(false)
        .metaJson(messageMetaJson(null, messageId, ircv3Tags))
        .build();
  }

  public LogLine status(TargetRef target, String from, String text) {
    return base(target)
        .direction(LogDirection.SYSTEM)
        .kind(LogKind.STATUS)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(false)
        .softIgnored(false)
        .build();
  }

  /** Same as {@link #status(TargetRef, String, String)} but with an explicit epoch timestamp. */
  public LogLine statusAt(TargetRef target, String from, String text, long tsEpochMs) {
    return baseAt(target, tsEpochMs)
        .direction(LogDirection.SYSTEM)
        .kind(LogKind.STATUS)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(false)
        .softIgnored(false)
        .build();
  }

  /** Same as {@link #statusAt(TargetRef, String, String, long)} but with IRCv3 identity metadata. */
  public LogLine statusAt(
      TargetRef target,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    return baseAt(target, tsEpochMs)
        .direction(LogDirection.SYSTEM)
        .kind(LogKind.STATUS)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(false)
        .softIgnored(false)
        .metaJson(messageMetaJson(null, messageId, ircv3Tags))
        .build();
  }

  public LogLine error(TargetRef target, String from, String text) {
    return base(target)
        .direction(LogDirection.SYSTEM)
        .kind(LogKind.ERROR)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(false)
        .softIgnored(false)
        .build();
  }

  /** Same as {@link #error(TargetRef, String, String)} but with an explicit epoch timestamp. */
  public LogLine errorAt(TargetRef target, String from, String text, long tsEpochMs) {
    return baseAt(target, tsEpochMs)
        .direction(LogDirection.SYSTEM)
        .kind(LogKind.ERROR)
        .fromNick(normNick(from))
        .text(normText(text))
        .outgoingLocalEcho(false)
        .softIgnored(false)
        .build();
  }

  public LogLine presence(TargetRef target, PresenceEvent event) {
    Objects.requireNonNull(event, "event");
    String fromNick = switch (event.kind()) {
      case JOIN, PART, QUIT -> normNick(event.nick());
      case NICK -> normNick(event.oldNick());
    };

    return base(target)
        .direction(LogDirection.SYSTEM)
        .kind(LogKind.PRESENCE)
        .fromNick(fromNick)
        .text(normText(event.displayText()))
        .outgoingLocalEcho(false)
        .softIgnored(false)
        .build();
  }

  private Builder base(TargetRef target) {
    Objects.requireNonNull(target, "target");
    return new Builder(target.serverId(), target.target(), clock.millis());
  }

  private Builder baseAt(TargetRef target, long tsEpochMs) {
    Objects.requireNonNull(target, "target");
    long ts = (tsEpochMs > 0L) ? tsEpochMs : clock.millis();
    return new Builder(target.serverId(), target.target(), ts);
  }

  private static String normNick(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private static String normText(String s) {
    // Preserve message content exactly; just prevent nulls from bubbling into persistence.
    return s == null ? "" : s;
  }

  private static String messageMetaJson(String pendingId, String messageId, Map<String, String> ircv3Tags) {
    String pid = normToken(pendingId);
    String msgId = normToken(messageId);
    Map<String, String> tags = normalizeTagsForMeta(ircv3Tags);

    if (pid == null && msgId == null && tags.isEmpty()) return null;

    StringBuilder sb = new StringBuilder(96);
    sb.append('{');
    boolean needComma = false;
    if (pid != null) {
      appendJsonField(sb, "pendingId", pid);
      needComma = true;
    }
    if (msgId != null) {
      if (needComma) sb.append(',');
      appendJsonField(sb, "messageId", msgId);
      needComma = true;
    }
    if (!tags.isEmpty()) {
      if (needComma) sb.append(',');
      appendJsonString(sb, "ircv3Tags");
      sb.append(':').append('{');
      boolean tagComma = false;
      for (Map.Entry<String, String> e : tags.entrySet()) {
        if (tagComma) sb.append(',');
        appendJsonString(sb, e.getKey());
        sb.append(':');
        if (e.getValue() == null) {
          sb.append("null");
        } else {
          appendJsonString(sb, e.getValue());
        }
        tagComma = true;
      }
      sb.append('}');
    }
    sb.append('}');
    return sb.toString();
  }

  private static String normToken(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    return s.isEmpty() ? null : s;
  }

  private static Map<String, String> normalizeTagsForMeta(Map<String, String> raw) {
    if (raw == null || raw.isEmpty()) return Map.of();
    TreeMap<String, String> out = new TreeMap<>();
    for (Map.Entry<String, String> e : raw.entrySet()) {
      if (e == null || e.getKey() == null) continue;
      out.put(e.getKey(), e.getValue());
    }
    return out;
  }

  private static void appendJsonField(StringBuilder sb, String key, String value) {
    appendJsonString(sb, key);
    sb.append(':');
    appendJsonString(sb, value);
  }

  private static void appendJsonString(StringBuilder sb, String value) {
    sb.append('"').append(escapeJson(value)).append('"');
  }

  private static String escapeJson(String s) {
    if (s == null || s.isEmpty()) return "";
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }

  private static final class Builder {
    private final String serverId;
    private final String target;
    private final long tsEpochMs;
    private LogDirection direction;
    private LogKind kind;
    private String fromNick;
    private String text;
    private boolean outgoingLocalEcho;
    private boolean softIgnored;
    private String metaJson;

    private Builder(String serverId, String target, long tsEpochMs) {
      this.serverId = serverId;
      this.target = target;
      this.tsEpochMs = tsEpochMs;
    }

    private Builder direction(LogDirection direction) {
      this.direction = direction;
      return this;
    }

    private Builder kind(LogKind kind) {
      this.kind = kind;
      return this;
    }

    private Builder fromNick(String fromNick) {
      this.fromNick = fromNick;
      return this;
    }

    private Builder text(String text) {
      this.text = text;
      return this;
    }

    private Builder outgoingLocalEcho(boolean outgoingLocalEcho) {
      this.outgoingLocalEcho = outgoingLocalEcho;
      return this;
    }

    private Builder softIgnored(boolean softIgnored) {
      this.softIgnored = softIgnored;
      return this;
    }

    @SuppressWarnings("unused")
    private Builder metaJson(String metaJson) {
      this.metaJson = metaJson;
      return this;
    }

    private LogLine build() {
      return new LogLine(
          serverId,
          target,
          tsEpochMs,
          Objects.requireNonNull(direction, "direction"),
          Objects.requireNonNull(kind, "kind"),
          fromNick,
          Objects.requireNonNull(text, "text"),
          outgoingLocalEcho,
          softIgnored,
          metaJson
      );
    }
  }
}
