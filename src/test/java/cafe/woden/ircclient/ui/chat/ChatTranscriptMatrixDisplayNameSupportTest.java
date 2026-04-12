package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;

class ChatTranscriptMatrixDisplayNameSupportTest {

  @Test
  void normalizeDisplayModeMapsAliasesToCompactAndVerbose() {
    assertEquals(
        "compact",
        ChatTranscriptMatrixDisplayNameSupport.normalizeMatrixUserListNameDisplayMode(
            "display-name-only"));
    assertEquals(
        "verbose",
        ChatTranscriptMatrixDisplayNameSupport.normalizeMatrixUserListNameDisplayMode("full"));
    assertEquals(
        "compact",
        ChatTranscriptMatrixDisplayNameSupport.normalizeMatrixUserListNameDisplayMode("unknown"));
  }

  @Test
  void looksLikeMatrixUserIdRecognizesMatrixUserIdsOnly() {
    assertTrue(ChatTranscriptMatrixDisplayNameSupport.looksLikeMatrixUserId("@alice:matrix.org"));
    assertFalse(ChatTranscriptMatrixDisplayNameSupport.looksLikeMatrixUserId("alice"));
    assertFalse(ChatTranscriptMatrixDisplayNameSupport.looksLikeMatrixUserId("@alice"));
  }

  @Test
  void renderTranscriptFromUsesCompactAndVerboseModesWithLearnedRealNames() {
    UserListStore userListStore = new UserListStore();
    userListStore.updateRealNameAcrossChannels("matrix", "@alice:matrix.org", "Alice");
    TargetRef ref = new TargetRef("matrix", "#room:matrix.org");

    ChatTranscriptMatrixDisplayNameSupport.Context compactContext =
        new ChatTranscriptMatrixDisplayNameSupport.Context(null, userListStore, target -> null);
    assertEquals(
        "Alice",
        ChatTranscriptMatrixDisplayNameSupport.renderTranscriptFrom(
            compactContext, ref, "@alice:matrix.org"));

    UiSettings settings = mock(UiSettings.class);
    when(settings.matrixUserListNameDisplayMode()).thenReturn("verbose");
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(settings);
    ChatTranscriptMatrixDisplayNameSupport.Context verboseContext =
        new ChatTranscriptMatrixDisplayNameSupport.Context(
            settingsBus, userListStore, target -> null);

    assertEquals(
        "Alice (@alice:matrix.org)",
        ChatTranscriptMatrixDisplayNameSupport.renderTranscriptFrom(
            verboseContext, ref, "@alice:matrix.org"));
    assertEquals(
        "alice",
        ChatTranscriptMatrixDisplayNameSupport.renderTranscriptFrom(verboseContext, ref, "alice"));
  }

  @Test
  void refreshMatrixDisplayNamesRelabelsOnlyMatchingMatrixRuns() throws Exception {
    UserListStore userListStore = new UserListStore();
    userListStore.updateRealNameAcrossChannels("matrix", "@alice:matrix.org", "Alice");
    DefaultStyledDocument doc = new DefaultStyledDocument();
    TargetRef ref = new TargetRef("matrix", "#room:matrix.org");

    SimpleAttributeSet aliceFrom = new SimpleAttributeSet();
    aliceFrom.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_FROM);
    aliceFrom.addAttribute(ChatStyles.ATTR_META_FROM, "@alice:matrix.org");
    doc.insertString(0, "@alice:matrix.org: ", aliceFrom);
    doc.insertString(doc.getLength(), "hello\n", new SimpleAttributeSet());

    SimpleAttributeSet bobFrom = new SimpleAttributeSet();
    bobFrom.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_FROM);
    bobFrom.addAttribute(ChatStyles.ATTR_META_FROM, "@bob:matrix.org");
    doc.insertString(doc.getLength(), "@bob:matrix.org: ", bobFrom);
    doc.insertString(doc.getLength(), "hi\n", new SimpleAttributeSet());

    SimpleAttributeSet actionFrom = new SimpleAttributeSet();
    actionFrom.addAttribute(ChatStyles.ATTR_STYLE, ChatStyles.STYLE_ACTION_FROM);
    actionFrom.addAttribute(ChatStyles.ATTR_META_FROM, "@alice:matrix.org");
    doc.insertString(doc.getLength(), "@alice:matrix.org", actionFrom);
    doc.insertString(doc.getLength(), " waves", new SimpleAttributeSet());

    ChatTranscriptMatrixDisplayNameSupport.Context context =
        new ChatTranscriptMatrixDisplayNameSupport.Context(null, userListStore, target -> doc);

    int changed =
        ChatTranscriptMatrixDisplayNameSupport.refreshMatrixDisplayNames(
            context, ref, "@alice:matrix.org");

    assertEquals(2, changed);
    String text = doc.getText(0, doc.getLength());
    assertTrue(text.contains("Alice: hello"));
    assertTrue(text.contains("Alice waves"));
    assertTrue(text.contains("@bob:matrix.org: hi"));
    assertFalse(text.contains("@alice:matrix.org: hello"));
  }

  @Test
  void refreshMatrixDisplayNamesReturnsZeroForNonMatrixFilter() {
    ChatTranscriptMatrixDisplayNameSupport.Context context =
        new ChatTranscriptMatrixDisplayNameSupport.Context(
            null, new UserListStore(), target -> null);
    assertEquals(
        0,
        ChatTranscriptMatrixDisplayNameSupport.refreshMatrixDisplayNames(
            context, new TargetRef("matrix", "#room:matrix.org"), "alice"));
  }
}
