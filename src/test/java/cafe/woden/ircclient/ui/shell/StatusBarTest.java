package cafe.woden.ircclient.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock(value = "UIManager", mode = ResourceAccessMode.READ_WRITE)
class StatusBarTest {
  private static final String[] UI_SNAPSHOT_KEYS = {
    "Panel.background", "Label.foreground", "Component.linkColor", "@accentColor"
  };

  private Map<String, Object> uiSnapshot;

  @BeforeEach
  void snapshotUiManager() {
    uiSnapshot = new LinkedHashMap<>();
    for (String key : UI_SNAPSHOT_KEYS) {
      uiSnapshot.put(key, UIManager.get(key));
    }
  }

  @AfterEach
  void restoreUiManager() {
    if (uiSnapshot == null) return;
    uiSnapshot.forEach(UIManager::put);
  }

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
  void noticeForegroundKeepsReadableContrastAgainstDarkBackground() throws Exception {
    Color bg = new Color(0x24, 0x27, 0x2C);
    Color nearBg = new Color(0x31, 0x35, 0x3B);
    UIManager.put("Panel.background", bg);
    UIManager.put("Label.foreground", nearBg);
    UIManager.put("Component.linkColor", nearBg);
    UIManager.put("@accentColor", nearBg);

    StatusBar statusBar = onEdt(StatusBar::new);
    JLabel noticeLabel = readLabel(statusBar, "noticeLabel");

    onEdtVoid(
        () -> {
          statusBar.setBackground(bg);
          statusBar.enqueueNotification("Highlight notice", null);
        });

    Color fg = noticeLabel.getForeground();
    assertTrue(relativeLuminance(fg) > relativeLuminance(bg));
    assertTrue(contrastRatio(fg, bg) >= 4.5);
  }

  @Test
  void noticeForegroundRefreshesWhenThemeColorsChange() throws Exception {
    Color lightBg = new Color(0xF7, 0xF8, 0xFA);
    Color darkBg = new Color(0x22, 0x25, 0x2A);
    Color lightAccent = new Color(0x1D, 0x5C, 0xB0);
    Color darkAccent = new Color(0x34, 0x38, 0x3F);

    UIManager.put("Panel.background", lightBg);
    UIManager.put("Label.foreground", new Color(0x22, 0x26, 0x2C));
    UIManager.put("Component.linkColor", lightAccent);
    UIManager.put("@accentColor", lightAccent);

    StatusBar statusBar = onEdt(StatusBar::new);
    JLabel noticeLabel = readLabel(statusBar, "noticeLabel");

    onEdtVoid(
        () -> {
          statusBar.setBackground(lightBg);
          statusBar.enqueueNotification("Theme refresh notice", null);
        });
    Color before = noticeLabel.getForeground();

    onEdtVoid(
        () -> {
          UIManager.put("Panel.background", darkBg);
          UIManager.put("Label.foreground", darkAccent);
          UIManager.put("Component.linkColor", darkAccent);
          UIManager.put("@accentColor", darkAccent);
          statusBar.setBackground(darkBg);
          invokePrivate(statusBar, "refreshThemeAwareColors");
        });

    Color after = noticeLabel.getForeground();
    assertNotEquals(before, after);
    assertTrue(relativeLuminance(after) > relativeLuminance(darkBg));
    assertTrue(contrastRatio(after, darkBg) >= 4.5);
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
