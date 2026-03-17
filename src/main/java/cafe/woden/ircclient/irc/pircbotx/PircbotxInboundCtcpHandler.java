package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.parse.*;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.hooks.events.FingerEvent;
import org.pircbotx.hooks.types.GenericCTCPEvent;
import org.pircbotx.hooks.types.GenericChannelUserEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles inbound library-level CTCP requests and optional auto-replies. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class PircbotxInboundCtcpHandler {
  private static final Logger log = LoggerFactory.getLogger(PircbotxInboundCtcpHandler.class);

  @NonNull private final String serverId;
  @NonNull private final Supplier<String> selfNickHintSupplier;
  @NonNull private final BiPredicate<PircBotX, String> nickMatchesSelf;
  @NonNull private final BiPredicate<PircBotX, String> selfEchoDetector;
  @NonNull private final Function<PircBotX, String> selfNickResolver;
  @NonNull private final Function<PircBotX, String> botNickResolver;
  @NonNull private final Function<Object, String> rawLineResolver;
  @NonNull private final Function<Object, String> privateTargetResolver;
  @NonNull private final BiConsumer<String, User> hostmaskObserver;
  @NonNull private final Consumer<ServerIrcEvent> emit;
  @NonNull private final PircbotxBridgeListener.CtcpRequestHandler autoReplySender;

  void onGenericCtcp(GenericCTCPEvent event) {
    String command = deriveCommand(event);
    String argument = deriveArgument(event);
    CtcpRoute route = prepareRoute(event, command, argument);
    if (route == null) return;

    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.CtcpRequestReceived(
                route.at(), route.from(), route.command(), route.argument(), route.channel())));

    log.debug(
        "[{}] CTCPDBG autoreply-eval shouldAutoReply={} pmDest={} pmDestKnown={} botNick={} rawIsPrivmsg={} rawIsNotice={} cmd={} arg={}",
        serverId,
        route.shouldAutoReply(),
        route.pmDest(),
        route.pmDestKnown(),
        route.botNick(),
        route.rawIsPrivmsg(),
        route.rawIsNotice(),
        route.command(),
        route.argument());
    if (route.shouldAutoReply()) {
      String wrapped =
          "\u0001"
              + route.command()
              + ((route.argument() != null && !route.argument().isBlank())
                  ? (" " + route.argument())
                  : "")
              + "\u0001";
      log.debug(
          "[{}] CTCPDBG autoreply-send to={} wrapped={}",
          serverId,
          route.from(),
          wrapped.replace('\u0001', '|'));
      autoReplySender.handle(route.bot(), route.from(), wrapped);
    }
  }

  void onFinger(FingerEvent event) {
    CtcpRoute route = prepareRoute(event, "FINGER", null);
    if (route == null) return;

    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.CtcpRequestReceived(
                route.at(), route.from(), route.command(), route.argument(), route.channel())));

    if (route.shouldAutoReply()) {
      autoReplySender.handle(route.bot(), route.from(), "\u0001FINGER\u0001");
    }
  }

  private CtcpRoute prepareRoute(
      GenericChannelUserEvent event, String commandUpper, String argument) {
    Instant at = Ircv3ServerTime.orNow(Ircv3ServerTime.fromEvent(event), Instant.now());
    log.info("CTCP: {}", event);

    PircBotX bot = event != null ? event.getBot() : null;
    String botNick = Objects.toString(selfNickResolver.apply(bot), "").trim();
    String botNickDirect = Objects.toString(botNickResolver.apply(bot), "").trim();
    String from =
        Objects.toString(
                event != null && event.getUser() != null ? event.getUser().getNick() : "", "")
            .trim();
    String rawLine = Objects.toString(rawLineResolver.apply(event), "");
    String normalizedRaw = PircbotxLineParseUtil.normalizeIrcLineForParsing(rawLine);
    ParsedIrcLine rawParsed = PircbotxInboundLineParsers.parseIrcLine(normalizedRaw);
    String rawFrom =
        Objects.toString(
                rawParsed == null
                    ? ""
                    : PircbotxInboundLineParsers.nickFromPrefix(rawParsed.prefix()),
                "")
            .trim();
    if (from.isBlank() && !rawFrom.isBlank()) {
      from = rawFrom;
    }
    if (from.isBlank()) from = "server";

    String rawCmd =
        (rawParsed != null && rawParsed.command() != null)
            ? rawParsed.command().toUpperCase(Locale.ROOT)
            : "";
    boolean rawIsNotice = "NOTICE".equals(rawCmd);
    boolean rawIsPrivmsg = "PRIVMSG".equals(rawCmd);
    String selfHintBefore = Objects.toString(selfNickHintSupplier.get(), "").trim();
    boolean fromSelf = nickMatchesSelf.test(bot, from);
    boolean rawFromSelf = nickMatchesSelf.test(bot, rawFrom);
    boolean selfEcho = selfEchoDetector.test(bot, from);
    String selfHintAfter = Objects.toString(selfNickHintSupplier.get(), "").trim();

    log.debug(
        "[{}] CTCPDBG pre cmdClass={} from={} rawFrom={} rawCmd={} selfHintBefore={} selfHintAfter={} botNick={} botNickDirect={} fromSelf={} rawFromSelf={} selfEcho={} raw={}",
        serverId,
        (event == null) ? "null" : event.getClass().getSimpleName(),
        from,
        rawFrom,
        rawCmd,
        selfHintBefore,
        selfHintAfter,
        botNick,
        botNickDirect,
        fromSelf,
        rawFromSelf,
        selfEcho,
        normalizedRaw);

    if (fromSelf) {
      log.debug("[{}] CTCPDBG drop reason=self-from from={}", serverId, from);
      return null;
    }
    if (rawFromSelf) {
      log.debug("[{}] CTCPDBG drop reason=self-raw-from rawFrom={}", serverId, rawFrom);
      return null;
    }
    if (selfEcho) {
      log.debug("[{}] CTCPDBG drop reason=self-echo from={}", serverId, from);
      return null;
    }

    String pmDest = null;
    if (event != null && event.getChannel() == null) {
      pmDest = Objects.toString(privateTargetResolver.apply(event), "").trim();
      if (pmDest.isBlank()
          && rawParsed != null
          && rawParsed.params() != null
          && !rawParsed.params().isEmpty()) {
        pmDest = Objects.toString(rawParsed.params().getFirst(), "").trim();
      }
      boolean pmDestKnown = !pmDest.isBlank();
      boolean destMismatch = pmDestKnown && !botNick.isBlank() && !pmDest.equalsIgnoreCase(botNick);
      log.debug(
          "[{}] CTCPDBG route channel={} pmDest={} pmDestKnown={} botNick={} rawIsPrivmsg={} rawIsNotice={} destMismatch={}",
          serverId,
          event.getChannel(),
          pmDest,
          pmDestKnown,
          botNick,
          rawIsPrivmsg,
          rawIsNotice,
          destMismatch);
      if (log.isDebugEnabled()) {
        log.debug(
            "[{}] CTCP parsed rawCmd={} pmDest={} from={} botNick={} raw={}",
            serverId,
            rawCmd,
            pmDest,
            from,
            botNick,
            normalizedRaw);
      }
      if (destMismatch) {
        log.debug(
            "[{}] CTCPDBG drop reason=dest-not-self pmDest={} botNick={}",
            serverId,
            pmDest,
            botNick);
        return null;
      }
    }

    if (event != null && event.getUser() != null) {
      String ch = (event.getChannel() != null) ? event.getChannel().getName() : "";
      hostmaskObserver.accept(ch, event.getUser());
    }

    String channel =
        (event != null && event.getChannel() != null) ? event.getChannel().getName() : null;
    boolean pmDestKnown = pmDest != null && !pmDest.isBlank();
    boolean shouldAutoReply =
        event != null
            && event.getChannel() == null
            && !botNick.isBlank()
            && (!pmDestKnown || pmDest.equalsIgnoreCase(botNick));
    return new CtcpRoute(
        at,
        bot,
        from,
        commandUpper,
        argument,
        channel,
        shouldAutoReply,
        pmDest,
        pmDestKnown,
        botNick,
        rawIsPrivmsg,
        rawIsNotice);
  }

  private static String deriveArgument(GenericCTCPEvent event) {
    if (event == null) return null;
    try {
      java.lang.reflect.Method m = event.getClass().getMethod("getPingValue");
      Object v = m.invoke(event);
      if (v != null) return v.toString();
    } catch (Exception ignored) {
    }
    return null;
  }

  private static String deriveCommand(GenericCTCPEvent event) {
    String simple = (event == null) ? "CTCP" : event.getClass().getSimpleName();
    String cmd =
        simple.endsWith("Event") ? simple.substring(0, simple.length() - "Event".length()) : simple;
    cmd = cmd.toUpperCase(Locale.ROOT);
    return cmd.isBlank() ? "CTCP" : cmd;
  }

  private record CtcpRoute(
      Instant at,
      PircBotX bot,
      String from,
      String command,
      String argument,
      String channel,
      boolean shouldAutoReply,
      String pmDest,
      boolean pmDestKnown,
      String botNick,
      boolean rawIsPrivmsg,
      boolean rawIsNotice) {}
}
