package cafe.woden.ircclient.app.api;

import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record PresenceEvent(
    PresenceKind kind, String nick, String oldNick, String newNick, String reason) {

  public static PresenceEvent join(String nick) {
    return new PresenceEvent(PresenceKind.JOIN, nick, null, null, null);
  }

  public static PresenceEvent part(String nick, String reason) {
    return new PresenceEvent(PresenceKind.PART, nick, null, null, reason);
  }

  public static PresenceEvent quit(String nick, String reason) {
    return new PresenceEvent(PresenceKind.QUIT, nick, null, null, reason);
  }

  public static PresenceEvent nick(String oldNick, String newNick) {
    return new PresenceEvent(PresenceKind.NICK, null, oldNick, newNick, null);
  }

  public PresenceEvent {
    Objects.requireNonNull(kind, "kind");
  }

  public String displayText() {
    return switch (kind) {
      case JOIN -> safe(nick) + " joined";
      case PART -> {
        String base = safe(nick) + " left";
        String r = safe(reason);
        yield r.isBlank() ? base : base + " (" + r + ")";
      }
      case QUIT -> {
        String base = safe(nick) + " quit";
        String r = safe(reason);
        yield r.isBlank() ? base : base + " (" + r + ")";
      }
      case NICK -> safe(oldNick) + " → " + safe(newNick);
    };
  }

  private static String safe(String s) {
    return s == null ? "" : s.trim();
  }
}
