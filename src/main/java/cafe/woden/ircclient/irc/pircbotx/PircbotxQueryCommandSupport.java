package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import java.util.Locale;
import org.pircbotx.PircBotX;

/** Sends small IRC query commands and updates related local query state. */
final class PircbotxQueryCommandSupport {

  void requestNames(PircBotX bot, String channel) {
    String sanitizedChannel = PircbotxUtil.sanitizeChannel(channel);
    bot.sendRaw().rawLine("NAMES " + sanitizedChannel);
  }

  void whois(PircbotxConnectionState connection, PircBotX bot, String nick) {
    String sanitizedNick = PircbotxUtil.sanitizeNick(nick);
    String nickLower = sanitizedNick.toLowerCase(Locale.ROOT);
    connection.whoisSawAwayByNickLower.putIfAbsent(nickLower, Boolean.FALSE);
    connection.whoisSawAccountByNickLower.putIfAbsent(nickLower, Boolean.FALSE);
    bot.sendRaw().rawLine("WHOIS " + sanitizedNick);
  }

  void whowas(PircBotX bot, String nick, int count) {
    String sanitizedNick = PircbotxUtil.sanitizeNick(nick);
    if (count > 0) {
      bot.sendRaw().rawLine("WHOWAS " + sanitizedNick + " " + count);
    } else {
      bot.sendRaw().rawLine("WHOWAS " + sanitizedNick);
    }
  }
}
