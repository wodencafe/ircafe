package cafe.woden.ircclient.ui.settings.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.SingleLineEmojiTextPane;
import java.awt.Color;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ThemeTextComponentPaletteSyncServiceTest {
  @Test
  void paletteRefreshPreservesComposeAndLabelTransparency() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          JPanel panel = new JPanel();
          SingleLineEmojiTextPane input = new SingleLineEmojiTextPane();
          input.setOpaque(false);
          Color inputBackground = input.getBackground();
          JTextPane label = new JTextPane();
          label.setEditable(false);
          label.setFocusable(false);
          label.setOpaque(false);
          JTextPane editor = new JTextPane();
          JTextField field = new JTextField();
          panel.add(input);
          panel.add(label);
          panel.add(editor);
          panel.add(field);

          int updated =
              ThemeTextComponentPaletteSyncService.syncComponentTree(
                  panel,
                  Color.ORANGE,
                  Color.WHITE,
                  Color.GRAY,
                  Color.CYAN,
                  Color.BLUE,
                  Color.YELLOW);

          assertEquals(4, updated);
          assertFalse(input.isOpaque(), "compose shell must remain visible");
          assertEquals(inputBackground, input.getBackground());
          assertEquals(Color.CYAN, input.getForeground());
          assertEquals(Color.CYAN, input.getCaretColor());
          assertEquals(Color.BLUE, input.getSelectionColor());
          assertEquals(Color.YELLOW, input.getSelectedTextColor());
          assertFalse(label.isOpaque(), "wrapping labels must remain transparent");
          assertTrue(editor.isOpaque());
          assertEquals(Color.GRAY, editor.getBackground());
          assertTrue(field.isOpaque());
          assertEquals(Color.ORANGE, field.getBackground());
        });
  }
}
