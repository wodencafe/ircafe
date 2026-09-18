package cafe.woden.ircclient.ui.chat.transcript;

import static cafe.woden.ircclient.ui.chat.transcript.support.ChatTranscriptStoreDocumentTestSupport.transcriptText;
import static cafe.woden.ircclient.ui.chat.transcript.support.ChatTranscriptStoreTargetRefTestSupport.channelRef;
import static cafe.woden.ircclient.ui.chat.transcript.support.ChatTranscriptStoreTestFactory.newStore;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.transcript.message.RedactedMessageContent;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatTranscriptStoreMessageMutationTest {

  @Test
  void replyContextLineShowsCachedSnippetWhenReferencedMessageIsKnown() throws Exception {
    ChatTranscriptStore store = newStore();
    TargetRef ref = channelRef();

    store.appendChatAt(
        ref, "alice", "original message text", false, 6_000L, "m-1", Map.of("msgid", "m-1"));
    store.appendChatAt(
        ref,
        "bob",
        "reply body",
        false,
        6_050L,
        "m-2",
        Map.of("msgid", "m-2", "draft/reply", "m-1"));

    String text = transcriptText(store.document(ref));
    assertTrue(text.contains("↪ Reply to alice: original message text\nbob: reply body"));
    assertFalse(text.contains("m-1"));
    var doc = store.document(ref);
    assertEquals(
        ChatStyles.REPLY_BLOCK_BODY,
        doc.getParagraphElement(text.indexOf("bob:"))
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_REPLY_BLOCK));
    assertNull(
        doc.getCharacterElement(text.indexOf("reply body"))
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_MSG_REF));
    store.appendChat(ref, "carol", "ordinary message");
    String appended = transcriptText(doc);
    assertNull(
        doc.getParagraphElement(appended.indexOf("carol:"))
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_REPLY_BLOCK));
    store.restyleAllDocuments();
    assertEquals(
        "m-1",
        doc.getCharacterElement(text.indexOf("↪"))
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_MSG_REF));
  }

  @Test
  void actionReplyDoesNotStyleFollowingMessagesAsReplies() throws Exception {
    ChatTranscriptStore store = newStore();
    TargetRef ref = channelRef();
    store.appendChatAt(ref, "alice", "hello", false, 1_000L, "m-1", Map.of());
    store.appendActionAt(ref, "bob", "waves", false, 2_000L, "m-2", Map.of("+reply", "m-1"));
    store.appendChat(ref, "carol", "ordinary message");
    var doc = store.document(ref);
    String text = transcriptText(doc);
    assertTrue(text.contains("↪ Reply to alice: hello\n* bob waves"));
    assertEquals(
        ChatStyles.REPLY_BLOCK_BODY,
        doc.getParagraphElement(text.indexOf("* bob"))
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_REPLY_BLOCK));
    assertNull(
        doc.getParagraphElement(text.indexOf("carol:"))
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_REPLY_BLOCK));
  }

  @Test
  void replyContextLineUsesEditedTextPreviewAfterMessageEdit() throws Exception {
    ChatTranscriptStore store = newStore();
    TargetRef ref = channelRef();

    store.appendChatAt(ref, "alice", "before", false, 6_000L, "m-1", Map.of("msgid", "m-1"));
    assertTrue(store.applyMessageEdit(ref, "m-1", "after", "alice", 6_030L, "", Map.of()));

    store.appendChatAt(
        ref,
        "bob",
        "reply body",
        false,
        6_050L,
        "m-2",
        Map.of("msgid", "m-2", "draft/reply", "m-1"));

    String text = transcriptText(store.document(ref));
    assertTrue(text.contains("↪ Reply to alice: after (edited)\nbob: reply body"));
  }

  @Test
  void applyMessageEditWithBlankTextFallsBackToEditedMarker() throws Exception {
    ChatTranscriptStore store = newStore();
    TargetRef ref = channelRef();

    store.appendChatAt(ref, "alice", "before", false, 6_000L, "m-1", Map.of("msgid", "m-1"));

    assertTrue(store.applyMessageEdit(ref, "m-1", "   ", "alice", 6_030L, "", Map.of()));

    assertEquals("alice: (edited)", store.messagePreviewById(ref, "m-1"));
    assertTrue(transcriptText(store.document(ref)).contains("(edited)"));
  }

  @Test
  void applyMessageEditReplacesActionLineAndUpdatesPreview() throws Exception {
    ChatTranscriptStore store = newStore();
    TargetRef ref = channelRef();

    store.appendActionAt(ref, "alice", "waves", false, 6_000L, "a-1", Map.of("msgid", "a-1"));

    assertTrue(store.applyMessageEdit(ref, "a-1", "jumps", "alice", 6_030L, "", Map.of()));

    String text = transcriptText(store.document(ref));
    assertTrue(text.contains("* alice jumps (edited)"));
    assertFalse(text.contains("* alice waves"));
    assertEquals("* alice jumps (edited)", store.messagePreviewById(ref, "a-1"));
  }

  @Test
  void applyMessageRedactionPreservesRevealableOriginalWithoutLeakingIntoTranscript()
      throws Exception {
    ChatTranscriptStore store = newStore();
    TargetRef ref = channelRef();

    store.appendChatAt(ref, "alice", "before", false, 6_000L, "m-1", Map.of("msgid", "m-1"));

    assertTrue(store.applyMessageRedaction(ref, "m-1", "alice", 6_050L, "", Map.of()));

    String text = transcriptText(store.document(ref));
    assertTrue(text.contains("[message redacted]"));
    assertFalse(text.contains("alice: before"));

    RedactedMessageContent reveal = store.redactedOriginalById(ref, "m-1");
    assertNotNull(reveal);
    assertEquals("before", reveal.originalText());
    assertEquals("alice", reveal.originalFromNick());
    assertEquals("alice", reveal.redactedBy());
    assertEquals(6_050L, reveal.redactedAtEpochMs());
  }

  @Test
  void applyMessageRedactionKeepsEditedTextForRevealAfterEdit() {
    ChatTranscriptStore store = newStore();
    TargetRef ref = channelRef();

    store.appendChatAt(ref, "alice", "before", false, 6_000L, "m-1", Map.of("msgid", "m-1"));
    assertTrue(store.applyMessageEdit(ref, "m-1", "after", "alice", 6_020L, "", Map.of()));
    assertTrue(store.applyMessageRedaction(ref, "m-1", "alice", 6_050L, "", Map.of()));

    RedactedMessageContent reveal = store.redactedOriginalById(ref, "m-1");
    assertNotNull(reveal);
    assertEquals("after (edited)", reveal.originalText());
    assertEquals("alice: [message redacted]", store.messagePreviewById(ref, "m-1"));
  }

  @Test
  void redactedMessageMetadataSurvivesTranscriptRestyle() {
    ChatTranscriptStore store = newStore();
    TargetRef ref = channelRef();

    store.appendChatAt(ref, "alice", "before", false, 6_000L, "m-1", Map.of("msgid", "m-1"));
    assertTrue(store.applyMessageRedaction(ref, "m-1", "alice", 6_050L, "", Map.of()));

    assertTrue(store.messageOffsetById(ref, "m-1") >= 0);

    store.restyleAllDocuments();

    assertTrue(store.messageOffsetById(ref, "m-1") >= 0);
    assertNotNull(store.redactedOriginalById(ref, "m-1"));
  }

  @Test
  void messagePreviewByIdReturnsCachedReplySnippet() {
    ChatTranscriptStore store = newStore();
    TargetRef ref = channelRef();

    store.appendChatAt(ref, "alice", "hello from preview cache", false, 6_000L, "m-1", Map.of());

    assertEquals("alice: hello from preview cache", store.messagePreviewById(ref, "m-1"));
  }
}
