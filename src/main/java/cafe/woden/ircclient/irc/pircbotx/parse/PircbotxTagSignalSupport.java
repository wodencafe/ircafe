package cafe.woden.ircclient.irc.pircbotx.parse;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Emits IRCv3 tag-derived message signals and exposes shared tag parsing helpers. */
public final class PircbotxTagSignalSupport {

  private static final Logger log = LoggerFactory.getLogger(PircbotxTagSignalSupport.class);

  private final String serverId;
  private final Consumer<ServerIrcEvent> sink;

  public PircbotxTagSignalSupport(String serverId, Consumer<ServerIrcEvent> sink) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.sink = Objects.requireNonNull(sink, "sink");
  }

  public void emitObservedSignals(
      Instant at,
      String nick,
      String rawTarget,
      String command,
      List<String> parsedLine,
      ImmutableMap<String, String> tags) {
    if (tags == null || tags.isEmpty()) return;

    String cmd = Objects.toString(command, "").trim().toUpperCase(Locale.ROOT);
    String firstParam = firstParam(parsedLine);
    String msgTarget = !firstParam.isBlank() ? firstParam : stripLeadingColon(rawTarget);
    String channelContext =
        firstTag(
            tags,
            "draft/channel-context",
            "+draft/channel-context",
            "channel-context",
            "+channel-context");
    String convTarget = resolveSignalTarget(msgTarget, nick, channelContext);

    if (cmd.equals("PRIVMSG") || cmd.equals("NOTICE") || cmd.equals("TAGMSG")) {
      String replyTo = firstTag(tags, "reply", "+reply", "draft/reply", "+draft/reply");
      if (!replyTo.isBlank()) {
        sink.accept(
            new ServerIrcEvent(
                serverId, new IrcEvent.MessageReplyObserved(at, nick, convTarget, replyTo)));
      }

      String react = firstTag(tags, "draft/react", "+draft/react");
      if (!react.isBlank()) {
        sink.accept(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.MessageReactObserved(
                    at, nick, convTarget, react, observedMessageId(tags))));
      }

      String unreact = firstTag(tags, "draft/unreact", "+draft/unreact");
      if (!unreact.isBlank()) {
        sink.accept(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.MessageUnreactObserved(
                    at, nick, convTarget, unreact, observedMessageId(tags))));
      }

      String redactMsgId =
          firstTag(tags, "draft/delete", "+draft/delete", "draft/redact", "+draft/redact");
      if (!redactMsgId.isBlank()) {
        sink.accept(
            new ServerIrcEvent(
                serverId,
                new IrcEvent.MessageRedactionObserved(at, nick, convTarget, redactMsgId)));
      }

      String typing = firstTag(tags, "typing", "+typing");
      if (!typing.isBlank()) {
        if (log.isDebugEnabled()) {
          log.debug(
              "[{}] IRCv3 +typing tag: from={} target={} state={} cmd={}",
              serverId,
              nick,
              convTarget,
              typing,
              cmd);
        }
        sink.accept(
            new ServerIrcEvent(
                serverId, new IrcEvent.UserTypingObserved(at, nick, convTarget, typing)));
      }
    }

    String readMarker =
        firstTag(tags, "draft/read-marker", "+draft/read-marker", "read-marker", "+read-marker");
    if (!readMarker.isBlank()) {
      sink.accept(
          new ServerIrcEvent(
              serverId, new IrcEvent.ReadMarkerObserved(at, nick, convTarget, readMarker)));
    }
  }

  public static String resolveConversationTarget(String rawTarget, String fromNick) {
    String target = Objects.toString(rawTarget, "").trim();
    if (isChannelName(target)) return target;
    String from = Objects.toString(fromNick, "").trim();
    return from.isBlank() ? target : from;
  }

  public static boolean isChannelName(String target) {
    String value = Objects.toString(target, "").trim();
    if (value.isEmpty()) return false;
    char leading = value.charAt(0);
    return leading == '#' || leading == '&' || leading == '!' || leading == '+';
  }

  public static String firstTag(ImmutableMap<String, String> tags, String... keys) {
    if (tags == null || tags.isEmpty() || keys == null) return "";
    for (String key : keys) {
      if (key == null || key.isBlank()) continue;
      String wanted = normalizeTagKey(key);
      for (Map.Entry<String, String> entry : tags.entrySet()) {
        String actual = normalizeTagKey(entry.getKey());
        if (!wanted.equals(actual)) continue;
        String value = Objects.toString(entry.getValue(), "").trim();
        if (value.isEmpty()) continue;
        return unescapeTagValue(value);
      }
    }
    return "";
  }

  private static String firstParam(List<String> parsedLine) {
    if (parsedLine == null || parsedLine.isEmpty()) return "";
    return stripLeadingColon(parsedLine.get(0));
  }

  private static String stripLeadingColon(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.startsWith(":")) value = value.substring(1).trim();
    return value;
  }

  private static String resolveSignalTarget(
      String rawTarget, String fromNick, String channelContextTag) {
    String context = Objects.toString(channelContextTag, "").trim();
    if (isChannelName(context)) return context;
    return resolveConversationTarget(rawTarget, fromNick);
  }

  private static String observedMessageId(ImmutableMap<String, String> tags) {
    String msgId = firstTag(tags, "reply", "+reply", "draft/reply", "+draft/reply");
    if (!msgId.isBlank()) return msgId;
    return firstTag(tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid");
  }

  private static String normalizeTagKey(String raw) {
    String key = Objects.toString(raw, "").trim();
    if (key.startsWith("@")) key = key.substring(1).trim();
    if (key.startsWith("+")) key = key.substring(1).trim();
    return key.toLowerCase(Locale.ROOT);
  }

  private static String unescapeTagValue(String raw) {
    if (raw == null || raw.isEmpty() || raw.indexOf('\\') < 0) return raw == null ? "" : raw;
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char current = raw.charAt(i);
      if (current != '\\') {
        out.append(current);
        continue;
      }
      if (i + 1 >= raw.length()) break;
      char escaped = raw.charAt(++i);
      switch (escaped) {
        case ':' -> out.append(';');
        case 's' -> out.append(' ');
        case 'r' -> out.append('\r');
        case 'n' -> out.append('\n');
        case '\\' -> out.append('\\');
        default -> out.append(escaped);
      }
    }
    return out.toString();
  }
}
