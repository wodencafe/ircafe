package cafe.woden.ircclient.irc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Parsers for IRC ISON responses (RPL_ISON 303). */
public final class PircbotxIsonParsers {
  private PircbotxIsonParsers() {}

  /**
   * Parse online nick list from RPL_ISON (303).
   *
   * <p>Typical shape:
   *
   * <pre>
   * :server 303 me :nick1 nick2
   * </pre>
   *
   * @return online nicks for a 303 line (possibly empty), or {@code null} if not a 303 line.
   */
  public static List<String> parseRpl303IsonOnlineNicks(String rawLine) {
    ParsedLine parsed = parseLine(rawLine);
    if (parsed == null || !"303".equals(parsed.command())) return null;

    String listToken = parsed.trailing();
    if (listToken.isEmpty()) {
      List<String> params = parsed.params();
      if (params.size() > 1) {
        listToken = String.join(" ", params.subList(1, params.size()));
      }
    }
    return parseNickList(listToken);
  }

  private static List<String> parseNickList(String rawNicks) {
    String raw = Objects.toString(rawNicks, "").trim();
    if (raw.isEmpty()) return List.of();

    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    String normalizedSeparators = raw.replace(',', ' ');
    for (String token : normalizedSeparators.split("\\s+")) {
      String nick = Objects.toString(token, "").trim();
      if (nick.isEmpty()) continue;
      if (nick.startsWith(":")) nick = nick.substring(1).trim();
      if (nick.isEmpty()) continue;

      int bang = nick.indexOf('!');
      if (bang > 0) nick = nick.substring(0, bang).trim();
      if (nick.isEmpty()) continue;
      if (nick.startsWith("#") || nick.startsWith("&")) continue;

      out.putIfAbsent(nick.toLowerCase(Locale.ROOT), nick);
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out.values());
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
