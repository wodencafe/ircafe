package cafe.woden.ircclient.irc;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Parsers/formatters for IRC channel-list numerics (321/322/323). */
final class PircbotxListParsers {
  private PircbotxListParsers() {}

  static record ListEntry(
      String channel, int visibleUsers, boolean hasVisibleUsers, String topic) {}

  static record BanListEntry(String channel, String mask, String setBy, Long setAtEpochSeconds) {}

  static String parseListStartBanner(String command, String trailing) {
    String cmd = Objects.toString(command, "").trim();
    if (!"321".equals(cmd)) return null;
    String t = Objects.toString(trailing, "").trim();
    return t.isEmpty() ? "Channel list follows" : t;
  }

  static String parseListEndSummary(String command, String trailing) {
    String cmd = Objects.toString(command, "").trim();
    if (!"323".equals(cmd)) return null;
    String t = Objects.toString(trailing, "").trim();
    return t.isEmpty() ? "End of /LIST" : t;
  }

  static ListEntry parseListEntry(
      String command, List<String> params, String trailing, String myNick) {
    String cmd = Objects.toString(command, "").trim();
    if (!"322".equals(cmd)) return null;

    int idx = findListChannelIndex(params, myNick);
    if (idx < 0) return null;

    int visibleUsers = -1;
    boolean hasVisibleUsers = false;
    if (params.size() > idx + 1) {
      Integer maybeCount = parseOptionalNonNegativeIntToken(params.get(idx + 1));
      if (maybeCount != null) {
        hasVisibleUsers = true;
        visibleUsers = maybeCount.intValue();
      }
    }

    String channel = Objects.toString(params.get(idx), "").trim();
    String topic = Objects.toString(trailing, "").trim();
    return new ListEntry(channel, Math.max(-1, visibleUsers), hasVisibleUsers, topic);
  }

  static ListEntry parseAlisNoticeEntry(String fromNick, String text) {
    String from = Objects.toString(fromNick, "").trim();
    if (!isLikelyAlisSource(from)) return null;

    String raw = Objects.toString(text, "").trim();
    if (raw.isEmpty()) return null;

    String[] tokens = raw.split("\\s+");
    if (tokens.length < 2) return null;

    int channelIdx = -1;
    String channel = "";
    for (int i = 0; i < tokens.length; i++) {
      String maybeChannel = stripListToken(tokens[i]);
      if (PircbotxLineParseUtil.looksLikeChannel(maybeChannel)) {
        channelIdx = i;
        channel = maybeChannel;
        break;
      }
    }
    if (channelIdx < 0) return null;

    Integer users = null;
    int usersIdx = -1;
    for (int i = channelIdx + 1; i < Math.min(tokens.length, channelIdx + 4); i++) {
      Integer n = parseOptionalNonNegativeIntToken(tokens[i]);
      if (n != null) {
        users = n;
        usersIdx = i;
        break;
      }
    }
    if (users == null && channelIdx > 0) {
      users = parseOptionalNonNegativeIntToken(tokens[channelIdx - 1]);
      usersIdx = (users == null) ? -1 : (channelIdx - 1);
    }
    if (users == null) return null;

    int topicStart = (usersIdx > channelIdx) ? usersIdx + 1 : channelIdx + 1;
    String topic = "";
    if (topicStart >= 0 && topicStart < tokens.length) {
      topic = joinTokens(tokens, topicStart);
      if (topic.startsWith(":")) topic = topic.substring(1).trim();
    }

    return new ListEntry(channel, users.intValue(), true, topic);
  }

  static String parseAlisNoticeEndSummary(String fromNick, String text) {
    String from = Objects.toString(fromNick, "").trim();
    if (!isLikelyAlisSource(from)) return null;

    String raw = Objects.toString(text, "").trim();
    if (raw.isEmpty()) return null;

    String normalized = raw.endsWith(".") ? raw.substring(0, raw.length() - 1).trim() : raw;
    if (!"End of output".equalsIgnoreCase(normalized)) return null;
    return raw;
  }

  static BanListEntry parseBanListEntry(String command, List<String> params) {
    String cmd = Objects.toString(command, "").trim();
    if (!"367".equals(cmd)) return null;
    if (params == null || params.isEmpty()) return null;

    int channelIdx = firstChannelTokenIndex(params);
    if (channelIdx < 0 || channelIdx + 1 >= params.size()) return null;
    String channel = stripListToken(params.get(channelIdx));
    if (!PircbotxLineParseUtil.looksLikeChannel(channel)) return null;

    String mask = Objects.toString(params.get(channelIdx + 1), "").trim();
    if (mask.isEmpty()) return null;

    String setBy = "";
    if (channelIdx + 2 < params.size()) {
      setBy = Objects.toString(params.get(channelIdx + 2), "").trim();
    }
    Long setAtEpochSeconds = null;
    if (channelIdx + 3 < params.size()) {
      Long secs = parseOptionalNonNegativeLongToken(params.get(channelIdx + 3));
      if (secs != null && secs.longValue() > 0L) {
        setAtEpochSeconds = secs;
      }
    }
    return new BanListEntry(channel, mask, setBy, setAtEpochSeconds);
  }

  static String parseBanListEndChannel(String command, List<String> params) {
    String cmd = Objects.toString(command, "").trim();
    if (!"368".equals(cmd)) return null;
    if (params == null || params.isEmpty()) return null;
    int channelIdx = firstChannelTokenIndex(params);
    if (channelIdx < 0) return null;
    String channel = stripListToken(params.get(channelIdx));
    return PircbotxLineParseUtil.looksLikeChannel(channel) ? channel : null;
  }

  static String parseBanListEndSummary(String command, String trailing) {
    String cmd = Objects.toString(command, "").trim();
    if (!"368".equals(cmd)) return null;
    String t = Objects.toString(trailing, "").trim();
    return t.isEmpty() ? "End of channel ban list" : t;
  }

  /**
   * Returns a friendly rendering for LIST numerics, or {@code null} if the command is not part of
   * LIST handling (or if an entry line is malformed).
   */
  static String tryFormatListNumeric(
      String command, List<String> params, String trailing, String myNick) {
    String start = parseListStartBanner(command, trailing);
    if (start != null) return start;

    String end = parseListEndSummary(command, trailing);
    if (end != null) return end;

    ListEntry entry = parseListEntry(command, params, trailing, myNick);
    if (entry == null) return null;

    StringBuilder out = new StringBuilder(entry.channel());
    if (entry.hasVisibleUsers()) out.append(" (").append(entry.visibleUsers()).append(")");
    if (entry.topic() != null && !entry.topic().isEmpty()) out.append(": ").append(entry.topic());
    return out.toString();
  }

  private static int findListChannelIndex(List<String> params, String myNick) {
    if (params == null || params.isEmpty()) return -1;

    int idx = 0;
    String p0 = Objects.toString(params.get(0), "").trim();
    String nick = Objects.toString(myNick, "").trim();
    if ((!nick.isEmpty() && p0.equalsIgnoreCase(nick)) || "*".equals(p0)) {
      idx = 1;
    }

    if (idx < params.size()) {
      String candidate = Objects.toString(params.get(idx), "").trim();
      if (PircbotxLineParseUtil.looksLikeChannel(candidate)) return idx;
    }
    return firstChannelTokenIndex(params);
  }

  private static int firstChannelTokenIndex(List<String> params) {
    if (params == null || params.isEmpty()) return -1;
    for (int i = 0; i < params.size(); i++) {
      String token = stripListToken(params.get(i));
      if (PircbotxLineParseUtil.looksLikeChannel(token)) return i;
    }
    return -1;
  }

  private static String stripListToken(String token) {
    String t = Objects.toString(token, "").trim();
    while (!t.isEmpty()) {
      char c = t.charAt(0);
      if (c == '[' || c == '(' || c == '{' || c == '<' || c == ':') {
        t = t.substring(1).trim();
        continue;
      }
      break;
    }
    while (!t.isEmpty()) {
      char c = t.charAt(t.length() - 1);
      if (c == ']' || c == ')' || c == '}' || c == '>' || c == ',') {
        t = t.substring(0, t.length() - 1).trim();
        continue;
      }
      break;
    }
    return t;
  }

  private static Integer parseOptionalNonNegativeIntToken(String token) {
    Long v = parseOptionalNonNegativeLongToken(token);
    if (v == null || v.longValue() > Integer.MAX_VALUE) return null;
    return Integer.valueOf(v.intValue());
  }

  private static Long parseOptionalNonNegativeLongToken(String token) {
    String normalized = stripListToken(token);
    if (normalized.isEmpty()) return null;

    StringBuilder digits = new StringBuilder(normalized.length());
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if (c >= '0' && c <= '9') {
        digits.append(c);
      } else if (digits.length() > 0) {
        break;
      }
    }
    if (digits.isEmpty()) return null;
    try {
      return Long.valueOf(digits.toString());
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String joinTokens(String[] tokens, int start) {
    if (tokens == null || tokens.length == 0 || start >= tokens.length) return "";
    StringBuilder out = new StringBuilder();
    for (int i = Math.max(0, start); i < tokens.length; i++) {
      String token = Objects.toString(tokens[i], "").trim();
      if (token.isEmpty()) continue;
      if (out.length() > 0) out.append(' ');
      out.append(token);
    }
    return out.toString().trim();
  }

  private static boolean isLikelyAlisSource(String fromNick) {
    String from = Objects.toString(fromNick, "").trim().toLowerCase(Locale.ROOT);
    if (from.isEmpty()) return true;
    if ("alis".equals(from)) return true;
    // Some service NOTICE events can resolve sender user as null and surface as "server".
    return "server".equals(from);
  }
}
