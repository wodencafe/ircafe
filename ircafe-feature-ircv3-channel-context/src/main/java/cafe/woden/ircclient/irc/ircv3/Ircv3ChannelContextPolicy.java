package cafe.woden.ircclient.irc.ircv3;

import java.util.Map;
import java.util.Objects;

/** Resolves the conversation target for IRCv3 channel-context tagged messages. */
public final class Ircv3ChannelContextPolicy {

  private static final String CHANNEL_CONTEXT = "channel-context";
  private static final String DRAFT_CHANNEL_CONTEXT = "draft/channel-context";

  private Ircv3ChannelContextPolicy() {}

  public static String resolveTarget(Map<String, String> tags, String rawTarget, String fromNick) {
    // The tag only supplies context for private messages, never for an existing channel target.
    if (isChannelName(rawTarget)) return Objects.toString(rawTarget, "").trim();
    String context = Ircv3Tags.firstDecodedTagValue(tags, CHANNEL_CONTEXT, DRAFT_CHANNEL_CONTEXT);
    if (isChannelName(context)) return context;
    return resolveConversationTarget(rawTarget, fromNick);
  }

  public static String resolveConversationTarget(String rawTarget, String fromNick) {
    String target = Objects.toString(rawTarget, "").trim();
    if (isChannelName(target)) return target;
    String from = Objects.toString(fromNick, "").trim();
    return from.isBlank() ? target : from;
  }

  public static boolean isChannelName(String target) {
    String value = Objects.toString(target, "").trim();
    if (value.isEmpty()) return false;
    if (value.indexOf(',') >= 0
        || value.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
      return false;
    }
    char leading = value.charAt(0);
    return leading == '#' || leading == '&' || leading == '!' || leading == '+';
  }
}
