package cafe.woden.ircclient.irc.pircbotx.parse;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.ircv3.Ircv3CapabilityLine;
import cafe.woden.ircclient.irc.ircv3.spi.*;
import cafe.woden.ircclient.irc.mode.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.playback.*;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.pircbotx.InputParser;
import org.pircbotx.PircBotX;
import org.pircbotx.UserHostmask;
import org.pircbotx.exception.DaoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InputParser hook for a few low-cost IRCv3 capabilities (away-notify, account-notify,
 * extended-join, account-tag).
 *
 * <p>Away-notify arrives as raw lines like:
 *
 * <ul>
 *   <li><code>:nick!user@host AWAY :Gone away for now</code>
 *   <li><code>:nick!user@host AWAY</code>
 * </ul>
 */
final class PircbotxIrcv3InputParser extends InputParser {

  private static final Logger log = LoggerFactory.getLogger(PircbotxIrcv3InputParser.class);

  private final String serverId;
  private final Consumer<ServerIrcEvent> sink;

  private final PircbotxConnectionState conn;
  private final Ircv3StsPolicyService stsPolicies;
  private final PircbotxCapabilityNegotiationSupport capabilityNegotiationSupport;
  private final PircbotxMultilineCapStateSupport multilineCapStateSupport;
  private final PircbotxAccountTagSupport accountTagSupport;
  private final PircbotxPresenceSignalSupport presenceSignalSupport;
  private final PircbotxStandardReplySupport standardReplySupport;
  private final PircbotxTagSignalSupport tagSignalSupport;
  private final Ircv3InboundCommandSignalRuntimeCatalog inboundCommandRuntimeCatalog;
  private final Ircv3MessageMutationRuntimeSupport messageMutationRuntimeSupport;
  private final Ircv3ReadMarkerRuntimeSupport readMarkerRuntimeSupport;
  private final Ircv3TypingRuntimeSupport typingRuntimeSupport;
  private final Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport;
  private final Ircv3EchoMessageRuntimeSupport echoMessageRuntimeSupport;
  private final Ircv3LabeledResponseRuntimeSupport labeledResponseRuntimeSupport;

  PircbotxIrcv3InputParser(
      PircBotX bot,
      String serverId,
      PircbotxConnectionState conn,
      Consumer<ServerIrcEvent> sink,
      Ircv3StsPolicyService stsPolicies,
      Ircv3InboundCommandSignalRuntimeCatalog inboundCommandRuntimeCatalog,
      Ircv3CapabilityNegotiationRuntimeSupport capabilityNegotiationRuntimeSupport,
      Ircv3HistoryTransportRuntimeSupport historyTransportRuntimeSupport,
      Ircv3MessageMutationRuntimeSupport messageMutationRuntimeSupport,
      Ircv3ReadMarkerRuntimeSupport readMarkerRuntimeSupport,
      Ircv3TypingRuntimeSupport typingRuntimeSupport,
      Ircv3AccountTagRuntimeSupport accountTagRuntimeSupport,
      Ircv3ChannelContextRuntimeSupport channelContextRuntimeSupport,
      PircbotxMultilineCapStateSupport multilineCapStateSupport,
      Ircv3StandardReplyRuntimeSupport standardReplyRuntimeSupport,
      Ircv3ServerTimeRuntimeSupport serverTimeRuntimeSupport,
      Ircv3EchoMessageRuntimeSupport echoMessageRuntimeSupport,
      Ircv3LabeledResponseRuntimeSupport labeledResponseRuntimeSupport) {
    super(bot);
    this.serverId = serverId;
    this.sink = Objects.requireNonNull(sink, "sink");
    this.conn = Objects.requireNonNull(conn, "conn");
    this.stsPolicies = Objects.requireNonNull(stsPolicies, "stsPolicies");
    this.inboundCommandRuntimeCatalog =
        Objects.requireNonNull(inboundCommandRuntimeCatalog, "inboundCommandRuntimeCatalog");
    this.messageMutationRuntimeSupport =
        Objects.requireNonNull(messageMutationRuntimeSupport, "messageMutationRuntimeSupport");
    this.readMarkerRuntimeSupport =
        Objects.requireNonNull(readMarkerRuntimeSupport, "readMarkerRuntimeSupport");
    this.typingRuntimeSupport =
        Objects.requireNonNull(typingRuntimeSupport, "typingRuntimeSupport");
    this.multilineCapStateSupport =
        Objects.requireNonNull(multilineCapStateSupport, "multilineCapStateSupport");
    this.serverTimeRuntimeSupport =
        Objects.requireNonNull(serverTimeRuntimeSupport, "serverTimeRuntimeSupport");
    this.echoMessageRuntimeSupport =
        Objects.requireNonNull(echoMessageRuntimeSupport, "echoMessageRuntimeSupport");
    this.labeledResponseRuntimeSupport =
        Objects.requireNonNull(labeledResponseRuntimeSupport, "labeledResponseRuntimeSupport");
    PircbotxCapabilityStateSupport capabilityStateSupport =
        new PircbotxCapabilityStateSupport(this.serverId, this.conn);
    this.capabilityNegotiationSupport =
        new PircbotxCapabilityNegotiationSupport(
            bot,
            this.serverId,
            this.conn,
            this.sink,
            capabilityStateSupport,
            Objects.requireNonNull(
                capabilityNegotiationRuntimeSupport, "capabilityNegotiationRuntimeSupport"),
            Objects.requireNonNull(
                historyTransportRuntimeSupport, "historyTransportRuntimeSupport"));
    this.accountTagSupport =
        new PircbotxAccountTagSupport(
            this.serverId,
            this.sink,
            Objects.requireNonNull(accountTagRuntimeSupport, "accountTagRuntimeSupport"));
    this.presenceSignalSupport =
        new PircbotxPresenceSignalSupport(
            this.serverId, this.sink, this.inboundCommandRuntimeCatalog);
    this.standardReplySupport =
        new PircbotxStandardReplySupport(
            this.serverId,
            this.sink,
            Objects.requireNonNull(standardReplyRuntimeSupport, "standardReplyRuntimeSupport"));
    this.tagSignalSupport =
        new PircbotxTagSignalSupport(
            this.serverId,
            this.sink,
            Objects.requireNonNull(channelContextRuntimeSupport, "channelContextRuntimeSupport"),
            this.messageMutationRuntimeSupport,
            this.readMarkerRuntimeSupport,
            this.typingRuntimeSupport);
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
    Instant now = serverTimeRuntimeSupport.resolve(tags, line).orElseGet(Instant::now);
    String sourceNick = source != null ? Objects.toString(source.getNick(), "").trim() : "";

    // Capture self-query target hints *before* default dispatch so onPrivateMessage/onAction can
    // resolve the destination even when PircBotX doesn't expose recipient accessors.
    captureSelfPrivateMessageTargetHint(now, sourceNick, target, command, line, parsedLine, tags);
    emitLabeledResponseObservation(now, command, tags);

    // Preserve default behavior first (this keeps User.isAway()/getAwayMessage() accurate).
    super.processCommand(target, source, command, line, parsedLine, tags);
    if (command == null) return;

    if ("PONG".equalsIgnoreCase(command)) {
      String lagToken = extractTrailingParamToken(parsedLine, line);
      if (conn.observeLagProbePong(lagToken, System.currentTimeMillis())) {
        return;
      }
      observePassiveLagSampleFromServerTime(tags, line);
      return;
    }

    if ("PING".equalsIgnoreCase(command)) {
      observePassiveLagSampleFromServerTime(tags, line);
      return;
    }

    // Detect CAP state changes for capabilities we care about.
    if ("CAP".equalsIgnoreCase(command) && parsedLine != null && parsedLine.size() >= 2) {
      Ircv3CapabilityLine capLine =
          Ircv3CapabilityLine.parse(parsedLine.get(1), capListFrom(parsedLine));
      if (capLine.hasTokens()) {
        if (capLine.isAction("LS", "NEW", "ACK")) {
          stsPolicies.observeFromCapList(
              serverId, conn.connectedHost(), conn.connectedWithTls(), capLine.normalizedCaps());
        }
        if (capLine.isAction("LS", "NEW", "ACK", "DEL")) {
          multilineCapStateSupport.observe(capLine, conn);
        }
        capabilityNegotiationSupport.observe(capLine, getCapHandlersRemaining());
      }
      return;
    }

    if (emitReadMarkerIfSupported(now, sourceNick, command, line, parsedLine, tags)) {
      return;
    }

    if (standardReplySupport.emitIfSupported(now, command, line, parsedLine, tags)) {
      return;
    }

    if (emitMessageRedactionIfSupported(now, sourceNick, command, line, parsedLine, tags)) {
      return;
    }

    if (source == null) return;
    String nick = sourceNick;
    if (nick.isEmpty()) return;

    accountTagSupport.observe(now, nick, command, target, tags);

    tagSignalSupport.emitObservedSignals(now, nick, target, command, parsedLine, tags);

    if (presenceSignalSupport.observeIdentityChange(now, nick, command, line, parsedLine)) {
      return;
    }

    presenceSignalSupport.observe(now, nick, command, line, parsedLine);
  }

  private void emitLabeledResponseObservation(
      Instant at, String command, ImmutableMap<String, String> tags) {
    labeledResponseRuntimeSupport
        .fromTags(command, tags)
        .ifPresent(
            observed ->
                sink.accept(
                    new ServerIrcEvent(
                        serverId,
                        new IrcEvent.LabeledResponseObserved(
                            at,
                            command,
                            observed.label(),
                            observed.outcome()
                                == Ircv3LabeledResponseRuntimeSupport.Outcome.FAILURE))));
  }

  private boolean emitReadMarkerIfSupported(
      Instant at,
      String sourceNick,
      String command,
      String rawLine,
      List<String> parsedLine,
      ImmutableMap<String, String> tags) {
    return readMarkerRuntimeSupport
        .fromCommand(new Ircv3InboundCommandRequest(sourceNick, command, rawLine, parsedLine, tags))
        .map(
            readMarker -> {
              String from = sourceNick.isBlank() ? "server" : sourceNick;
              sink.accept(
                  new ServerIrcEvent(
                      serverId,
                      new IrcEvent.ReadMarkerObserved(
                          at, from, readMarker.target(), readMarker.marker())));
              return true;
            })
        .orElse(false);
  }

  private boolean emitMessageRedactionIfSupported(
      Instant at,
      String sourceNick,
      String command,
      String rawLine,
      List<String> parsedLine,
      ImmutableMap<String, String> tags) {
    return messageMutationRuntimeSupport
        .redactionFromCommand(
            new Ircv3InboundCommandRequest(sourceNick, command, rawLine, parsedLine, tags))
        .map(
            redaction -> {
              String from = sourceNick.isBlank() ? "server" : sourceNick;
              String conversationTarget =
                  Ircv3ChannelContextPolicy.resolveConversationTarget(
                      redaction.target(), sourceNick);
              sink.accept(
                  new ServerIrcEvent(
                      serverId,
                      new IrcEvent.MessageRedactionObserved(
                          at, from, conversationTarget, redaction.messageId())));
              return true;
            })
        .orElse(false);
  }

  @Override
  public void processServerResponse(int code, String line, List<String> parsedLine) {
    if (code == 1) {
      conn.markRegistrationComplete();
    }
    try {
      super.processServerResponse(code, line, parsedLine);
      return;
    } catch (RuntimeException ex) {
      if (isIgnorableMissingChannelNumeric(code, parsedLine, ex)) {
        log.debug(
            "[{}] ignoring late numeric {} for channel already removed from DAO: channel={} line={}",
            serverId,
            code,
            channelForIgnorableMissingChannelNumeric(code, parsedLine),
            Objects.toString(line, ""));
        return;
      }
      if (isIgnorableDuplicateUserHostmaskNumeric(code, ex)) {
        log.debug(
            "[{}] ignoring duplicate-user numeric {} from PircBotX DAO: line={}",
            serverId,
            code,
            Objects.toString(line, ""));
        return;
      }
      if (code != 324) {
        throw ex;
      }
      PircbotxChannelModeParsers.ParsedRpl324 parsed =
          PircbotxChannelModeParsers.parseRpl324Fallback(line, parsedLine);
      if (parsed != null) {
        log.warn(
            "[{}] recovered from PircBotX RPL 324 parse failure ({}): channel={} details={} line={}",
            serverId,
            ex.getClass().getSimpleName(),
            parsed.channel(),
            parsed.details(),
            Objects.toString(line, ""));
        sink.accept(
            new ServerIrcEvent(
                serverId,
                ChannelModeObservationFactory.fromNumeric324Fallback(
                    Instant.now(), parsed.channel(), parsed.details())));
      } else {
        log.warn(
            "[{}] recovered from PircBotX RPL 324 parse failure but could not parse mode line: line={} parsed={}",
            serverId,
            Objects.toString(line, ""),
            parsedLine,
            ex);
      }
    }
  }

  private static boolean isIgnorableMissingChannelNumeric(
      int code, List<String> parsedLine, RuntimeException ex) {
    if (!(ex instanceof DaoException dao)
        || dao.getReason() != DaoException.Reason.UNKNOWN_CHANNEL) {
      return false;
    }
    return !channelForIgnorableMissingChannelNumeric(code, parsedLine).isBlank();
  }

  private static boolean isIgnorableDuplicateUserHostmaskNumeric(int code, RuntimeException ex) {
    if (code != 353 || ex instanceof DaoException) {
      return false;
    }
    String message = Objects.toString(ex.getMessage(), "");
    return message.contains("Cannot create a user from hostmask that already exists");
  }

  private static String channelForIgnorableMissingChannelNumeric(
      int code, List<String> parsedLine) {
    if (parsedLine == null || parsedLine.isEmpty()) return "";
    int channelIndex =
        switch (code) {
          case 353 -> 2;
          case 366, 367, 368, 728, 729 -> 1;
          default -> -1;
        };
    if (channelIndex < 0 || parsedLine.size() <= channelIndex) return "";
    return stripLeadingColon(parsedLine.get(channelIndex)).trim();
  }

  private void observePassiveLagSampleFromServerTime(
      ImmutableMap<String, String> tags, String rawLine) {
    serverTimeRuntimeSupport
        .passiveLag(tags, rawLine, System.currentTimeMillis())
        .ifPresent(sample -> conn.observePassiveLagSample(sample.lagMs(), sample.observedAtMs()));
  }

  private static String extractTrailingParamToken(List<String> parsedLine, String rawLine) {
    if (parsedLine != null) {
      for (int i = parsedLine.size() - 1; i >= 0; i--) {
        String token = stripLeadingColon(parsedLine.get(i)).trim();
        if (!token.isEmpty()) return token;
      }
    }

    String raw = Objects.toString(rawLine, "").trim();
    if (raw.isEmpty()) return "";
    int tailStart = raw.lastIndexOf(' ');
    String tail = tailStart >= 0 ? raw.substring(tailStart + 1) : raw;
    return stripLeadingColon(tail).trim();
  }

  private static String capListFrom(List<String> parsedLine) {
    if (parsedLine == null || parsedLine.size() < 3) return "";
    int start = 2;
    if ("*".equals(parsedLine.get(start)) && parsedLine.size() > start + 1) {
      start++;
    }

    StringBuilder out = new StringBuilder();
    for (int i = start; i < parsedLine.size(); i++) {
      String token = Objects.toString(parsedLine.get(i), "").trim();
      if (token.isEmpty()) continue;
      if (out.length() > 0) out.append(' ');
      out.append(token);
    }
    return out.toString().trim();
  }

  private static String stripLeadingColon(String raw) {
    String s = Objects.toString(raw, "").trim();
    if (s.startsWith(":")) s = s.substring(1).trim();
    return s;
  }

  private void captureSelfPrivateMessageTargetHint(
      Instant at,
      String fromNick,
      String rawTarget,
      String command,
      String rawLine,
      List<String> parsedLine,
      ImmutableMap<String, String> tags) {
    Ircv3InboundTagRequest request =
        new Ircv3InboundTagRequest(
            command,
            fromNick,
            rawTarget,
            parsedLine,
            tags,
            rawLine,
            at == null ? 0L : at.toEpochMilli(),
            selfNickAliases());
    echoMessageRuntimeSupport
        .targetHint(request)
        .ifPresent(
            hint ->
                conn.rememberPrivateTargetHint(
                    fromNick,
                    hint.target(),
                    hint.kind(),
                    hint.payload(),
                    hint.messageId(),
                    at == null ? System.currentTimeMillis() : at.toEpochMilli()));
  }

  private List<String> selfNickAliases() {
    String hinted = Objects.toString(conn.selfNickHint(), "").trim();
    String fromBot = "";
    try {
      PircBotX liveBot = this.bot;
      fromBot = liveBot == null ? "" : Objects.toString(liveBot.getNick(), "").trim();
    } catch (Exception ignored) {
    }
    if (hinted.isEmpty()) return fromBot.isEmpty() ? List.of() : List.of(fromBot);
    if (fromBot.isEmpty() || hinted.equalsIgnoreCase(fromBot)) return List.of(hinted);
    return List.of(hinted, fromBot);
  }
}
