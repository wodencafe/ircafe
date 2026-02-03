package cafe.woden.ircclient.irc;

/**
 * Small, dependency-free parsing helpers used by {@link PircbotxIrcClientService}.
 *
 * <p>Keeping these in a dedicated file reduces the size/visual noise of the service and makes
 * future parser extraction less risky.
 */
final class PircbotxLineParseUtil {

  private PircbotxLineParseUtil() {}

  /**
   * Best-effort normalization for server lines we parse ourselves.
   *
   * <ul>
   *   <li>Strips IRCv3 message tags (leading "@tag=value;... ")</li>
   *   <li>Unwraps some toString() formats like "UnknownEvent(line=...)"</li>
   * </ul>
   */
  static String normalizeIrcLineForParsing(String raw) {
    if (raw == null) return null;
    String s = raw.trim();

    // Some PircBotX/ListenerManager implementations (or fallbacks to toString())
    // may format unknown lines like: "UnknownEvent(line=:nick!u@h AWAY :msg)".
    // Try to unwrap to the actual IRC line.
    int idx = s.indexOf("line=");
    int keyLen = 5;
    if (idx < 0) {
      idx = s.indexOf("rawLine=");
      keyLen = 8;
    }
    if (idx >= 0) {
      String sub = s.substring(idx + keyLen).trim();
      // Strip trailing wrapper chars, but keep whitespace inside the IRC line intact.
      while (!sub.isEmpty()) {
        char last = sub.charAt(sub.length() - 1);
        if (last == ')' || last == '}' || last == ']') sub = sub.substring(0, sub.length() - 1).trim();
        else break;
      }
      if (sub.startsWith("\"") && sub.endsWith("\"") && sub.length() >= 2) {
        sub = sub.substring(1, sub.length() - 1);
      }
      s = sub;
    }

    // IRCv3 message tags: "@aaa=bbb;ccc :prefix COMMAND ..."
    if (s.startsWith("@")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) {
        s = s.substring(sp + 1).trim();
      }
    }

    return s;
  }

  static boolean looksNumeric(String s) {
    if (s == null || s.isBlank()) return false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < '0' || c > '9') return false;
    }
    return true;
  }

  static boolean looksLikeChannel(String s) {
    if (s == null || s.isBlank()) return false;
    char c = s.charAt(0);
    return c == '#' || c == '&';
  }

  static boolean looksLikeUser(String s) {
    if (s == null || s.isBlank()) return false;
    if (looksLikeChannel(s)) return false;
    if (looksNumeric(s)) return false;
    if (s.indexOf('!') >= 0 || s.indexOf('@') >= 0) return false;
    // Usernames are typically short-ish and don't contain spaces or colons.
    if (s.indexOf(':') >= 0) return false;
    if (s.length() > 64) return false;
    return true;
  }

  static boolean looksLikeHost(String s) {
    if (s == null || s.isBlank()) return false;
    if (looksLikeChannel(s)) return false;
    if (s.indexOf('!') >= 0 || s.indexOf('@') >= 0) return false;
    // Hostnames (or vhost/gateway strings) usually contain '.', ':', or '/'.
    return (s.indexOf('.') >= 0) || (s.indexOf(':') >= 0) || (s.indexOf('/') >= 0);
  }

  static boolean looksLikeIp(String s) {
    if (s == null || s.isBlank()) return false;
    // IPv4
    if (s.matches("\\d{1,3}(?:\\.\\d{1,3}){3}")) return true;
    // IPv6 (very loose)
    return s.indexOf(':') >= 0 && s.matches("[0-9A-Fa-f:]+");
  }

  static boolean looksLikeNick(String s) {
    if (s == null || s.isBlank()) return false;
    if (looksLikeChannel(s)) return false;
    if (looksNumeric(s)) return false;
    // Loose IRC nick pattern; allow '.' and '-' for permissive networks.
    return s.matches("[A-Za-z\\[\\]\\\\`_\\^\\{\\|\\}][A-Za-z0-9\\-\\.\\[\\]\\\\`_\\^\\{\\|\\}]*");
  }
}
