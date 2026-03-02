package cafe.woden.ircclient.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;
import java.lang.reflect.Field;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class StatusBarTest {

  @Test
  void serverLabelShowsServerIdAndMovesHostPortToTooltip() throws Exception {
    StatusBar statusBar = onEdt(StatusBar::new);

    onEdtVoid(() -> statusBar.setServer("libera  (irc.libera.chat:6697)"));

    JLabel serverLabel = readLabel(statusBar, "serverLabel");
    assertEquals("libera", serverLabel.getText());
    assertEquals("irc.libera.chat:6697", serverLabel.getToolTipText());

    onEdtVoid(() -> statusBar.setServer(null));
    assertEquals("(disconnected)", serverLabel.getText());
    assertNull(serverLabel.getToolTipText());
  }

  @Test
  void lagIndicatorUsesSameForegroundAsStatusText() throws Exception {
    StatusBar statusBar = onEdt(StatusBar::new);

    JLabel serverLabel = readLabel(statusBar, "serverLabel");
    JLabel lagLabel = readLabel(statusBar, "lagLabel");

    Color statusTextColor = new Color(240, 240, 240);
    onEdtVoid(
        () -> {
          serverLabel.setForeground(statusTextColor);
          statusBar.setLagIndicatorReading(null, "Measuring lag...");
        });
    assertEquals(statusTextColor, lagLabel.getForeground());

    onEdtVoid(() -> statusBar.setLagIndicatorReading(1100L, "Lag measured"));
    assertEquals(statusTextColor, lagLabel.getForeground());
  }

  private static JLabel readLabel(StatusBar statusBar, String fieldName) throws Exception {
    Field field = StatusBar.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return (JLabel) field.get(statusBar);
  }

  private static <T> T onEdt(ThrowingSupplier<T> supplier) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return supplier.get();
    }
    final Object[] holder = new Object[1];
    final Exception[] error = new Exception[1];
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            holder[0] = supplier.get();
          } catch (Exception ex) {
            error[0] = ex;
          }
        });
    if (error[0] != null) throw error[0];
    @SuppressWarnings("unchecked")
    T value = (T) holder[0];
    return value;
  }

  private static void onEdtVoid(ThrowingRunnable runnable) throws Exception {
    onEdt(
        () -> {
          runnable.run();
          return null;
        });
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
