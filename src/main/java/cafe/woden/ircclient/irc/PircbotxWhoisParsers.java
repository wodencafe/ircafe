package cafe.woden.ircclient.irc;

/**
 * Parsers for WHOIS/WHOWAS related numerics.
 *
 * <p>This class intentionally contains only pure parsing helpers extracted from
 * {@link PircbotxIrcClientService} during refactor step B2.3.
 */
final class PircbotxWhoisParsers {
  private PircbotxWhoisParsers() {}

  /** Parsed RPL_WHOISUSER (311) or RPL_WHOWASUSER (314) user/host fields. */
  static record ParsedWhoisUser(String nick, String user, String host) {}

  /** Parsed RPL_AWAY (301) during WHOIS. */
  static record ParsedWhoisAway(String nick, String message) {}

  /**
   * Parse RPL_WHOISUSER (311) lines.
   *
   * <p>Format: ":server 311 <me> <nick> <user> <host> * :<realname>"
   */
  static ParsedWhoisUser parseRpl311WhoisUser(String line) {
    if (line == null) return null;
    String s = line.trim();
    if (s.isEmpty()) return null;

    // Drop prefix (e.g., ":server ")
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
    }

    String[] toks = s.split("\\s+");
    if (toks.length < 5) return null;
    if (!"311".equals(toks[0])) return null;

    // 311 <me> <nick> <user> <host> ...
    String nick = toks[2];
    String user = toks[3];
    String host = toks[4];
    if (nick == null || nick.isBlank() || user == null || user.isBlank() || host == null || host.isBlank()) return null;
    return new ParsedWhoisUser(nick, user, host);
  }

  /**
   * Parse RPL_WHOWASUSER (314) lines.
   *
   * <p>Format: ":server 314 <me> <nick> <user> <host> * :<realname>"
   */
  static ParsedWhoisUser parseRpl314WhowasUser(String line) {
    if (line == null) return null;
    String s = line.trim();
    if (s.isEmpty()) return null;

    // Drop prefix (e.g., ":server ")
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
    }

    String[] toks = s.split("\\s+");
    if (toks.length < 5) return null;
    if (!"314".equals(toks[0])) return null;

    // 314 <me> <nick> <user> <host> ...
    String nick = toks[2];
    String user = toks[3];
    String host = toks[4];
    if (nick == null || nick.isBlank() || user == null || user.isBlank() || host == null || host.isBlank()) return null;
    return new ParsedWhoisUser(nick, user, host);
  }

  /**
   * Parse RPL_AWAY (301) lines received during WHOIS.
   *
   * <p>Format: ":server 301 <me> <nick> :<away message>"
   */
  static ParsedWhoisAway parseRpl301WhoisAway(String line) {
    if (line == null) return null;
    String s = line.trim();
    if (s.isEmpty()) return null;

    // Drop prefix (e.g., ":server ")
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
    }

    if (!s.startsWith("301 ") && !s.equals("301") && !s.startsWith("301\t")) {
      // Token-based check below will handle most cases; this is just a small fast-path.
    }

    String[] toks = s.split("\\s+");
    if (toks.length < 3) return null;
    if (!"301".equals(toks[0])) return null;

    String nick = toks[2];
    if (nick == null || nick.isBlank()) return null;

    String msg = null;
    int colon = s.indexOf(" :");
    if (colon >= 0 && colon + 2 < s.length()) {
      msg = s.substring(colon + 2).trim();
    }
    return new ParsedWhoisAway(nick, msg);
  }

  /**
   * Parse RPL_ENDOFWHOIS (318) lines and extract the nick.
   *
   * <p>Format: ":server 318 <me> <nick> :End of /WHOIS list."
   */
  static String parseRpl318EndOfWhoisNick(String line) {
    if (line == null) return null;
    String s = line.trim();
    if (s.isEmpty()) return null;

    // Drop prefix (e.g., ":server ")
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
    }

    String[] toks = s.split("\\s+");
    if (toks.length < 3) return null;
    if (!"318".equals(toks[0])) return null;

    String nick = toks[2];
    if (nick == null || nick.isBlank()) return null;
    return nick;
  }
}
