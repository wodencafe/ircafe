package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import java.util.Map;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.Test;

class ChatTranscriptStoreTest {

  @Test
  void appendChatAtWithDuplicateMessageIdIsIgnored() throws Exception {
    ChatTranscriptStore store = newStore();
    TargetRef ref = new TargetRef("srv", "#chan");

    store.appendChatAt(ref, "alice", "first", false, 1_000L, "m-1", Map.of("msgid", "m-1"));
    StyledDocument doc = store.document(ref);
    int lenAfterFirst = doc.getLength();

    store.appendChatAt(ref, "alice", "second", false, 1_050L, "m-1", Map.of("msgid", "m-1"));

    assertEquals(lenAfterFirst, doc.getLength());
    assertTrue(transcriptText(doc).contains("first"));
    assertFalse(transcriptText(doc).contains("second"));
    assertTrue(store.messageOffsetById(ref, "m-1") >= 0);
  }

  @Test
  void appendActionAtWithDuplicateMessageIdIsIgnored() throws Exception {
    ChatTranscriptStore store = newStore();
    TargetRef ref = new TargetRef("srv", "#chan");

    store.appendActionAt(ref, "alice", "waves", false, 2_000L, "act-1", Map.of("msgid", "act-1"));
    StyledDocument doc = store.document(ref);
    int lenAfterFirst = doc.getLength();

    store.appendActionAt(ref, "alice", "jumps", false, 2_050L, "act-1", Map.of("msgid", "act-1"));

    assertEquals(lenAfterFirst, doc.getLength());
    assertTrue(transcriptText(doc).contains("waves"));
    assertFalse(transcriptText(doc).contains("jumps"));
  }

  @Test
  void appendNoticeAtWithDuplicateMessageIdIsIgnored() throws Exception {
    ChatTranscriptStore store = newStore();
    TargetRef ref = new TargetRef("srv", "status");

    store.appendNoticeAt(
        ref, "(notice) server", "maintenance", 3_000L, "n-1", Map.of("msgid", "n-1"));
    StyledDocument doc = store.document(ref);
    int lenAfterFirst = doc.getLength();

    store.appendNoticeAt(ref, "(notice) server", "new text", 3_100L, "n-1", Map.of("msgid", "n-1"));

    assertEquals(lenAfterFirst, doc.getLength());
    assertTrue(transcriptText(doc).contains("maintenance"));
    assertFalse(transcriptText(doc).contains("new text"));
  }

  @Test
  void appendStatusAtWithDuplicateMessageIdIsIgnored() throws Exception {
    ChatTranscriptStore store = newStore();
    TargetRef ref = new TargetRef("srv", "status");

    store.appendStatusAt(
        ref, "(server)", "421 NO_SUCH_COMMAND", 4_000L, "s-1", Map.of("msgid", "s-1"));
    StyledDocument doc = store.document(ref);
    int lenAfterFirst = doc.getLength();

    store.appendStatusAt(
        ref, "(server)", "different status", 4_100L, "s-1", Map.of("msgid", "s-1"));

    assertEquals(lenAfterFirst, doc.getLength());
    assertTrue(transcriptText(doc).contains("421 NO_SUCH_COMMAND"));
    assertFalse(transcriptText(doc).contains("different status"));
  }

  @Test
  void blankMessageIdDoesNotSuppressRepeatedMessages() {
    ChatTranscriptStore store = newStore();
    TargetRef ref = new TargetRef("srv", "#chan");

    store.appendChatAt(ref, "alice", "same", false, 5_000L, "", Map.of());
    store.appendChatAt(ref, "alice", "same", false, 5_010L, "", Map.of());

    assertEquals(2, lineCount(store.document(ref)));
  }

  private static ChatTranscriptStore newStore() {
    ChatStyles styles = new ChatStyles(null);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    return new ChatTranscriptStore(styles, renderer, null, null, null, null, null, null, null);
  }

  private static String transcriptText(StyledDocument doc) throws Exception {
    return doc.getText(0, doc.getLength());
  }

  private static int lineCount(StyledDocument doc) {
    try {
      String text = transcriptText(doc);
      return (int) text.chars().filter(ch -> ch == '\n').count();
    } catch (Exception ignored) {
      return 0;
    }
  }
}
