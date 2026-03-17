package cafe.woden.ircclient.irc.pircbotx;

import java.io.IOException;
import java.util.Objects;
import java.util.function.ObjLongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;

/**
 * Small PircBotX wrapper that watches outbound transport PINGs so IRCafe can reuse the library's
 * built-in keepalive traffic for lag measurement.
 */
class PircbotxLagAwareBot extends PircBotX {
  private static final ObjLongConsumer<String> NO_OP_OBSERVER = (token, sentAtMs) -> {};
  private static final Pattern OUTBOUND_PING_PATTERN =
      Pattern.compile("^PING\\s+:?([^\\s]+)\\s*$", Pattern.CASE_INSENSITIVE);

  private volatile ObjLongConsumer<String> lagProbeObserver = NO_OP_OBSERVER;

  PircbotxLagAwareBot(Configuration configuration) {
    super(configuration);
  }

  void setLagProbeObserver(ObjLongConsumer<String> observer) {
    lagProbeObserver = observer == null ? NO_OP_OBSERVER : observer;
  }

  @Override
  protected void sendRawLineToServer(String line) throws IOException {
    super.sendRawLineToServer(line);

    String token = outboundPingToken(line);
    if (token.isBlank()) return;
    lagProbeObserver.accept(token, System.currentTimeMillis());
  }

  static String outboundPingToken(String rawLine) {
    String raw = Objects.toString(rawLine, "").trim();
    if (raw.isEmpty()) return "";
    Matcher matcher = OUTBOUND_PING_PATTERN.matcher(raw);
    if (!matcher.matches()) return "";
    return Objects.toString(matcher.group(1), "").trim();
  }
}
