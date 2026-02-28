package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TypingSignalIndicatorTest {

  @Test
  void unavailableStateShowsWhiteArrow() throws Exception {
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
