package cafe.woden.ircclient.irc.pircbotx.parse;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.pircbotx.PircbotxIrcClientService;
import cafe.woden.ircclient.irc.playback.*;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Parsers for IRC channel-mode listing lines.
 *
 * <p>This class intentionally contains only pure parsing helpers extracted from {@link
 * PircbotxIrcClientService} during refactor step B2.5.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PircbotxChannelModeParsers {
  public record ParsedRpl324(String channel, String details) {}

  /**
   * Parse RPL_CHANNELMODEIS (324).
   *
   * <p>Expected tokenized format (after optional prefix): {@code 324 <me> <#chan> <modes>
   * [args...]}.
   */
  public static ParsedRpl324 parseRpl324(String line) {
    if (line == null) return null;
    String s = line.trim();
    if (s.isEmpty()) return null;

    // Drop prefix (e.g., ":server ")
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 0 && sp + 1 < s.length()) s = s.substring(sp + 1).trim();
    }

    String[] toks = s.split("\\s+");
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

  /**
   * Recover RPL_CHANNELMODEIS (324) details when PircBotX tokenization succeeded but the normal
   * parser path failed.
   */
  public static ParsedRpl324 parseRpl324Fallback(String line, List<String> parsedLine) {
    ParsedRpl324 fromLine = parseRpl324(line);
    if (fromLine != null) return fromLine;

    if (parsedLine == null || parsedLine.size() < 3) return null;
    String channel = stripLeadingColon(parsedLine.get(1));
    if (channel.isBlank()) return null;

    StringBuilder details = new StringBuilder();
    for (int i = 2; i < parsedLine.size(); i++) {
      String token = stripLeadingColon(parsedLine.get(i));
      if (token.isBlank()) continue;
      if (details.length() > 0) details.append(' ');
      details.append(token);
    }
    if (details.length() == 0) return null;
    return new ParsedRpl324(channel, details.toString());
  }

  private static String stripLeadingColon(String raw) {
    String value = Objects.toString(raw, "").trim();
    if (value.startsWith(":")) value = value.substring(1).trim();
    return value;
  }
}
