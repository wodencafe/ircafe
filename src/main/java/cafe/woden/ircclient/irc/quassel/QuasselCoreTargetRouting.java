package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreDisplayText.looksLikeChannel;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.containsCrlf;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Interprets network-qualified targets and routes raw commands without consulting session state.
 */
final class QuasselCoreTargetRouting {
  private static final int BUFFER_STATUS = 0x01;
  private static final int BUFFER_CHANNEL = 0x02;
  private static final int BUFFER_QUERY = 0x04;
  private static final String NETWORK_QUALIFIER_PREFIX = "{net:";
  private static final String NETWORK_QUALIFIER_SUFFIX = "}";
  private static final Set<String> TARGET_ROUTED_RAW_COMMANDS =
      Set.of("PRIVMSG", "NOTICE", "TAGMSG", "MARKREAD", "REDACT");

  private QuasselCoreTargetRouting() {}

  record QualifiedTarget(String rawTarget, String baseTarget, String networkToken) {}

  record OutboundRawRoute(
      String command,
      QualifiedTarget requestedTarget,
      String rewrittenRawLine,
      int targetTypeBitsHint) {}

  static String qualifyTarget(String baseTarget, String networkToken) {
    String base = Objects.toString(baseTarget, "").trim();
    String token = Objects.toString(networkToken, "").trim();
    if (base.isEmpty() || token.isEmpty()) return base;
    return base + NETWORK_QUALIFIER_PREFIX + token + NETWORK_QUALIFIER_SUFFIX;
  }

  static QualifiedTarget sanitizeHistoryTarget(String target) {
    QualifiedTarget parsed = parseQualifiedTarget(target);
    String base = parsed.baseTarget();
    if (base.isEmpty()) {
      throw new IllegalArgumentException("target is blank");
    }
    if (containsCrlf(base)) {
      throw new IllegalArgumentException("target contains CR/LF");
    }
    if (base.indexOf(' ') >= 0) {
      throw new IllegalArgumentException("target contains spaces");
    }
    return parsed;
  }

  static String normalizeTargetHintKey(String target) {
    QualifiedTarget parsed = parseQualifiedTarget(target);
    String normalized = Objects.toString(parsed.baseTarget(), "").trim();
    if (normalized.isEmpty()) return "";
    return normalized.toLowerCase(Locale.ROOT);
  }

  static String normalizeMembershipKey(String target, int networkId) {
    QualifiedTarget parsed = parseQualifiedTarget(target);
    String base = Objects.toString(parsed.baseTarget(), "").trim().toLowerCase(Locale.ROOT);
    if (base.isEmpty()) return "";
    if (networkId >= 0) {
      return networkId + "|" + base;
    }
    String token = Objects.toString(parsed.networkToken(), "").trim().toLowerCase(Locale.ROOT);
    if (!token.isEmpty()) {
      return "net:" + token + "|" + base;
    }
    return "global|" + base;
  }

  static QualifiedTarget parseQualifiedTarget(String target) {
    String raw = Objects.toString(target, "").trim();
    if (raw.isEmpty()) return new QualifiedTarget("", "", "");
    if (raw.endsWith(NETWORK_QUALIFIER_SUFFIX)) {
      int marker = raw.lastIndexOf(NETWORK_QUALIFIER_PREFIX);
      if (marker > 0) {
        int tokenStart = marker + NETWORK_QUALIFIER_PREFIX.length();
        int tokenEnd = raw.length() - NETWORK_QUALIFIER_SUFFIX.length();
        if (tokenEnd > tokenStart) {
          String base = raw.substring(0, marker).trim();
          String token = raw.substring(tokenStart, tokenEnd).trim().toLowerCase(Locale.ROOT);
          if (!base.isEmpty() && !token.isEmpty()) {
            return new QualifiedTarget(raw, base, token);
          }
        }
      }
    }
    return new QualifiedTarget(raw, raw, "");
  }

  static OutboundRawRoute routeOutboundRawLine(String rawLine) {
    String line = Objects.toString(rawLine, "").strip();
    if (line.isEmpty()) {
      return new OutboundRawRoute("", null, "", BUFFER_STATUS);
    }

    int len = line.length();
    int cursor = 0;
    if (line.charAt(0) == '@') {
      int space = line.indexOf(' ');
      if (space <= 0 || space >= (len - 1)) {
        return new OutboundRawRoute("", null, line, BUFFER_STATUS);
      }
      cursor = space + 1;
      while (cursor < len && line.charAt(cursor) == ' ') cursor++;
    }
    if (cursor < len && line.charAt(cursor) == ':') {
      int space = line.indexOf(' ', cursor);
      if (space <= cursor || space >= (len - 1)) {
        return new OutboundRawRoute("", null, line, BUFFER_STATUS);
      }
      cursor = space + 1;
      while (cursor < len && line.charAt(cursor) == ' ') cursor++;
    }
    if (cursor >= len) {
      return new OutboundRawRoute("", null, line, BUFFER_STATUS);
    }

    int commandStart = cursor;
    while (cursor < len && line.charAt(cursor) != ' ') cursor++;
    String command = line.substring(commandStart, cursor).trim().toUpperCase(Locale.ROOT);
    if (command.isEmpty() || !TARGET_ROUTED_RAW_COMMANDS.contains(command)) {
      return new OutboundRawRoute(command, null, line, BUFFER_STATUS);
    }

    while (cursor < len && line.charAt(cursor) == ' ') cursor++;
    if (cursor >= len || line.charAt(cursor) == ':') {
      return new OutboundRawRoute(command, null, line, BUFFER_STATUS);
    }

    int targetStart = cursor;
    while (cursor < len && line.charAt(cursor) != ' ') cursor++;
    int targetEnd = cursor;
    String rawTarget = line.substring(targetStart, targetEnd).trim();
    if (rawTarget.isEmpty()) {
      return new OutboundRawRoute(command, null, line, BUFFER_STATUS);
    }
    QualifiedTarget parsedTarget = parseQualifiedTarget(rawTarget);
    if (parsedTarget.baseTarget().isEmpty()) {
      return new OutboundRawRoute(command, null, line, BUFFER_STATUS);
    }

    String rewritten = line;
    if (!parsedTarget.networkToken().isEmpty() && !parsedTarget.baseTarget().equals(rawTarget)) {
      rewritten =
          line.substring(0, targetStart) + parsedTarget.baseTarget() + line.substring(targetEnd);
    }
    int typeBitsHint = looksLikeChannel(parsedTarget.baseTarget()) ? BUFFER_CHANNEL : BUFFER_QUERY;
    return new OutboundRawRoute(command, parsedTarget, rewritten, typeBitsHint);
  }
}
