package cafe.woden.ircclient.irc.quassel;

import cafe.woden.ircclient.irc.IrcEvent;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Interprets Core display text without changing transport or session state. */
final class QuasselCoreDisplayText {
  private QuasselCoreDisplayText() {}

  record KickDetails(String nick, String reason) {}

  static IrcEvent.ServerResponseLine serverResponse(
      Instant at, String displayLine, String rawLine, String messageId, Map<String, String> tags) {
    String display = Objects.toString(displayLine, "").trim();
    String raw = Objects.toString(rawLine, "").trim();
    if (raw.isEmpty()) raw = display;
    int code = extractNumericCode(raw);
    String message = display;
    if (code != 0) {
      String fromRaw = renderNumericMessage(raw);
      if (!fromRaw.isEmpty()) {
        message = fromRaw;
      }
    }
    return new IrcEvent.ServerResponseLine(
        at, code, message, raw, messageId, tags == null ? Map.of() : tags);
  }

  static int extractNumericCode(String rawLine) {
    String payload = stripIrcEnvelope(rawLine);
    if (payload.length() < 3) return 0;
    if (!Character.isDigit(payload.charAt(0))
        || !Character.isDigit(payload.charAt(1))
        || !Character.isDigit(payload.charAt(2))) {
      return 0;
    }
    if (payload.length() > 3 && payload.charAt(3) != ' ') return 0;
    try {
      return Integer.parseInt(payload.substring(0, 3));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private static String renderNumericMessage(String rawLine) {
    String payload = stripIrcEnvelope(rawLine);
    int code = extractNumericCode(payload);
    if (code == 0 || payload.length() <= 3) {
      return "";
    }
    String tail = payload.substring(3).trim();
    int trailingStart = tail.indexOf(" :");
    if (trailingStart >= 0 && trailingStart + 2 < tail.length()) {
      return tail.substring(trailingStart + 2).trim();
    }
    if (tail.startsWith(":")) {
      return tail.substring(1).trim();
    }
    return tail;
  }

  private static String stripIrcEnvelope(String rawLine) {
    String line = Objects.toString(rawLine, "").trim();
    if (line.isEmpty()) return "";
    if (line.startsWith("@")) {
      int sp = line.indexOf(' ');
      if (sp <= 0 || sp >= line.length() - 1) return "";
      line = line.substring(sp + 1).trim();
    }
    if (line.startsWith(":")) {
      int sp = line.indexOf(' ');
      if (sp <= 1 || sp >= line.length() - 1) return "";
      line = line.substring(sp + 1).trim();
    }
    return line;
  }

  static String parseNickChange(String content, String fallback) {
    String text = Objects.toString(content, "").trim();
    if (text.isEmpty()) return fallback;
    String lower = text.toLowerCase(Locale.ROOT);
    int idx = lower.lastIndexOf(" is now known as ");
    if (idx >= 0) {
      String tail = text.substring(idx + " is now known as ".length()).trim();
      if (!tail.isEmpty()) return tail.split("\\s+")[0];
    }
    String[] parts = text.split("\\s+");
    if (parts.length > 0) {
      String last = parts[parts.length - 1].trim();
      if (!last.isEmpty()) return last;
    }
    return fallback;
  }

  static String parseTopic(String content) {
    String text = Objects.toString(content, "").trim();
    if (text.isEmpty()) return "";
    String lower = text.toLowerCase(Locale.ROOT);
    int idx = lower.indexOf(" topic to ");
    if (idx >= 0) {
      String topic = text.substring(idx + " topic to ".length()).trim();
      return stripWrappingQuotes(topic);
    }
    idx = lower.indexOf(" changed topic to ");
    if (idx >= 0) {
      String topic = text.substring(idx + " changed topic to ".length()).trim();
      return stripWrappingQuotes(topic);
    }
    return stripWrappingQuotes(text);
  }

  static String parseModeDetails(String content) {
    String text = Objects.toString(content, "").trim();
    if (text.isEmpty()) return "";
    String lower = text.toLowerCase(Locale.ROOT);
    int idx = lower.indexOf(" mode ");
    if (idx >= 0) {
      return text.substring(idx + " mode ".length()).trim();
    }
    idx = lower.indexOf(" set mode ");
    if (idx >= 0) {
      return text.substring(idx + " set mode ".length()).trim();
    }
    return text;
  }

  static KickDetails parseKickDetails(String content) {
    String text = Objects.toString(content, "").trim();
    if (text.isEmpty()) return new KickDetails("", "");
    String lower = text.toLowerCase(Locale.ROOT);
    String tail;
    if (lower.startsWith("kicked ")) {
      tail = text.substring("kicked ".length()).trim();
    } else {
      int idx = lower.indexOf(" kicked ");
      tail = idx >= 0 ? text.substring(idx + " kicked ".length()).trim() : text;
    }
    String nick = tail;
    String reason = "";
    int reasonStart = tail.indexOf('(');
    int reasonEnd = tail.lastIndexOf(')');
    if (reasonStart >= 0 && reasonEnd > reasonStart) {
      nick = tail.substring(0, reasonStart).trim();
      reason = tail.substring(reasonStart + 1, reasonEnd).trim();
    } else {
      String[] parts = tail.split("\\s+", 2);
      nick = parts.length > 0 ? parts[0].trim() : "";
      if (parts.length > 1) reason = parts[1].trim();
    }
    return new KickDetails(nick, reason);
  }

  static String firstChannelToken(String content) {
    String text = Objects.toString(content, "").trim();
    if (text.isEmpty()) return "";
    for (String token : text.split("\\s+")) {
      if (looksLikeChannel(token)) return token;
      String cleaned = stripPunctuation(token);
      if (looksLikeChannel(cleaned)) return cleaned;
    }
    return "";
  }

  static boolean looksLikeChannel(String token) {
    String t = Objects.toString(token, "").trim();
    if (t.length() < 2) return false;
    char c = t.charAt(0);
    return c == '#' || c == '&' || c == '+' || c == '!';
  }

  private static String stripPunctuation(String token) {
    String t = Objects.toString(token, "").trim();
    while (!t.isEmpty() && (t.endsWith(",") || t.endsWith(".") || t.endsWith(":"))) {
      t = t.substring(0, t.length() - 1).trim();
    }
    return t;
  }

  private static String stripWrappingQuotes(String text) {
    String t = Objects.toString(text, "").trim();
    if (t.length() >= 2) {
      if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
        return t.substring(1, t.length() - 1).trim();
      }
    }
    return t;
  }

  static String normalizeReason(String content) {
    String text = Objects.toString(content, "").trim();
    if (text.isEmpty()) return "";
    int open = text.lastIndexOf('(');
    int close = text.lastIndexOf(')');
    if (open >= 0 && close > open) {
      String reason = text.substring(open + 1, close).trim();
      if (!reason.isEmpty()) return reason;
    }
    return text;
  }

  static String extractNick(String sender) {
    String hostmask = Objects.toString(sender, "").trim();
    if (hostmask.isEmpty()) return "";
    int bang = hostmask.indexOf('!');
    if (bang <= 0) return hostmask;
    return hostmask.substring(0, bang);
  }
}
