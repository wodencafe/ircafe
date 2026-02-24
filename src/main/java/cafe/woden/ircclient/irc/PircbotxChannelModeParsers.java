package cafe.woden.ircclient.irc;

/**
 * Parsers for IRC channel-mode listing lines.
 *
 * <p>This class intentionally contains only pure parsing helpers extracted from {@link
 * PircbotxIrcClientService} during refactor step B2.5.
 */
final class PircbotxChannelModeParsers {
  private PircbotxChannelModeParsers() {}

  record ParsedRpl324(String channel, String details) {}

  /**
   * Parse RPL_CHANNELMODEIS (324).
   *
   * <p>Expected tokenized format (after optional prefix): {@code 324 <me> <#chan> <modes>
   * [args...]}.
   */
  static ParsedRpl324 parseRpl324(String line) {
    if (line == null) return null;
    String s = line.trim();
    if (s.isEmpty()) return null;

    // Drop prefix (e.g., ":server ")
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
    }

    String[] toks = s.split("\s+");
    if (toks.length < 4) return null;

    // Format: 324 <me> <#chan> <modes> [args...]
    if (!"324".equals(toks[0])) return null;

    String channel = toks[2];
    if (channel == null || channel.isBlank()) return null;

    StringBuilder details = new StringBuilder();
    for (int i = 3; i < toks.length; i++) {
      if (i > 3) details.append(' ');
      details.append(toks[i]);
    }

    return new ParsedRpl324(channel, details.toString());
  }
}
