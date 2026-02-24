package cafe.woden.ircclient.ui.settings;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.Locale;
import javax.swing.JComboBox;
import javax.swing.text.JTextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

@org.springframework.stereotype.Component
@Lazy
class ThemeTextComponentPaletteSyncService {

  private static final Logger log =
      LoggerFactory.getLogger(ThemeTextComponentPaletteSyncService.class);
  private static final String NIMBUS_LAF_CLASS = "javax.swing.plaf.nimbus.NimbusLookAndFeel";

  void syncAllWindows() {
    if (!isNimbusLookAndFeelActive()) return;

    Color fieldBg = firstUiColor("TextField.background", "TextComponent.background", "nimbusLightBackground");
    Color fieldFg = firstUiColor("TextField.foreground", "Label.foreground", "textText");
    Color areaBg = firstUiColor("TextPane.background", "TextArea.background", "TextComponent.background");
    Color areaFg = firstUiColor("TextPane.foreground", "TextArea.foreground", "Label.foreground");
    Color selectionBg =
        firstUiColor(
            "TextComponent.selectionBackground",
            "TextField.selectionBackground",
            "TextPane.selectionBackground");
    Color selectionFg =
        firstUiColor(
            "TextComponent.selectionForeground",
            "TextField.selectionForeground",
            "TextPane.selectionForeground");

    int updated = 0;
    for (Window window : Window.getWindows()) {
      updated += syncComponentTree(window, fieldBg, fieldFg, areaBg, areaFg, selectionBg, selectionFg);
    }

    if (ThemeLookAndFeelUtils.isNimbusDebugEnabled()) {
      String message = String.format(
          Locale.ROOT,
          "[ircafe][nimbus] text-component palette sync touched %d components (laf=%s fieldBg=%s areaBg=%s fieldFg=%s areaFg=%s selBg=%s selFg=%s)",
          updated,
          ThemeLookAndFeelUtils.currentLookAndFeelClassName(),
          toHexOrNull(fieldBg),
          toHexOrNull(areaBg),
          toHexOrNull(fieldFg),
          toHexOrNull(areaFg),
          toHexOrNull(selectionBg),
          toHexOrNull(selectionFg));
      log.warn(message);
      System.err.println(message);
    }
  }

  private static int syncComponentTree(
      Component component,
      Color fieldBg,
      Color fieldFg,
      Color areaBg,
      Color areaFg,
      Color selectionBg,
      Color selectionFg) {
    if (component == null) return 0;

    int updated = 0;
    if (component instanceof javax.swing.JTextField field) {
      applyPalette(field, fieldBg, fieldFg, selectionBg, selectionFg);
      updated++;
    } else if (component instanceof javax.swing.JTextArea area) {
      applyPalette(area, areaBg, areaFg, selectionBg, selectionFg);
      updated++;
    } else if (component instanceof javax.swing.JTextPane pane) {
      applyPalette(pane, areaBg, areaFg, selectionBg, selectionFg);
      updated++;
    } else if (component instanceof javax.swing.JEditorPane editor) {
      applyPalette(editor, areaBg, areaFg, selectionBg, selectionFg);
      updated++;
    } else if (component instanceof JComboBox<?> combo && combo.isEditable()) {
      javax.swing.ComboBoxEditor editor = combo.getEditor();
      if (editor != null) {
        Component editorComponent = editor.getEditorComponent();
        if (editorComponent instanceof javax.swing.JTextField field) {
          applyPalette(field, fieldBg, fieldFg, selectionBg, selectionFg);
          updated++;
        }
      }
    }

    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        updated +=
            syncComponentTree(child, fieldBg, fieldFg, areaBg, areaFg, selectionBg, selectionFg);
      }
    }
    return updated;
  }

  private static void applyPalette(
      JTextComponent c,
      Color bg,
      Color fg,
      Color selectionBg,
      Color selectionFg) {
    if (bg != null) c.setBackground(bg);
    if (fg != null) {
      c.setForeground(fg);
      c.setCaretColor(fg);
    }
    if (selectionBg != null) c.setSelectionColor(selectionBg);
    if (selectionFg != null) c.setSelectedTextColor(selectionFg);
    c.setOpaque(true);
  }

  private static Color firstUiColor(String... keys) {
    if (keys == null) return null;
    for (String key : keys) {
      if (key == null || key.isBlank()) continue;
      Color c = javax.swing.UIManager.getColor(key);
      if (c != null) return c;
    }
    return null;
  }

  private static boolean isNimbusLookAndFeelActive() {
    return NIMBUS_LAF_CLASS.equals(ThemeLookAndFeelUtils.currentLookAndFeelClassName());
  }

  private static String toHexOrNull(Color c) {
    if (c == null) return "null";
    return String.format(
        Locale.ROOT,
        "#%02X%02X%02X(%d,%d,%d)",
        c.getRed(), c.getGreen(), c.getBlue(), c.getRed(), c.getGreen(), c.getBlue());
  }
}
