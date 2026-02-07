package cafe.woden.ircclient.irc;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.function.Consumer;
import org.pircbotx.InputParser;
import org.pircbotx.PircBotX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Installs small PircBotX hooks that require reflection.
 *
 * <p>PircBotX does not expose a public API for swapping its {@link InputParser}, but we need to
 * decorate it to surface a few low-cost IRCv3 capabilities into our own event stream
 * (currently: {@code away-notify}, {@code account-notify}, {@code extended-join}, and {@code account-tag}).
 */
@Component
public class PircbotxInputParserHookInstaller {

  private static final Logger log = LoggerFactory.getLogger(PircbotxInputParserHookInstaller.class);

  
  public void installAwayNotifyHook(PircBotX bot, String serverId, Consumer<ServerIrcEvent> sink) {
    if (bot == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    try {
      InputParser replacement = new PircbotxAwayNotifyInputParser(bot, sid, sink);
      boolean swapped = swapInputParser(bot, replacement);
      if (swapped) {
        log.info("[{}] installed IRCv3 InputParser hook (away/account/extended-join/account-tag)", sid);
      } else {
        log.warn("[{}] could not install away-notify InputParser hook (no compatible field found)", sid);
      }
    } catch (Exception ex) {
      log.warn("[{}] failed to install away-notify InputParser hook", sid, ex);
    }
  }

  
  boolean swapInputParser(PircBotX bot, InputParser replacement) throws Exception {
    Field target = null;
    Class<?> c = bot.getClass();
    while (c != null) {
      for (Field f : c.getDeclaredFields()) {
        if (InputParser.class.isAssignableFrom(f.getType())) {
          target = f;
          break;
        }
      }
      if (target != null) break;
      c = c.getSuperclass();
    }
    if (target == null) return false;

    target.setAccessible(true);
    target.set(bot, replacement);
    return true;
  }
}
