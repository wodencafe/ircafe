package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.util.UiColorKeys;
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
    UiColorKeys.TEXT_FIELD_BACKGROUND,
    UiColorKeys.TEXT_PANE_BACKGROUND,
    UiColorKeys.PANEL_BACKGROUND,
    UiColorKeys.CONTROL,
    UiColorKeys.LABEL_FOREGROUND,
    UiColorKeys.TEXT_FIELD_FOREGROUND
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
    UIManager.put(UiColorKeys.TEXT_FIELD_BACKGROUND, new Color(0xF9, 0xFA, 0xFC));
    UIManager.put(UiColorKeys.LABEL_FOREGROUND, new Color(0x1F, 0x26, 0x2F));

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
    UIManager.put(UiColorKeys.TEXT_FIELD_BACKGROUND, new Color(0x19, 0x1F, 0x28));
    UIManager.put(UiColorKeys.LABEL_FOREGROUND, new Color(0x2A, 0x31, 0x3A));

    TypingSignalIndicator indicator = createOnEdt();
    onEdt(
        () -> {
          indicator.setAvailable(false);
          assertEquals(0xFFFFFF, rgbHex(indicator.debugArrowColorForTest()));
          assertEquals(0f, indicator.debugArrowGlowForTest());
        });
  }

  @Test
  void pendingTransitionFadesFromGreenToVioletThenBlueWithoutJumping() throws Exception {
    ManualClock clock = new ManualClock();
    TypingSignalIndicator indicator = createOnEdt(clock);
    onEdt(
        () -> {
          indicator.setAvailable(true);
          indicator.pulse("pending");
          assertEquals(0x35C86E, rgbHex(indicator.debugArrowColorForTest()));
          clock.advance(280);
          Color violet = indicator.debugArrowColorForTest();
          float glow = indicator.debugArrowGlowForTest();
          assertTrue(violet.getBlue() > violet.getRed());
          assertTrue(violet.getRed() > violet.getGreen());
          assertTrue(glow > 0.2f);
          indicator.pulse("pending");
          assertEquals(violet, indicator.debugArrowColorForTest());
          indicator.pulse("active");
          assertEquals(violet, indicator.debugArrowColorForTest());
          assertEquals(glow, indicator.debugArrowGlowForTest());
          clock.advance(280);
          Color blue = indicator.debugArrowColorForTest();
          assertTrue(blue.getBlue() > blue.getGreen());
          assertTrue(blue.getGreen() > blue.getRed());
          assertTrue(indicator.debugArrowGlowForTest() > 0.2f);
        });
  }

  @Test
  void activeTransitionFadesFromGreenToGlowingBlue() throws Exception {
    ManualClock clock = new ManualClock();
    TypingSignalIndicator indicator = createOnEdt(clock);
    onEdt(
        () -> {
          indicator.setAvailable(true);
          indicator.pulse("active");
          assertEquals(0x35C86E, rgbHex(indicator.debugArrowColorForTest()));
        });
    clock.advance(120);
    onEdt(() -> assertNotEquals(0x35C86E, rgbHex(indicator.debugArrowColorForTest())));
    clock.advance(160);
    onEdt(
        () -> {
          Color blue = indicator.debugArrowColorForTest();
          assertTrue(blue.getBlue() > blue.getGreen());
          assertTrue(indicator.debugArrowGlowForTest() > 0.2f);
          indicator.pulse("active");
          assertEquals(blue, indicator.debugArrowColorForTest());
        });
  }

  @Test
  void pausedAcknowledgementPulsesGreenOnceAndSettlesToIdle() throws Exception {
    ManualClock clock = new ManualClock();
    TypingSignalIndicator indicator = createOnEdt(clock);
    onEdt(
        () -> {
          indicator.setAvailable(true);
          float idleGlow = indicator.debugArrowGlowForTest();
          indicator.pulsePausedSent();
          assertEquals(idleGlow, indicator.debugArrowGlowForTest());
          clock.advance(250);
          assertEquals(0x35C86E, rgbHex(indicator.debugArrowColorForTest()));
          assertTrue(indicator.debugArrowGlowForTest() > 0.5f);
          clock.advance(250);
          assertEquals(0x35C86E, rgbHex(indicator.debugArrowColorForTest()));
          assertEquals(idleGlow, indicator.debugArrowGlowForTest());
          clock.advance(1000);
          assertEquals(idleGlow, indicator.debugArrowGlowForTest());
        });
  }

  @Test
  void pausedAcknowledgementAndResumptionPreserveCurrentColorAndGlow() throws Exception {
    ManualClock clock = new ManualClock();
    TypingSignalIndicator indicator = createOnEdt(clock);
    onEdt(
        () -> {
          indicator.setAvailable(true);
          indicator.pulse("active");
          clock.advance(280);
          Color blue = indicator.debugArrowColorForTest();
          float blueGlow = indicator.debugArrowGlowForTest();
          indicator.pulsePausedSent();
          assertEquals(blue, indicator.debugArrowColorForTest());
          assertEquals(blueGlow, indicator.debugArrowGlowForTest());
          clock.advance(250);
          Color green = indicator.debugArrowColorForTest();
          float greenGlow = indicator.debugArrowGlowForTest();
          assertEquals(0x35C86E, rgbHex(green));
          indicator.pulsePausedSent();
          assertEquals(greenGlow, indicator.debugArrowGlowForTest());
          indicator.pulse("pending");
          assertEquals(green, indicator.debugArrowColorForTest());
          assertEquals(greenGlow, indicator.debugArrowGlowForTest());
          clock.advance(280);
          Color violet = indicator.debugArrowColorForTest();
          assertTrue(violet.getBlue() > violet.getRed());
          assertTrue(violet.getRed() > violet.getGreen());
        });
  }

  @Test
  void disablingAvailabilityCancelsPausedPulse() throws Exception {
    ManualClock clock = new ManualClock();
    TypingSignalIndicator indicator = createOnEdt(clock);
    onEdt(
        () -> {
          indicator.setAvailable(true);
          indicator.pulsePausedSent();
          clock.advance(250);
          indicator.setAvailable(false);
          indicator.pulsePausedSent();
          assertEquals(0f, indicator.debugArrowGlowForTest());
          indicator.setAvailable(true);
          assertEquals(0x35C86E, rgbHex(indicator.debugArrowColorForTest()));
          assertEquals(0.12f, indicator.debugArrowGlowForTest());
        });
  }

  @Test
  void pausedTransitionFadesBackToGreen() throws Exception {
    ManualClock clock = new ManualClock();
    TypingSignalIndicator indicator = createOnEdt(clock);
    onEdt(
        () -> {
          indicator.setAvailable(true);
          indicator.pulse("active");
          clock.advance(280);
          indicator.pulse("paused");
          assertNotEquals(0x35C86E, rgbHex(indicator.debugArrowColorForTest()));
        });

    clock.advance(500);
    onEdt(() -> assertEquals(0x35C86E, rgbHex(indicator.debugArrowColorForTest())));
  }

  @Test
  void doneTransitionFadesBackToGreen() throws Exception {
    ManualClock clock = new ManualClock();
    TypingSignalIndicator indicator = createOnEdt(clock);
    onEdt(
        () -> {
          indicator.setAvailable(true);
          indicator.pulse("active");
          clock.advance(280);
        });
    onEdt(
        () -> {
          indicator.pulse("done");
          assertNotEquals(0x35C86E, rgbHex(indicator.debugArrowColorForTest()));
          assertTrue(indicator.debugArrowGlowForTest() >= 0f);
        });

    clock.advance(500);
    onEdt(() -> assertEquals(0x35C86E, rgbHex(indicator.debugArrowColorForTest())));
  }

  @Test
  void resumingDuringReturnFadePreservesCurrentColorAndGlow() throws Exception {
    ManualClock clock = new ManualClock();
    TypingSignalIndicator indicator = createOnEdt(clock);
    onEdt(
        () -> {
          indicator.setAvailable(true);
          indicator.pulse("active");
          clock.advance(280);
          indicator.pulse("paused");
          clock.advance(120);
          Color color = indicator.debugArrowColorForTest();
          float glow = indicator.debugArrowGlowForTest();
          indicator.pulse("active");
          assertEquals(color, indicator.debugArrowColorForTest());
          assertEquals(glow, indicator.debugArrowGlowForTest());
        });
  }

  private static TypingSignalIndicator createOnEdt()
      throws InvocationTargetException, InterruptedException {
    final TypingSignalIndicator[] out = new TypingSignalIndicator[1];
    onEdt(() -> out[0] = new TypingSignalIndicator());
    return out[0];
  }

  private static TypingSignalIndicator createOnEdt(ManualClock clock)
      throws InvocationTargetException, InterruptedException {
    final TypingSignalIndicator[] out = new TypingSignalIndicator[1];
    onEdt(() -> out[0] = new TypingSignalIndicator(clock::nowMs));
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

  private static final class ManualClock {
    private long nowMs;

    long nowMs() {
      return nowMs;
    }

    void advance(long millis) {
      nowMs += millis;
    }
  }
}
