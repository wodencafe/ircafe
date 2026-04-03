package cafe.woden.ircclient.irc.pircbotx.parse;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.mode.*;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import cafe.woden.ircclient.irc.pircbotx.support.PircbotxUtil;
import cafe.woden.ircclient.irc.playback.*;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
  private final PircbotxMultilineCapStateSupport multilineCapStateSupport =
      new PircbotxMultilineCapStateSupport();
  private final PircbotxAccountTagSupport accountTagSupport;
  private final PircbotxPresenceSignalSupport presenceSignalSupport;
  private final PircbotxStandardReplySupport standardReplySupport;
  private final PircbotxTagSignalSupport tagSignalSupport;

  PircbotxIrcv3InputParser(
      PircBotX bot,
      String serverId,
      PircbotxConnectionState conn,
      Consumer<ServerIrcEvent> sink,
      Ircv3StsPolicyService stsPolicies) {
    super(bot);
    this.serverId = serverId;
    this.sink = Objects.requireNonNull(sink, "sink");
    this.conn = Objects.requireNonNull(conn, "conn");
    this.stsPolicies = Objects.requireNonNull(stsPolicies, "stsPolicies");
    PircbotxCapabilityStateSupport capabilityStateSupport =
        new PircbotxCapabilityStateSupport(this.serverId, this.conn);
    this.capabilityNegotiationSupport =
        new PircbotxCapabilityNegotiationSupport(
            bot, this.serverId, this.conn, this.sink, capabilityStateSupport);
    this.accountTagSupport = new PircbotxAccountTagSupport(this.serverId, this.sink);
    this.presenceSignalSupport = new PircbotxPresenceSignalSupport(this.serverId, this.sink);
    this.standardReplySupport = new PircbotxStandardReplySupport(this.serverId, this.sink);
    this.tagSignalSupport = new PircbotxTagSignalSupport(this.serverId, this.sink);
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
    Instant now = Instant.now();
    String sourceNick = source != null ? Objects.toString(source.getNick(), "").trim() : "";

    // Capture self-query target hints *before* default dispatch so onPrivateMessage/onAction can
    // resolve the destination even when PircBotX doesn't expose recipient accessors.
    captureSelfPrivateMessageTargetHint(now, sourceNick, target, command, line, parsedLine, tags);

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
      ParsedCapLine capLine = ParsedCapLine.parse(parsedLine.get(1), capListFrom(parsedLine));
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

    if ("MARKREAD".equalsIgnoreCase(command)) {
      String markerTarget = firstParam(parsedLine);
      String marker = secondParam(parsedLine);
      String from = source != null ? Objects.toString(source.getNick(), "").trim() : "server";
      sink.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.ReadMarkerObserved(now, from, markerTarget, marker)));
      return;
    }

    if (standardReplySupport.emitIfSupported(now, command, line, parsedLine, tags)) {
      return;
    }

    if ("REDACT".equalsIgnoreCase(command)) {
      String redactTarget = firstParam(parsedLine);
      String redactMsgId = secondParam(parsedLine);
      if (!redactMsgId.isBlank()) {
        String from = sourceNick.isBlank() ? "server" : sourceNick;
        String convTarget =
            PircbotxTagSignalSupport.resolveConversationTarget(redactTarget, sourceNick);
        sink.accept(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.MessageRedactionObserved(now, from, convTarget, redactMsgId)));
      }
      return;
    }

    if (source == null) return;
    String nick = sourceNick;
    if (nick.isEmpty()) return;

    accountTagSupport.observe(now, nick, command, target, tags);

    tagSignalSupport.emitObservedSignals(now, nick, target, command, parsedLine, tags);

    if ("SETNAME".equalsIgnoreCase(command)) {
      String realName = firstParam(parsedLine);
      sink.accept(
          new ServerIrcEvent(
              serverId,
              new IrcEvent.UserSetNameObserved(
                  now, nick, realName, IrcEvent.UserSetNameObserved.Source.SETNAME)));
      return;
    }

    if ("CHGHOST".equalsIgnoreCase(command)) {
      String user = firstParam(parsedLine);
      String host = secondParam(parsedLine);
      sink.accept(
          new ServerIrcEvent(serverId, new IrcEvent.UserHostChanged(now, nick, user, host)));

      if (!user.isBlank() && !host.isBlank()) {
        String hm = nick + "!" + user + "@" + host;
        sink.accept(
            new ServerIrcEvent(serverId, new IrcEvent.UserHostmaskObserved(now, "", nick, hm)));
      }
      return;
    }

    if (!presenceSignalSupport.observes(command)) return;
    presenceSignalSupport.observe(now, nick, command, line, parsedLine);
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
    Instant serverTaggedAt = serverTimeFromTagsOrLine(tags, rawLine);
    if (serverTaggedAt == null) return;
    long nowMs = System.currentTimeMillis();
    conn.observePassiveLagSample(nowMs - serverTaggedAt.toEpochMilli(), nowMs);
  }

  private static Instant serverTimeFromTagsOrLine(
      ImmutableMap<String, String> tags, String rawLine) {
    String tagged = Ircv3Tags.firstTagValue(tags, "time");
    if (!tagged.isBlank()) {
      try {
        return Instant.parse(tagged);
      } catch (Exception ignored) {
      }
    }
    return Ircv3ServerTime.parseServerTimeFromRawLine(rawLine);
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

  private static String firstParam(List<String> parsedLine) {
    if (parsedLine == null || parsedLine.isEmpty()) return "";
    return stripLeadingColon(parsedLine.get(0));
  }

  private static String secondParam(List<String> parsedLine) {
    if (parsedLine == null || parsedLine.size() < 2) return "";
    return stripLeadingColon(parsedLine.get(1));
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
    String cmd = Objects.toString(command, "").trim().toUpperCase(Locale.ROOT);
    if (!"PRIVMSG".equals(cmd)) return;
    if (!isSelfNick(fromNick)) return;

    String messageTarget = stripLeadingColon(rawTarget);
    if (messageTarget.isBlank()) {
      messageTarget = firstParam(parsedLine);
    }
    if (messageTarget.isBlank()
        || PircbotxTagSignalSupport.isChannelName(messageTarget)
        || looksLikeSelfTarget(messageTarget)) {
      return;
    }

    String first = firstParam(parsedLine);
    String second = secondParam(parsedLine);
    String payload = second;
    if (payload.isBlank() && !first.isBlank() && !first.equalsIgnoreCase(messageTarget)) {
      payload = first;
    }
    if (payload.isBlank()) {
      payload = trailingParam(rawLine);
    }
    String action = PircbotxUtil.parseCtcpAction(payload);
    String kind = action == null ? "PRIVMSG" : "ACTION";
    String normalizedPayload = action == null ? payload : action;
    String msgId =
        PircbotxTagSignalSupport.firstTag(tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid");
    conn.rememberPrivateTargetHint(
        fromNick,
        messageTarget,
        kind,
        normalizedPayload,
        msgId,
        at == null ? System.currentTimeMillis() : at.toEpochMilli());
  }

  private boolean isSelfNick(String nick) {
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return false;

    String hinted = Objects.toString(conn.selfNickHint(), "").trim();
    if (!hinted.isEmpty() && n.equalsIgnoreCase(hinted)) {
      return true;
    }

    try {
      PircBotX liveBot = this.bot;
      String fromBot = liveBot == null ? "" : Objects.toString(liveBot.getNick(), "").trim();
      return !fromBot.isEmpty() && n.equalsIgnoreCase(fromBot);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean looksLikeSelfTarget(String target) {
    String t = Objects.toString(target, "").trim();
    if (t.isEmpty()) return false;
    String hinted = Objects.toString(conn.selfNickHint(), "").trim();
    if (!hinted.isEmpty() && t.equalsIgnoreCase(hinted)) return true;
    try {
      PircBotX liveBot = this.bot;
      String fromBot = liveBot == null ? "" : Objects.toString(liveBot.getNick(), "").trim();
      return !fromBot.isEmpty() && t.equalsIgnoreCase(fromBot);
    } catch (Exception ignored) {
      return false;
    }
  }

  private static String trailingParam(String rawLine) {
    String line = Objects.toString(rawLine, "");
    int idx = line.indexOf(" :");
    if (idx < 0 || idx + 2 >= line.length()) return "";
    return line.substring(idx + 2).trim();
  }
}
