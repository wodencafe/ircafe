package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import java.util.Objects;
import org.pircbotx.Channel;
import org.pircbotx.User;

/** Shared low-level accessors for extracting data from PircBotX event objects. */
final class PircbotxEventAccessors {
  private PircbotxEventAccessors() {}

  static String rawLineFromEvent(Object event) {
    if (event == null) return null;

    try {
      Object raw = reflectCall(event, "getRawLine");
      String s = toRawIrcLine(raw);
      if (s != null) return s;
    } catch (Exception ignored) {
    }

    try {
      Object line = reflectCall(event, "getLine");
      String s = toRawIrcLine(line);
      if (s != null) return s;
    } catch (Exception ignored) {
    }

    try {
      Object raw = reflectCall(event, "getRaw");
      String s = toRawIrcLine(raw);
      if (s != null) return s;
    } catch (Exception ignored) {
    }

    return null;
  }

  static String privmsgTargetFromEvent(Object event) {
    try {
      String raw = rawLineFromEvent(event);
      String normalized = PircbotxLineParseUtil.normalizeIrcLineForParsing(raw);
      ParsedIrcLine parsed = PircbotxInboundLineParsers.parseIrcLine(normalized);
      if (parsed != null
          && parsed.command() != null
          && ("PRIVMSG".equalsIgnoreCase(parsed.command())
              || "NOTICE".equalsIgnoreCase(parsed.command()))
          && !parsed.params().isEmpty()) {
        return parsed.params().getFirst();
      }
    } catch (Exception ignored) {
    }

    try {
      Object target = reflectCall(event, "getTarget");
      if (target == null) target = reflectCall(event, "getRecipient");
      if (target == null) target = reflectCall(event, "getChannelSource");
      if (target == null) target = reflectCall(event, "getMessageTarget");
      if (target == null) target = reflectCall(event, "getMessageTargetName");
      if (target == null) target = reflectCall(event, "getDestination");

      if (target instanceof Channel channel) {
        return channel.getName();
      }
      if (target instanceof User user) {
        return user.getNick();
      }
      if (target != null) {
        String s = String.valueOf(target);
        if (!s.isBlank()) return s.trim();
      }
    } catch (Exception ignored) {
    }

    return null;
  }

  static String nickFromEvent(Object event) {
    if (event == null) return null;

    Object user = reflectCall(event, "getUser");
    if (user == null) user = reflectCall(event, "getSource");
    if (user == null) user = reflectCall(event, "getSetter");

    if (user != null) {
      Object nick = reflectCall(user, "getNick");
      if (nick != null) return String.valueOf(nick);
    }

    String rawNick = nickFromRawLine(event);
    if (rawNick != null && !rawNick.isBlank()) return rawNick;
    return null;
  }

  static String senderNickFromEvent(Object event) {
    String directUserNick = "";
    Object user = reflectCall(event, "getUser");
    if (user != null) {
      directUserNick = Objects.toString(reflectCall(user, "getNick"), "").trim();
    }
    if (!directUserNick.isEmpty()) return directUserNick;

    String rawNick = Objects.toString(nickFromRawLine(event), "").trim();
    if (!rawNick.isEmpty()) return rawNick;

    return "server";
  }

  static String modeDetailsFromEvent(Object event, String channelName) {
    if (event == null) return null;

    Object raw = reflectCall(event, "getRawLine");
    if (raw == null) raw = reflectCall(event, "getRaw");
    if (raw == null) raw = reflectCall(event, "getLine");
    if (raw != null) {
      String reduced = extractModeDetails(String.valueOf(raw), channelName);
      if (reduced != null && !reduced.isBlank()) return reduced;
    }

    Object modeLine = reflectCall(event, "getModeLine");
    if (modeLine == null) modeLine = reflectCall(event, "getModeString");
    if (modeLine != null) {
      String s = String.valueOf(modeLine);
      String reduced = extractModeDetails(s, channelName);
      if (reduced != null && !reduced.isBlank()) return reduced;
      if (!s.isBlank()) return s.trim();
    }

    Object mode = reflectCall(event, "getMode");
    if (mode == null) return null;

    String s = String.valueOf(mode);
    String reduced = extractModeDetails(s, channelName);
    return (reduced != null) ? reduced : s;
  }

  static Object reflectCall(Object target, String method) {
    if (target == null || method == null) return null;
    try {
      java.lang.reflect.Method m = target.getClass().getMethod(method);
      return m.invoke(target);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String nickFromRawLine(Object event) {
    if (event == null) return null;
    String raw = rawLineFromEvent(event);
    if (raw == null || raw.isBlank()) return null;

    ParsedIrcLine parsed =
        PircbotxInboundLineParsers.parseIrcLine(
            PircbotxLineParseUtil.normalizeIrcLineForParsing(raw));
    if (parsed != null) {
      String nick = PircbotxInboundLineParsers.nickFromPrefix(parsed.prefix());
      if (nick != null && !nick.isBlank()) return nick;
    }

    String line = raw.trim();
    if (!line.startsWith(":")) return null;
    int sp = line.indexOf(' ');
    if (sp <= 1) return null;

    String prefix = line.substring(1, sp);
    int bang = prefix.indexOf('!');
    if (bang >= 0) prefix = prefix.substring(0, bang);
    return prefix;
  }

  private static String extractModeDetails(String rawOrLine, String channelName) {
    if (rawOrLine == null) return null;
    String line = rawOrLine.trim();
    if (line.isEmpty()) return null;

    String working = line;
    if (working.startsWith(":")) {
      int sp = working.indexOf(' ');
      if (sp > 0) working = working.substring(sp + 1).trim();
    }

    String[] tokens = working.split("\\s+");
    for (int i = 0; i < tokens.length; i++) {
      if ("MODE".equalsIgnoreCase(tokens[i])) {
        int idx = i + 2;
        if (idx <= tokens.length - 1) {
          StringBuilder sb = new StringBuilder();
          for (int j = idx; j < tokens.length; j++) {
            if (j > idx) sb.append(' ');
            sb.append(tokens[j]);
          }
          String reduced = sb.toString().trim();
          return reduced.isEmpty() ? null : reduced;
        }
        return null;
      }
    }

    if (channelName != null && !channelName.isBlank()) {
      String channel = channelName.trim();
      if (line.startsWith(channel + " ")) return line.substring(channel.length()).trim();
    }

    return null;
  }

  private static String toRawIrcLine(Object maybeLine) {
    if (maybeLine == null) return null;

    if (maybeLine instanceof String s) {
      String trimmed = s.trim();
      return trimmed.isEmpty() ? null : trimmed;
    }

    for (String method : new String[] {"getRawLine", "getLine", "getRaw", "rawLine", "line"}) {
      try {
        Object nested = reflectCall(maybeLine, method);
        if (nested == null || nested == maybeLine) continue;
        String s = toRawIrcLine(nested);
        if (s != null) return s;
      } catch (Exception ignored) {
      }
    }

    try {
      String t = String.valueOf(maybeLine).trim();
      if (t.startsWith("@") || t.startsWith(":")) return t;
    } catch (Exception ignored) {
    }

    return null;
  }
}
