package cafe.woden.ircclient.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.JLabel;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
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

  @Test
  void updateNotifierTooltipAlertUsesExtendedAutoHideDelay() throws Exception {
    StatusBar statusBar = onEdt(StatusBar::new);

    onEdtVoid(() -> invokePrivate(statusBar, "ensureUpdateNotifierTooltipHideTimerOnEdt"));
    Timer timer = readField(statusBar, "updateNotifierTooltipHideTimer", Timer.class);
    assertEquals(12_000, timer.getDelay());
    assertEquals(12_000, timer.getInitialDelay());
    assertFalse(timer.isRepeats());
  }

  @Test
  void updateNotifierTooltipAlertCanBeDismissedByClick() throws Exception {
    StatusBar statusBar = onEdt(StatusBar::new);
    TestPopup popup = new TestPopup();

    onEdtVoid(() -> writeField(statusBar, "updateNotifierTooltipPopup", popup));

    JToolTip tooltip =
        onEdt(
            () ->
                invokePrivate(
                    statusBar, "createUpdateNotifierTooltipAlertOnEdt", "Update is available"));
    assertTrue(tooltip.getMouseListeners().length > 0);

    MouseEvent click =
        new MouseEvent(
            tooltip, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 1, 1, 1, false);
    onEdtVoid(() -> tooltip.dispatchEvent(click));

    assertTrue(popup.hidden);
    assertNull(readField(statusBar, "updateNotifierTooltipPopup", Popup.class));
  }

  private static JLabel readLabel(StatusBar statusBar, String fieldName) throws Exception {
    return readField(statusBar, fieldName, JLabel.class);
  }

  private static <T> T readField(StatusBar statusBar, String fieldName, Class<T> type)
      throws Exception {
    Field field = StatusBar.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return type.cast(field.get(statusBar));
  }

  private static void writeField(StatusBar statusBar, String fieldName, Object value)
      throws Exception {
    Field field = StatusBar.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(statusBar, value);
  }

  private static void invokePrivate(StatusBar statusBar, String methodName) throws Exception {
    Method method = StatusBar.class.getDeclaredMethod(methodName);
    method.setAccessible(true);
    method.invoke(statusBar);
  }

  private static JToolTip invokePrivate(StatusBar statusBar, String methodName, String arg)
      throws Exception {
    Method method = StatusBar.class.getDeclaredMethod(methodName, String.class);
    method.setAccessible(true);
    return (JToolTip) method.invoke(statusBar, arg);
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

  private static final class TestPopup extends Popup {
    private boolean hidden;

    @Override
    public void show() {}

    @Override
    public void hide() {
      hidden = true;
    }
  }
}
