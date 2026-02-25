package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.settings.ChatThemeSettings;
import cafe.woden.ircclient.ui.settings.ChatThemeSettingsBus;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.UIManager;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.Test;

class ChatTranscriptMessageColorsFunctionalTest {

  @Test
  void applyingAndClearingMessageColorOverridesRestylesExistingLines() throws Exception {
    Map<String, Object> snapshot = snapshotUi();
    configureUiForDeterministicContrast();

    ChatThemeSettingsBus bus = new ChatThemeSettingsBus(null);
    ChatStyles styles = new ChatStyles(bus);
    ChatRichTextRenderer renderer = new ChatRichTextRenderer(null, null, styles, null);
    ChatTranscriptStore store =
        new ChatTranscriptStore(styles, renderer, null, null, null, null, null, null, null);

    try {
      TargetRef ref = new TargetRef("srv", "#colors");
      store.appendChat(ref, "alice", "hello colors");
      store.appendStatus(ref, "(server)", "status line");
      store.appendError(ref, "(error)", "error line");

      StyledDocument doc = store.document(ref);
      String text = doc.getText(0, doc.getLength());
      int messagePos = text.indexOf("hello colors");
      int statusPos = text.indexOf("status line");
      int errorPos = text.indexOf("error line");

      Color baseMessage = fgAt(doc, messagePos);
      Color baseStatus = fgAt(doc, statusPos);
      Color baseError = fgAt(doc, errorPos);

      bus.set(
          new ChatThemeSettings(
              ChatThemeSettings.Preset.DEFAULT,
              null,
              "#223344",
              null,
              35,
              "#112233",
              null,
              null,
              "#556677",
              null));
      styles.reload();
      store.restyleAllDocuments();

      assertEquals(new Color(0x11, 0x22, 0x33), fgAt(doc, messagePos));
      assertEquals(new Color(0x22, 0x33, 0x44), fgAt(doc, statusPos));
      assertEquals(new Color(0x55, 0x66, 0x77), fgAt(doc, errorPos));

      bus.set(
          new ChatThemeSettings(
              ChatThemeSettings.Preset.DEFAULT,
              null,
              null,
              null,
              35,
              null,
              null,
              null,
              null,
              null));
      styles.reload();
      store.restyleAllDocuments();

      assertEquals(baseMessage, fgAt(doc, messagePos));
      assertEquals(baseStatus, fgAt(doc, statusPos));
      assertEquals(baseError, fgAt(doc, errorPos));
      assertNotEquals(new Color(0x11, 0x22, 0x33), fgAt(doc, messagePos));
    } finally {
      restoreUi(snapshot);
    }
  }

  private static Color fgAt(StyledDocument doc, int pos) {
    return StyleConstants.getForeground(doc.getCharacterElement(pos).getAttributes());
  }

  private static Map<String, Object> snapshotUi() {
    Map<String, Object> out = new LinkedHashMap<>();
    String[] keys = {
      "TextPane.background",
      "TextPane.foreground",
      "Label.foreground",
      "Label.disabledForeground",
      "Component.warningColor",
      "Component.errorColor"
    };
    for (String key : keys) {
      out.put(key, UIManager.get(key));
    }
    return out;
  }

  private static void restoreUi(Map<String, Object> snapshot) {
    if (snapshot == null) return;
    snapshot.forEach(UIManager::put);
  }

  private static void configureUiForDeterministicContrast() {
    UIManager.put("TextPane.background", Color.WHITE);
    UIManager.put("TextPane.foreground", Color.BLACK);
    UIManager.put("Label.foreground", Color.BLACK);
    UIManager.put("Label.disabledForeground", new Color(0x66, 0x66, 0x66));
    UIManager.put("Component.warningColor", new Color(0xAA, 0x77, 0x11));
    UIManager.put("Component.errorColor", new Color(0xAA, 0x22, 0x22));
  }
}
