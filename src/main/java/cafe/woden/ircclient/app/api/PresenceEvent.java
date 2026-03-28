package cafe.woden.ircclient.app.api;

import java.util.Objects;
import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record PresenceEvent(
    PresenceKind kind,
    String nick,
    String oldNick,
    String newNick,
    String reason,
    String hostmask) {

  public static PresenceEvent join(String nick) {
    return join(nick, null);
  }

  public static PresenceEvent join(String nick, String hostmask) {
    return new PresenceEvent(PresenceKind.JOIN, nick, null, null, null, hostmask);
  }

  public static PresenceEvent part(String nick, String reason) {
    return part(nick, reason, null);
  }

  public static PresenceEvent part(String nick, String reason, String hostmask) {
    return new PresenceEvent(PresenceKind.PART, nick, null, null, reason, hostmask);
  }

  public static PresenceEvent quit(String nick, String reason) {
    return quit(nick, reason, null);
  }

  public static PresenceEvent quit(String nick, String reason, String hostmask) {
    return new PresenceEvent(PresenceKind.QUIT, nick, null, null, reason, hostmask);
  }

  public static PresenceEvent nick(String oldNick, String newNick) {
    return new PresenceEvent(PresenceKind.NICK, null, oldNick, newNick, null, null);
  }

  public PresenceEvent {
    Objects.requireNonNull(kind, "kind");
  }

  public String displayText() {
    return switch (kind) {
      case JOIN -> joinDisplayText();
      case PART -> partDisplayText();
      case QUIT -> quitDisplayText();
      case NICK -> safe(oldNick) + " is now known as " + safe(newNick);
    };
  }

  private static String safe(String s) {
    return s == null ? "" : s.trim();
  }

  private String joinDisplayText() {
    String joinedNick = renderActor(nick);
    String renderedHostmask = renderPresenceHostmask(hostmask);
    if (renderedHostmask.isBlank()) {
      return "--> " + joinedNick + " has joined this channel.";
    }
    return "--> " + joinedNick + " (" + renderedHostmask + ") has joined this channel.";
  }

  private String partDisplayText() {
    String partedNick = renderActor(nick);
    String renderedHostmask = renderPresenceHostmask(hostmask);
    String base =
        renderedHostmask.isBlank()
            ? "<-- " + partedNick + " has left this channel"
            : "<-- " + partedNick + " (" + renderedHostmask + ") has left this channel";
    return appendOptionalReason(base, reason);
  }

  private String quitDisplayText() {
    String quitNick = renderActor(nick);
    String renderedHostmask = renderPresenceHostmask(hostmask);
    String base =
        renderedHostmask.isBlank()
            ? "<-- " + quitNick + " has quit IRC"
            : "<-- " + quitNick + " (" + renderedHostmask + ") has quit IRC";
    return appendOptionalReason(base, reason);
  }

  private static String renderPresenceHostmask(String rawHostmask) {
    String normalized = safe(rawHostmask);
    if (normalized.isBlank()) {
      return "";
    }
    int bang = normalized.indexOf('!');
    if (bang >= 0 && bang + 1 < normalized.length()) {
      normalized = normalized.substring(bang + 1).trim();
    } else if (bang >= 0) {
      return "";
    }
    return normalized;
  }

  private static String renderActor(String rawNick) {
    String normalized = safe(rawNick);
    return normalized.isBlank() ? "Someone" : normalized;
  }

  private static String appendOptionalReason(String base, String rawReason) {
    String normalizedReason = safe(rawReason);
    if (normalizedReason.isBlank()) {
      return base + ".";
    }
    return base + " (" + normalizedReason + ").";
  }
}
