package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.NotificationRuleMatch;
import cafe.woden.ircclient.app.api.NotificationRuleMatcherPort;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.model.TargetRef;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
@ApplicationLayer
@RequiredArgsConstructor
public class MediatorInboundEventPreparationService {

  @Qualifier("ircMediatorInteractionPort")
  private final IrcMediatorInteractionPort irc;

  private final NotificationRuleMatcherPort notificationRuleMatcherPort;
  private final InboundIgnorePolicyPort inboundIgnorePolicy;

  boolean shouldPrepareOffEdt(ServerIrcEvent se) {
    if (se == null || se.event() == null) return false;
    // Start with the hottest text-matching paths and keep event ordering unchanged.
    return se.event() instanceof IrcEvent.ChannelMessage
        || se.event() instanceof IrcEvent.ChannelAction
        || se.event() instanceof IrcEvent.PrivateMessage
        || se.event() instanceof IrcEvent.PrivateAction
        || se.event() instanceof IrcEvent.Notice
        || se.event() instanceof IrcEvent.CtcpRequestReceived;
  }

  PreparedServerIrcEvent prepare(ServerIrcEvent se) {
    if (se == null) return PreparedServerIrcEvent.empty();

    String sid = se.serverId();
    IrcEvent event = se.event();
    if (event instanceof IrcEvent.ChannelMessage ev) {
      return new PreparedServerIrcEvent(
          se,
          prepareChannelMessage(sid, ev),
          PreparedPrivateMessage.empty(),
          PreparedPrivateAction.empty(),
          PreparedNotice.empty(),
          PreparedCtcpRequest.empty());
    }
    if (event instanceof IrcEvent.ChannelAction ev) {
      return new PreparedServerIrcEvent(
          se,
          prepareChannelAction(sid, ev),
          PreparedPrivateMessage.empty(),
          PreparedPrivateAction.empty(),
          PreparedNotice.empty(),
          PreparedCtcpRequest.empty());
    }
    if (event instanceof IrcEvent.PrivateMessage ev) {
      return new PreparedServerIrcEvent(
          se,
          PreparedChannelText.empty(),
          preparePrivateMessage(sid, ev),
          PreparedPrivateAction.empty(),
          PreparedNotice.empty(),
          PreparedCtcpRequest.empty());
    }
    if (event instanceof IrcEvent.PrivateAction ev) {
      return new PreparedServerIrcEvent(
          se,
          PreparedChannelText.empty(),
          PreparedPrivateMessage.empty(),
          preparePrivateAction(sid, ev),
          PreparedNotice.empty(),
          PreparedCtcpRequest.empty());
    }
    if (event instanceof IrcEvent.Notice ev) {
      return new PreparedServerIrcEvent(
          se,
          PreparedChannelText.empty(),
          PreparedPrivateMessage.empty(),
          PreparedPrivateAction.empty(),
          prepareNotice(sid, ev),
          PreparedCtcpRequest.empty());
    }
    if (event instanceof IrcEvent.CtcpRequestReceived ev) {
      return new PreparedServerIrcEvent(
          se,
          PreparedChannelText.empty(),
          PreparedPrivateMessage.empty(),
          PreparedPrivateAction.empty(),
          PreparedNotice.empty(),
          prepareCtcpRequest(sid, ev));
    }
    return PreparedServerIrcEvent.unprepared(se);
  }

  ParsedCtcp parseCtcp(String text) {
    if (text == null || text.length() < 2) return null;
    if (text.charAt(0) != 0x01 || text.charAt(text.length() - 1) != 0x01) return null;
    String inner = text.substring(1, text.length() - 1).trim();
    if (inner.isEmpty()) return null;
    int sp = inner.indexOf(' ');
    String cmd = (sp >= 0) ? inner.substring(0, sp) : inner;
    String arg = (sp >= 0) ? inner.substring(sp + 1).trim() : "";
    cmd = cmd.trim().toUpperCase(Locale.ROOT);
    return new ParsedCtcp(cmd, arg);
  }

  InboundIgnorePolicyPort.Decision decideInbound(
      String sid,
      String from,
      boolean isCtcp,
      String inboundChannel,
      String inboundText,
      String... levels) {
    if (inboundIgnorePolicy == null) return InboundIgnorePolicyPort.Decision.ALLOW;
    String f = Objects.toString(from, "").trim();
    if (f.isEmpty()) return InboundIgnorePolicyPort.Decision.ALLOW;
    if ("server".equalsIgnoreCase(f)) return InboundIgnorePolicyPort.Decision.ALLOW;
    String ch = Objects.toString(inboundChannel, "").trim();
    String text = Objects.toString(inboundText, "");
    List<String> levelList = (levels == null || levels.length == 0) ? List.of() : List.of(levels);
    String scopeServerId = inboundIgnoreScopeServerId(sid, ch);
    return inboundIgnorePolicy.decide(scopeServerId, f, null, isCtcp, levelList, ch, text);
  }

  boolean isFromSelf(String serverId, String from) {
    if (serverId == null || from == null) return false;
    String me = irc.currentNick(serverId).orElse(null);
    if (me == null || me.isBlank()) return false;
    String meNorm = normalizeNickForCompare(me);
    String fromNorm = normalizeNickForCompare(from);
    return fromNorm != null && meNorm != null && fromNorm.equalsIgnoreCase(meNorm);
  }

  static String normalizeNickForCompare(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isEmpty()) return s;
    if (s.startsWith("(") && s.endsWith(")") && s.length() > 2) {
      s = s.substring(1, s.length() - 1).trim();
    }
    while (!s.isEmpty()) {
      char c = s.charAt(0);
      if (c == '@' || c == '+' || c == '%' || c == '~' || c == '&') {
        s = s.substring(1);
      } else {
        break;
      }
    }
    return s;
  }

  private PreparedChannelText prepareChannelMessage(String serverId, IrcEvent.ChannelMessage ev) {
    return prepareChannelText(
        serverId,
        ev.channel(),
        ev.from(),
        ev.text(),
        decideInbound(serverId, ev.from(), false, ev.channel(), ev.text(), "MSGS", "PUBLIC"));
  }

  private PreparedChannelText prepareChannelAction(String serverId, IrcEvent.ChannelAction ev) {
    return prepareChannelText(
        serverId,
        ev.channel(),
        ev.from(),
        ev.action(),
        decideInbound(serverId, ev.from(), true, ev.channel(), ev.action(), "ACTIONS", "CTCPS"));
  }

  private PreparedChannelText prepareChannelText(
      String serverId,
      String channel,
      String from,
      String text,
      InboundIgnorePolicyPort.Decision decision) {
    TargetRef chan = new TargetRef(serverId, channel);
    return new PreparedChannelText(
        firstRuleMatchForChannel(serverId, chan, from, text),
        containsSelfMention(serverId, from, text),
        decision);
  }

  private PreparedPrivateMessage preparePrivateMessage(
      String serverId, IrcEvent.PrivateMessage ev) {
    boolean fromSelf = isFromSelf(serverId, ev.from());
    String peer = resolvePrivatePeer(fromSelf, ev.from(), ev.ircv3Tags());
    ParsedCtcp ctcp = parseCtcp(ev.text());
    InboundIgnorePolicyPort.Decision decision =
        fromSelf
            ? InboundIgnorePolicyPort.Decision.ALLOW
            : decideInbound(serverId, ev.from(), false, "", ev.text(), "MSGS");
    InboundIgnorePolicyPort.Decision dccDecision =
        !fromSelf && ctcp != null && "DCC".equals(ctcp.commandUpper())
            ? decideInbound(serverId, ev.from(), true, "", ctcp.arg(), "DCC", "CTCPS")
            : null;
    return new PreparedPrivateMessage(fromSelf, peer, ctcp, decision, dccDecision);
  }

  private PreparedPrivateAction preparePrivateAction(String serverId, IrcEvent.PrivateAction ev) {
    boolean fromSelf = isFromSelf(serverId, ev.from());
    String peer = resolvePrivatePeer(fromSelf, ev.from(), ev.ircv3Tags());
    InboundIgnorePolicyPort.Decision decision =
        fromSelf
            ? InboundIgnorePolicyPort.Decision.ALLOW
            : decideInbound(serverId, ev.from(), true, "", ev.action(), "ACTIONS", "CTCPS");
    return new PreparedPrivateAction(fromSelf, peer, decision);
  }

  private PreparedNotice prepareNotice(String serverId, IrcEvent.Notice ev) {
    boolean fromSelf = isFromSelf(serverId, ev.from());
    String noticeChannel = noticeChannelForTarget(serverId, ev.target());
    boolean isCtcp = parseCtcp(ev.text()) != null;
    InboundIgnorePolicyPort.Decision decision =
        isCtcp
            ? decideInbound(serverId, ev.from(), true, noticeChannel, ev.text(), "NOTICES", "CTCPS")
            : decideInbound(serverId, ev.from(), false, noticeChannel, ev.text(), "NOTICES");
    return new PreparedNotice(fromSelf, noticeChannel, decision);
  }

  private PreparedCtcpRequest prepareCtcpRequest(String serverId, IrcEvent.CtcpRequestReceived ev) {
    String command = Objects.toString(ev.command(), "").trim();
    String argument = Objects.toString(ev.argument(), "").trim();
    String normalizedText = normalizeCtcpPayload(command, argument);
    return new PreparedCtcpRequest(
        command,
        argument,
        normalizedText,
        decideInbound(serverId, ev.from(), true, ev.channel(), normalizedText, "CTCPS"));
  }

  private NotificationRuleMatch firstRuleMatchForChannel(
      String serverId, TargetRef chan, String from, String text) {
    if (notificationRuleMatcherPort == null) return null;
    if (chan == null || text == null || text.isBlank()) return null;
    if (isFromSelf(serverId, from)) return null;

    List<NotificationRuleMatch> matches;
    try {
      matches = notificationRuleMatcherPort.matchAll(text);
    } catch (Exception ignored) {
      return null;
    }
    if (matches == null || matches.isEmpty()) return null;
    // Keep only the first match to avoid over-highlighting and duplicate events.
    return matches.get(0);
  }

  private boolean containsSelfMention(String serverId, String from, String message) {
    if (serverId == null || message == null || message.isEmpty()) return false;
    String me = irc.currentNick(serverId).orElse(null);
    if (me == null || me.isBlank()) return false;

    String fromNorm = normalizeNickForCompare(from);
    if (fromNorm != null && fromNorm.equalsIgnoreCase(me)) return false;

    return containsNickToken(message, me);
  }

  private static String resolvePrivatePeer(
      boolean fromSelf, String from, Map<String, String> ircv3Tags) {
    String peer = from;
    if (fromSelf && ircv3Tags != null) {
      String dest = ircv3Tags.get("ircafe/pm-target");
      if (dest != null && !dest.isBlank()) {
        peer = dest;
      }
    }
    return Objects.toString(peer, "").trim();
  }

  private static String noticeChannelForTarget(String serverId, String target) {
    String rawTarget = Objects.toString(target, "").trim();
    if (rawTarget.isEmpty()) return "";
    TargetRef targetRef = new TargetRef(serverId, rawTarget);
    return targetRef.isChannel() ? targetRef.target() : "";
  }

  private static String inboundIgnoreScopeServerId(String serverId, String inboundChannel) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return "";
    String token = TargetRef.parseQualifiedTarget(inboundChannel).networkToken();
    return token.isEmpty() ? sid : TargetRef.withNetworkQualifier(sid, token);
  }

  private static String normalizeCtcpPayload(String command, String argument) {
    String cmd = Objects.toString(command, "").trim();
    String arg = Objects.toString(argument, "").trim();
    return cmd + (arg.isBlank() ? "" : (" " + arg));
  }

  private static boolean containsNickToken(String message, String nick) {
    if (message == null || nick == null || nick.isEmpty()) return false;

    String nickLower = nick.toLowerCase(Locale.ROOT);
    int nlen = nickLower.length();

    int i = 0;
    final int len = message.length();
    while (i < len) {
      while (i < len && !isNickChar(message.charAt(i))) i++;
      if (i >= len) break;
      int start = i;
      while (i < len && isNickChar(message.charAt(i))) i++;
      int end = i;
      int tokLen = end - start;
      if (tokLen == nlen) {
        String tokenLower = message.substring(start, end).toLowerCase(Locale.ROOT);
        if (tokenLower.equals(nickLower)) return true;
      }
    }
    return false;
  }

  private static boolean isNickChar(char ch) {
    if (ch >= '0' && ch <= '9') return true;
    if (ch >= 'A' && ch <= 'Z') return true;
    if (ch >= 'a' && ch <= 'z') return true;
    return ch == '['
        || ch == ']'
        || ch == '\\'
        || ch == '`'
        || ch == '_'
        || ch == '^'
        || ch == '{'
        || ch == '|'
        || ch == '}'
        || ch == '-';
  }
}

record ParsedCtcp(String commandUpper, String arg) {}

record PreparedChannelText(
    NotificationRuleMatch ruleMatch, boolean mention, InboundIgnorePolicyPort.Decision decision) {
  private static final PreparedChannelText EMPTY =
      new PreparedChannelText(null, false, InboundIgnorePolicyPort.Decision.ALLOW);

  static PreparedChannelText empty() {
    return EMPTY;
  }
}

record PreparedPrivateMessage(
    boolean fromSelf,
    String peer,
    ParsedCtcp ctcp,
    InboundIgnorePolicyPort.Decision decision,
    InboundIgnorePolicyPort.Decision dccDecision) {
  private static final PreparedPrivateMessage EMPTY =
      new PreparedPrivateMessage(false, "", null, InboundIgnorePolicyPort.Decision.ALLOW, null);

  static PreparedPrivateMessage empty() {
    return EMPTY;
  }
}

record PreparedPrivateAction(
    boolean fromSelf, String peer, InboundIgnorePolicyPort.Decision decision) {
  private static final PreparedPrivateAction EMPTY =
      new PreparedPrivateAction(false, "", InboundIgnorePolicyPort.Decision.ALLOW);

  static PreparedPrivateAction empty() {
    return EMPTY;
  }
}

record PreparedNotice(
    boolean fromSelf, String noticeChannel, InboundIgnorePolicyPort.Decision decision) {
  private static final PreparedNotice EMPTY =
      new PreparedNotice(false, "", InboundIgnorePolicyPort.Decision.ALLOW);

  static PreparedNotice empty() {
    return EMPTY;
  }
}

record PreparedCtcpRequest(
    String command,
    String argument,
    String normalizedText,
    InboundIgnorePolicyPort.Decision decision) {
  private static final PreparedCtcpRequest EMPTY =
      new PreparedCtcpRequest("", "", "", InboundIgnorePolicyPort.Decision.ALLOW);

  static PreparedCtcpRequest empty() {
    return EMPTY;
  }
}

record PreparedServerIrcEvent(
    ServerIrcEvent event,
    PreparedChannelText channelText,
    PreparedPrivateMessage privateMessage,
    PreparedPrivateAction privateAction,
    PreparedNotice notice,
    PreparedCtcpRequest ctcpRequest) {
  static PreparedServerIrcEvent empty() {
    return new PreparedServerIrcEvent(
        null,
        PreparedChannelText.empty(),
        PreparedPrivateMessage.empty(),
        PreparedPrivateAction.empty(),
        PreparedNotice.empty(),
        PreparedCtcpRequest.empty());
  }

  static PreparedServerIrcEvent unprepared(ServerIrcEvent event) {
    return new PreparedServerIrcEvent(
        event,
        PreparedChannelText.empty(),
        PreparedPrivateMessage.empty(),
        PreparedPrivateAction.empty(),
        PreparedNotice.empty(),
        PreparedCtcpRequest.empty());
  }
}
