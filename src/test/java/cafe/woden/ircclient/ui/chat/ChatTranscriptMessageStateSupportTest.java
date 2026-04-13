package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;

class ChatTranscriptMessageStateSupportTest {

  private static final ChatTranscriptMessageStateSupport.Context CONTEXT =
      new ChatTranscriptMessageStateSupport.Context(12, "[message redacted]", () -> 42_424L);

  @Test
  void rememberMessagePreviewStoresPreviewForSupportedKinds() {
    Map<String, String> previews = new HashMap<>();
    ChatTranscriptMessageStateSupport.rememberMessagePreview(
        CONTEXT, previews, lineMeta(LogKind.CHAT, "m-1", 1_000L), "alice", "hello there");

    assertEquals("alice: hello there", previews.get("m-1"));

    ChatTranscriptMessageStateSupport.rememberMessagePreview(
        CONTEXT, previews, lineMeta(LogKind.STATUS, "m-2", 2_000L), "server", "status line");
    assertFalse(previews.containsKey("m-2"));
  }

  @Test
  void rememberCurrentMessageContentStoresTrimmedFromNickAndEpoch() {
    Map<String, ChatTranscriptStore.MessageContentSnapshot> current = new HashMap<>();

    ChatTranscriptMessageStateSupport.rememberCurrentMessageContent(
        current, lineMeta(LogKind.ACTION, "m-1", 1_234L), " alice ", "* waves");

    ChatTranscriptStore.MessageContentSnapshot snapshot = current.get("m-1");
    assertEquals(LogKind.ACTION, snapshot.kind());
    assertEquals("alice", snapshot.fromNick());
    assertEquals("* waves", snapshot.renderedText());
    assertEquals(1_234L, snapshot.epochMs());
  }

  @Test
  void rememberEditedCurrentMessageContentFallsBackToAttrsWhenNoSnapshotExists() {
    Map<String, ChatTranscriptStore.MessageContentSnapshot> current = new HashMap<>();
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_META_KIND, "notice");
    attrs.addAttribute(ChatStyles.ATTR_META_FROM, "server");
    attrs.addAttribute(ChatStyles.ATTR_META_EPOCH_MS, 7_777L);

    ChatTranscriptMessageStateSupport.rememberEditedCurrentMessageContent(
        current, "m-2", attrs, "edited text");

    ChatTranscriptStore.MessageContentSnapshot snapshot = current.get("m-2");
    assertEquals(LogKind.NOTICE, snapshot.kind());
    assertEquals("server", snapshot.fromNick());
    assertEquals("edited text", snapshot.renderedText());
    assertEquals(7_777L, snapshot.epochMs());
  }

  @Test
  void rememberEditedCurrentMessageContentPrefersExistingSnapshot() {
    Map<String, ChatTranscriptStore.MessageContentSnapshot> current = new HashMap<>();
    current.put(
        "m-2",
        new ChatTranscriptStore.MessageContentSnapshot(
            LogKind.CHAT, "alice", "original text", 1_111L));
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_META_KIND, "notice");
    attrs.addAttribute(ChatStyles.ATTR_META_FROM, "server");
    attrs.addAttribute(ChatStyles.ATTR_META_EPOCH_MS, 7_777L);

    ChatTranscriptMessageStateSupport.rememberEditedCurrentMessageContent(
        current, " m-2 ", attrs, "edited text");

    ChatTranscriptStore.MessageContentSnapshot snapshot = current.get("m-2");
    assertTrue(current.containsKey("m-2"));
    assertEquals(LogKind.CHAT, snapshot.kind());
    assertEquals("alice", snapshot.fromNick());
    assertEquals("edited text", snapshot.renderedText());
    assertEquals(1_111L, snapshot.epochMs());
  }

  @Test
  void rememberRedactedOriginalUsesCurrentSnapshotAndFallbackClock() {
    Map<String, ChatTranscriptStore.MessageContentSnapshot> current = new HashMap<>();
    current.put(
        "m-3",
        new ChatTranscriptStore.MessageContentSnapshot(LogKind.CHAT, "alice", "hello", 3_333L));
    Map<String, ChatTranscriptStore.RedactedMessageContent> redacted = new HashMap<>();

    ChatTranscriptMessageStateSupport.rememberRedactedOriginal(
        CONTEXT, current, redacted, "m-3", null, "mod", 0L);

    ChatTranscriptStore.RedactedMessageContent content = redacted.get("m-3");
    assertEquals(LogKind.CHAT, content.originalKind());
    assertEquals("alice", content.originalFromNick());
    assertEquals("hello", content.originalText());
    assertEquals(3_333L, content.originalEpochMs());
    assertEquals("mod", content.redactedBy());
    assertEquals(42_424L, content.redactedAtEpochMs());
  }

  @Test
  void rememberRedactedOriginalSkipsPlaceholderOnlyFallback() {
    Map<String, ChatTranscriptStore.RedactedMessageContent> redacted = new HashMap<>();
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(ChatStyles.ATTR_META_KIND, "chat");
    attrs.addAttribute(ChatStyles.ATTR_META_FROM, "alice");
    attrs.addAttribute(ChatStyles.ATTR_META_EPOCH_MS, 8_888L);

    ChatTranscriptMessageStateSupport.rememberRedactedOriginal(
        CONTEXT, new HashMap<>(), redacted, "m-4", attrs, "mod", 9_999L);

    assertNull(redacted.get("m-4"));
  }

  private static ChatTranscriptStore.LineMeta lineMeta(LogKind kind, String msgId, long epochMs) {
    return new ChatTranscriptStore.LineMeta(
        "buffer",
        kind,
        LogDirection.IN,
        "alice",
        epochMs,
        Set.of("tag"),
        msgId,
        "msgid=" + msgId,
        Map.of("msgid", msgId));
  }
}
