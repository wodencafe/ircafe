package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.app.api.PresenceEvent;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ChatTranscriptLineTagSupport {

  private ChatTranscriptLineTagSupport() {}

  static Set<String> computeTags(
      LogKind kind,
      LogDirection direction,
      String fromNick,
      PresenceEvent presenceEvent,
      String messageId,
      Map<String, String> ircv3Tags) {
    LinkedHashSet<String> out = new LinkedHashSet<>();

    if (kind != null) {
      switch (kind) {
        case CHAT -> out.add("irc_privmsg");
        case ACTION -> out.add("irc_action");
        case NOTICE -> out.add("irc_notice");
        case PRESENCE -> out.add("irc_presence");
        case STATUS -> out.add("irc_status");
        case ERROR -> out.add("irc_error");
        case SPOILER -> {
          out.add("irc_privmsg");
          out.add("irc_spoiler");
        }
        default -> out.add("irc_misc");
      }
    }

    if (direction != null) {
      switch (direction) {
        case IN -> out.add("irc_in");
        case OUT -> out.add("irc_out");
        case SYSTEM -> out.add("irc_system");
      }
    }

    String nick = Objects.toString(fromNick, "").trim();
    if (!nick.isEmpty()) {
      out.add("nick_" + nick.toLowerCase(Locale.ROOT));
    }

    if (presenceEvent != null) {
      try {
        String presenceNick = Objects.toString(presenceEvent.nick(), "").trim();
        String oldNick = Objects.toString(presenceEvent.oldNick(), "").trim();
        String newNick = Objects.toString(presenceEvent.newNick(), "").trim();
        switch (presenceEvent.kind()) {
          case JOIN -> {
            out.add("irc_join");
            if (!presenceNick.isEmpty()) {
              out.add("nick_" + presenceNick.toLowerCase(Locale.ROOT));
            }
          }
          case PART -> {
            out.add("irc_part");
            if (!presenceNick.isEmpty()) {
              out.add("nick_" + presenceNick.toLowerCase(Locale.ROOT));
            }
          }
          case QUIT -> {
            out.add("irc_quit");
            if (!presenceNick.isEmpty()) {
              out.add("nick_" + presenceNick.toLowerCase(Locale.ROOT));
            }
          }
          case NICK -> {
            out.add("irc_nick");
            if (!oldNick.isEmpty()) {
              out.add("nick_" + oldNick.toLowerCase(Locale.ROOT));
            }
            if (!newNick.isEmpty()) {
              out.add("nick_" + newNick.toLowerCase(Locale.ROOT));
            }
          }
        }
      } catch (Exception ignored) {
      }
    }

    if (messageId != null && !messageId.isBlank()) {
      out.add("ircv3_msgid");
    }

    if (ircv3Tags != null && !ircv3Tags.isEmpty()) {
      out.add("ircv3_tagged");
      for (String rawKey : ircv3Tags.keySet()) {
        String key = ChatTranscriptMessageMetadataSupport.sanitizeTagForMeta(rawKey);
        if (!key.isEmpty()) {
          out.add("ircv3_tag_" + key);
        }
      }
    }

    return Set.copyOf(out);
  }
}
