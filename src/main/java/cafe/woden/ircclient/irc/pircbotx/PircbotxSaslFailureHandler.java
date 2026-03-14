package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.function.Consumer;
import org.pircbotx.PircBotX;

/** Handles SASL failure numerics and the resulting disconnect/reconnect policy. */
final class PircbotxSaslFailureHandler {
  private static final int ERR_SASL_FAIL = 904;
  private static final int ERR_SASL_TOO_LONG = 905;
  private static final int ERR_SASL_ABORTED = 906;
  private static final int ERR_SASL_ALREADY = 907;

  private final String serverId;
  private final PircbotxConnectionState conn;
  private final Consumer<ServerIrcEvent> emit;
  private final boolean disconnectOnSaslFailure;

  PircbotxSaslFailureHandler(
      String serverId,
      PircbotxConnectionState conn,
      Consumer<ServerIrcEvent> emit,
      boolean disconnectOnSaslFailure) {
    this.serverId = serverId;
    this.conn = conn;
    this.emit = emit;
    this.disconnectOnSaslFailure = disconnectOnSaslFailure;
  }

  boolean isFailureCode(int code) {
    return code == ERR_SASL_FAIL
        || code == ERR_SASL_TOO_LONG
        || code == ERR_SASL_ABORTED
        || code == ERR_SASL_ALREADY;
  }

  Integer parseFailureCode(String rawLine) {
    if (rawLine == null || rawLine.isBlank()) return null;
    String s = rawLine.trim();
    String[] parts = s.split("\\s+");
    if (parts.length == 0) return null;

    int codeIdx = parts[0].startsWith(":") ? 1 : 0;
    if (parts.length <= codeIdx) return null;

    String codeStr = parts[codeIdx];
    if (!PircbotxLineParseUtil.looksNumeric(codeStr)) return null;

    int code;
    try {
      code = Integer.parseInt(codeStr);
    } catch (Exception ignored) {
      return null;
    }

    return isFailureCode(code) ? code : null;
  }

  void handle(int code, String rawLine) {
    String msg = extractTrailingMessage(rawLine);

    String base =
        switch (code) {
          case ERR_SASL_FAIL -> "SASL authentication failed";
          case ERR_SASL_TOO_LONG -> "SASL authentication failed (payload too long)";
          case ERR_SASL_ABORTED -> "SASL authentication aborted";
          case ERR_SASL_ALREADY -> "SASL authentication already completed";
          default -> "SASL authentication failed";
        };

    String detail = base;
    if (msg != null && !msg.isBlank()) {
      String trimmed = msg.trim();
      if (!trimmed.equalsIgnoreCase(base)) {
        detail = base + ": " + trimmed;
      }
    }

    String reason = "Login failed — " + detail;
    String existing = conn.disconnectReasonOverride.get();
    if (existing != null && !existing.isBlank()) {
      conn.suppressAutoReconnectOnce.set(true);
      return;
    }

    conn.disconnectReasonOverride.set(reason);
    conn.suppressAutoReconnectOnce.set(true);
    emit.accept(new ServerIrcEvent(serverId, new IrcEvent.Error(Instant.now(), reason, null)));
    if (disconnectOnSaslFailure) {
      PircBotX bot = conn.botRef.get();
      if (bot != null) {
        try {
          bot.stopBotReconnect();
        } catch (Exception ignored) {
        }
        try {
          bot.sendIRC().quitServer(reason);
        } catch (Exception ignored) {
        }
      }
    }
  }

  private static String extractTrailingMessage(String rawLine) {
    if (rawLine == null) return null;
    String s = PircbotxLineParseUtil.normalizeIrcLineForParsing(rawLine);
    if (s == null) return null;
    int idx = s.indexOf(" :");
    if (idx < 0) return null;
    int start = idx + 2;
    if (start >= s.length()) return null;
    String trailing = s.substring(start).trim();
    return trailing.isEmpty() ? null : trailing;
  }
}
