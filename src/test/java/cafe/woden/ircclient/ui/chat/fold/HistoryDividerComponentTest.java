package cafe.woden.ircclient.ui.chat.fold;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class HistoryDividerComponentTest {

  @Test
  void constructorIsSafeDuringUiInitialization() throws Exception {
    onEdt(() -> assertDoesNotThrow(() -> new HistoryDividerComponent("History")));
  }

  @Test
  void viewCopiesTrackChangesWhileMountedAndReleaseTheirSourceListeners() throws Exception {
    onEdt(
        () -> {
          HistoryDividerComponent source = new HistoryDividerComponent("History");
          HistoryDividerComponent main = source.createComponent();
          HistoryDividerComponent extra = source.createComponent();
          assertNotSame(main, extra);
          int baselineListeners = source.getPropertyChangeListeners().length;
          JPanel mounted = new JPanel();
          mounted.add(main);
          mounted.add(extra);
          mounted.addNotify();
          try {
            assertEquals(baselineListeners + 4, source.getPropertyChangeListeners().length);
            source.setText("History — Oct 6, 2026");
            source.setTranscriptFont(new Font(Font.DIALOG, Font.PLAIN, 18));
            assertEquals(source.getText(), main.getText());
            assertEquals(source.getText(), extra.getText());
            Font font = ((JLabel) main.getComponent(0)).getFont();
            assertEquals(17f, font.getSize2D());
            assertEquals(Font.ITALIC, font.getStyle());
            assertEquals(font, ((JLabel) extra.getComponent(0)).getFont());

            mounted.remove(extra);
            assertEquals(baselineListeners + 2, source.getPropertyChangeListeners().length);
            source.setText("History — Oct 7, 2026");
            assertEquals(source.getText(), main.getText());
            assertEquals("History — Oct 6, 2026", extra.getText());

            // Remounting refreshes changes that happened while the view was detached.
            mounted.add(extra);
            assertEquals(source.getText(), extra.getText());
            assertEquals(baselineListeners + 4, source.getPropertyChangeListeners().length);
          } finally {
            mounted.removeNotify();
          }
          assertEquals(baselineListeners, source.getPropertyChangeListeners().length);
        });
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}
