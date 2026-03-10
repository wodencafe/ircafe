package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.embed.ChatImageEmbedder;
import cafe.woden.ircclient.ui.chat.embed.ChatLinkPreviewEmbedder;
import cafe.woden.ircclient.ui.chat.fold.MessageReactionsComponent;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.settings.MemoryUsageDisplayMode;
import cafe.woden.ircclient.ui.settings.NotificationBackendMode;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
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
  void replyContextLineShowsCachedSnippetWhenReferencedMessageIsKnown() throws Exception {
    ChatTranscriptStore store = newStore();
    TargetRef ref = new TargetRef("srv", "#chan");

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
    assertTrue(text.contains("-> bob replied to m-1 (alice: original message text)"));
  }

  @Test
  void replyContextLineUsesEditedTextPreviewAfterMessageEdit() throws Exception {
    ChatTranscriptStore store = newStore();
    TargetRef ref = new TargetRef("srv", "#chan");

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
    assertTrue(text.contains("-> bob replied to m-1 (alice: after (edited))"));
  }

  @Test
  void messagePreviewByIdReturnsCachedReplySnippet() {
    ChatTranscriptStore store = newStore();
    TargetRef ref = new TargetRef("srv", "#chan");

    store.appendChatAt(ref, "alice", "hello from preview cache", false, 6_000L, "m-1", Map.of());

    assertEquals("alice: hello from preview cache", store.messagePreviewById(ref, "m-1"));
  }

  @Test
  void reactionChipClickDispatchesConfiguredActionHandler() {
    ChatTranscriptStore store = newStore();
    TargetRef ref = new TargetRef("srv", "#chan");
    AtomicReference<TargetRef> clickedTarget = new AtomicReference<>();
    AtomicReference<String> clickedMsgId = new AtomicReference<>();
    AtomicReference<String> clickedReaction = new AtomicReference<>();
    AtomicBoolean unreact = new AtomicBoolean();

    store.setReactionChipActionHandler(
        (target, messageId, reactionToken, unreactRequested) -> {
          clickedTarget.set(target);
          clickedMsgId.set(messageId);
          clickedReaction.set(reactionToken);
          unreact.set(unreactRequested);
        });

    store.appendChatAt(ref, "alice", "hello", false, 6_000L, "m-42", Map.of("msgid", "m-42"));
    store.applyMessageReaction(ref, "m-42", ":+1:", "bob", 6_050L);

    MessageReactionsComponent reactions = reactionComponent(store.document(ref));
    assertNotNull(reactions);
    JLabel chip = (JLabel) reactions.getComponent(0);
    MouseEvent click =
        new MouseEvent(
            chip,
            MouseEvent.MOUSE_RELEASED,
            System.currentTimeMillis(),
            0,
            4,
            4,
            1,
            false,
            MouseEvent.BUTTON1);
    for (MouseListener listener : chip.getMouseListeners()) {
      listener.mouseReleased(click);
    }

    assertEquals(ref, clickedTarget.get());
    assertEquals("m-42", clickedMsgId.get());
    assertEquals(":+1:", clickedReaction.get());
    assertFalse(unreact.get());
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

  @Test
  void readMarkerPersistsWhenSetBeforeUnreadLinesExist() {
    ChatTranscriptStore store = newStore();
    TargetRef ref = new TargetRef("srv", "#chan");

    store.updateReadMarker(ref, 1_000L);
    assertEquals(-1, store.readMarkerJumpOffset(ref));

    store.appendChatAt(ref, "alice", "older", false, 900L);
    assertEquals(-1, store.readMarkerJumpOffset(ref));

    store.appendChatAt(ref, "alice", "newer", false, 1_100L);
    assertTrue(store.readMarkerJumpOffset(ref) >= 0);
  }

  @Test
  void clearReadMarkersForServerRemovesMarkerStateWithoutAffectingOtherServers() {
    ChatTranscriptStore store = newStore();
    TargetRef onServer = new TargetRef("srv", "#chan");
    TargetRef otherServer = new TargetRef("other", "#chan");

    store.appendChatAt(onServer, "alice", "older", false, 900L);
    store.appendChatAt(onServer, "alice", "newer", false, 1_100L);
    store.updateReadMarker(onServer, 1_000L);
    assertTrue(store.readMarkerJumpOffset(onServer) >= 0);

    store.appendChatAt(otherServer, "alice", "older", false, 900L);
    store.appendChatAt(otherServer, "alice", "newer", false, 1_100L);
    store.updateReadMarker(otherServer, 1_000L);
    assertTrue(store.readMarkerJumpOffset(otherServer) >= 0);

    store.clearReadMarkersForServer("srv");

    assertEquals(-1, store.readMarkerJumpOffset(onServer));
    assertTrue(store.readMarkerJumpOffset(otherServer) >= 0);

    store.appendChatAt(onServer, "alice", "latest", false, 1_200L);
    assertEquals(-1, store.readMarkerJumpOffset(onServer));
  }

  @Test
  void appendChatAtAddsManualPreviewMarkerForPolicyBlockedUrls() throws Exception {
    ChatStyles styles = new ChatStyles(null);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(settingsWithTranscriptCap(0));

    ChatImageEmbedder imageEmbeds = mock(ChatImageEmbedder.class);
    ChatLinkPreviewEmbedder linkPreviews = mock(ChatLinkPreviewEmbedder.class);
    when(imageEmbeds.appendEmbeds(any(), any(), anyString(), anyString(), any()))
        .thenReturn(
            new ChatImageEmbedder.AppendResult(0, List.of("https://blocked.example/a.png")));
    when(linkPreviews.appendPreviews(any(), any(), anyString(), anyString(), any()))
        .thenReturn(new ChatLinkPreviewEmbedder.AppendResult(0, List.of()));

    ChatTranscriptStore store =
        new ChatTranscriptStore(
            styles, renderer, null, null, null, imageEmbeds, linkPreviews, settingsBus, null, null);
    TargetRef ref = new TargetRef("srv", "#chan");

    store.appendChatAt(ref, "alice", "https://blocked.example/a.png", false, 9_000L);

    StyledDocument doc = store.document(ref);
    String text = transcriptText(doc);
    int marker = text.indexOf("👁");
    assertTrue(marker >= 0);
    Object markerUrl =
        doc.getCharacterElement(marker)
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_MANUAL_PREVIEW_URL);
    assertEquals("https://blocked.example/a.png", markerUrl);
  }

  @Test
  void insertManualPreviewAtFallsBackToLinkPreviewWhenImageInsertDeclines() {
    ChatStyles styles = new ChatStyles(null);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    ChatImageEmbedder imageEmbeds = mock(ChatImageEmbedder.class);
    ChatLinkPreviewEmbedder linkPreviews = mock(ChatLinkPreviewEmbedder.class);

    ChatTranscriptStore store =
        new ChatTranscriptStore(
            styles, renderer, null, null, null, imageEmbeds, linkPreviews, null, null, null);
    TargetRef ref = new TargetRef("srv", "#chan");
    store.appendChat(ref, "alice", "line");

    when(imageEmbeds.insertEmbedForUrlAt(any(), any(), anyString(), anyInt())).thenReturn(false);
    when(linkPreviews.insertPreviewForUrlAt(any(), any(), anyString(), anyInt())).thenReturn(true);

    assertTrue(store.insertManualPreviewAt(ref, 0, "https://example.com/x"));
    verify(imageEmbeds).insertEmbedForUrlAt(any(), any(), anyString(), anyInt());
    verify(linkPreviews).insertPreviewForUrlAt(any(), any(), anyString(), anyInt());
  }

  @Test
  void appendPendingOutgoingChatSkipsSpinnerWhenDeliveryIndicatorsAreDisabled() {
    ChatTranscriptStore store = newStoreWithTranscriptCapAndDeliveryIndicators(0, false);
    TargetRef ref = new TargetRef("srv", "#chan");

    store.appendPendingOutgoingChat(ref, "pending-1", "me", "hello", 10_000L);

    StyledDocument doc = store.document(ref);
    assertTrue(transcriptTextUnchecked(doc).contains("hello"));
    assertEquals(0, inlineComponentCount(doc, OutgoingSendIndicator.PendingSpinner.class));
  }

  @Test
  void resolvePendingOutgoingChatSkipsConfirmedDotWhenDeliveryIndicatorsAreDisabled() {
    ChatTranscriptStore store = newStoreWithTranscriptCapAndDeliveryIndicators(0, false);
    TargetRef ref = new TargetRef("srv", "#chan");

    store.appendPendingOutgoingChat(ref, "pending-2", "me", "hello", 10_000L);
    boolean resolved =
        store.resolvePendingOutgoingChat(
            ref, "pending-2", "me", "hello", 10_100L, "msg-1", Map.of("msgid", "msg-1"));

    assertTrue(resolved);
    StyledDocument doc = store.document(ref);
    assertTrue(transcriptTextUnchecked(doc).contains("hello"));
    assertEquals(0, inlineComponentCount(doc, OutgoingSendIndicator.ConfirmedDot.class));
  }

  @Test
  void appendChatAtRendersMatrixDisplayNameInCompactModeAndPreservesRawMetaFrom() throws Exception {
    UserListStore userListStore = new UserListStore();
    TargetRef ref = new TargetRef("matrix", "#ircafe:matrix.example.org");
    userListStore.put(
        "matrix",
        "#ircafe:matrix.example.org",
        List.of(new NickInfo("@alice:matrix.example.org", "", "")));
    userListStore.updateRealNameAcrossChannels("matrix", "@alice:matrix.example.org", "Alice");

    ChatTranscriptStore store = newStoreWithTranscriptCapAndUserList(0, userListStore);
    store.appendChatAt(
        ref,
        "@alice:matrix.example.org",
        "hello matrix",
        false,
        11_000L,
        "m-1",
        Map.of("msgid", "m-1"));

    StyledDocument doc = store.document(ref);
    String text = transcriptText(doc);
    assertTrue(text.contains("Alice: hello matrix"));
    assertFalse(text.contains("@alice:matrix.example.org: hello matrix"));

    Element firstLine = doc.getDefaultRootElement().getElement(0);
    Object metaFrom =
        doc.getCharacterElement(firstLine.getStartOffset())
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_META_FROM);
    assertEquals("@alice:matrix.example.org", String.valueOf(metaFrom));
  }

  private static ChatTranscriptStore newStore() {
    ChatStyles styles = new ChatStyles(null);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    return new ChatTranscriptStore(
        styles, renderer, null, null, null, null, null, null, null, null);
  }

  private static ChatTranscriptStore newStoreWithTranscriptCap(int maxLines) {
    ChatStyles styles = new ChatStyles(null);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(settingsWithTranscriptCap(maxLines));
    return new ChatTranscriptStore(
        styles, renderer, null, null, null, null, null, settingsBus, null, null);
  }

  private static ChatTranscriptStore newStoreWithTranscriptCapAndDeliveryIndicators(
      int maxLines, boolean enabled) {
    ChatStyles styles = new ChatStyles(null);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(settingsWithTranscriptCap(maxLines, enabled));
    return new ChatTranscriptStore(
        styles, renderer, null, null, null, null, null, settingsBus, null, null);
  }

  private static ChatTranscriptStore newStoreWithTranscriptCapAndUserList(
      int maxLines, UserListStore userListStore) {
    ChatStyles styles = new ChatStyles(null);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(settingsWithTranscriptCap(maxLines));
    return new ChatTranscriptStore(
        styles, renderer, null, null, null, null, null, settingsBus, null, userListStore);
  }

  private static UiSettings settingsWithTranscriptCap(int maxLines) {
    return settingsWithTranscriptCap(maxLines, true);
  }

  private static UiSettings settingsWithTranscriptCap(
      int maxLines, boolean outgoingDeliveryIndicatorsEnabled) {
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
        true,
        false,
        0,
        0,
        true,
        true,
        false,
        true,
        true,
        true,
        true,
        "dots",
        true,
        true,
        true,
        true,
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
        outgoingDeliveryIndicatorsEnabled,
        true,
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
        1000,
        5,
        true,
        false,
        false,
        false,
        List.of(),
        null,
        null,
        false,
        "compact");
  }

  private static String transcriptText(StyledDocument doc) throws Exception {
    return doc.getText(0, doc.getLength());
  }

  private static MessageReactionsComponent reactionComponent(StyledDocument doc) {
    Element root = doc.getDefaultRootElement();
    if (root == null) return null;
    int len = doc.getLength();
    for (int i = 0; i < root.getElementCount(); i++) {
      Element line = root.getElement(i);
      if (line == null) continue;
      int start = Math.max(0, line.getStartOffset());
      if (start >= len) continue;
      Object comp = StyleConstants.getComponent(doc.getCharacterElement(start).getAttributes());
      if (comp instanceof MessageReactionsComponent reactions) {
        return reactions;
      }
    }
    return null;
  }

  private static int lineCount(StyledDocument doc) {
    try {
      String text = transcriptText(doc);
      return (int) text.chars().filter(ch -> ch == '\n').count();
    } catch (Exception ignored) {
      return 0;
    }
  }

  private static String transcriptTextUnchecked(StyledDocument doc) {
    try {
      return transcriptText(doc);
    } catch (Exception ignored) {
      return "";
    }
  }

  private static int inlineComponentCount(StyledDocument doc, Class<?> componentType) {
    if (doc == null || componentType == null) return 0;
    int count = 0;
    int len = doc.getLength();
    for (int i = 0; i < len; i++) {
      Object component = StyleConstants.getComponent(doc.getCharacterElement(i).getAttributes());
      if (component != null && componentType.isInstance(component)) {
        count++;
      }
    }
    return count;
  }
}
