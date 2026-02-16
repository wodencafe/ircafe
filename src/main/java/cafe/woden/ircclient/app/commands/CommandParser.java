package cafe.woden.ircclient.app.commands;

import org.springframework.stereotype.Component;

/**
 * Small command parser for the input line.
 *
 * Unknown commands return {@link ParsedInput.Unknown}.
 *
 */
@Component
public class CommandParser {

  private final FilterCommandParser filterCommandParser;

  public CommandParser(FilterCommandParser filterCommandParser) {
    this.filterCommandParser = filterCommandParser;
  }

  public ParsedInput parse(String raw) {
    String line = raw == null ? "" : raw.trim();
    if (line.isEmpty()) return new ParsedInput.Say("");

    if (!line.startsWith("/")) {
      return new ParsedInput.Say(line);
    }

    if (matchesCommand(line, "/join")) {
      String args = argAfter(line, "/join");
      return parseJoinInput(args);
    }

    // Common alias used by many IRC clients.
    if (matchesCommand(line, "/j")) {
      String args = argAfter(line, "/j");
      return parseJoinInput(args);
    }

    if (matchesCommand(line, "/part") || matchesCommand(line, "/leave")) {
      String rest = matchesCommand(line, "/part") ? argAfter(line, "/part") : argAfter(line, "/leave");
      String r = rest == null ? "" : rest.trim();
      if (r.isEmpty()) return new ParsedInput.Part("", "");
      String first;
      String tail;
      int sp = r.indexOf(' ' );
      if (sp < 0) {
        first = r;
        tail = "";
      } else {
        first = r.substring(0, sp).trim();
        tail = r.substring(sp + 1).trim();
      }
      // If the first token looks like a channel, treat it as the explicit channel;
      // otherwise treat the whole rest as a part reason for the current channel.
      if (first.startsWith("#") || first.startsWith("&")) {
        return new ParsedInput.Part(first, tail);
      }
      return new ParsedInput.Part("", r);
    }

    if (matchesCommand(line, "/connect")) {
      String target = argAfter(line, "/connect");
      return new ParsedInput.Connect(target);
    }

    if (matchesCommand(line, "/disconnect")) {
      String target = argAfter(line, "/disconnect");
      return new ParsedInput.Disconnect(target);
    }

    if (matchesCommand(line, "/reconnect")) {
      String target = argAfter(line, "/reconnect");
      return new ParsedInput.Reconnect(target);
    }

    if (matchesCommand(line, "/quit")) {
      String reason = argAfter(line, "/quit");
      return new ParsedInput.Quit(reason);
    }

    if (matchesCommand(line, "/nick")) {
      String nick = argAfter(line, "/nick");
      return new ParsedInput.Nick(nick);
    }

    if (matchesCommand(line, "/away")) {
      String msg = argAfter(line, "/away");
      return new ParsedInput.Away(msg);
    }

    if (matchesCommand(line, "/query")) {
      String nick = argAfter(line, "/query");
      return new ParsedInput.Query(nick);
    }

    if (matchesCommand(line, "/whois")) {
      String nick = argAfter(line, "/whois");
      return new ParsedInput.Whois(nick);
    }

    if (matchesCommand(line, "/whowas")) {
      String rest = argAfter(line, "/whowas");
      return parseWhowasInput(rest);
    }

    // Common alias used by many IRC clients.
    if (matchesCommand(line, "/wi")) {
      String nick = argAfter(line, "/wi");
      return new ParsedInput.Whois(nick);
    }

    if (matchesCommand(line, "/msg")) {
      String rest = argAfter(line, "/msg");
      int sp = rest.indexOf(' ');
      if (sp <= 0) return new ParsedInput.Msg(rest.trim(), "");
      String nick = rest.substring(0, sp).trim();
      String body = rest.substring(sp + 1).trim();
      return new ParsedInput.Msg(nick, body);
    }


    if (matchesCommand(line, "/notice")) {
      String rest = argAfter(line, "/notice");
      int sp = rest.indexOf(' ');
      if (sp <= 0) return new ParsedInput.Notice(rest.trim(), "");
      String target = rest.substring(0, sp).trim();
      String body = rest.substring(sp + 1).trim();
      return new ParsedInput.Notice(target, body);
    }

    if (matchesCommand(line, "/me")) {
      String action = argAfter(line, "/me");
      return new ParsedInput.Me(action);
    }

    if (matchesCommand(line, "/topic")) {
      String rest = argAfter(line, "/topic");
      if (rest.isEmpty()) return new ParsedInput.Topic("", "");
      int sp = rest.indexOf(' ');
      if (sp < 0) return new ParsedInput.Topic(rest.trim(), "");
      String first = rest.substring(0, sp).trim();
      String tail = rest.substring(sp + 1).trim();
      return new ParsedInput.Topic(first, tail);
    }

    if (matchesCommand(line, "/kick")) {
      ParsedKick p = parseKickArgs(argAfter(line, "/kick"));
      return new ParsedInput.Kick(p.channel(), p.nick(), p.reason());
    }

    if (matchesCommand(line, "/invite")) {
      String rest = argAfter(line, "/invite");
      String r = rest == null ? "" : rest.trim();
      if (r.isEmpty()) return new ParsedInput.Invite("", "");
      String[] toks = r.split("\\s+", 3);
      String nick = toks.length > 0 ? toks[0].trim() : "";
      String channel = toks.length > 1 ? toks[1].trim() : "";
      return new ParsedInput.Invite(nick, channel);
    }

    if (matchesCommand(line, "/names")) {
      String channel = argAfter(line, "/names");
      return new ParsedInput.Names(channel);
    }

    if (matchesCommand(line, "/who")) {
      String args = argAfter(line, "/who");
      return new ParsedInput.Who(args);
    }

    if (matchesCommand(line, "/list")) {
      String args = argAfter(line, "/list");
      return new ParsedInput.ListCmd(args);
    }


    if (matchesCommand(line, "/mode")) {
      String rest = argAfter(line, "/mode");
      if (rest.isEmpty()) return new ParsedInput.Mode("", "");
      int sp = rest.indexOf(' ');
      if (sp < 0) return new ParsedInput.Mode(rest.trim(), "");
      String first = rest.substring(0, sp).trim();
      String tail = rest.substring(sp + 1).trim();
      return new ParsedInput.Mode(first, tail);
    }

    
    if (matchesCommand(line, "/op")) {
      ParsedTargetList p = parseTargetList(argAfter(line, "/op"));
      return new ParsedInput.Op(p.channel(), p.items());
    }

    if (matchesCommand(line, "/deop")) {
      ParsedTargetList p = parseTargetList(argAfter(line, "/deop"));
      return new ParsedInput.Deop(p.channel(), p.items());
    }

    if (matchesCommand(line, "/voice")) {
      ParsedTargetList p = parseTargetList(argAfter(line, "/voice"));
      return new ParsedInput.Voice(p.channel(), p.items());
    }

    if (matchesCommand(line, "/devoice")) {
      ParsedTargetList p = parseTargetList(argAfter(line, "/devoice"));
      return new ParsedInput.Devoice(p.channel(), p.items());
    }

    if (matchesCommand(line, "/ban")) {
      ParsedTargetList p = parseTargetList(argAfter(line, "/ban"));
      return new ParsedInput.Ban(p.channel(), p.items());
    }

    if (matchesCommand(line, "/unban")) {
      ParsedTargetList p = parseTargetList(argAfter(line, "/unban"));
      return new ParsedInput.Unban(p.channel(), p.items());
    }

    if (matchesCommand(line, "/ignore")) {
      String arg = argAfter(line, "/ignore");
      return new ParsedInput.Ignore(arg);
    }

    if (matchesCommand(line, "/unignore")) {
      String arg = argAfter(line, "/unignore");
      return new ParsedInput.Unignore(arg);
    }

    if (matchesCommand(line, "/ignorelist") || matchesCommand(line, "/ignores")) {
      return new ParsedInput.IgnoreList();
    }

    if (matchesCommand(line, "/softignore")) {
      String arg = argAfter(line, "/softignore");
      return new ParsedInput.SoftIgnore(arg);
    }

    if (matchesCommand(line, "/unsoftignore")) {
      String arg = argAfter(line, "/unsoftignore");
      return new ParsedInput.UnsoftIgnore(arg);
    }

    if (matchesCommand(line, "/softignorelist") || matchesCommand(line, "/softignores")) {
      return new ParsedInput.SoftIgnoreList();
    }



    if (matchesCommand(line, "/version")) {
      String nick = argAfter(line, "/version");
      return new ParsedInput.CtcpVersion(nick);
    }

    if (matchesCommand(line, "/ping")) {
      String nick = argAfter(line, "/ping");
      return new ParsedInput.CtcpPing(nick);
    }

    if (matchesCommand(line, "/time")) {
      String nick = argAfter(line, "/time");
      return new ParsedInput.CtcpTime(nick);
    }

    if (matchesCommand(line, "/ctcp")) {
      String rest = argAfter(line, "/ctcp");
      String nick = "";
      String cmd = "";
      String args = "";

      int sp1 = rest.indexOf(' ');
      if (sp1 < 0) {
        nick = rest.trim();
      } else {
        nick = rest.substring(0, sp1).trim();
        String rest2 = rest.substring(sp1 + 1).trim();
        int sp2 = rest2.indexOf(' ');
        if (sp2 < 0) {
          cmd = rest2.trim();
        } else {
          cmd = rest2.substring(0, sp2).trim();
          args = rest2.substring(sp2 + 1).trim();
        }
      }

      return new ParsedInput.Ctcp(nick, cmd, args);
    }



    // IRCv3 CHATHISTORY
    if (matchesCommand(line, "/chathistory") || matchesCommand(line, "/history")) {
      String rest = matchesCommand(line, "/chathistory") ? argAfter(line, "/chathistory") : argAfter(line, "/history");
      return parseChatHistoryInput(rest);
    }

    // IRCv3 compose helpers (used by first-class reply/reaction input UX).
    if (matchesCommand(line, "/reply")) {
      return parseReplyInput(argAfter(line, "/reply"));
    }
    if (matchesCommand(line, "/react")) {
      return parseReactInput(argAfter(line, "/react"));
    }

    // Local-only filters (weechat-style).
    if (matchesCommand(line, "/filter")) {
      return new ParsedInput.Filter(filterCommandParser.parse(line));
    }

    // Raw IRC line escape hatch.
    if (matchesCommand(line, "/quote")) {
      String rawLine = argAfter(line, "/quote");
      return new ParsedInput.Quote(rawLine);
    }

    // Alias used by some clients.
    if (matchesCommand(line, "/raw")) {
      String rawLine = argAfter(line, "/raw");
      return new ParsedInput.Quote(rawLine);
    }

    return new ParsedInput.Unknown(line);
  }

  private static String argAfter(String line, String cmd) {
    if (line.equalsIgnoreCase(cmd)) return "";
    if (line.length() <= cmd.length()) return "";
    String rest = line.substring(cmd.length());
    return rest.trim();
  }

  private static ParsedInput parseJoinInput(String rest) {
    String r = rest == null ? "" : rest.trim();
    if (r.isEmpty()) return new ParsedInput.Join("", "");

    String[] toks = r.split("\\s+", 3);
    if (toks.length == 0) return new ParsedInput.Join("", "");
    if (toks.length > 2) return new ParsedInput.Join("", "");

    String channel = toks[0].trim();
    String key = toks.length > 1 ? toks[1].trim() : "";
    return new ParsedInput.Join(channel, key);
  }

  private static ParsedInput parseWhowasInput(String rest) {
    String r = rest == null ? "" : rest.trim();
    if (r.isEmpty()) return new ParsedInput.Whowas("", 0);

    String[] toks = r.split("\\s+", 3);
    if (toks.length == 0) return new ParsedInput.Whowas("", 0);
    if (toks.length > 2) return new ParsedInput.Whowas("", 0);

    String nick = toks[0].trim();
    if (nick.isEmpty()) return new ParsedInput.Whowas("", 0);

    if (toks.length == 1) return new ParsedInput.Whowas(nick, 0);

    String countRaw = toks[1].trim();
    if (!isIntegerToken(countRaw)) return new ParsedInput.Whowas("", 0);
    int count = parseIntOrZero(countRaw);
    if (count < 0) return new ParsedInput.Whowas("", 0);
    return new ParsedInput.Whowas(nick, count);
  }

  private static ParsedInput parseReplyInput(String rest) {
    String r = rest == null ? "" : rest.trim();
    if (r.isEmpty()) return new ParsedInput.ReplyMessage("", "");
    int sp = r.indexOf(' ');
    if (sp <= 0) return new ParsedInput.ReplyMessage(r.trim(), "");
    String msgId = r.substring(0, sp).trim();
    String body = r.substring(sp + 1).trim();
    return new ParsedInput.ReplyMessage(msgId, body);
  }

  private static ParsedInput parseReactInput(String rest) {
    String r = rest == null ? "" : rest.trim();
    if (r.isEmpty()) return new ParsedInput.ReactMessage("", "");
    int sp = r.indexOf(' ');
    if (sp <= 0) return new ParsedInput.ReactMessage(r.trim(), "");
    String msgId = r.substring(0, sp).trim();
    String reaction = r.substring(sp + 1).trim();
    return new ParsedInput.ReactMessage(msgId, reaction);
  }

  private static boolean isIntegerToken(String raw) {
    String s = raw == null ? "" : raw.trim();
    if (s.isEmpty()) return false;
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      if (i == 0 && (ch == '+' || ch == '-')) {
        if (s.length() == 1) return false;
        continue;
      }
      if (ch < '0' || ch > '9') return false;
    }
    return true;
  }

  private static int parseIntOrZero(String raw) {
    try {
      return Integer.parseInt(raw == null ? "" : raw.trim());
    } catch (Exception ignored) {
      return 0;
    }
  }

  private static ParsedInput parseChatHistoryInput(String rest) {
    String r = rest == null ? "" : rest.trim();
    if (r.isEmpty()) {
      return new ParsedInput.ChatHistoryBefore(0, "");
    }

    String[] toks = r.split("\\s+");
    if (toks.length == 0) {
      return new ParsedInput.ChatHistoryBefore(0, "");
    }

    String head = toks[0].toLowerCase(java.util.Locale.ROOT);
    return switch (head) {
      case "before" -> parseChatHistoryBefore(toks, 1);
      case "latest" -> parseChatHistoryLatest(toks, 1);
      case "between" -> parseChatHistoryBetween(toks, 1);
      case "around" -> parseChatHistoryAround(toks, 1);
      default -> parseChatHistoryBefore(toks, 0);
    };
  }

  private static ParsedInput parseChatHistoryBefore(String[] toks, int startIdx) {
    if (toks == null || startIdx >= toks.length) {
      return new ParsedInput.ChatHistoryBefore(0, "");
    }

    int idx = startIdx;
    int lim = 50;
    String selector = "";
    String first = toks[idx];
    if (isIntegerToken(first)) {
      lim = parseIntOrZero(first);
      idx++;
    } else {
      selector = normalizeChatHistorySelector(first);
      idx++;
      if (selector.isEmpty()) {
        return new ParsedInput.ChatHistoryBefore(0, "");
      }
    }

    if (idx < toks.length) {
      if (!isIntegerToken(toks[idx])) return new ParsedInput.ChatHistoryBefore(0, "");
      lim = parseIntOrZero(toks[idx]);
      idx++;
    }
    if (idx < toks.length) {
      return new ParsedInput.ChatHistoryBefore(0, "");
    }
    return new ParsedInput.ChatHistoryBefore(lim, selector);
  }

  private static ParsedInput parseChatHistoryLatest(String[] toks, int startIdx) {
    int idx = startIdx;
    int lim = 50;
    String selector = "*";

    if (toks == null) return new ParsedInput.ChatHistoryLatest(0, selector);

    if (idx < toks.length) {
      String first = toks[idx];
      if (isIntegerToken(first)) {
        lim = parseIntOrZero(first);
        idx++;
      } else {
        selector = normalizeChatHistorySelectorOrWildcard(first);
        idx++;
        if (selector.isEmpty()) {
          return new ParsedInput.ChatHistoryLatest(0, "");
        }
      }
    }

    if (idx < toks.length) {
      if (!isIntegerToken(toks[idx])) return new ParsedInput.ChatHistoryLatest(0, "");
      lim = parseIntOrZero(toks[idx]);
      idx++;
    }
    if (idx < toks.length) return new ParsedInput.ChatHistoryLatest(0, "");

    return new ParsedInput.ChatHistoryLatest(lim, selector);
  }

  private static ParsedInput parseChatHistoryAround(String[] toks, int startIdx) {
    if (toks == null || startIdx >= toks.length) return new ParsedInput.ChatHistoryAround("", 0);

    int idx = startIdx;
    String selector = normalizeChatHistorySelector(toks[idx]);
    if (selector.isEmpty()) return new ParsedInput.ChatHistoryAround("", 0);
    idx++;

    int lim = 50;
    if (idx < toks.length) {
      if (!isIntegerToken(toks[idx])) return new ParsedInput.ChatHistoryAround("", 0);
      lim = parseIntOrZero(toks[idx]);
      idx++;
    }
    if (idx < toks.length) return new ParsedInput.ChatHistoryAround("", 0);

    return new ParsedInput.ChatHistoryAround(selector, lim);
  }

  private static ParsedInput parseChatHistoryBetween(String[] toks, int startIdx) {
    if (toks == null || startIdx + 1 >= toks.length) {
      return new ParsedInput.ChatHistoryBetween("", "", 0);
    }

    int idx = startIdx;
    String startSelector = normalizeChatHistorySelectorOrWildcard(toks[idx++]);
    String endSelector = normalizeChatHistorySelectorOrWildcard(toks[idx++]);
    if (startSelector.isEmpty() || endSelector.isEmpty()) {
      return new ParsedInput.ChatHistoryBetween("", "", 0);
    }

    int lim = 50;
    if (idx < toks.length) {
      if (!isIntegerToken(toks[idx])) return new ParsedInput.ChatHistoryBetween("", "", 0);
      lim = parseIntOrZero(toks[idx]);
      idx++;
    }
    if (idx < toks.length) return new ParsedInput.ChatHistoryBetween("", "", 0);

    return new ParsedInput.ChatHistoryBetween(startSelector, endSelector, lim);
  }

  private static String normalizeChatHistorySelector(String raw) {
    String s = raw == null ? "" : raw.trim();
    if (s.isEmpty()) return "";
    int eq = s.indexOf('=');
    if (eq <= 0 || eq == s.length() - 1) return "";
    String key = s.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT);
    String value = s.substring(eq + 1).trim();
    if (value.isEmpty()) return "";
    if (!"timestamp".equals(key) && !"msgid".equals(key)) return "";
    return key + "=" + value;
  }

  private static String normalizeChatHistorySelectorOrWildcard(String raw) {
    String s = raw == null ? "" : raw.trim();
    if ("*".equals(s)) return "*";
    return normalizeChatHistorySelector(s);
  }

  
  private record ParsedTargetList(String channel, java.util.List<String> items) {}
  private record ParsedKick(String channel, String nick, String reason) {}

  private static ParsedTargetList parseTargetList(String rest) {
    String r = rest == null ? "" : rest.trim();
    if (r.isEmpty()) return new ParsedTargetList("", java.util.List.of());

    String[] toks = r.split("\\s+");
    String channel = "";
    int idx = 0;
    if (toks.length > 0 && (toks[0].startsWith("#") || toks[0].startsWith("&"))) {
      channel = toks[0];
      idx = 1;
    }

    java.util.List<String> items = new java.util.ArrayList<>();
    for (int i = idx; i < toks.length; i++) {
      String t = toks[i].trim();
      if (!t.isEmpty()) items.add(t);
    }
    return new ParsedTargetList(channel, java.util.List.copyOf(items));
  }

  private static ParsedKick parseKickArgs(String rest) {
    String r = rest == null ? "" : rest.trim();
    if (r.isEmpty()) return new ParsedKick("", "", "");

    String first;
    String afterFirst;
    int sp = r.indexOf(' ');
    if (sp < 0) {
      first = r;
      afterFirst = "";
    } else {
      first = r.substring(0, sp).trim();
      afterFirst = r.substring(sp + 1).trim();
    }

    if (first.startsWith("#") || first.startsWith("&")) {
      if (afterFirst.isEmpty()) return new ParsedKick(first, "", "");
      int sp2 = afterFirst.indexOf(' ');
      if (sp2 < 0) return new ParsedKick(first, afterFirst.trim(), "");
      String nick = afterFirst.substring(0, sp2).trim();
      String reason = afterFirst.substring(sp2 + 1).trim();
      return new ParsedKick(first, nick, reason);
    }

    return new ParsedKick("", first, afterFirst);
  }


  private static boolean matchesCommand(String line, String cmd) {
    if (line == null || cmd == null) return false;
    if (line.length() < cmd.length()) return false;
    if (!line.regionMatches(true, 0, cmd, 0, cmd.length())) return false;
    if (line.length() == cmd.length()) return true;
    char next = line.charAt(cmd.length());
    return Character.isWhitespace(next);
  }
}
