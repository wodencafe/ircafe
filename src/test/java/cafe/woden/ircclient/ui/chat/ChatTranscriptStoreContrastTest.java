package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.settings.ChatThemeSettings;
import cafe.woden.ircclient.ui.settings.ChatThemeSettingsBus;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.UIManager;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock(value = "UIManager", mode = ResourceAccessMode.READ_WRITE)
class ChatTranscriptStoreContrastTest {

  private static final String[] SNAPSHOT_KEYS = {
    "TextPane.background",
    "TextPane.foreground",
    "Label.foreground",
    "Label.disabledForeground",
    "Component.linkColor",
    "TextPane.selectionBackground",
    "Component.warningColor",
    "Component.errorColor"
  };

  private Map<String, Object> snapshot;

  @BeforeEach
  void snapshotUiManager() {
    snapshot = new LinkedHashMap<>();
    for (String key : SNAPSHOT_KEYS) {
      snapshot.put(key, UIManager.get(key));
    }
  }

  @AfterEach
  void restoreUiManager() {
    if (snapshot == null) return;
    snapshot.forEach(UIManager::put);
  }

  @Test
  void outgoingOverrideColorIsKeptReadableOnAppendForLightThemes() throws Exception {
    Color bg = configureLightTranscriptPalette();
    Fixture fixture = newFixture(outgoingSettings(true, "#EAF2FF"));
    TargetRef ref = new TargetRef("srv", "#chan");

    fixture.store().appendChatAt(ref, "me", "append-outgoing", true, 1_000L);

    Color fg = fgAtToken(fixture.store().document(ref), "append-outgoing");
    assertNotNull(fg);
    assertTrue(relativeLuminance(fg) < relativeLuminance(bg));
    assertTrue(contrastRatio(fg, bg) >= 4.5);
  }

  @Test
  void outgoingOverrideColorIsKeptReadableOnRestyleForLightThemes() throws Exception {
    Color bg = configureLightTranscriptPalette();
    Fixture fixture = newFixture(outgoingSettings(false, "#EAF2FF"));
    TargetRef ref = new TargetRef("srv", "#chan");

    fixture.store().appendChatAt(ref, "me", "restyle-outgoing", true, 2_000L);
    fixture.settings().set(outgoingSettings(true, "#EAF2FF"));
    fixture.store().restyleAllDocuments();

    Color fg = fgAtToken(fixture.store().document(ref), "restyle-outgoing");
    assertNotNull(fg);
    assertTrue(relativeLuminance(fg) < relativeLuminance(bg));
    assertTrue(contrastRatio(fg, bg) >= 4.5);
  }

  @Test
  void messageTypeOverridesApplyWhenAppendingTranscriptLines() throws Exception {
    configureLightTranscriptPalette();
    ThemeFixture fixture =
        newThemeFixture(
            messageTypeOverrides("#102030", "#203040", "#304050", "#405060", "#506070"));
    TargetRef ref = new TargetRef("srv", "#chan");

    fixture.store().appendChatAt(ref, "alice", "chat-override", false, 1_000L);
    fixture.store().appendNoticeAt(ref, "(notice) server", "notice-override", 2_000L);
    fixture.store().appendActionAt(ref, "alice", "action-override", false, 3_000L);
    fixture.store().appendErrorAt(ref, "(error) server", "error-override", 4_000L);
    fixture.store().appendPresenceFromHistory(ref, "presence-override", 5_000L);

    StyledDocument doc = fixture.store().document(ref);
    assertEquals(new Color(0x10, 0x20, 0x30), fgAtToken(doc, "chat-override"));
    assertEquals(new Color(0x20, 0x30, 0x40), fgAtToken(doc, "notice-override"));
    assertEquals(new Color(0x30, 0x40, 0x50), fgAtToken(doc, "action-override"));
    assertEquals(new Color(0x40, 0x50, 0x60), fgAtToken(doc, "error-override"));
    assertEquals(new Color(0x50, 0x60, 0x70), fgAtToken(doc, "presence-override"));
  }

  @Test
  void messageTypeOverridesApplyWhenRestylingExistingTranscriptLines() throws Exception {
    configureLightTranscriptPalette();
    ThemeFixture fixture = newThemeFixture(messageTypeOverrides(null, null, null, null, null));
    TargetRef ref = new TargetRef("srv", "#chan");

    fixture.store().appendChatAt(ref, "alice", "chat-restyle", false, 11_000L);
    fixture.store().appendNoticeAt(ref, "(notice) server", "notice-restyle", 12_000L);
    fixture.store().appendActionAt(ref, "alice", "action-restyle", false, 13_000L);
    fixture.store().appendErrorAt(ref, "(error) server", "error-restyle", 14_000L);
    fixture.store().appendPresenceFromHistory(ref, "presence-restyle", 15_000L);

    StyledDocument doc = fixture.store().document(ref);
    Color originalMessageColor = fgAtToken(doc, "chat-restyle");

    fixture
        .chatThemeSettingsBus()
        .set(messageTypeOverrides("#112233", "#223344", "#334455", "#445566", "#556677"));
    fixture.styles().reload();
    fixture.store().restyleAllDocuments();

    assertNotEquals(originalMessageColor, fgAtToken(doc, "chat-restyle"));
    assertEquals(new Color(0x11, 0x22, 0x33), fgAtToken(doc, "chat-restyle"));
    assertEquals(new Color(0x22, 0x33, 0x44), fgAtToken(doc, "notice-restyle"));
    assertEquals(new Color(0x33, 0x44, 0x55), fgAtToken(doc, "action-restyle"));
    assertEquals(new Color(0x44, 0x55, 0x66), fgAtToken(doc, "error-restyle"));
    assertEquals(new Color(0x55, 0x66, 0x77), fgAtToken(doc, "presence-restyle"));
  }

  @Test
  void invalidMessageTypeOverrideFallsBackToDefaultTranscriptColor() throws Exception {
    configureLightTranscriptPalette();
    TargetRef ref = new TargetRef("srv", "#chan");

    ThemeFixture baseline = newThemeFixture(messageTypeOverrides(null, null, null, null, null));
    baseline.store().appendChatAt(ref, "alice", "chat-baseline", false, 21_000L);
    Color baselineMessageColor = fgAtToken(baseline.store().document(ref), "chat-baseline");

    ThemeFixture invalid =
        newThemeFixture(
            messageTypeOverrides("#GGHHII", "#ZZYYXX", "#nothex", "0xoops", "bad-color"));
    invalid.store().appendChatAt(ref, "alice", "chat-invalid", false, 22_000L);
    assertEquals(baselineMessageColor, fgAtToken(invalid.store().document(ref), "chat-invalid"));
  }

  private static Fixture newFixture(UiSettings initialSettings) {
    ChatStyles styles = new ChatStyles(null);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);

    AtomicReference<UiSettings> settings = new AtomicReference<>(initialSettings);
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenAnswer(invocation -> settings.get());

    ChatTranscriptStore store =
        new ChatTranscriptStore(styles, renderer, null, null, null, null, null, settingsBus, null);
    return new Fixture(store, settings);
  }

  private static ThemeFixture newThemeFixture(ChatThemeSettings initialSettings) {
    ChatThemeSettingsBus chatThemeSettingsBus = new ChatThemeSettingsBus(null);
    if (initialSettings != null) {
      chatThemeSettingsBus.set(initialSettings);
    }
    ChatStyles styles = new ChatStyles(chatThemeSettingsBus);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    ChatTranscriptStore store =
        new ChatTranscriptStore(styles, renderer, null, null, null, null, null, null, null);
    return new ThemeFixture(store, styles, chatThemeSettingsBus);
  }

  private static ChatThemeSettings messageTypeOverrides(
      String messageColor,
      String noticeColor,
      String actionColor,
      String errorColor,
      String presenceColor) {
    return new ChatThemeSettings(
        ChatThemeSettings.Preset.DEFAULT,
        null,
        null,
        null,
        35,
        messageColor,
        noticeColor,
        actionColor,
        errorColor,
        presenceColor);
  }

  private static UiSettings outgoingSettings(boolean enabled, String color) {
    return new UiSettings(
        "light",
        "Monospaced",
        12,
        true,
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
        "HH:mm:ss",
        true,
        100,
        200,
        enabled,
        color,
        true,
        7,
        6,
        30,
        5);
  }

  private static Color configureLightTranscriptPalette() {
    Color bg = new Color(0xFA, 0xFB, 0xFD);
    UIManager.put("TextPane.background", bg);
    UIManager.put("TextPane.foreground", new Color(0xEA, 0xED, 0xF2));
    UIManager.put("Label.foreground", new Color(0x26, 0x2D, 0x36));
    UIManager.put("Label.disabledForeground", new Color(0xD9, 0xDE, 0xE6));
    UIManager.put("Component.linkColor", new Color(0xBE, 0xCF, 0xF5));
    UIManager.put("TextPane.selectionBackground", new Color(0xD8, 0xE4, 0xFA));
    UIManager.put("Component.warningColor", new Color(0xF0, 0xB0, 0x00));
    UIManager.put("Component.errorColor", new Color(0xD0, 0x50, 0x50));
    return bg;
  }

  private static Color fgAtToken(StyledDocument doc, String token) throws Exception {
    String text = doc.getText(0, doc.getLength());
    int idx = text.indexOf(token);
    assertTrue(idx >= 0, "token not found in transcript");
    return StyleConstants.getForeground(doc.getCharacterElement(idx).getAttributes());
  }

  private static double contrastRatio(Color fg, Color bg) {
    if (fg == null || bg == null) return 0.0;

    double l1 = relativeLuminance(fg);
    double l2 = relativeLuminance(bg);
    if (l1 < l2) {
      double t = l1;
      l1 = l2;
      l2 = t;
    }
    return (l1 + 0.05) / (l2 + 0.05);
  }

  private static double relativeLuminance(Color c) {
    double r = srgbToLinear(c.getRed());
    double g = srgbToLinear(c.getGreen());
    double b = srgbToLinear(c.getBlue());
    return (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
  }

  private static double srgbToLinear(int channel) {
    double v = channel / 255.0;
    return (v <= 0.04045) ? (v / 12.92) : Math.pow((v + 0.055) / 1.055, 2.4);
  }

  private record ThemeFixture(
      ChatTranscriptStore store, ChatStyles styles, ChatThemeSettingsBus chatThemeSettingsBus) {}

  private record Fixture(ChatTranscriptStore store, AtomicReference<UiSettings> settings) {}
}
