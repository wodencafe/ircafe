package cafe.woden.ircclient.app.interceptors;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;

/** Event kinds that interceptor rules can match. */
public enum InterceptorEventType {
  MESSAGE("message", "Message"),
  ACTION("action", "Action"),
  NOTICE("notice", "Notice"),
  MODE("mode", "Mode"),
  JOIN("join", "Join"),
  PART("part", "Part"),
  QUIT("quit", "Quit"),
  NICK("nick", "Nick"),
  TOPIC("topic", "Topic"),
  INVITE("invite", "Invite"),
  KICK("kick", "Kick"),
  CTCP("ctcp", "CTCP"),
  PRIVATE_MESSAGE("private-message", "Private message"),
  PRIVATE_ACTION("private-action", "Private action"),
  SERVER("server", "Server"),
  ERROR("error", "Error");

  private final String token;
  private final String label;

  InterceptorEventType(String token, String label) {
    this.token = token;
    this.label = label;
  }

  public String token() {
    return token;
  }

  @Override
  public String toString() {
    return label;
  }

  public static InterceptorEventType fromToken(String raw) {
    String token = normalizeToken(raw);
    if (token.isEmpty()) return null;
    return switch (token) {
      case "msg", "message", "privmsg" -> MESSAGE;
      case "act", "action", "me" -> ACTION;
      case "notice" -> NOTICE;
      case "mode" -> MODE;
      case "join" -> JOIN;
      case "part" -> PART;
      case "quit" -> QUIT;
      case "nick", "nickchange", "nick-change" -> NICK;
      case "topic" -> TOPIC;
      case "invite" -> INVITE;
      case "kick" -> KICK;
      case "ctcp" -> CTCP;
      case "pm", "private", "private-message", "private_message" -> PRIVATE_MESSAGE;
      case "pm-action", "pm_action", "private-action", "private_action" -> PRIVATE_ACTION;
      case "server", "status" -> SERVER;
      case "error" -> ERROR;
      default -> null;
    };
  }

  public static EnumSet<InterceptorEventType> parseCsv(String csv) {
    String raw = Objects.toString(csv, "").trim();
    if (raw.isEmpty()) {
      return EnumSet.allOf(InterceptorEventType.class);
    }

    EnumSet<InterceptorEventType> out = EnumSet.noneOf(InterceptorEventType.class);
    String[] parts = raw.split("[,\\n;]");
    for (String part : parts) {
      String token = normalizeToken(part);
      if (token.isEmpty()) continue;
      if ("*".equals(token) || "any".equals(token) || "all".equals(token)) {
        return EnumSet.allOf(InterceptorEventType.class);
      }
      InterceptorEventType type = fromToken(token);
      if (type != null) out.add(type);
    }
    return out;
  }

  public static String suggestedTokens() {
    return "message,action,notice,mode,join,part,quit,nick,topic,invite,kick,ctcp,private-message,private-action,server,error";
  }

  private static String normalizeToken(String raw) {
    String token = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    token = token.replace('_', '-');
    return token;
  }
}
