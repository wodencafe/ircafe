package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.PresenceEvent;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatTranscriptLineTagSupportTest {

  @Test
  void computeTagsIncludesKindDirectionNickAndMessageMetadata() {
    Set<String> tags =
        ChatTranscriptLineTagSupport.computeTags(
            LogKind.CHAT,
            LogDirection.OUT,
            "Alice",
            null,
            "m-1",
            Map.of("draft/reply", "m-0", "+react", ":+1:"));

    assertTrue(tags.contains("irc_privmsg"));
    assertTrue(tags.contains("irc_out"));
    assertTrue(tags.contains("nick_alice"));
    assertTrue(tags.contains("ircv3_msgid"));
    assertTrue(tags.contains("ircv3_tagged"));
    assertTrue(tags.contains("ircv3_tag_draft_reply"));
    assertTrue(tags.contains("ircv3_tag_react"));
  }

  @Test
  void computeTagsIncludesPresenceJoinSignals() {
    Set<String> tags =
        ChatTranscriptLineTagSupport.computeTags(
            LogKind.PRESENCE, LogDirection.SYSTEM, null, PresenceEvent.join("Bob"), "", Map.of());

    assertTrue(tags.contains("irc_presence"));
    assertTrue(tags.contains("irc_system"));
    assertTrue(tags.contains("irc_join"));
    assertTrue(tags.contains("nick_bob"));
  }

  @Test
  void computeTagsIncludesNickChangeSignals() {
    Set<String> tags =
        ChatTranscriptLineTagSupport.computeTags(
            LogKind.PRESENCE,
            LogDirection.SYSTEM,
            null,
            PresenceEvent.nick("OldNick", "NewNick"),
            "",
            Map.of());

    assertTrue(tags.contains("irc_nick"));
    assertTrue(tags.contains("nick_oldnick"));
    assertTrue(tags.contains("nick_newnick"));
  }

  @Test
  void computeTagsMarksSpoilersButNotTagStateWhenNothingIsTagged() {
    Set<String> tags =
        ChatTranscriptLineTagSupport.computeTags(
            LogKind.SPOILER, LogDirection.IN, "alice", null, "", Map.of());

    assertTrue(tags.contains("irc_privmsg"));
    assertTrue(tags.contains("irc_spoiler"));
    assertTrue(tags.contains("irc_in"));
    assertFalse(tags.contains("ircv3_tagged"));
  }
}
