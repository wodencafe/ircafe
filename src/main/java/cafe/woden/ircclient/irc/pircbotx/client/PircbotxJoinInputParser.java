package cafe.woden.ircclient.irc.pircbotx.client;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.List;
import org.pircbotx.InputParser;
import org.pircbotx.PircBotX;
import org.pircbotx.UserHostmask;

/** Keeps PircBotX's automatic self-JOIN queries from waiting on the socket reader. */
public class PircbotxJoinInputParser extends InputParser {
  public PircbotxJoinInputParser(PircBotX bot) {
    super(bot);
  }

  @Override
  public void processCommand(
      String target,
      UserHostmask source,
      String command,
      String line,
      List<String> parsedLine,
      ImmutableMap<String, String> tags)
      throws IOException {
    if ("JOIN".equalsIgnoreCase(command)
        && source != null
        && source.getNick().equalsIgnoreCase(bot.getNick())
        && bot.sendRaw() instanceof PircbotxPacedOutput output) {
      output.deferJoinQueries(
          target, () -> super.processCommand(target, source, command, line, parsedLine, tags));
    } else {
      super.processCommand(target, source, command, line, parsedLine, tags);
    }
  }
}
