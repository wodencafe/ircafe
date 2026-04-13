package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.coordinator.MessageActionCapabilityPolicy;
import org.junit.jupiter.api.Test;

class MessageReactionToggleSupportTest {

  @Test
  void resolveCommandUsesUnreactWhenCurrentNickAlreadyReacted() {
    ChatTranscriptStore transcripts = newStore();
    TargetRef target = new TargetRef("srv", "#chan");
    MessageActionCapabilityPolicy capabilityPolicy = mock(MessageActionCapabilityPolicy.class);
    when(capabilityPolicy.canReact("srv")).thenReturn(true);
    when(capabilityPolicy.canUnreact("srv")).thenReturn(true);

    transcripts.appendChatAt(
        target, "alice", "hello", false, 6_000L, "m-42", java.util.Map.of("msgid", "m-42"));
    transcripts.applyMessageReaction(target, "m-42", ":+1:", "me", 6_050L);

    String command =
        MessageReactionToggleSupport.resolveCommand(
            target, "m-42", ":+1:", false, transcripts, capabilityPolicy, sid -> "me");

    assertEquals("/unreact m-42 :+1:", command);
  }

  @Test
  void resolveCommandUsesReactWhenCurrentNickHasNotReactedYet() {
    ChatTranscriptStore transcripts = newStore();
    TargetRef target = new TargetRef("srv", "#chan");
    MessageActionCapabilityPolicy capabilityPolicy = mock(MessageActionCapabilityPolicy.class);
    when(capabilityPolicy.canReact("srv")).thenReturn(true);
    when(capabilityPolicy.canUnreact("srv")).thenReturn(true);

    transcripts.appendChatAt(
        target, "alice", "hello", false, 6_000L, "m-42", java.util.Map.of("msgid", "m-42"));
    transcripts.applyMessageReaction(target, "m-42", ":+1:", "bob", 6_050L);

    String command =
        MessageReactionToggleSupport.resolveCommand(
            target, "m-42", ":+1:", false, transcripts, capabilityPolicy, sid -> "me");

    assertEquals("/react m-42 :+1:", command);
  }

  private static ChatTranscriptStore newStore() {
    ChatStyles styles = new ChatStyles(null);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    return new ChatTranscriptStore(
        styles,
        renderer,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        new cafe.woden.ircclient.irc.roster.UserListStore());
  }
}
