package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.PresenceEvent;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.logging.model.LogDirection;
import cafe.woden.ircclient.logging.model.LogKind;
import cafe.woden.ircclient.logging.model.LogLine;
import java.time.Clock;
import java.util.Objects;
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
