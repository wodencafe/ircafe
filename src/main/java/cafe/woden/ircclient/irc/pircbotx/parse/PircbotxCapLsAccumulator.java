package cafe.woden.ircclient.irc.pircbotx.parse;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adapts CAP 302 multiline LS replies to PircBotX's single-list CAP handler API. */
final class PircbotxCapLsAccumulator {
  private static final int MAX_CAP_LIST_CHARS = 65_536;
  private static final Pattern LS_LINE =
      Pattern.compile(
          "^((?:@\\S+ +)?(?::\\S+ +)?CAP +\\S+ +LS +)(\\* +)?:?(.*)$", Pattern.CASE_INSENSITIVE);
  private final StringBuilder pending = new StringBuilder();

  /** Returns null for a continuation, or a complete line ready for the library parser. */
  String accept(String rawLine) throws IOException {
    Matcher match = LS_LINE.matcher(rawLine);
    if (!match.matches()) return rawLine;
    String caps = match.group(3);
    if (pending.length() + caps.length() + 1 > MAX_CAP_LIST_CHARS) {
      clear();
      throw new IOException("CAP LS capability list exceeds " + MAX_CAP_LIST_CHARS + " characters");
    }
    if (!pending.isEmpty()) pending.append(' ');
    pending.append(caps);
    if (match.group(2) != null) return null;
    String complete = match.group(1) + ":" + pending;
    clear();
    return complete;
  }

  void clear() {
    pending.setLength(0);
  }
}
