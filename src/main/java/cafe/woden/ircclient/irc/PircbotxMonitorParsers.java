package cafe.woden.ircclient.irc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Parsers for IRC MONITOR numerics and RPL_ISUPPORT MONITOR token. */
final class PircbotxMonitorParsers {
  private PircbotxMonitorParsers() {}

  static record ParsedMonitorSupport(boolean supported, int limit) {}

  static record ParsedMonitorStatusEntry(String nick, String hostmask) {
    ParsedMonitorStatusEntry {
      nick = Objects.toString(nick, "").trim();
      hostmask = Objects.toString(hostmask, "").trim();
    }
  }

  static record ParsedMonitorListFull(int limit, List<String> nicks, String message) {
    ParsedMonitorListFull {
      if (limit < 0) limit = 0;
      if (nicks == null) nicks = List.of();
      message = Objects.toString(message, "").trim();
    }
  }

  static List<ParsedMonitorStatusEntry> parseRpl730MonitorOnlineEntries(String rawLine) {
    return parseStatusEntriesForNumeric(rawLine, "730");
  }

  static List<String> parseRpl730MonitorOnlineNicks(String rawLine) {
    return statusEntriesToNickList(parseRpl730MonitorOnlineEntries(rawLine));
  }

  static List<ParsedMonitorStatusEntry> parseRpl731MonitorOfflineEntries(String rawLine) {
    return parseStatusEntriesForNumeric(rawLine, "731");
  }

  static List<String> parseRpl731MonitorOfflineNicks(String rawLine) {
    return statusEntriesToNickList(parseRpl731MonitorOfflineEntries(rawLine));
  }

  static List<String> parseRpl732MonitorListNicks(String rawLine) {
    return parseNickListForNumeric(rawLine, "732");
  }

  static boolean isRpl733MonitorListEnd(String rawLine) {
    ParsedLine parsed = parseLine(rawLine);
    return parsed != null && "733".equals(parsed.command());
  }

  static ParsedMonitorListFull parseErr734MonitorListFull(String rawLine) {
    ParsedLine parsed = parseLine(rawLine);
    if (parsed == null || !"734".equals(parsed.command())) return null;

    List<String> params = parsed.params();
    int limit = 0;
    String nicksToken = "";

    // Typical shape: 734 <target> <limit> <nick[,nick...]> :message
    for (int i = 1; i < params.size(); i++) {
      String tok = Objects.toString(params.get(i), "").trim();
      if (tok.isEmpty()) continue;
      if (limit <= 0) {
        int parsedLimit = parseNonNegativeInt(tok);
        if (parsedLimit >= 0) {
          limit = parsedLimit;
          continue;
        }
      }
      if (nicksToken.isEmpty() && looksLikeNickListToken(tok)) {
        nicksToken = tok;
      }
    }

    if (nicksToken.isEmpty() && looksLikeNickListToken(parsed.trailing())) {
      nicksToken = parsed.trailing();
    }

    return new ParsedMonitorListFull(limit, parseNickList(nicksToken), parsed.trailing());
  }

  /**
   * Parse RPL_ISUPPORT (005) MONITOR support token.
   *
   * <p>Examples:
   * MONITOR=100, MONITOR, -MONITOR
   *
   * @return parsed support info when MONITOR token is present; otherwise null.
   */
  static ParsedMonitorSupport parseRpl005MonitorSupport(String rawLine) {
    ParsedLine parsed = parseLine(rawLine);
    if (parsed == null || !"005".equals(parsed.command())) return null;

    boolean found = false;
    boolean supported = false;
    int limit = 0;
    List<String> params = parsed.params();
    int start = params.isEmpty() ? 0 : 1; // skip target nick
    for (int i = start; i < params.size(); i++) {
      String tok = Objects.toString(params.get(i), "").trim();
      if (tok.isEmpty()) continue;
      if (tok.startsWith(":")) break;

      boolean remove = tok.startsWith("-");
      if (remove) tok = tok.substring(1).trim();
      if (tok.isEmpty()) continue;

      int eq = tok.indexOf('=');
      String key = eq >= 0 ? tok.substring(0, eq).trim() : tok.trim();
      String value = eq >= 0 ? tok.substring(eq + 1).trim() : "";
      if (!"MONITOR".equalsIgnoreCase(key)) continue;

      found = true;
      if (remove) {
        supported = false;
        limit = 0;
        continue;
      }

      supported = true;
      int parsedLimit = parseNonNegativeInt(value);
      if (parsedLimit >= 0) {
        limit = parsedLimit;
      } else if (value.isEmpty()) {
        limit = 0;
      }
    }
    if (!found) return null;
    return new ParsedMonitorSupport(supported, limit);
  }

  private static List<String> parseNickListForNumeric(String rawLine, String expectedCode) {
    ParsedLine parsed = parseLine(rawLine);
    if (parsed == null || !expectedCode.equals(parsed.command())) return List.of();

    String nicksToken = parsed.trailing();
    if (nicksToken.isEmpty() && !parsed.params().isEmpty()) {
      nicksToken = parsed.params().get(parsed.params().size() - 1);
    }
    return parseNickList(nicksToken);
  }

  private static List<ParsedMonitorStatusEntry> parseStatusEntriesForNumeric(String rawLine, String expectedCode) {
    ParsedLine parsed = parseLine(rawLine);
    if (parsed == null || !expectedCode.equals(parsed.command())) return List.of();

    String token = parsed.trailing();
    if (token.isEmpty() && !parsed.params().isEmpty()) {
      token = parsed.params().get(parsed.params().size() - 1);
    }
    return parseStatusEntries(token);
  }

  private static List<ParsedMonitorStatusEntry> parseStatusEntries(String rawEntries) {
    String raw = Objects.toString(rawEntries, "").trim();
    if (raw.isEmpty()) return List.of();

    LinkedHashMap<String, ParsedMonitorStatusEntry> out = new LinkedHashMap<>();
    for (String token : raw.split(",")) {
      ParsedMonitorStatusEntry parsed = parseStatusEntry(token);
      if (parsed == null || parsed.nick().isEmpty()) continue;
      String key = parsed.nick().toLowerCase(Locale.ROOT);
      ParsedMonitorStatusEntry prev = out.get(key);
      if (prev == null) {
        out.put(key, parsed);
      } else if (prev.hostmask().isEmpty() && !parsed.hostmask().isEmpty()) {
        out.put(key, parsed);
      }
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out.values());
  }

  private static ParsedMonitorStatusEntry parseStatusEntry(String token) {
    String value = Objects.toString(token, "").trim();
    if (value.startsWith(":")) value = value.substring(1).trim();
    if (value.isEmpty()) return null;

    String nick = value;
    String hostmask = "";
    int bang = value.indexOf('!');
    int at = value.indexOf('@', bang + 1);
    if (bang > 0 && at > bang + 1 && at < value.length() - 1) {
      nick = value.substring(0, bang).trim();
      hostmask = value;
    }
    if (nick.isEmpty()) return null;
    return new ParsedMonitorStatusEntry(nick, hostmask);
  }

  private static List<String> statusEntriesToNickList(List<ParsedMonitorStatusEntry> entries) {
    if (entries == null || entries.isEmpty()) return List.of();
    ArrayList<String> out = new ArrayList<>(entries.size());
    for (ParsedMonitorStatusEntry entry : entries) {
      if (entry == null) continue;
      String nick = Objects.toString(entry.nick(), "").trim();
      if (!nick.isEmpty()) out.add(nick);
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }

  private static List<String> parseNickList(String rawNicks) {
    String raw = Objects.toString(rawNicks, "").trim();
    if (raw.isEmpty()) return List.of();

    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    for (String token : raw.split(",")) {
      String nick = Objects.toString(token, "").trim();
      if (nick.startsWith(":")) nick = nick.substring(1).trim();
      if (nick.isEmpty()) continue;

      int bang = nick.indexOf('!');
      if (bang > 0) nick = nick.substring(0, bang).trim();
      if (nick.isEmpty()) continue;

      String key = nick.toLowerCase(Locale.ROOT);
      out.putIfAbsent(key, nick);
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out.values());
  }

  private static boolean looksLikeNickListToken(String token) {
    String s = Objects.toString(token, "").trim();
    if (s.isEmpty()) return false;
    if (s.startsWith(":")) s = s.substring(1).trim();
    if (s.isEmpty()) return false;
    if (s.contains(",")) return true;
    if (s.indexOf('!') >= 0) return true;
    if (s.startsWith("#") || s.startsWith("&")) return false;
    if (PircbotxLineParseUtil.looksNumeric(s)) return false;
    return true;
  }

  private static int parseNonNegativeInt(String raw) {
    String s = Objects.toString(raw, "").trim();
    if (s.isEmpty()) return -1;
    if (!PircbotxLineParseUtil.looksNumeric(s)) return -1;
    try {
      int v = Integer.parseInt(s);
      return Math.max(v, 0);
    } catch (Exception ignored) {
      return -1;
    }
  }

  private record ParsedLine(String command, List<String> params, String trailing) {}

  private static ParsedLine parseLine(String rawLine) {
    String s = Objects.toString(rawLine, "").trim();
    if (s.isEmpty()) return null;

    // Strip IRCv3 tags.
    if (s.startsWith("@")) {
      int sp = s.indexOf(' ');
      if (sp <= 0 || sp + 1 >= s.length()) return null;
      s = s.substring(sp + 1).trim();
    }

    // Strip prefix.
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp <= 0 || sp + 1 >= s.length()) return null;
      s = s.substring(sp + 1).trim();
    }

    if (s.isEmpty()) return null;

    String trailing = "";
    int trailingIdx = s.indexOf(" :");
    if (trailingIdx >= 0) {
      trailing = s.substring(trailingIdx + 2).trim();
      s = s.substring(0, trailingIdx).trim();
    }
    if (s.isEmpty()) return null;

    String[] tokens = s.split("\\s+");
    if (tokens.length == 0) return null;

    String command = Objects.toString(tokens[0], "").trim();
    if (command.isEmpty()) return null;

    List<String> params = new ArrayList<>();
    for (int i = 1; i < tokens.length; i++) {
      String tok = Objects.toString(tokens[i], "").trim();
      if (!tok.isEmpty()) params.add(tok);
    }
    return new ParsedLine(command, List.copyOf(params), trailing);
  }
}
