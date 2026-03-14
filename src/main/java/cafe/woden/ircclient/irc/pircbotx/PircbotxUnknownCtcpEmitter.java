package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.events.UnknownEvent;

/** Emits structured CTCP request events from raw unknown PRIVMSG/NOTICE lines. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PircbotxUnknownCtcpEmitter {
  @NonNull private final String serverId;
  @NonNull private final Consumer<ServerIrcEvent> emit;
  @NonNull private final BiPredicate<PircBotX, String> nickMatchesSelf;
  @NonNull private final BiPredicate<PircBotX, String> selfEchoDetector;
  @NonNull private final Function<PircBotX, String> selfNickResolver;

  boolean maybeEmit(
      UnknownEvent event,
      String lineWithTags,
      String normalizedRawLine,
      ParsedIrcLine parsedRawLine) {
    ParsedIrcLine parsed = parsedRawLine;
    if (parsed == null) {
      parsed = PircbotxInboundLineParsers.parseIrcLine(normalizedRawLine);
    }
    if (parsed == null) return false;

    String rawCmd = Objects.toString(parsed.command(), "").trim().toUpperCase(Locale.ROOT);
    if (!"PRIVMSG".equals(rawCmd) && !"NOTICE".equals(rawCmd)) return false;

    String rawTrailing = PircbotxInboundLineParsers.rawTrailingFromIrcLine(lineWithTags);
    ParsedCtcp ctcp = parseCtcpPayload(rawTrailing);
    if (ctcp == null) {
      ctcp = parseCtcpPayload(parsed.trailing());
    }
    if (ctcp == null) return false;

    String from =
        Objects.toString(PircbotxInboundLineParsers.nickFromPrefix(parsed.prefix()), "").trim();
    if (from.isEmpty()) {
      from = Objects.toString(event != null ? event.getNick() : "", "").trim();
    }
    if (from.isEmpty()) from = "server";

    PircBotX bot = event != null ? event.getBot() : null;
    if (nickMatchesSelf.test(bot, from)) return true;
    if (selfEchoDetector.test(bot, from)) return true;

    String botNick = Objects.toString(selfNickResolver.apply(bot), "").trim();
    List<String> params = parsed.params();
    String destination =
        (params == null || params.isEmpty()) ? "" : Objects.toString(params.getFirst(), "").trim();
    String channel = PircbotxLineParseUtil.looksLikeChannel(destination) ? destination : null;
    if (channel == null
        && !destination.isBlank()
        && !botNick.isBlank()
        && !destination.equalsIgnoreCase(botNick)) {
      return true;
    }

    Instant at = Ircv3ServerTime.parseServerTimeFromRawLine(lineWithTags);
    if (at == null) at = Instant.now();
    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.CtcpRequestReceived(
                at, from, ctcp.commandUpper(), ctcp.argument(), channel)));
    return true;
  }

  private static ParsedCtcp parseCtcpPayload(String message) {
    if (!PircbotxUtil.isCtcpWrapped(message)) return null;
    String inner = message.substring(1, message.length() - 1).trim();
    if (inner.isEmpty()) return null;

    int sp = inner.indexOf(' ');
    String cmd = (sp >= 0) ? inner.substring(0, sp) : inner;
    String arg = (sp >= 0) ? inner.substring(sp + 1).trim() : "";
    String commandUpper = Objects.toString(cmd, "").trim().toUpperCase(Locale.ROOT);
    if (commandUpper.isEmpty()) return null;
    return new ParsedCtcp(commandUpper, arg.isBlank() ? null : arg);
  }

  private record ParsedCtcp(String commandUpper, String argument) {}
}
