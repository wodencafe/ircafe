package cafe.woden.ircclient.irc;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Parsers/helpers for IRCv3 client-only message tags as advertised via RPL_ISUPPORT.
 *
 * <p>Per IRCv3 message-tags, servers may optionally advertise {@code CLIENTTAGDENY} in numeric 005
 * to indicate which client-only tags are blocked / ignored.
 */
final class PircbotxClientTagParsers {
  private PircbotxClientTagParsers() {}

  /**
   * Parse a single RPL_ISUPPORT (005) line and return the raw CLIENTTAGDENY value (without the key).
   *
   * <p>Returns {@code null} if the token is not present on this line. Returns empty string if
   * {@code CLIENTTAGDENY=} is present (meaning "allow all client-only tags").
   */
  static String parseRpl005ClientTagDenyValue(String line) {
    if (line == null) return null;
    String s = line.trim();
    if (s.isEmpty()) return null;

    // Strip optional prefix (server name)
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
    }

    String[] toks = s.split("\s+");
    if (toks.length < 3) return null;
    if (!"005".equals(toks[0])) return null;

    for (int i = 2; i < toks.length; i++) {
      String t = toks[i];
      if (t == null || t.isBlank()) continue;
      if (t.startsWith(":")) break;

      int eq = t.indexOf('=');
      String key = eq >= 0 ? t.substring(0, eq) : t;
      if (!"CLIENTTAGDENY".equalsIgnoreCase(key)) continue;

      if (eq < 0) return ""; // token present with no value
      return t.substring(eq + 1);
    }

    return null;
  }

  /**
   * Determine whether a client-only tag is allowed given the RPL_ISUPPORT CLIENTTAGDENY value.
   *
   * <p>The tag name must not include the client-only '+' prefix (e.g. use {@code "typing"}).
   *
   * <p>If {@code clientTagDenyValue} is null/blank, the default is to allow all client-only tags.
   */
  static boolean isClientOnlyTagAllowed(String clientTagDenyValue, String tagNameNoPlus) {
    String deny = (clientTagDenyValue == null) ? "" : clientTagDenyValue.trim();
    if (deny.isEmpty()) return true;

    String tag = (tagNameNoPlus == null) ? "" : tagNameNoPlus.trim().toLowerCase(Locale.ROOT);
    if (tag.isEmpty()) return true;

    String[] items = deny.split(",");
    boolean catchAllBlocked = false;
    Set<String> blocked = new HashSet<>();
    Set<String> exceptions = new HashSet<>();

    for (int i = 0; i < items.length; i++) {
      String raw = items[i] == null ? "" : items[i].trim();
      if (raw.isEmpty()) continue;
      if (i == 0 && "*".equals(raw)) {
        catchAllBlocked = true;
        continue;
      }
      if (raw.startsWith("-") && raw.length() > 1) {
        exceptions.add(raw.substring(1).trim().toLowerCase(Locale.ROOT));
      } else {
        blocked.add(raw.toLowerCase(Locale.ROOT));
      }
    }

    if (catchAllBlocked) {
      return exceptions.contains(tag);
    }
    return !blocked.contains(tag);
  }
}
