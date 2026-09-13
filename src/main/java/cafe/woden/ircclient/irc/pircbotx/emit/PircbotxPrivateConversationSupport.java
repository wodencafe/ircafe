package cafe.woden.ircclient.irc.pircbotx.emit;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.*;
import java.util.Locale;
import java.util.Objects;

/** Shared routing and suppression helpers for private conversations. */
public final class PircbotxPrivateConversationSupport {
  private final PircbotxConnectionState conn;
  private final Ircv3ZncPlaybackRuntimeSupport zncPlaybackRuntimeSupport;

  public PircbotxPrivateConversationSupport(
      PircbotxConnectionState conn, Ircv3ZncPlaybackRuntimeSupport zncPlaybackRuntimeSupport) {
    this.conn = Objects.requireNonNull(conn, "conn");
    this.zncPlaybackRuntimeSupport =
        Objects.requireNonNull(zncPlaybackRuntimeSupport, "zncPlaybackRuntimeSupport");
  }

  public String deriveConversationTarget(String botNick, String fromNick, String dest) {
    String from = fromNick == null ? "" : fromNick.trim();
    String d = dest == null ? "" : dest.trim();
    String me = botNick == null ? "" : botNick.trim();

    if (d.isBlank()) return from;
    if (me.isBlank()) return from;
    if (d.equalsIgnoreCase(me)) return from;
    return d;
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
    return zncPlaybackRuntimeSupport.shouldSuppressBootstrap(fromSelf, target, msg);
  }
}
