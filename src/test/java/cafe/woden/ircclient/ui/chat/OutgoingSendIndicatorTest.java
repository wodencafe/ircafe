package cafe.woden.ircclient.ui.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class OutgoingSendIndicatorTest {

  @Test
  void confirmedDotPreferredHeightTracksTextMetricsWithoutExtraLeading() throws Exception {
    onEdt(
        () -> {
          OutgoingSendIndicator.ConfirmedDot dot =
              new OutgoingSendIndicator.ConfirmedDot(new Color(0x2ecc71), 200, 900, null);
          dot.setFont(new Font("Dialog", Font.PLAIN, 13));
          FontMetrics fm = dot.getFontMetrics(dot.getFont());
          int expectedHeight = Math.max(10, Math.max(1, fm.getAscent() + fm.getDescent()));

          Dimension pref = dot.getPreferredSize();
          assertEquals(expectedHeight, pref.height);
          assertEquals(Math.min(pref.height - 1, fm.getAscent()), dot.getBaseline(pref.width, pref.height));
        });
  }

  @Test
  void pendingSpinnerPreferredHeightTracksTextMetricsWithoutExtraLeading() throws Exception {
    onEdt(
        () -> {
          OutgoingSendIndicator.PendingSpinner spinner =
              new OutgoingSendIndicator.PendingSpinner(new Color(0x4b8de8));
          spinner.setFont(new Font("Dialog", Font.PLAIN, 13));
          FontMetrics fm = spinner.getFontMetrics(spinner.getFont());
          int expectedHeight = Math.max(12, Math.max(1, fm.getAscent() + fm.getDescent()));

          Dimension pref = spinner.getPreferredSize();
          assertEquals(expectedHeight, pref.height);
          assertEquals(
              Math.min(pref.height - 1, fm.getAscent()), spinner.getBaseline(pref.width, pref.height));
        });
  }

  private static void onEdt(ThrowingRunnable r) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            r.run();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
