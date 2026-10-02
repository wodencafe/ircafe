package cafe.woden.ircclient.ui.settings.theme;

import cafe.woden.ircclient.ui.SingleLineEmojiTextPane;
import cafe.woden.ircclient.ui.util.UiColorKeys;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.Locale;
import javax.swing.JComboBox;
import javax.swing.text.JTextComponent;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

@org.springframework.stereotype.Component
@InterfaceLayer
@Lazy
class ThemeTextComponentPaletteSyncService {

  private static final Logger log =
      LoggerFactory.getLogger(ThemeTextComponentPaletteSyncService.class);
  private static final String NIMBUS_LAF_CLASS = "javax.swing.plaf.nimbus.NimbusLookAndFeel";

  void syncAllWindows() {
    if (!isNimbusLookAndFeelActive()) return;

    Color fieldBg =
        firstUiColor(
            UiColorKeys.TEXT_FIELD_BACKGROUND,
            UiColorKeys.TEXT_COMPONENT_BACKGROUND,
            UiColorKeys.NIMBUS_LIGHT_BACKGROUND);
    Color fieldFg =
        firstUiColor(
            UiColorKeys.TEXT_FIELD_FOREGROUND, UiColorKeys.LABEL_FOREGROUND, UiColorKeys.TEXT_TEXT);
    Color areaBg =
        firstUiColor(
            UiColorKeys.TEXT_PANE_BACKGROUND,
            UiColorKeys.TEXT_AREA_BACKGROUND,
            UiColorKeys.TEXT_COMPONENT_BACKGROUND);
    Color areaFg =
        firstUiColor(
            UiColorKeys.TEXT_PANE_FOREGROUND,
            UiColorKeys.TEXT_AREA_FOREGROUND,
            UiColorKeys.LABEL_FOREGROUND);
    Color selectionBg =
        firstUiColor(
            UiColorKeys.TEXT_COMPONENT_SELECTION_BACKGROUND,
            UiColorKeys.TEXT_FIELD_SELECTION_BACKGROUND,
            UiColorKeys.TEXT_PANE_SELECTION_BACKGROUND);
    Color selectionFg =
        firstUiColor(
            UiColorKeys.TEXT_COMPONENT_SELECTION_FOREGROUND,
            UiColorKeys.TEXT_FIELD_SELECTION_FOREGROUND,
            UiColorKeys.TEXT_PANE_SELECTION_FOREGROUND);

    int updated = 0;
    for (Window window : Window.getWindows()) {
      updated +=
          syncComponentTree(window, fieldBg, fieldFg, areaBg, areaFg, selectionBg, selectionFg);
    }

    if (ThemeLookAndFeelUtils.isNimbusDebugEnabled()) {
      String message =
          String.format(
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

  static int syncComponentTree(
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
      JTextComponent c, Color bg, Color fg, Color selectionBg, Color selectionFg) {
    // Wrapping preference labels and the compose editor let their parent paint the surface.
    // Preserve that intentional transparency across theme changes.
    boolean transparentSurface =
        !c.isOpaque()
            && (c instanceof SingleLineEmojiTextPane || (!c.isEditable() && !c.isFocusable()));
    if (bg != null && !transparentSurface) c.setBackground(bg);
    if (fg != null) {
      c.setForeground(fg);
      c.setCaretColor(fg);
    }
    if (selectionBg != null) c.setSelectionColor(selectionBg);
    if (selectionFg != null) c.setSelectedTextColor(selectionFg);
    if (!transparentSurface) c.setOpaque(true);
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
        c.getRed(),
        c.getGreen(),
        c.getBlue(),
        c.getRed(),
        c.getGreen(),
        c.getBlue());
  }
}
