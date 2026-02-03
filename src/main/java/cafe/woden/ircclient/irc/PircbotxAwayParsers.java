package cafe.woden.ircclient.irc;

/**
 * Parsers for IRC away-related lines.
 *
 * <p>This class intentionally contains only pure parsing helpers extracted from
 * {@link PircbotxIrcClientService} during refactor step B2.2.
 */
final class PircbotxAwayParsers {
  private PircbotxAwayParsers() {}

  /** Parsed IRCv3 away-notify line (AWAY command from another user). */
  static record ParsedAwayNotify(String nick, IrcEvent.AwayState awayState, String message) {}

  /** Parsed RPL_UNAWAY (305) / RPL_NOWAWAY (306) confirmation line. */
  static record ParsedAwayConfirmation(boolean away, String server, String message) {}

  /**
   * Parse an IRCv3 away-notify line, which is delivered as a raw {@code AWAY} command
   * from another user.
   *
   * <p>Examples:
   * <ul>
   *   <li>{@code :nick!user@host AWAY :Gone away for now} (sets away)</li>
   *   <li>{@code :nick!user@host AWAY} (clears away)</li>
   * </ul>
   */
  static ParsedAwayNotify parseAwayNotify(String line) {
    if (line == null) return null;
    String s = line.trim();
    if (!s.startsWith(":")) return null;

    // Extract prefix up to first space.
    int sp = s.indexOf(' ');
    if (sp <= 1 || sp + 1 >= s.length()) return null;
    String prefix = s.substring(1, sp);
    String rest = s.substring(sp + 1).trim();

    if (!(rest.startsWith("AWAY") && (rest.length() == 4 || Character.isWhitespace(rest.charAt(4))))) {
      return null;
    }

    String nick = prefix;
    int bang = nick.indexOf('!');
    if (bang > 0) nick = nick.substring(0, bang);
    nick = nick.trim();
    if (nick.isBlank()) return null;

    // RFC: AWAY with a parameter sets away; AWAY with no parameter clears it.
    // away-notify SHOULD follow that; however, be resilient to weird formatting.
    boolean hasParam = rest.length() > 4;
    IrcEvent.AwayState state = hasParam ? IrcEvent.AwayState.AWAY : IrcEvent.AwayState.HERE;

    String msg = null;
    if (state == IrcEvent.AwayState.AWAY) {
      String rem = rest.substring(4).trim();
      if (rem.startsWith(":")) rem = rem.substring(1).trim();
      msg = rem.isEmpty() ? null : rem;
    }

    return new ParsedAwayNotify(nick, state, msg);
  }

  /**
   * Parse RPL_UNAWAY (305) / RPL_NOWAWAY (306) lines and extract the trailing message.
   *
   * <p>Typical format: ":server 305 <me> :You are no longer marked as being away"
   * <br>Typical format: ":server 306 <me> :You have been marked as being away"
   */
  static ParsedAwayConfirmation parseRpl305or306Away(String line) {
    if (line == null) return null;
    String s = line.trim();
    if (s.isEmpty()) return null;

    String server = "";
    // Capture and drop prefix (e.g., ":server ")
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 1) {
        server = s.substring(1, sp);
        if (sp + 1 < s.length()) s = s.substring(sp + 1).trim();
      }
    }

    boolean is305 = s.startsWith("305 ") || s.startsWith("305\t") || s.equals("305");
    boolean is306 = s.startsWith("306 ") || s.startsWith("306\t") || s.equals("306");
    if (!is305 && !is306) return null;

    boolean away = is306;

    // Prefer IRC trailing parameter extraction (space-colon).
    String msg = null;
    int trail = s.indexOf(" :");
    if (trail >= 0 && trail + 2 < s.length()) {
      msg = s.substring(trail + 2).trim();
    } else {
      // Fallback: join tokens after the numeric + target.
      String[] toks = s.split("\\s+");
      if (toks.length >= 3) {
        msg = String.join(" ", java.util.Arrays.copyOfRange(toks, 2, toks.length)).trim();
      }
    }

    return new ParsedAwayConfirmation(away, server, msg);
  }
}
