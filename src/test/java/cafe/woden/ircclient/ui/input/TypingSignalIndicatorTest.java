package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock(value = "UIManager", mode = ResourceAccessMode.READ_WRITE)
class TypingSignalIndicatorTest {

  private static final String[] SNAPSHOT_KEYS = {
    "TextField.background",
    "TextPane.background",
    "Panel.background",
    "control",
    "Label.foreground",
    "TextField.foreground"
  };

  private Map<String, Object> uiSnapshot;

  @BeforeEach
  void snapshotUi() {
    uiSnapshot = new LinkedHashMap<>();
    for (String key : SNAPSHOT_KEYS) {
      uiSnapshot.put(key, UIManager.get(key));
    }
  }

  @AfterEach
  void restoreUi() {
    if (uiSnapshot == null) return;
    uiSnapshot.forEach(UIManager::put);
  }

  @Test
  void unavailableStateUsesDarkArrowOnLightTheme() throws Exception {
    UIManager.put("TextField.background", new Color(0xF9, 0xFA, 0xFC));
    UIManager.put("Label.foreground", new Color(0x1F, 0x26, 0x2F));

    TypingSignalIndicator indicator = createOnEdt();
    onEdt(
        () -> {
          indicator.setAvailable(false);
          assertEquals(0x1F262F, rgbHex(indicator.debugArrowColorForTest()));
          assertEquals(0f, indicator.debugArrowGlowForTest());
        });
  }

  @Test
  void unavailableStateUsesWhiteArrowOnDarkTheme() throws Exception {
    UIManager.put("TextField.background", new Color(0x19, 0x1F, 0x28));
    UIManager.put("Label.foreground", new Color(0x2A, 0x31, 0x3A));

    TypingSignalIndicator indicator = createOnEdt();
    onEdt(
        () -> {
          indicator.setAvailable(false);
          assertEquals(0xFFFFFF, rgbHex(indicator.debugArrowColorForTest()));
          assertEquals(0f, indicator.debugArrowGlowForTest());
        });
  }

  @Test
  void pausedTransitionFadesToGray() throws Exception {
    TypingSignalIndicator indicator = createOnEdt();
    onEdt(
        () -> {
          indicator.setAvailable(true);
          indicator.pulse("active");
          indicator.pulse("paused");
          assertNotEquals(0xC6CDD5, rgbHex(indicator.debugArrowColorForTest()));
        });

    Thread.sleep(280);
    onEdt(() -> assertEquals(0xC6CDD5, rgbHex(indicator.debugArrowColorForTest())));
  }

  @Test
  void doneTransitionFadesBackToGreen() throws Exception {
    TypingSignalIndicator indicator = createOnEdt();
    onEdt(
        () -> {
          indicator.setAvailable(true);
          indicator.pulse("active");
          indicator.pulse("paused");
        });
    Thread.sleep(280);

    onEdt(
        () -> {
          assertEquals(0xC6CDD5, rgbHex(indicator.debugArrowColorForTest()));
          indicator.pulse("done");
          assertNotEquals(0x35C86E, rgbHex(indicator.debugArrowColorForTest()));
          assertTrue(indicator.debugArrowGlowForTest() >= 0f);
        });

    Thread.sleep(500);
    onEdt(() -> assertEquals(0x35C86E, rgbHex(indicator.debugArrowColorForTest())));
  }

  private static TypingSignalIndicator createOnEdt()
      throws InvocationTargetException, InterruptedException {
    final TypingSignalIndicator[] out = new TypingSignalIndicator[1];
    onEdt(() -> out[0] = new TypingSignalIndicator());
    return out[0];
  }

  private static int rgbHex(java.awt.Color color) {
    return color == null ? 0 : (color.getRGB() & 0xFFFFFF);
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }
}
