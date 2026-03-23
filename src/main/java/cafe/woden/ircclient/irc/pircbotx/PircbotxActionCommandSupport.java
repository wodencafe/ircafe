package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import java.lang.reflect.Method;
import org.pircbotx.PircBotX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sends outbound IRC ACTION messages with a reflective native-action fallback. */
final class PircbotxActionCommandSupport {

  private static final Logger log = LoggerFactory.getLogger(PircbotxActionCommandSupport.class);

  void sendAction(String serverId, PircBotX bot, String target, String action) throws Exception {
    String renderedTarget = target == null ? "" : target.trim();
    String renderedAction = action == null ? "" : action;
    if (renderedTarget.isEmpty()) {
      throw new IllegalArgumentException("target is blank");
    }

    String destination = sanitizeTarget(renderedTarget);
    Object outputIrc = bot.sendIRC();

    boolean sent = false;
    try {
      Method actionMethod = outputIrc.getClass().getMethod("action", String.class, String.class);
      actionMethod.invoke(outputIrc, destination, renderedAction);
      sent = true;
    } catch (NoSuchMethodException ignored) {
    } catch (Exception e) {
      log.debug(
          "[{}] sendAction native action() invoke failed, falling back to CTCP wrapper",
          serverId,
          e);
    }

    if (!sent) {
      bot.sendIRC().message(destination, "\u0001ACTION " + renderedAction + "\u0001");
    }
  }

  private static String sanitizeTarget(String target) {
    if (target.startsWith("#") || target.startsWith("&")) {
      return PircbotxUtil.sanitizeChannel(target);
    }
    return PircbotxUtil.sanitizeNick(target);
  }
}
