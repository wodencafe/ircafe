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
      String chan = argAfter(line, "/join");
      return new ParsedInput.Join(chan);
    }

    // Common alias used by many IRC clients.
    if (matchesCommand(line, "/j")) {
      String chan = argAfter(line, "/j");
      return new ParsedInput.Join(chan);
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



    // IRCv3 CHATHISTORY (debug/developer helper)
    if (matchesCommand(line, "/chathistory") || matchesCommand(line, "/history")) {
      String rest = matchesCommand(line, "/chathistory") ? argAfter(line, "/chathistory") : argAfter(line, "/history");
      String r = rest == null ? "" : rest.trim();
      int lim = 50;
      if (!r.isEmpty()) {
        try {
          lim = Integer.parseInt(r);
        } catch (NumberFormatException ignored) {
          lim = 0;
        }
      }
      return new ParsedInput.ChatHistoryBefore(lim);
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

  
  private record ParsedTargetList(String channel, java.util.List<String> items) {}

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


  private static boolean matchesCommand(String line, String cmd) {
    if (line == null || cmd == null) return false;
    if (line.length() < cmd.length()) return false;
    if (!line.regionMatches(true, 0, cmd, 0, cmd.length())) return false;
    if (line.length() == cmd.length()) return true;
    char next = line.charAt(cmd.length());
    return Character.isWhitespace(next);
  }
}
