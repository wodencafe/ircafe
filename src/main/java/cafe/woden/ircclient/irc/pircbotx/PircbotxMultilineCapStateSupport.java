package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.pircbotx.parse.ParsedCapLine;
import java.util.Locale;
import java.util.Objects;

/** Applies multiline capability offers and negotiated limits to connection state. */
final class PircbotxMultilineCapStateSupport {

  void observe(ParsedCapLine capLine, PircbotxConnectionState conn) {
    boolean fromLs = capLine.isAction("LS");
    boolean fromNew = capLine.isAction("NEW");
    boolean fromAck = capLine.isAction("ACK");
    boolean fromDel = capLine.isAction("DEL");
    if (!fromLs && !fromNew && !fromAck && !fromDel) return;

    for (String token : capLine.tokens()) {
      boolean tokenDisable = token.startsWith("-");
      String capName = canonicalMultilineCapName(token);
      if (capName == null) continue;

      String capValue = capValueFromToken(token);
      MultilineCapLimits parsed = parseMultilineCapLimits(capValue);

      if (fromLs || fromNew) {
        if (tokenDisable) {
          setOfferedMaxBytes(conn, capName, 0L);
          setOfferedMaxLines(conn, capName, 0L);
        } else {
          if (parsed.maxBytes() > 0L) {
            setOfferedMaxBytes(conn, capName, parsed.maxBytes());
          }
          if (parsed.maxLines() > 0L) {
            setOfferedMaxLines(conn, capName, parsed.maxLines());
          }
        }
      }

      if (fromAck) {
        if (tokenDisable) {
          setNegotiatedMaxBytes(conn, capName, 0L);
          setNegotiatedMaxLines(conn, capName, 0L);
          setOfferedMaxBytes(conn, capName, 0L);
          setOfferedMaxLines(conn, capName, 0L);
        } else {
          if (parsed.maxBytes() > 0L) {
            setOfferedMaxBytes(conn, capName, parsed.maxBytes());
          }
          if (parsed.maxLines() > 0L) {
            setOfferedMaxLines(conn, capName, parsed.maxLines());
          }
          long effectiveMaxBytes =
              parsed.maxBytes() > 0L ? parsed.maxBytes() : offeredMaxBytes(conn, capName);
          long effectiveMaxLines =
              parsed.maxLines() > 0L ? parsed.maxLines() : offeredMaxLines(conn, capName);
          setNegotiatedMaxBytes(conn, capName, effectiveMaxBytes);
          setNegotiatedMaxLines(conn, capName, effectiveMaxLines);
        }
      }

      if (fromDel) {
        setNegotiatedMaxBytes(conn, capName, 0L);
        setNegotiatedMaxLines(conn, capName, 0L);
        setOfferedMaxBytes(conn, capName, 0L);
        setOfferedMaxLines(conn, capName, 0L);
      }
    }
  }

  private static String canonicalMultilineCapName(String rawToken) {
    String capName = canonicalCapName(rawToken);
    if (capName == null) return null;
    String normalized = capName.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "multiline", "draft/multiline" -> normalized;
      default -> null;
    };
  }

  private static String canonicalCapName(String rawToken) {
    String s = Objects.toString(rawToken, "").trim();
    if (s.isEmpty()) return null;
    if (s.startsWith(":")) s = s.substring(1).trim();
    while (!s.isEmpty()) {
      char leading = s.charAt(0);
      if (leading == '-' || leading == '~' || leading == '=') {
        s = s.substring(1).trim();
      } else {
        break;
      }
    }
    if (s.isEmpty()) return null;
    int eq = s.indexOf('=');
    if (eq >= 0) s = s.substring(0, eq).trim();
    return s.isEmpty() ? null : s;
  }

  private static String capValueFromToken(String rawToken) {
    String s = Objects.toString(rawToken, "").trim();
    if (s.isEmpty()) return "";
    if (s.startsWith(":")) s = s.substring(1).trim();
    while (!s.isEmpty()) {
      char leading = s.charAt(0);
      if (leading == '-' || leading == '~' || leading == '=') {
        s = s.substring(1).trim();
        continue;
      }
      break;
    }
    int eq = s.indexOf('=');
    if (eq < 0 || eq + 1 >= s.length()) return "";
    return s.substring(eq + 1).trim();
  }

  private static long offeredMaxBytes(PircbotxConnectionState conn, String capName) {
    if ("multiline".equals(capName)) return Math.max(0L, conn.multilineOfferedMaxBytes.get());
    return Math.max(0L, conn.draftMultilineOfferedMaxBytes.get());
  }

  private static long offeredMaxLines(PircbotxConnectionState conn, String capName) {
    if ("multiline".equals(capName)) return Math.max(0L, conn.multilineOfferedMaxLines.get());
    return Math.max(0L, conn.draftMultilineOfferedMaxLines.get());
  }

  private static void setOfferedMaxBytes(
      PircbotxConnectionState conn, String capName, long maxBytes) {
    long normalized = Math.max(0L, maxBytes);
    if ("multiline".equals(capName)) {
      conn.multilineOfferedMaxBytes.set(normalized);
    } else {
      conn.draftMultilineOfferedMaxBytes.set(normalized);
    }
  }

  private static void setOfferedMaxLines(
      PircbotxConnectionState conn, String capName, long maxLines) {
    long normalized = Math.max(0L, maxLines);
    if ("multiline".equals(capName)) {
      conn.multilineOfferedMaxLines.set(normalized);
    } else {
      conn.draftMultilineOfferedMaxLines.set(normalized);
    }
  }

  private static void setNegotiatedMaxBytes(
      PircbotxConnectionState conn, String capName, long maxBytes) {
    long normalized = Math.max(0L, maxBytes);
    if ("multiline".equals(capName)) {
      conn.multilineMaxBytes.set(normalized);
    } else {
      conn.draftMultilineMaxBytes.set(normalized);
    }
  }

  private static void setNegotiatedMaxLines(
      PircbotxConnectionState conn, String capName, long maxLines) {
    long normalized = Math.max(0L, maxLines);
    if ("multiline".equals(capName)) {
      conn.multilineMaxLines.set(normalized);
    } else {
      conn.draftMultilineMaxLines.set(normalized);
    }
  }

  private static MultilineCapLimits parseMultilineCapLimits(String capValueRaw) {
    String raw = Objects.toString(capValueRaw, "").trim();
    if (raw.isEmpty()) return new MultilineCapLimits(0L, 0L);

    long maxBytes = 0L;
    long maxLines = 0L;
    for (String partRaw : raw.split(",")) {
      String part = Objects.toString(partRaw, "").trim();
      if (part.isEmpty()) continue;
      int eq = part.indexOf('=');
      if (eq <= 0 || eq + 1 >= part.length()) continue;

      String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
      String value = part.substring(eq + 1).trim();
      if (value.isEmpty()) continue;
      try {
        long parsed = Long.parseLong(value);
        if (parsed <= 0L) continue;
        if ("max-bytes".equals(key)) {
          maxBytes = parsed;
        } else if ("max-lines".equals(key)) {
          maxLines = parsed;
        }
      } catch (NumberFormatException ignored) {
        // Ignore invalid attribute values while keeping any other valid limits.
      }
    }
    return new MultilineCapLimits(maxBytes, maxLines);
  }

  private record MultilineCapLimits(long maxBytes, long maxLines) {}
}
