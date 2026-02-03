package cafe.woden.ircclient.irc;

/**
 * Parsers for WHO/WHOX and USERHOST related numerics.
 *
 * <p>This class intentionally contains only pure parsing helpers extracted from
 * {@link PircbotxIrcClientService} during refactor step B2.4.
 */
final class PircbotxWhoUserhostParsers {
  private PircbotxWhoUserhostParsers() {}

  static record ParsedWhoReply(String channel, String nick, String user, String host) {}

  static record ParsedWhoxReply(String channel, String nick, String user, String host) {}

  static record UserhostEntry(String nick, String hostmask, IrcEvent.AwayState awayState) {}


  /**
   * Parse RPL_WHOREPLY (352) lines.
   *
   * <p>Common format: ":server 352 <me> <channel> <user> <host> <server> <nick> <flags> :<hopcount> <realname>"
   */
  static ParsedWhoReply parseRpl352WhoReply(String line) {
    if (line == null) return null;
    String s = line.trim();
    if (s.isEmpty()) return null;

    // Drop prefix (e.g., ":server ")
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
    }

    String[] toks = s.split("\\s+");
    if (toks.length < 8) return null;
    if (!"352".equals(toks[0])) return null;

    // 352 <me> <channel> <user> <host> <server> <nick> <flags> ...
    String channel = toks[2];
    String user = toks[3];
    String host = toks[4];
    String nick = toks[6];

    if (channel == null || channel.isBlank()) return null;
    if (nick == null || nick.isBlank()) return null;
    if (user == null || user.isBlank()) return null;
    if (host == null || host.isBlank()) return null;

    return new ParsedWhoReply(channel, nick, user, host);
  }


  /**
   * Parse RPL_WHOSPCRPL (354) / WHOX lines.
   *
   * <p>Format varies based on the requested WHOX fields (see the WHOX extension). We use a conservative heuristic:
   * <ul>
   *   <li>Strip the server prefix</li>
   *   <li>Tokenize up to the trailing ":" parameter (realname)</li>
   *   <li>Capture an optional querytype (integer) and optional channel token</li>
   *   <li>Find a (user, host) pair (adjacent or separated by an IP field)</li>
   *   <li>Then pick the first plausible nick token after that host (skipping host-like tokens such as server names)</li>
   * </ul>
   *
   * <p>If we cannot confidently extract user/host/nick, returns null.
   */
  static ParsedWhoxReply parseRpl354WhoxReply(String line) {
    if (line == null) return null;
    String s = line.trim();
    if (s.isEmpty()) return null;

    // Drop prefix (e.g., ":server ")
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
    }

    // Remove trailing ":" parameter (usually realname)
    int colon = s.indexOf(" :");
    String head = colon >= 0 ? s.substring(0, colon).trim() : s;

    String[] toks = head.split("\\s+");
    if (toks.length < 3) return null;
    if (!"354".equals(toks[0])) return null;

    // toks: 354 <me> <fields...>
    java.util.List<String> fields = new java.util.ArrayList<>();
    for (int i = 2; i < toks.length; i++) {
      String t = toks[i];
      if (t == null || t.isBlank()) continue;
      fields.add(t);
    }
    if (fields.isEmpty()) return null;

    // Optional querytype at start
    int idx = 0;
    if (PircbotxLineParseUtil.looksNumeric(fields.get(0))) idx++;

    String channel = "";
    if (idx < fields.size() && PircbotxLineParseUtil.looksLikeChannel(fields.get(idx))) {
      channel = fields.get(idx);
    } else {
      // Channel might appear later depending on requested fields.
      for (String f : fields) {
        if (PircbotxLineParseUtil.looksLikeChannel(f)) {
          channel = f;
          break;
        }
      }
    }

    // Find a user/host pair.
    int userIdx = -1;
    int hostIdx = -1;
    for (int i = 0; i < fields.size(); i++) {
      String a = fields.get(i);
      if (!PircbotxLineParseUtil.looksLikeUser(a)) continue;

      if (i + 1 < fields.size()) {
        String b = fields.get(i + 1);
        if (PircbotxLineParseUtil.looksLikeHost(b)
            && !PircbotxLineParseUtil.looksLikeChannel(b)
            && !PircbotxLineParseUtil.looksNumeric(b)) {
          userIdx = i;
          hostIdx = i + 1;
          break;
        }
      }
      if (i + 2 < fields.size()) {
        String b = fields.get(i + 1);
        String c = fields.get(i + 2);
        if (PircbotxLineParseUtil.looksLikeIp(b)
            && PircbotxLineParseUtil.looksLikeHost(c)
            && !PircbotxLineParseUtil.looksLikeChannel(c)
            && !PircbotxLineParseUtil.looksNumeric(c)) {
          userIdx = i;
          hostIdx = i + 2;
          break;
        }
      }
    }
    if (userIdx < 0 || hostIdx < 0) return null;

    String user = fields.get(userIdx);
    String host = fields.get(hostIdx);
    if (user == null || user.isBlank() || host == null || host.isBlank()) return null;

    // Find nick after host, skipping server/host-like tokens, flags/hops, etc.
    String nick = null;
    for (int j = hostIdx + 1; j < fields.size(); j++) {
      String t = fields.get(j);
      if (t == null || t.isBlank()) continue;
      if (PircbotxLineParseUtil.looksNumeric(t)) continue;
      if (PircbotxLineParseUtil.looksLikeChannel(t)) continue;
      if (PircbotxLineParseUtil.looksLikeHost(t) || PircbotxLineParseUtil.looksLikeIp(t)) continue;
      if (!PircbotxLineParseUtil.looksLikeNick(t)) continue;
      nick = t;
      break;
    }
    if (nick == null || nick.isBlank()) return null;

    String hm = nick + "!" + user + "@" + host;
    if (!PircbotxUtil.isUsefulHostmask(hm)) return null;

    return new ParsedWhoxReply(channel, nick, user, host);
  }


  /**
   * Parse RPL_USERHOST (302) lines.
   *
   * <p>Format: ":server 302 <me> :nick[\*]=[+|-]user@host ..."
   */
  static java.util.List<UserhostEntry> parseRpl302Userhost(String line) {
    if (line == null) return null;
    String s = line.trim();
    if (s.isEmpty()) return null;

    // Drop prefix (e.g., ":server ")
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
    }

    // Quick check
    if (!s.startsWith("302 ") && !s.startsWith("302\t") && !s.startsWith("302\n")) {
      // Sometimes the numeric is not at offset 0 due to stray formatting; fall back to token check.
    }

    String[] toks = s.split("\\s+");
    if (toks.length < 4) return null;
    if (!"302".equals(toks[0])) return null;

    int colon = s.indexOf(" :");
    if (colon < 0 || colon + 2 >= s.length()) return null;
    String payload = s.substring(colon + 2).trim();
    if (payload.isEmpty()) return null;

    java.util.List<UserhostEntry> out = new java.util.ArrayList<>();
    for (String part : payload.split("\\s+")) {
      if (part == null || part.isBlank()) continue;
      String p = part.trim();
      if (p.startsWith(":")) p = p.substring(1);

      int eq = p.indexOf('=');
      if (eq <= 0 || eq >= p.length() - 1) continue;

      String nickPart = p.substring(0, eq).trim();
      if (nickPart.endsWith("*")) nickPart = nickPart.substring(0, nickPart.length() - 1);
      String nick = nickPart.trim();
      if (nick.isEmpty()) continue;

      String rhs = p.substring(eq + 1).trim();
      if (rhs.isEmpty()) continue;

      // Strip away/available marker.
      // Per RPL_USERHOST (302), '+' means not away and '-' means away.
      IrcEvent.AwayState as = IrcEvent.AwayState.UNKNOWN;
      if (rhs.charAt(0) == '+' || rhs.charAt(0) == '-') {
        as = (rhs.charAt(0) == '-') ? IrcEvent.AwayState.AWAY : IrcEvent.AwayState.HERE;
        rhs = rhs.substring(1);
      }

      int at = rhs.indexOf('@');
      if (at <= 0 || at >= rhs.length() - 1) continue;
      String user = rhs.substring(0, at).trim();
      String host = rhs.substring(at + 1).trim();
      if (user.isEmpty() || host.isEmpty()) continue;

      String hm = nick + "!" + user + "@" + host;
      if (!PircbotxUtil.isUsefulHostmask(hm)) continue;
      out.add(new UserhostEntry(nick, hm, as));
    }

    return out.isEmpty() ? null : java.util.List.copyOf(out);
  }
}
