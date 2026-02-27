package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.settings.MemoryUsageDisplayMode;
import cafe.woden.ircclient.ui.settings.NotificationBackendMode;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.util.List;
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

  @Test
  void removeMessageReactionRemovesRenderedReactionSummaryWhenLastReactionIsCleared() {
    ChatTranscriptStore store = newStore();
    TargetRef ref = new TargetRef("srv", "#chan");

    store.appendChatAt(ref, "alice", "hello", false, 6_000L, "m-42", Map.of("msgid", "m-42"));
    int baseLines = lineCount(store.document(ref));

    store.applyMessageReaction(ref, "m-42", ":+1:", "bob", 6_050L);
    int withReactionLines = lineCount(store.document(ref));

    store.removeMessageReaction(ref, "m-42", ":+1:", "bob", 6_100L);
    int afterRemovalLines = lineCount(store.document(ref));

    assertTrue(withReactionLines > baseLines);
    assertEquals(baseLines, afterRemovalLines);
  }

  @Test
  void appendChatAtTrimsOldestLinesWhenTranscriptCapIsExceeded() throws Exception {
    ChatTranscriptStore store = newStoreWithTranscriptCap(2);
    TargetRef ref = new TargetRef("srv", "#chan");

    store.appendChatAt(ref, "alice", "line-1", false, 7_000L);
    store.appendChatAt(ref, "alice", "line-2", false, 7_010L);
    store.appendChatAt(ref, "alice", "line-3", false, 7_020L);

    StyledDocument doc = store.document(ref);
    String text = transcriptText(doc);
    assertFalse(text.contains("line-1"));
    assertTrue(text.contains("line-2"));
    assertTrue(text.contains("line-3"));
    assertEquals(2, lineCount(doc));
  }

  @Test
  void transcriptCapZeroDisablesHeadTrimming() throws Exception {
    ChatTranscriptStore store = newStoreWithTranscriptCap(0);
    TargetRef ref = new TargetRef("srv", "#chan");

    store.appendChatAt(ref, "alice", "line-1", false, 8_000L);
    store.appendChatAt(ref, "alice", "line-2", false, 8_010L);
    store.appendChatAt(ref, "alice", "line-3", false, 8_020L);

    StyledDocument doc = store.document(ref);
    String text = transcriptText(doc);
    assertTrue(text.contains("line-1"));
    assertTrue(text.contains("line-2"));
    assertTrue(text.contains("line-3"));
    assertEquals(3, lineCount(doc));
  }

  private static ChatTranscriptStore newStore() {
    ChatStyles styles = new ChatStyles(null);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    return new ChatTranscriptStore(styles, renderer, null, null, null, null, null, null, null);
  }

  private static ChatTranscriptStore newStoreWithTranscriptCap(int maxLines) {
    ChatStyles styles = new ChatStyles(null);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(settingsWithTranscriptCap(maxLines));
    return new ChatTranscriptStore(
        styles, renderer, null, null, null, null, null, settingsBus, null);
  }

  private static UiSettings settingsWithTranscriptCap(int maxLines) {
    return new UiSettings(
        "darcula",
        "Monospaced",
        12,
        true,
        true,
        false,
        false,
        false,
        true,
        true,
        false,
        true,
        false,
        false,
        true,
        NotificationBackendMode.AUTO,
        false,
        false,
        0,
        0,
        true,
        false,
        false,
        true,
        true,
        true,
        true,
        "dots",
        true,
        "HH:mm:ss",
        true,
        true,
        100,
        200,
        2000,
        20,
        10,
        6,
        false,
        6,
        18,
        360,
        500,
        maxLines,
        true,
        "#6AA2FF",
        true,
        7,
        6,
        30,
        5,
        false,
        15,
        3,
        60,
        5,
        false,
        45,
        120,
        false,
        300,
        2,
        30,
        15,
        MemoryUsageDisplayMode.LONG,
        5,
        true,
        false,
        false,
        false,
        List.of());
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
