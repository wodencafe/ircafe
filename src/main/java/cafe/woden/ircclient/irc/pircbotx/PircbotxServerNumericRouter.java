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
import org.pircbotx.hooks.events.ServerResponseEvent;

/** Routes numeric server responses to the appropriate structured IRC event translators. */
final class PircbotxServerNumericRouter {
  private static final int ERR_LINKCHANNEL = 470;
  private static final int RPL_MONONLINE = 730;
  private static final int RPL_MONOFFLINE = 731;
  private static final int RPL_MONLIST = 732;
  private static final int RPL_ENDOFMONLIST = 733;
  private static final int ERR_MONLISTFULL = 734;

  private final String serverId;
  private final Consumer<String> rememberSelfNickHint;
  private final Consumer<ServerIrcEvent> emit;
  private final PircbotxSaslFailureHandler saslFailures;
  private final PircbotxMonitorEventEmitter monitorEvents;
  private final PircbotxIsupportObserver isupportObserver;
  private final PircbotxRegistrationLifecycleHandler registrationLifecycle;
  private final PircbotxWhoEventEmitter whoEvents;
  private final PircbotxServerResponseEmitter serverResponses;

  PircbotxServerNumericRouter(
      String serverId,
      Consumer<String> rememberSelfNickHint,
      Consumer<ServerIrcEvent> emit,
      PircbotxSaslFailureHandler saslFailures,
      PircbotxMonitorEventEmitter monitorEvents,
      PircbotxIsupportObserver isupportObserver,
      PircbotxRegistrationLifecycleHandler registrationLifecycle,
      PircbotxWhoEventEmitter whoEvents,
      PircbotxServerResponseEmitter serverResponses) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.rememberSelfNickHint =
        Objects.requireNonNull(rememberSelfNickHint, "rememberSelfNickHint");
    this.emit = Objects.requireNonNull(emit, "emit");
    this.saslFailures = Objects.requireNonNull(saslFailures, "saslFailures");
    this.monitorEvents = Objects.requireNonNull(monitorEvents, "monitorEvents");
    this.isupportObserver = Objects.requireNonNull(isupportObserver, "isupportObserver");
    this.registrationLifecycle =
        Objects.requireNonNull(registrationLifecycle, "registrationLifecycle");
    this.whoEvents = Objects.requireNonNull(whoEvents, "whoEvents");
    this.serverResponses = Objects.requireNonNull(serverResponses, "serverResponses");
  }

  void onServerResponse(ServerResponseEvent event) {
    int code;
    try {
      code = event.getCode();
    } catch (Exception ex) {
      return;
    }

    String line = eventLine(event);
    rememberSelfNickHintFromLine(line);

    if (saslFailures.isFailureCode(code)) {
      saslFailures.handle(code, line);
      return;
    }

    if (code == ERR_LINKCHANNEL) {
      emitChannelRedirect(code, line);
    }

    if (isJoinFailureNumeric(code)) {
      emitJoinFailure(code, line);
      return;
    }

    String rawLine = PircbotxLineParseUtil.normalizeIrcLineForParsing(line);
    if ((code == RPL_MONONLINE
            || code == RPL_MONOFFLINE
            || code == RPL_MONLIST
            || code == RPL_ENDOFMONLIST
            || code == ERR_MONLISTFULL)
        && monitorEvents.maybeEmitNumeric(rawLine, line)) {
      return;
    }

    if (code == 5) {
      isupportObserver.observe(rawLine);
      emit.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.ConnectionFeaturesUpdated(Instant.now(), "isupport")));
      serverResponses.emitServerResponseLine(event.getBot(), code, line);
      return;
    }

    if ((code == 4 || code == 324 || code == 376 || code == 422)
        && registrationLifecycle.maybeHandle(code, event.getBot(), line)) {
      return;
    }

    if (whoEvents.maybeEmitNumeric(code, line)) {
      return;
    }

    if (code != 305 && code != 306) {
      serverResponses.emitServerResponseLine(event.getBot(), code, line);
      return;
    }

    emitAwayStatusChanged(code, line);
  }

  private void rememberSelfNickHintFromLine(String line) {
    if (line == null || line.isBlank()) return;
    try {
      String raw = PircbotxLineParseUtil.normalizeIrcLineForParsing(line);
      ParsedIrcLine parsed = PircbotxInboundLineParsers.parseIrcLine(raw);
      if (parsed != null && parsed.params() != null && !parsed.params().isEmpty()) {
        rememberSelfNickHint.accept(parsed.params().get(0));
      }
    } catch (Exception ignored) {
    }
  }

  private void emitChannelRedirect(int code, String line) {
    ParsedChannelRedirect redirect = PircbotxInboundLineParsers.parseChannelRedirect(line);
    if (redirect == null) return;
    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.ChannelRedirected(
                Instant.now(),
                redirect.fromChannel(),
                redirect.toChannel(),
                code,
                redirect.message())));
  }

  private void emitJoinFailure(int code, String line) {
    ParsedJoinFailure joinFailure = PircbotxInboundLineParsers.parseJoinFailure(line);
    if (joinFailure == null) return;
    emit.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.JoinFailed(
                Instant.now(), joinFailure.channel(), code, joinFailure.message())));
  }

  private void emitAwayStatusChanged(int code, String line) {
    PircbotxAwayParsers.ParsedAwayConfirmation away =
        PircbotxAwayParsers.parseRpl305or306Away(line);
    boolean isAway = (code == 306);
    String message = null;
    if (away != null) {
      isAway = away.away();
      message = away.message();
    }
    if (message == null || message.isBlank()) {
      message =
          isAway ? "You have been marked as being away" : "You are no longer marked as being away";
    }

    emit.accept(
        new ServerIrcEvent(
            serverId, new IrcEvent.AwayStatusChanged(Instant.now(), isAway, message)));
  }

  private static boolean isJoinFailureNumeric(int code) {
    return code == 403
        || code == 405
        || code == 471
        || code == 473
        || code == 474
        || code == 475
        || code == 476
        || code == 477;
  }

  private static String eventLine(ServerResponseEvent event) {
    if (event == null) return null;
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
