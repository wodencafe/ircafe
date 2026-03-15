package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxMonitorEventEmitter;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxServerResponseEmitter;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.events.UnknownEvent;
import cafe.woden.ircclient.irc.pircbotx.emit.PircbotxChatHistoryBatchCollector;

/** Pre-routes unknown IRC lines before falling through to the lower-level fallback handlers. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PircbotxUnknownEventRouter {
  @NonNull private final String serverId;
  @NonNull private final Consumer<String> rememberSelfNickHint;
  @NonNull private final Function<PircBotX, String> selfNickResolver;
  @NonNull private final PircbotxServerResponseEmitter serverResponses;
  @NonNull private final PircbotxMonitorEventEmitter monitorEvents;
  @NonNull private final PircbotxChatHistoryBatchCollector chatHistoryBatches;
  @NonNull private final PircbotxUnknownCtcpEmitter unknownCtcp;
  @NonNull private final PircbotxUnknownLineFallbackHandler unknownLineFallback;
  @NonNull private final Consumer<ServerIrcEvent> emit;

  void handle(UnknownEvent event) {
    String line = eventLine(event);
    String rawLine = PircbotxLineParseUtil.normalizeIrcLineForParsing(line);
    ParsedIrcLine parsedRawLine = PircbotxInboundLineParsers.parseIrcLine(rawLine);

    if (parsedRawLine != null) {
      Instant at = Ircv3ServerTime.parseServerTimeFromRawLine(line);
      if (at == null) at = Instant.now();
      String myNick = selfNickResolver.apply(event != null ? event.getBot() : null);
      serverResponses.emitChannelListEvent(at, parsedRawLine, myNick);
      serverResponses.emitChannelBanListEvent(at, parsedRawLine);
    }

    if (parsedRawLine != null
        && parsedRawLine.command() != null
        && PircbotxLineParseUtil.looksNumeric(parsedRawLine.command())
        && parsedRawLine.params() != null
        && !parsedRawLine.params().isEmpty()) {
      rememberSelfNickHint.accept(parsedRawLine.params().get(0));
    }

    ParsedInviteLine parsedInvite = PircbotxInboundLineParsers.parseInviteLine(parsedRawLine);
    if (parsedInvite != null) {
      Instant at = Ircv3ServerTime.parseServerTimeFromRawLine(line);
      if (at == null) at = Instant.now();
      String from = Objects.toString(parsedInvite.fromNick(), "").trim();
      if (from.isEmpty()) from = "server";
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.InvitedToChannel(
                  at,
                  parsedInvite.channel(),
                  from,
                  parsedInvite.inviteeNick(),
                  parsedInvite.reason(),
                  true)));
      return;
    }

    ParsedWallopsLine parsedWallops = PircbotxInboundLineParsers.parseWallopsLine(parsedRawLine);
    if (parsedWallops != null) {
      Instant at = Ircv3ServerTime.parseServerTimeFromRawLine(line);
      if (at == null) at = Instant.now();
      emit.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.WallopsReceived(at, parsedWallops.from(), parsedWallops.message())));
      return;
    }

    if (monitorEvents.maybeEmitNumeric(rawLine, line)) {
      return;
    }

    if (chatHistoryBatches.handleBatchControlLine(rawLine)) {
      return;
    }

    if (unknownCtcp.maybeEmit(event, line, rawLine, parsedRawLine)) {
      return;
    }

    unknownLineFallback.handle(event, line, rawLine);
  }

  private static String eventLine(UnknownEvent event) {
    if (event == null) return "";
    Object line = reflectCall(event, "getLine");
    if (line == null) line = reflectCall(event, "getRawLine");
    if (line != null) {
      String value = String.valueOf(line);
      if (!value.isBlank()) return value;
    }
    return String.valueOf(event);
  }

  private static Object reflectCall(Object target, String method) {
    if (target == null || method == null) return null;
    try {
      java.lang.reflect.Method m = target.getClass().getMethod(method);
      return m.invoke(target);
    } catch (Exception ignored) {
      return null;
    }
  }
}
