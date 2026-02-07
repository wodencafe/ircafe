package cafe.woden.ircclient.irc;

final class PircbotxWhoUserhostParsers {
  private PircbotxWhoUserhostParsers() {}


  /**
   * Parse RPL_ISUPPORT (005) and return true if WHOX token is present.
   *
   * <p>Servers may split ISUPPORT across multiple 005 lines. Call this on each line and treat
   * a single true result as sufficient evidence of WHOX support.
   */
  static boolean parseRpl005IsupportHasWhox(String line) {
    if (line == null) return false;
    String s = line.trim();
    if (s.isEmpty()) return false;

    // Drop prefix (e.g., ":server ")
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
    }

    String[] toks = s.split("\\s+");
    if (toks.length < 3) return false;

    // 005 <me> <tokens...> :are supported by this server
    if (!"005".equals(toks[0])) return false;

    for (int i = 2; i < toks.length; i++) {
      String t = toks[i];
      if (t == null || t.isBlank()) continue;
      if (t.startsWith(":")) break;
      int eq = t.indexOf('=');
      String key = eq >= 0 ? t.substring(0, eq) : t;
      if ("WHOX".equalsIgnoreCase(key)) return true;
    }
    return false;
  }

  static record ParsedWhoReply(String channel, String nick, String user, String host, String flags) {}

  static record ParsedWhoxReply(String channel, String nick, String user, String host) {}

  /** Strict parse for IRCafe-issued WHOX scans: WHO <chan> %tcuhnaf,<token> */
  static record ParsedWhoxTcuhnaf(
      String token,
      String channel,
      String user,
      String host,
      String nick,
      String flags,
      String account
  ) {}

  static record UserhostEntry(String nick, String hostmask, IrcEvent.AwayState awayState) {}

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
    String flags = toks[7];

    if (channel == null || channel.isBlank()) return null;
    if (nick == null || nick.isBlank()) return null;
    if (user == null || user.isBlank()) return null;
    if (host == null || host.isBlank()) return null;

    if (flags == null) flags = "";

    return new ParsedWhoReply(channel, nick, user, host, flags);
  }


  /**
   * Strict parse for WHOX results for the IRCafe-issued field set: %tcuhnaf
   *
   * <p>Expected numeric form after stripping IRCv3 tags:
   * <pre>
   * :server 354 <me> <token> <channel> <user> <host> <nick> <flags> <account> :<optional trailing>
   * </pre>
   * We validate the token and channel-ish shape to avoid mis-parsing arbitrary WHOX formats.
   */
  static ParsedWhoxTcuhnaf parseRpl354WhoxTcuhnaf(String line, String expectedToken) {
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
    // Typical shape for the IRCafe-issued field set (%tcuhnaf or equivalent):
    //   354 <me> <token> <channel> <user> <host> <nick> <flags> <account>
    // However, some networks honor the requested field order strictly and will return:
    //   354 <me> <token> <channel> <user> <host> <nick> <account> <flags>
    // And a few omit account/flags entirely.
    if (toks.length < 7) return null;
    if (!"354".equals(toks[0])) return null;

    String token = toks[2];
    if (token == null || token.isBlank()) return null;
    if (expectedToken != null && !expectedToken.isBlank() && !expectedToken.equals(token)) return null;

    String channel = toks.length > 3 ? toks[3] : null;
    String user = toks.length > 4 ? toks[4] : null;
    String host = toks.length > 5 ? toks[5] : null;
    String nick = toks.length > 6 ? toks[6] : null;

    String f1 = toks.length > 7 ? toks[7] : null;
    String f2 = toks.length > 8 ? toks[8] : null;

    String flags = null;
    String account = null;
    if (f2 == null) {
      // Only one extra field present.
      if (looksLikeWhoxFlags(f1)) flags = f1;
      else account = f1;
    } else {
      boolean f1Flags = looksLikeWhoxFlags(f1);
      boolean f2Flags = looksLikeWhoxFlags(f2);
      boolean f1Acct = looksLikeAccountToken(f1);
      boolean f2Acct = looksLikeAccountToken(f2);

      // Prefer the most "obvious" assignment.
      if (f1Flags && f2Acct && !f2Flags) {
        flags = f1;
        account = f2;
      } else if (f2Flags && f1Acct && !f1Flags) {
        flags = f2;
        account = f1;
      } else {
        // Fall back to the historical interpretation (flags then account).
        flags = f1;
        account = f2;
      }
    }

    if (channel == null || channel.isBlank() || !PircbotxLineParseUtil.looksLikeChannel(channel)) return null;
    if (nick == null || nick.isBlank() || !PircbotxLineParseUtil.looksLikeNick(nick)) return null;
    if (user == null || user.isBlank() || !PircbotxLineParseUtil.looksLikeUser(user)) return null;
    if (host == null || host.isBlank() || !PircbotxLineParseUtil.looksLikeHost(host)) return null;

    // Some servers send "*" / "0" for logged-out; normalize blanks to null here.
    if (account != null && account.isBlank()) account = null;

    String hm = nick + "!" + user + "@" + host;
    if (!PircbotxUtil.isUsefulHostmask(hm)) return null;

    if (flags == null) flags = "";

    return new ParsedWhoxTcuhnaf(token, channel, user, host, nick, flags, account);
  }

  /**
   * Returns true if this line appears to be an RPL_WHOSPCRPL (354) WHOX reply for the given token.
   *
   * <p>This is used to detect schema mismatches when strict parsing fails, so enrichment can
   * fall back to plain WHO/USERHOST instead of silently "working" without producing account updates.
   */
  static boolean seemsRpl354WhoxWithToken(String line, String expectedToken) {
    if (line == null || expectedToken == null || expectedToken.isBlank()) return false;
    String s = line.trim();
    if (s.isEmpty()) return false;

    // Drop prefix (e.g., ":server ")
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
    }

    int colon = s.indexOf(" :");
    String head = colon >= 0 ? s.substring(0, colon).trim() : s;
    String[] toks = head.split("\\s+");
    if (toks.length < 4) return false;
    if (!"354".equals(toks[0])) return false;

    // Common shape: 354 <me> <token> <channel> ...
    if (!expectedToken.equals(toks[2])) return false;
    return PircbotxLineParseUtil.looksLikeChannel(toks[3]);
  }

  private static boolean looksLikeWhoxFlags(String s) {
    if (s == null || s.isBlank()) return false;
    if (s.length() > 32) return false;
    // Common away markers (H/G) and user status prefixes (@/+/%/~/&).* may also appear.
    return s.indexOf('H') >= 0
        || s.indexOf('G') >= 0
        || s.indexOf('@') >= 0
        || s.indexOf('+') >= 0
        || s.indexOf('%') >= 0
        || s.indexOf('~') >= 0
        || s.indexOf('&') >= 0
        || "*".equals(s);
  }

  private static boolean looksLikeAccountToken(String s) {
    if (s == null || s.isBlank()) return false;
    // Logged-out markers commonly seen in WHOX.
    if ("*".equals(s) || "0".equals(s)) return true;
    // Avoid treating obvious flags as accounts.
    if (looksLikeWhoxFlags(s) && s.length() <= 3) return false;
    // Account names are typically nick-ish; be permissive.
    return s.matches("[A-Za-z0-9_\\-\\.\\[\\]\\\\`\\^\\{\\|\\}]+");
  }

  /** Parse RPL_WHOSPCRPL (354) / WHOX lines. */
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
