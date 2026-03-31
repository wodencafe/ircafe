package cafe.woden.ircclient.irc.pircbotx.listener;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxEventAccessors;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.PircBotX;

/** Tracks our best-known nick for a single IRC connection and resolves self-message heuristics. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PircbotxSelfIdentityTracker {
  @NonNull private final PircbotxConnectionState conn;

  /** Best-effort resolve of our current nick (prefers UserBot nick when available). */
  static String resolveBotNick(PircBotX bot) {
    if (bot == null) return "";

    // Prefer UserBot nick, since it tends to reflect the current nick including alt-nick fallback.
    try {
      if (bot.getUserBot() != null) {
        String userBotNick = bot.getUserBot().getNick();
        if (userBotNick != null && !userBotNick.isBlank()) return userBotNick.trim();
      }
    } catch (Exception ignored) {
    }

    try {
      String nick = PircbotxUtil.safeStr(bot::getNick, "");
      if (nick != null && !nick.isBlank()) return nick.trim();
    } catch (Exception ignored) {
    }

    // Last resort: poke configuration via reflection since PircBotX variants differ.
    try {
      Object cfg = PircbotxEventAccessors.reflectCall(bot, "getConfiguration");
      Object nick = PircbotxEventAccessors.reflectCall(cfg, "getNick");
      if (nick != null) {
        String value = String.valueOf(nick);
        if (!value.isBlank()) return value.trim();
      }
    } catch (Exception ignored) {
    }

    return "";
  }

  /** True when an inbound event is actually our own message echoed back (IRCv3 echo-message). */
  static boolean isSelfEchoed(PircBotX bot, String fromNick) {
    try {
      if (fromNick == null || fromNick.isBlank()) return false;

      String botNick = resolveBotNick(bot);
      if (!botNick.isBlank() && fromNick.equalsIgnoreCase(botNick)) return true;

      try {
        if (bot != null && bot.getUserBot() != null) {
          String userBotNick = bot.getUserBot().getNick();
          if (userBotNick != null
              && !userBotNick.isBlank()
              && fromNick.equalsIgnoreCase(userBotNick)) {
            return true;
          }
        }
      } catch (Exception ignored) {
      }

      return false;
    } catch (Exception ignored) {
      return false;
    }
  }

  void rememberSelfNickHint(String nick) {
    if (!looksLikeSelfNickHint(nick)) return;
    conn.setSelfNickHint(nick.trim());
  }

  boolean nickMatchesSelf(PircBotX bot, String nick) {
    if (nick == null || nick.isBlank()) return false;
    String candidate = nick.trim();

    String hinted = conn.selfNickHint();
    if (hinted != null && !hinted.isBlank() && candidate.equalsIgnoreCase(hinted.trim())) {
      return true;
    }

    String resolvedBotNick = resolveBotNick(bot);
    if (!resolvedBotNick.isBlank() && candidate.equalsIgnoreCase(resolvedBotNick.trim())) {
      rememberSelfNickHint(resolvedBotNick);
      return true;
    }

    return false;
  }

  String resolveSelfNick(PircBotX bot) {
    String hinted = conn.selfNickHint();
    if (looksLikeSelfNickHint(hinted)) {
      return hinted.trim();
    }

    String resolvedBotNick = resolveBotNick(bot);
    if (!resolvedBotNick.isBlank()) {
      rememberSelfNickHint(resolvedBotNick);
      return resolvedBotNick;
    }

    return "";
  }

  private static boolean looksLikeSelfNickHint(String nick) {
    if (nick == null) return false;
    String candidate = nick.trim();
    if (candidate.isBlank()) return false;
    if ("*".equals(candidate)) return false;
    if (PircbotxLineParseUtil.looksNumeric(candidate)) return false;
    if (PircbotxLineParseUtil.looksLikeChannel(candidate)) return false;
    if (candidate.indexOf(' ') >= 0) return false;
    return true;
  }
}
