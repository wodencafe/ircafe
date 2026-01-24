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

    if (line.startsWith("/join")) {
      String chan = argAfter(line, "/join");
      return new ParsedInput.Join(chan);
    }

    if (line.startsWith("/nick")) {
      String nick = argAfter(line, "/nick");
      return new ParsedInput.Nick(nick);
    }

    if (line.startsWith("/query")) {
      String nick = argAfter(line, "/query");
      return new ParsedInput.Query(nick);
    }

    if (line.startsWith("/msg")) {
      String rest = argAfter(line, "/msg");
      int sp = rest.indexOf(' ');
      if (sp <= 0) return new ParsedInput.Msg(rest.trim(), "");
      String nick = rest.substring(0, sp).trim();
      String body = rest.substring(sp + 1).trim();
      return new ParsedInput.Msg(nick, body);
    }

    if (line.startsWith("/me")) {
      String action = argAfter(line, "/me");
      return new ParsedInput.Me(action);
    }

    return new ParsedInput.Unknown(line);
  }

  private static String argAfter(String line, String cmd) {
    if (line.equalsIgnoreCase(cmd)) return "";
    if (line.length() <= cmd.length()) return "";
    String rest = line.substring(cmd.length());
    return rest.trim();
  }
}
