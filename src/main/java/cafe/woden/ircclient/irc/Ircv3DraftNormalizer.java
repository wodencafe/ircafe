package cafe.woden.ircclient.irc;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure helpers for normalizing staged IRCv3 draft commands.
 *
 * <p>This is intentionally UI-free so it can be reused anywhere that needs to keep a draft string
 * compatible with currently negotiated capabilities.
 */
public final class Ircv3DraftNormalizer {

  private Ircv3DraftNormalizer() {}

  /**
   * Normalizes staged IRCv3 {@code /quote @...} drafts against currently negotiated capabilities.
   *
   * <p>This removes draft tags that are no longer supported and exits react-prefill mode when
   * required capabilities are missing.
   */
  public static String normalizeIrcv3DraftForCapabilities(
      String draft, boolean replySupported, boolean reactSupported) {
    String raw = (draft == null) ? "" : draft;
    if (raw.isBlank()) return raw;

    int ws = 0;
    while (ws < raw.length() && Character.isWhitespace(raw.charAt(ws))) ws++;
    String leading = raw.substring(0, ws);
    String rest = raw.substring(ws);

    if (!startsWithIgnoreCase(rest, "/quote")) return raw;
    int idx = "/quote".length();
    if (rest.length() > idx && !Character.isWhitespace(rest.charAt(idx))) return raw;
    while (idx < rest.length() && Character.isWhitespace(rest.charAt(idx))) idx++;
    if (idx >= rest.length() || rest.charAt(idx) != '@') return raw;

    int tagStart = idx;
    int tagEnd = rest.indexOf(' ', tagStart);
    if (tagEnd < 0) return raw;
    String tagBody = rest.substring(tagStart + 1, tagEnd);
    if (tagBody.isBlank()) return raw;

    String[] parts = tagBody.split(";");
    ArrayList<String> kept = new ArrayList<>(parts.length);
    boolean sawReplyTag = false;
    boolean sawReactTag = false;

    for (String part : parts) {
      String p = Objects.toString(part, "").trim();
      if (p.isEmpty()) continue;
      String key = normalizeIrcv3TagKey(p);
      if ("draft/reply".equals(key)) {
        sawReplyTag = true;
        if (replySupported) kept.add(part);
        continue;
      }
      if ("draft/react".equals(key)) {
        sawReactTag = true;
        kept.add(part);
        continue;
      }
      if ("draft/unreact".equals(key)) {
        sawReactTag = true;
        kept.add(part);
        continue;
      }
      kept.add(part);
    }

    // React prefill depends on draft/reply target metadata; disabling either capability exits the
    // mode.
    if (sawReactTag && (!reactSupported || !replySupported)) {
      return "";
    }
    if (!sawReplyTag || replySupported) {
      return raw;
    }

    String head = rest.substring(0, tagStart);
    String tail = rest.substring(tagEnd); // includes whitespace + command
    if (kept.isEmpty()) {
      String commandPart = tail.stripLeading();
      if (commandPart.isEmpty()) return "";
      return leading + head + commandPart;
    }
    return leading + head + "@" + String.join(";", kept) + tail;
  }

  private static String normalizeIrcv3TagKey(String tagPart) {
    String token = Objects.toString(tagPart, "");
    int eq = token.indexOf('=');
    if (eq >= 0) token = token.substring(0, eq);
    token = token.trim();
    while (token.startsWith("+")) token = token.substring(1);
    return token.toLowerCase(Locale.ROOT);
  }

  private static boolean startsWithIgnoreCase(String value, String prefix) {
    if (value == null || prefix == null) return false;
    if (value.length() < prefix.length()) return false;
    return value.regionMatches(true, 0, prefix, 0, prefix.length());
  }
}
