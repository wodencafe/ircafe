package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import org.pircbotx.PircBotX;

/** Sends small built-in IRC commands that do not need extra session orchestration. */
final class PircbotxBasicCommandSupport {

  void changeNick(PircBotX bot, String newNick) {
    String sanitizedNick = PircbotxUtil.sanitizeNick(newNick);
    bot.sendIRC().changeNick(sanitizedNick);
  }

  void setAway(PircBotX bot, String awayMessage) {
    String renderedMessage = awayMessage == null ? "" : awayMessage.trim();
    if (renderedMessage.contains("\r") || renderedMessage.contains("\n")) {
      throw new IllegalArgumentException("away message contains CR/LF");
    }
    if (renderedMessage.isEmpty()) {
      bot.sendRaw().rawLine("AWAY");
    } else {
      bot.sendRaw().rawLine("AWAY :" + renderedMessage);
    }
  }

  void joinChannel(PircBotX bot, String channel) {
    bot.sendIRC().joinChannel(channel);
  }

  void partChannel(PircBotX bot, String channel, String reason) {
    String sanitizedChannel = PircbotxUtil.sanitizeChannel(channel);
    String renderedReason = reason == null ? "" : reason.trim();
    if (renderedReason.isEmpty()) {
      bot.sendRaw().rawLine("PART " + sanitizedChannel);
    } else {
      bot.sendRaw().rawLine("PART " + sanitizedChannel + " :" + renderedReason);
    }
  }

  void sendRaw(PircBotX bot, String rawLine) {
    String renderedLine = rawLine == null ? "" : rawLine.trim();
    if (renderedLine.isEmpty()) {
      return;
    }
    bot.sendRaw().rawLine(renderedLine);
  }
}
