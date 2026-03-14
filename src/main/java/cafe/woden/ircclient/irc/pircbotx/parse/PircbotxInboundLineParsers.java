package cafe.woden.ircclient.irc.pircbotx.parse;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.backend.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import java.util.List;
import java.util.Objects;

/**
 * Shared raw-line parsing helpers for classic IRC inbound flows.
 *
 * <p>Keeping these parsers out of the bridge listener makes future translator/coordinator
 * extractions less risky because the line-shape logic no longer lives inside the event adapter.
 */
public final class PircbotxInboundLineParsers {

  private PircbotxInboundLineParsers() {}

  public static ParsedIrcLine parseIrcLine(String normalizedLine) {
    if (normalizedLine == null) return null;
    String s = normalizedLine.trim();
    if (s.isEmpty()) return null;

    String prefix = null;
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp > 1) {
        prefix = s.substring(1, sp);
        s = s.substring(sp + 1).trim();
      }
    }

    String trailing = null;
    int idx = s.indexOf(" :");
    if (idx >= 0) {
      trailing = s.substring(idx + 2);
      s = s.substring(0, idx).trim();
    }

    if (s.isEmpty()) return null;

    String[] parts = s.split("\\s+");
    if (parts.length == 0) return null;
    String cmd = parts[0];
    java.util.ArrayList<String> params = new java.util.ArrayList<>();
    for (int i = 1; i < parts.length; i++) {
      if (!parts[i].isBlank()) params.add(parts[i]);
    }

    return new ParsedIrcLine(prefix, cmd, params, trailing);
  }

  public static String rawTrailingFromIrcLine(String rawLine) {
    String s = Objects.toString(rawLine, "");
    if (s.isEmpty()) return "";

    if (s.startsWith("@")) {
      int sp = s.indexOf(' ');
      if (sp <= 0 || sp + 1 >= s.length()) return "";
      s = s.substring(sp + 1);
    }
    if (s.isEmpty()) return "";

    int pos = 0;
    if (s.charAt(0) == ':') {
      int sp = s.indexOf(' ');
      if (sp <= 0 || sp + 1 >= s.length()) return "";
      pos = sp + 1;
    }

    while (pos < s.length() && s.charAt(pos) == ' ') {
      pos++;
    }
    if (pos >= s.length()) return "";

    int cmdEnd = s.indexOf(' ', pos);
    if (cmdEnd < 0 || cmdEnd + 1 >= s.length()) return "";
    pos = cmdEnd + 1;

    int trailingIdx = s.indexOf(" :", pos);
    if (trailingIdx < 0 || trailingIdx + 2 > s.length()) return "";
    return s.substring(trailingIdx + 2);
  }

  public static ParsedInviteLine parseInviteLine(ParsedIrcLine parsed) {
    if (parsed == null) return null;
    String cmd = Objects.toString(parsed.command(), "").trim();
    if (!"INVITE".equalsIgnoreCase(cmd)) return null;

    String from = nickFromPrefix(parsed.prefix());
    String invitee = "";
    String channel = "";
    String reason = "";

    List<String> params = parsed.params();
    if (params != null && !params.isEmpty()) {
      invitee = Objects.toString(params.get(0), "").trim();
      if (params.size() >= 2) {
        channel = Objects.toString(params.get(1), "").trim();
      }
      if (params.size() >= 3) {
        reason = Objects.toString(params.get(2), "").trim();
      }
    }

    String trailing = Objects.toString(parsed.trailing(), "").trim();
    if (!trailing.isEmpty()) {
      if (channel.isEmpty()) {
        int sp = trailing.indexOf(' ');
        if (sp > 0) {
          channel = trailing.substring(0, sp).trim();
          reason = trailing.substring(sp + 1).trim();
        } else {
          channel = trailing;
        }
      } else if (!trailing.equalsIgnoreCase(channel)) {
        if (trailing.startsWith(channel + " ")) {
          reason = trailing.substring(channel.length()).trim();
        } else {
          reason = trailing;
        }
      }
    }

    if (channel.startsWith(":")) channel = channel.substring(1).trim();
    if (reason.startsWith(":")) reason = reason.substring(1).trim();
    if (channel.isBlank()) return null;

    return new ParsedInviteLine(from, invitee, channel, reason);
  }

  public static ParsedWallopsLine parseWallopsLine(ParsedIrcLine parsed) {
    if (parsed == null) return null;
    String cmd = Objects.toString(parsed.command(), "").trim();
    if (!"WALLOPS".equalsIgnoreCase(cmd)) return null;

    String from = Objects.toString(nickFromPrefix(parsed.prefix()), "").trim();
    if (from.isEmpty()) {
      from = Objects.toString(parsed.prefix(), "").trim();
    }
    if (from.isEmpty()) from = "server";

    String message = Objects.toString(parsed.trailing(), "").trim();
    if (message.isEmpty()) {
      List<String> params = parsed.params();
      if (params != null && !params.isEmpty()) {
        message = Objects.toString(params.get(params.size() - 1), "").trim();
      }
    }
    if (message.startsWith(":")) {
      message = message.substring(1).trim();
    }
    if (message.isEmpty()) return null;

    return new ParsedWallopsLine(from, message);
  }

  public static String nickFromPrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) return "";
    String p = prefix;
    int bang = p.indexOf('!');
    if (bang > 0) p = p.substring(0, bang);
    int at = p.indexOf('@');
    if (at > 0) p = p.substring(0, at);
    return p;
  }

  public static ParsedJoinFailure parseJoinFailure(String rawLine) {
    if (rawLine == null || rawLine.isBlank()) return null;
    String s = PircbotxLineParseUtil.normalizeIrcLineForParsing(rawLine);
    if (s == null || s.isBlank()) return null;

    String head = s;
    String trailing = null;
    int idx = s.indexOf(" :");
    if (idx >= 0) {
      head = s.substring(0, idx).trim();
      trailing = s.substring(idx + 2).trim();
      if (trailing != null && trailing.isBlank()) trailing = null;
    }

    String[] parts = head.split("\\s+");
    if (parts.length < 3) return null;

    int codeIdx = parts[0].startsWith(":") ? 1 : 0;
    if (parts.length <= codeIdx + 1) return null;
    if (!PircbotxLineParseUtil.looksNumeric(parts[codeIdx])) return null;
    int start = Math.min(parts.length, codeIdx + 2);
    String channel = null;
    for (int i = start; i < parts.length; i++) {
      String p = parts[i];
      if (PircbotxLineParseUtil.looksLikeChannel(p)) {
        channel = p;
        break;
      }
    }
    if (channel == null || channel.isBlank()) return null;

    return new ParsedJoinFailure(channel, trailing);
  }

  public static ParsedChannelRedirect parseChannelRedirect(String rawLine) {
    if (rawLine == null || rawLine.isBlank()) return null;
    String normalized = PircbotxLineParseUtil.normalizeIrcLineForParsing(rawLine);
    ParsedIrcLine pl = parseIrcLine(normalized);
    if (pl == null) return null;
    if (!"470".equals(Objects.toString(pl.command(), "").trim())) return null;
    List<String> params = pl.params();
    if (params == null || params.isEmpty()) return null;

    String from = null;
    String to = null;
    for (String p : params) {
      if (!PircbotxLineParseUtil.looksLikeChannel(p)) continue;
      if (from == null) {
        from = p;
      } else if (to == null) {
        to = p;
        break;
      }
    }
    if (from == null || from.isBlank() || to == null || to.isBlank()) return null;
    return new ParsedChannelRedirect(from, to, Objects.toString(pl.trailing(), "").trim());
  }
}
