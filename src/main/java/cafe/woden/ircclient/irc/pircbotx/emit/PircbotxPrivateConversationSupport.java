package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.*;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** Shared routing and suppression helpers for private conversations. */
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public final class PircbotxPrivateConversationSupport {
  @NonNull private final PircbotxConnectionState conn;

  public String deriveConversationTarget(String botNick, String fromNick, String dest) {
    String from = fromNick == null ? "" : fromNick.trim();
    String d = dest == null ? "" : dest.trim();
    String me = botNick == null ? "" : botNick.trim();

    if (d.isBlank()) return from;
    if (me.isBlank()) return from;
    if (d.equalsIgnoreCase(me)) return from;
    return d;
  }

  public boolean isZncPlayStarCursorCommand(String msg) {
    String m = Objects.toString(msg, "").trim();
    if (m.isEmpty()) return false;
    String[] parts = m.split("\\s+");
    if (parts.length < 3) return false;
    if (!"play".equalsIgnoreCase(parts[0])) return false;
    if (!"*".equals(parts[1])) return false;
    String n = parts[2];
    if (n.isEmpty()) return false;
    for (int i = 0; i < n.length(); i++) {
      if (!Character.isDigit(n.charAt(i))) return false;
    }
    return true;
  }

  public String inferPrivateDestinationFromHints(
      String from, String kind, String payload, String messageId) {
    String fromNick = Objects.toString(from, "").trim();
    String k = Objects.toString(kind, "").trim().toUpperCase(Locale.ROOT);
    String body = Objects.toString(payload, "").trim();
    if (fromNick.isBlank() || k.isBlank() || body.isBlank()) return "";
    return conn.findPrivateTargetHint(fromNick, k, body, messageId, System.currentTimeMillis());
  }

  public boolean shouldSuppressSelfBootstrapMessage(boolean fromSelf, String target, String msg) {
    if (!fromSelf) return false;
    if (isZncPlayStarCursorCommand(msg)) return true;
    if (target == null || target.isBlank()) return false;
    if ("*playback".equalsIgnoreCase(target)
        && msg != null
        && msg.toLowerCase(Locale.ROOT).startsWith("play ")) {
      return true;
    }
    if ("*status".equalsIgnoreCase(target) && "ListNetworks".equalsIgnoreCase(msg)) {
      return true;
    }
    return false;
  }
}
