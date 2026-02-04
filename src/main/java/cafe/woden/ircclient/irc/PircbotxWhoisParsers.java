package cafe.woden.ircclient.irc;

/**
 * Parsers for WHOIS/WHOWAS related numerics.
 *
 * <p>This class intentionally contains only pure parsing helpers extracted from
 * {@link PircbotxIrcClientService} during refactor step B2.3.
 */
final class PircbotxWhoisParsers {
  private PircbotxWhoisParsers() {}

  static record ParsedWhoisUser(String nick, String user, String host) {}

  static record ParsedWhoisAway(String nick, String message) {}

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
