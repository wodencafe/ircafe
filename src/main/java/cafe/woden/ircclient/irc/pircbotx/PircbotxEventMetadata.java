package cafe.woden.ircclient.irc.pircbotx;

import cafe.woden.ircclient.irc.*;
import cafe.woden.ircclient.irc.ircv3.*;
import cafe.woden.ircclient.irc.playback.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.pircbotx.User;

/** Shared IRCv3 metadata helpers for inbound PircBotX events. */
final class PircbotxEventMetadata {
  private static final String TAG_IRCAFE_HOSTMASK = "ircafe/hostmask";

  private PircbotxEventMetadata() {}

  static Instant inboundAt(Object pircbotxEvent) {
    Instant now = Instant.now();
    return Ircv3ServerTime.orNow(Ircv3ServerTime.fromEvent(pircbotxEvent), now);
  }

  static Map<String, String> ircv3TagsFromEvent(Object event) {
    return Ircv3Tags.fromEvent(event);
  }

  static Map<String, String> withObservedHostmaskTag(Map<String, String> tags, User user) {
    Map<String, String> out = (tags == null) ? new HashMap<>() : tags;
    if (user == null) return out;
    String hostmask = PircbotxUtil.hostmaskFromUser(user);
    if (!PircbotxUtil.isUsefulHostmask(hostmask)) return out;
    out.put(TAG_IRCAFE_HOSTMASK, hostmask.trim());
    return out;
  }

  static String ircv3MessageId(Map<String, String> tags) {
    return Ircv3Tags.firstTagValue(tags, "msgid", "draft/msgid", "znc.in/msgid");
  }
}
