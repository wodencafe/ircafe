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

    if (matchesCommand(line, "/nick")) {
      String nick = argAfter(line, "/nick");
      return new ParsedInput.Nick(nick);
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

    if (matchesCommand(line, "/me")) {
      String action = argAfter(line, "/me");
      return new ParsedInput.Me(action);
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

    return new ParsedInput.Unknown(line);
  }

  private static String argAfter(String line, String cmd) {
    if (line.equalsIgnoreCase(cmd)) return "";
    if (line.length() <= cmd.length()) return "";
    String rest = line.substring(cmd.length());
    return rest.trim();
  }

  /** Case-insensitive command match with a word boundary (end or whitespace). */
  private static boolean matchesCommand(String line, String cmd) {
    if (line == null || cmd == null) return false;
    if (line.length() < cmd.length()) return false;
    if (!line.regionMatches(true, 0, cmd, 0, cmd.length())) return false;
    if (line.length() == cmd.length()) return true;
    char next = line.charAt(cmd.length());
    return Character.isWhitespace(next);
  }
}
