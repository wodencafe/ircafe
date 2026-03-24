package cafe.woden.ircclient.irc.pircbotx.listener;

import cafe.woden.ircclient.irc.pircbotx.*;
import org.pircbotx.PircBotX;

/** Callback for sending optional client-side CTCP auto-replies. */
@FunctionalInterface
public interface PircbotxCtcpRequestHandler {
  boolean handle(PircBotX bot, String fromNick, String message);
}
