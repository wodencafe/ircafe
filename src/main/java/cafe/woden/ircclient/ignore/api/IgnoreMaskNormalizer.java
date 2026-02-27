package cafe.woden.ircclient.ignore.api;

import java.util.Objects;

/** Shared normalization for nick/mask inputs accepted by ignore commands. */
public final class IgnoreMaskNormalizer {

  private IgnoreMaskNormalizer() {}

  /**
   * Normalize a user-provided ignore input to a hostmask pattern.
   *
   * <p>Accepts full masks, ident@host patterns, host-only patterns, or nick patterns.
   */
  public static String normalizeMaskOrNickToHostmask(String rawMaskOrNick) {
    String s = Objects.toString(rawMaskOrNick, "").trim();
    // no internal whitespace in masks
    s = s.replaceAll("\\s+", "");
    if (s.isEmpty()) return "";

    // Full hostmask/pattern already.
    if (s.indexOf('!') >= 0 && s.indexOf('@') >= 0) {
      return s;
    }

    // Something@host (maybe ident@host or *@host).
    if (s.indexOf('@') >= 0) {
      // If it already has a leading "*!" prefix, keep it.
      if (s.startsWith("*!")) return s;
      // If it starts with "!" (rare), prefix nick wildcard.
      if (s.startsWith("!")) return "*" + s;
      return "*!" + s;
    }

    // Host-only
    if (looksLikeHost(s)) {
      return "*!*@" + s;
    }

    // Otherwise treat as nick.
    return s + "!*@*";
  }

  private static boolean looksLikeHost(String s) {
    String lower = s.toLowerCase(java.util.Locale.ROOT);
    return lower.contains(".") || lower.contains(":") || lower.endsWith("/");
  }
}
