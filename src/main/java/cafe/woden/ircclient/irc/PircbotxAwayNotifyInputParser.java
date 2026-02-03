package cafe.woden.ircclient.irc;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.pircbotx.InputParser;
import org.pircbotx.PircBotX;
import org.pircbotx.UserHostmask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InputParser hook for IRCv3 away-notify.
 *
 * <p>Away-notify arrives as raw lines like:
 * <ul>
 *   <li><code>:nick!user@host AWAY :Gone away for now</code></li>
 *   <li><code>:nick!user@host AWAY</code></li>
 * </ul>
 */
final class PircbotxAwayNotifyInputParser extends InputParser {

  private static final Logger log = LoggerFactory.getLogger(PircbotxAwayNotifyInputParser.class);

  private final String serverId;
  private final Consumer<ServerIrcEvent> sink;

  PircbotxAwayNotifyInputParser(PircBotX bot, String serverId, Consumer<ServerIrcEvent> sink) {
    super(bot);
    this.serverId = serverId;
    this.sink = Objects.requireNonNull(sink, "sink");
  }

  @Override
  public void processCommand(
      String target,
      UserHostmask source,
      String command,
      String line,
      List<String> parsedLine,
      ImmutableMap<String, String> tags
  ) throws IOException {
    // Preserve default behavior first (this keeps User.isAway()/getAwayMessage() accurate).
    super.processCommand(target, source, command, line, parsedLine, tags);

    if (source == null || command == null) return;
    if (!"AWAY".equalsIgnoreCase(command)) return;

    String nick = Objects.toString(source.getNick(), "").trim();
    if (nick.isEmpty()) return;

    // AWAY with a parameter means "now away". AWAY with no parameter means "back".
    // NOTE: In this callback, `command` is already the IRC command and `parsedLine` is the
    // *parameter* list (e.g. [":Gone away"]), not the entire tokenized line.
    boolean nowAway = (parsedLine != null && !parsedLine.isEmpty());
    IrcEvent.AwayState state = nowAway ? IrcEvent.AwayState.AWAY : IrcEvent.AwayState.HERE;

    String msg = null;
    if (nowAway && parsedLine != null && !parsedLine.isEmpty()) {
      // For AWAY, the only parameter is the away message (if present).
      msg = parsedLine.get(0);
      if (msg != null && msg.startsWith(":")) msg = msg.substring(1);
    }

    // Per-event away-notify logs can get noisy; keep at DEBUG.
    log.debug("[{}] away-notify observed via InputParser: nick={} state={} msg={} params={} raw={}",
        serverId, nick, state, msg, parsedLine, line);

    // Opportunistic hostmask capture: away-notify lines include a full prefix (nick!user@host),
    // which we can use to enrich the roster hostmask cache (same propagation path as WHOIS).
    String observedHostmask = null;
    if (line != null && !line.isBlank()) {
      String norm = line;
      // Strip IRCv3 message tags if present (e.g. "@time=...;... :nick!user@host AWAY ...")
      if (norm.startsWith("@")) {
        int sp = norm.indexOf(' ');
        if (sp > 0 && sp < norm.length() - 1) norm = norm.substring(sp + 1);
      }
      if (norm.startsWith(":")) {
        int sp = norm.indexOf(' ');
        if (sp > 1) {
          observedHostmask = norm.substring(1, sp).trim();
        }
      }
    }

    Instant now = Instant.now();
    if (PircbotxUtil.isUsefulHostmask(observedHostmask)) {
      sink.accept(new ServerIrcEvent(serverId,
          new IrcEvent.UserHostmaskObserved(now, "", nick, observedHostmask)));
    }

    sink.accept(new ServerIrcEvent(serverId,
        new IrcEvent.UserAwayStateObserved(now, nick, state, msg)));
  }
}
