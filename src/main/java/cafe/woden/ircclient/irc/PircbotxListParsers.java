package cafe.woden.ircclient.irc;

import java.util.List;
import java.util.Objects;

/** Parsers/formatters for IRC channel-list numerics (321/322/323). */
final class PircbotxListParsers {
  private PircbotxListParsers() {}

  static record ListEntry(String channel, int visibleUsers, boolean hasVisibleUsers, String topic) {}

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

  static ListEntry parseListEntry(String command, List<String> params, String trailing, String myNick) {
    String cmd = Objects.toString(command, "").trim();
    if (!"322".equals(cmd)) return null;

    int idx = 0;
    if (params != null && !params.isEmpty()) {
      String p0 = Objects.toString(params.get(0), "").trim();
      String nick = Objects.toString(myNick, "").trim();
      if ((!nick.isEmpty() && p0.equalsIgnoreCase(nick)) || "*".equals(p0)) {
        idx = 1;
      }
    }

    if (params == null || params.size() <= idx) return null;

    String channel = Objects.toString(params.get(idx), "").trim();
    if (!PircbotxLineParseUtil.looksLikeChannel(channel)) return null;

    int visibleUsers = -1;
    boolean hasVisibleUsers = false;
    if (params.size() > idx + 1) {
      String maybeCount = Objects.toString(params.get(idx + 1), "").trim();
      if (PircbotxLineParseUtil.looksNumeric(maybeCount)) {
        hasVisibleUsers = true;
        try {
          visibleUsers = Integer.parseInt(maybeCount);
        } catch (Exception ignored) {
          visibleUsers = -1;
          hasVisibleUsers = false;
        }
      }
    }

    String topic = Objects.toString(trailing, "").trim();
    return new ListEntry(channel, Math.max(-1, visibleUsers), hasVisibleUsers, topic);
  }

  /**
   * Returns a friendly rendering for LIST numerics, or {@code null} if the command is not part
   * of LIST handling (or if an entry line is malformed).
   */
  static String tryFormatListNumeric(String command, List<String> params, String trailing, String myNick) {
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
}
