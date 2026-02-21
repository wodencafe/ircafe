package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MessageInputTypingSupportTest {

  @Test
  void showsMultipleActiveTypersInSingleBannerLine() throws Exception {
    Fixture f = newFixture();
    onEdt(
        () -> {
          f.support.showRemoteTypingIndicator("alice", "active");
          f.support.showRemoteTypingIndicator("bob", "active");

          assertTrue(f.banner.isVisible());
          assertEquals("alice and bob are typing", f.label.getText());
          assertTrue(f.dots.isVisible());
          assertTrue(f.dots.isAnimating());
        });
  }

  @Test
  void doneRemovesOnlyThatNickFromRemoteTypingBanner() throws Exception {
    Fixture f = newFixture();
    onEdt(
        () -> {
          f.support.showRemoteTypingIndicator("alice", "active");
          f.support.showRemoteTypingIndicator("bob", "active");
          f.support.showRemoteTypingIndicator("alice", "done");

          assertTrue(f.banner.isVisible());
          assertEquals("bob is typing", f.label.getText());
          assertTrue(f.dots.isVisible());
          assertTrue(f.dots.isAnimating());
        });
  }

  @Test
  void rendersActiveAndPausedGroupsTogether() throws Exception {
    Fixture f = newFixture();
    onEdt(
        () -> {
          f.support.showRemoteTypingIndicator("alice", "active");
          f.support.showRemoteTypingIndicator("bob", "paused");

          assertTrue(f.banner.isVisible());
          assertEquals("alice is typing | bob paused typing", f.label.getText());
          assertTrue(f.dots.isVisible());
          assertTrue(f.dots.isAnimating());
        });
  }

  @Test
  void pausedOnlyHidesAnimatedDots() throws Exception {
    Fixture f = newFixture();
    onEdt(
        () -> {
          f.support.showRemoteTypingIndicator("bob", "paused");

          assertTrue(f.banner.isVisible());
          assertEquals("bob paused typing", f.label.getText());
          assertFalse(f.dots.isVisible());
          assertFalse(f.dots.isAnimating());
        });
  }

  @Test
  void clearHidesBannerAndText() throws Exception {
    Fixture f = newFixture();
    onEdt(
        () -> {
          f.support.showRemoteTypingIndicator("alice", "active");
          f.support.clearRemoteTypingIndicator();

          assertFalse(f.banner.isVisible());
          assertEquals("", f.label.getText());
          assertFalse(f.dots.isVisible());
          assertFalse(f.dots.isAnimating());
        });
  }

  @Test
  void localTypingSignalTelemetryIsHiddenWhenUnavailable() throws Exception {
    Fixture f = newFixture();
    onEdt(
        () -> {
          f.support.setTypingSignalAvailable(false);
          f.support.onLocalTypingIndicatorSent("active");
          assertFalse(f.signal.isVisible());
          assertFalse(f.signal.isArrowVisible());

          f.support.setTypingSignalAvailable(true);
          f.support.onLocalTypingIndicatorSent("active");
          assertTrue(f.signal.isVisible());
          assertTrue(f.signal.isArrowVisible());
        });
  }

  private static Fixture newFixture() throws Exception {
    final Fixture[] out = new Fixture[1];
    onEdt(
        () -> {
          JTextField input = new JTextField();
          JPanel banner = new JPanel();
          JLabel label = new JLabel();
          TypingDotsIndicator dots = new TypingDotsIndicator();
          TypingSignalIndicator signal = new TypingSignalIndicator();
          MessageInputTypingSupport support =
              new MessageInputTypingSupport(input, banner, label, dots, signal, () -> null, new NoOpHooks());
          out[0] = new Fixture(support, banner, label, dots, signal);
        });
    return out[0];
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }

  private record Fixture(
      MessageInputTypingSupport support,
      JPanel banner,
      JLabel label,
      TypingDotsIndicator dots,
      TypingSignalIndicator signal) {}

  private static final class NoOpHooks implements MessageInputUiHooks {
    @Override
    public void updateHint() {}

    @Override
    public void markCompletionUiDirty() {}

    @Override
    public void runProgrammaticEdit(Runnable r) {}

    @Override
    public void focusInput() {}

    @Override
    public void flushTypingDone() {}

    @Override
    public void fireDraftChanged() {}

    @Override
    public void sendOutbound(String line) {}
  }
}
