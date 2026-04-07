package cafe.woden.ircclient.irc.pircbotx.parse;

import cafe.woden.ircclient.irc.ircv3.Ircv3MultilineSupport;
import cafe.woden.ircclient.irc.pircbotx.state.PircbotxConnectionState;
import java.util.Locale;
import java.util.Objects;

/** Applies multiline capability offers and negotiated limits to connection state. */
public final class PircbotxMultilineCapStateSupport {

  public void observe(ParsedCapLine capLine, PircbotxConnectionState conn) {
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
    return Ircv3MultilineSupport.isMultilineCapability(normalized) ? normalized : null;
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
    return conn.multilineOfferedMaxBytes(isDraftMultiline(capName));
  }

  private static long offeredMaxLines(PircbotxConnectionState conn, String capName) {
    return conn.multilineOfferedMaxLines(isDraftMultiline(capName));
  }

  private static void setOfferedMaxBytes(
      PircbotxConnectionState conn, String capName, long maxBytes) {
    conn.setMultilineOfferedMaxBytes(isDraftMultiline(capName), maxBytes);
  }

  private static void setOfferedMaxLines(
      PircbotxConnectionState conn, String capName, long maxLines) {
    conn.setMultilineOfferedMaxLines(isDraftMultiline(capName), maxLines);
  }

  private static void setNegotiatedMaxBytes(
      PircbotxConnectionState conn, String capName, long maxBytes) {
    conn.setNegotiatedMultilineMaxBytes(isDraftMultiline(capName), maxBytes);
  }

  private static void setNegotiatedMaxLines(
      PircbotxConnectionState conn, String capName, long maxLines) {
    conn.setNegotiatedMultilineMaxLines(isDraftMultiline(capName), maxLines);
  }

  private static boolean isDraftMultiline(String capName) {
    return Ircv3MultilineSupport.isDraftMultilineCapability(capName);
  }

  private static MultilineCapLimits parseMultilineCapLimits(String capValueRaw) {
    String raw = Objects.toString(capValueRaw, "").trim();
    if (raw.isEmpty()) return new MultilineCapLimits(0L, 0L);
    Ircv3MultilineSupport.LimitParams parsed = Ircv3MultilineSupport.parseLimitParams(raw);
    long maxBytes = Math.max(0L, parsed.maxBytes());
    long maxLines = Math.max(0L, parsed.maxLines());
    return new MultilineCapLimits(maxBytes, maxLines);
  }

  private record MultilineCapLimits(long maxBytes, long maxLines) {}
}
