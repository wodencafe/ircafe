package cafe.woden.ircclient.ignore;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class IgnoreMaskMatcher {

  private IgnoreMaskMatcher() {}

  /**
   * True if the hostmask resembles a real "nick!ident@host" (with non-trivial ident/host).
   *
   * <p>We treat "*!*@*"-ish values as not useful.
   */
  public static boolean isUsefulHostmask(String hostmask) {
    String hm = Objects.toString(hostmask, "").trim();
    if (hm.isEmpty()) return false;

    int bang = hm.indexOf('!');
    int at = hm.indexOf('@');
    if (bang <= 0 || at <= bang + 1 || at >= hm.length() - 1) return false;

    String ident = hm.substring(bang + 1, at).trim();
    String host = hm.substring(at + 1).trim();

    boolean identUnknown = ident.isEmpty() || "*".equals(ident);
    boolean hostUnknown = host.isEmpty() || "*".equals(host);
    return !(identUnknown && hostUnknown);
  }

  public static boolean hostmaskTargetedByAny(List<String> masks, String hostmask) {
    if (masks == null || masks.isEmpty()) return false;
    String hm = Objects.toString(hostmask, "").trim();
    if (hm.isEmpty()) return false;

    for (String m : masks) {
      if (m == null || m.isBlank()) continue;
      if (globMatchIgnoreMask(m, hm)) return true;
    }
    return false;
  }

  /**
   * Match a nick against any ignore mask.
   *
   * <p>We treat masks as hostmask patterns; for nick matching we only look at the nick glob before
   * the '!'. Host-only patterns like "*!ident@host" are ignored here so we don't accidentally mark
   * everyone.
   */
  public static boolean nickTargetedByAny(List<String> masks, String nick) {
    if (masks == null || masks.isEmpty()) return false;
    String n = Objects.toString(nick, "").trim();
    if (n.isEmpty()) return false;

    for (String m : masks) {
      if (m == null || m.isBlank()) continue;
      int bang = m.indexOf('!');
      if (bang <= 0) continue;
      String nickGlob = m.substring(0, bang).trim();
      if (nickGlob.isEmpty()) continue;

      // Avoid matching everyone for host-only patterns like "*!*@host".
      if (nickGlob.chars().allMatch(ch -> ch == '*' || ch == '?')) continue;

      if (globMatches(nickGlob, n)) return true;
    }
    return false;
  }

  public static boolean globMatches(String glob, String text) {
    String ptn = Objects.toString(glob, "").trim().toLowerCase(Locale.ROOT);
    String txt = Objects.toString(text, "").trim().toLowerCase(Locale.ROOT);
    if (ptn.isEmpty() || txt.isEmpty()) return false;

    int p = 0;
    int t = 0;
    int starIdx = -1;
    int match = 0;

    while (t < txt.length()) {
      if (p < ptn.length() && (ptn.charAt(p) == '?' || ptn.charAt(p) == txt.charAt(t))) {
        p++;
        t++;
      } else if (p < ptn.length() && ptn.charAt(p) == '*') {
        starIdx = p;
        match = t;
        p++;
      } else if (starIdx != -1) {
        p = starIdx + 1;
        match++;
        t = match;
      } else {
        return false;
      }
    }

    while (p < ptn.length() && ptn.charAt(p) == '*') p++;
    return p == ptn.length();
  }

  public static boolean globMatchIgnoreMask(String pattern, String text) {
    return globMatches(pattern, text);
  }
}
