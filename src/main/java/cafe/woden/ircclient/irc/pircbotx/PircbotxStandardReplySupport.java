package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Parses and emits structured IRCv3 standard replies. */
final class PircbotxStandardReplySupport {

  private final String serverId;
  private final Consumer<ServerIrcEvent> sink;

  PircbotxStandardReplySupport(String serverId, Consumer<ServerIrcEvent> sink) {
    this.serverId = Objects.requireNonNull(serverId, "serverId");
    this.sink = Objects.requireNonNull(sink, "sink");
  }

  boolean emitIfSupported(
      Instant at,
      String command,
      String rawLine,
      List<String> parsedLine,
      ImmutableMap<String, String> tags) {
    IrcEvent.StandardReplyKind kind = toStandardReplyKind(command);
    if (kind == null) return false;

    ParsedStandardReply parsed = parse(parsedLine);
    String msgId =
        PircbotxTagSignalSupport.firstTag(tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid");
    Map<String, String> ircv3Tags = tags == null ? Map.of() : tags;
    String line = Objects.toString(rawLine, "").trim();
    sink.accept(
        new ServerIrcEvent(
            serverId,
            new IrcEvent.StandardReply(
                at,
                kind,
                parsed.command(),
                parsed.code(),
                parsed.context(),
                parsed.description(),
                line,
                msgId,
                ircv3Tags)));
    return true;
  }

  private static ParsedStandardReply parse(List<String> parsedLine) {
    String command = paramAt(parsedLine, 0);
    String code = paramAt(parsedLine, 1);
    String context = "";
    String description = "";
    if (parsedLine == null || parsedLine.size() <= 2) {
      return new ParsedStandardReply(command, code, context, description);
    }

    int trailingIdx = -1;
    for (int i = 2; i < parsedLine.size(); i++) {
      String token = Objects.toString(parsedLine.get(i), "");
      if (token.startsWith(":")) {
        trailingIdx = i;
        break;
      }
    }
    if (trailingIdx < 0) {
      trailingIdx = parsedLine.size() - 1;
    }

    description = stripLeadingColon(parsedLine.get(trailingIdx));
    if (trailingIdx > 2) {
      context = joinParams(parsedLine, 2, trailingIdx);
    }
    return new ParsedStandardReply(command, code, context, description);
  }

  private static String paramAt(List<String> parsedLine, int index) {
    if (parsedLine == null || index < 0 || index >= parsedLine.size()) return "";
    return stripLeadingColon(parsedLine.get(index));
  }

  private static String joinParams(List<String> parsedLine, int fromInclusive, int toExclusive) {
    if (parsedLine == null) return "";
    int from = Math.max(0, fromInclusive);
    int to = Math.min(parsedLine.size(), toExclusive);
    if (from >= to) return "";
    StringBuilder out = new StringBuilder();
    for (int i = from; i < to; i++) {
      String part = stripLeadingColon(parsedLine.get(i));
      if (part.isBlank()) continue;
      if (out.length() > 0) out.append(' ');
      out.append(part);
    }
    return out.toString().trim();
  }

  private static String stripLeadingColon(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.startsWith(":")) value = value.substring(1).trim();
    return value;
  }

  private static IrcEvent.StandardReplyKind toStandardReplyKind(String command) {
    if (command == null || command.isBlank()) return null;
    return switch (command.trim().toUpperCase(Locale.ROOT)) {
      case "FAIL" -> IrcEvent.StandardReplyKind.FAIL;
      case "WARN" -> IrcEvent.StandardReplyKind.WARN;
      case "NOTE" -> IrcEvent.StandardReplyKind.NOTE;
      default -> null;
    };
  }

  private record ParsedStandardReply(
      String command, String code, String context, String description) {}
}
