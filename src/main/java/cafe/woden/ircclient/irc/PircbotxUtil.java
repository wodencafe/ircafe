package cafe.woden.ircclient.irc;

import java.util.List;
import java.util.Objects;
import org.pircbotx.User;

/**
 * Small, boring helpers used by the PircBotX bridge.
 *
 * <p>Keep this intentionally lightweight and mostly pure so we can unit test later.
 */
final class PircbotxUtil {

  private PircbotxUtil() {}

  static String sanitizeNick(String nick) {
    String n = Objects.requireNonNull(nick, "nick").trim();
    if (n.isEmpty()) throw new IllegalArgumentException("nick is blank");
    if (n.contains("\r") || n.contains("\n"))
      throw new IllegalArgumentException("nick contains CR/LF");
    if (n.contains(" "))
      throw new IllegalArgumentException("nick contains spaces");
    return n;
  }

  static String sanitizeChannel(String channel) {
    String c = Objects.requireNonNull(channel, "channel").trim();
    if (c.isEmpty()) throw new IllegalArgumentException("channel is blank");
    if (c.contains("\r") || c.contains("\n"))
      throw new IllegalArgumentException("channel contains CR/LF");
    if (c.contains(" "))
      throw new IllegalArgumentException("channel contains spaces");
    if (!(c.startsWith("#") || c.startsWith("&")))
      throw new IllegalArgumentException("channel must start with # or & (got: " + c + ")");
    return c;
  }

  @FunctionalInterface
  interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  @FunctionalInterface
  interface ThrowingLongSupplier {
    long getAsLong() throws Exception;
  }

  static String safeStr(ThrowingSupplier<String> s, String def) {
    try {
      String v = s.get();
      return v == null ? def : v;
    } catch (Exception ignored) {
      return def;
    }
  }

  static long safeLong(ThrowingLongSupplier s, long def) {
    try {
      return s.getAsLong();
    } catch (Exception ignored) {
      return def;
    }
  }

  @SuppressWarnings("unchecked")
  static <T> List<T> safeList(ThrowingSupplier<List<T>> s) {
    try {
      List<T> v = s.get();
      return v == null ? java.util.List.of() : v;
    } catch (Exception ignored) {
      return java.util.List.of();
    }
  }

  /** Returns CTCP ACTION body or null if message isn't a CTCP ACTION. */
  static String parseCtcpAction(String message) {
    if (message == null || message.length() < 2) return null;
    if (message.charAt(0) != 0x01 || message.charAt(message.length() - 1) != 0x01) return null;
    String inner = message.substring(1, message.length() - 1).trim();
    if (inner.isEmpty()) return null;

    // CTCP ACTION: "\u0001ACTION <text>\u0001"
    if (inner.regionMatches(true, 0, "ACTION", 0, 6)) {
      String rest = inner.length() > 6 ? inner.substring(6).trim() : "";
      return rest;
    }
    return null;
  }

  static boolean isCtcpWrapped(String message) {
    if (message == null || message.length() < 2) return false;
    return message.charAt(0) == 0x01 && message.charAt(message.length() - 1) == 0x01;
  }

  /**
   * Best-effort hostmask derived from PircBotX's {@link User}.
   * Returns empty string if no useful information exists.
   */
  static String hostmaskFromUser(User user) {
    if (user == null) return "";

    String hm = safeStr(user::getHostmask, "");
    if (hm != null && !hm.isBlank()) return hm;

    String nick = safeStr(user::getNick, "");
    String login = safeStr(user::getLogin, "");
    String host = safeStr(user::getHostname, "");

    if (nick == null) nick = "";
    if (login == null) login = "";
    if (host == null) host = "";

    nick = nick.trim();
    login = login.trim();
    host = host.trim();

    if (!nick.isEmpty()) {
      String ident = login.isEmpty() ? "*" : login;
      String h = host.isEmpty() ? "*" : host;
      return nick + "!" + ident + "@" + h;
    }

    return "";
  }

  /**
   * True if hostmask contains something other than "*!*@*" placeholders.
   */
  static boolean isUsefulHostmask(String hostmask) {
    if (hostmask == null) return false;
    String hm = hostmask.trim();
    if (hm.isEmpty()) return false;

    int bang = hm.indexOf('!');
    int at = hm.indexOf('@');
    if (bang <= 0 || at <= bang + 1 || at >= hm.length() - 1) return false;

    String ident = hm.substring(bang + 1, at).trim();
    String host = hm.substring(at + 1).trim();

    // If both are unknown wildcards, this is just a placeholder derived from NAMES and isn't useful.
    boolean identUnknown = ident.isEmpty() || "*".equals(ident);
    boolean hostUnknown = host.isEmpty() || "*".equals(host);
    return !(identUnknown && hostUnknown);
  }
}
