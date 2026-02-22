package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.settings.UiSettings;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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

  @Test
  void receiveToggleOffSuppressesIncomingTypingBanner() throws Exception {
    AtomicReference<UiSettings> settings =
        new AtomicReference<>(defaultSettings().withTypingIndicatorsReceiveEnabled(false));
    Fixture f = newFixture(settings::get);
    onEdt(
        () -> {
          f.support.showRemoteTypingIndicator("alice", "active");
          assertFalse(f.banner.isVisible());
          assertEquals("", f.label.getText());
        });
  }

  @Test
  void sendToggleStillEmitsWhenReceiveToggleIsOff() throws Exception {
    AtomicReference<UiSettings> settings =
        new AtomicReference<>(
            defaultSettings().withTypingIndicatorsEnabled(true).withTypingIndicatorsReceiveEnabled(false));
    Fixture f = newFixture(settings::get);
    List<String> states = new ArrayList<>();
    onEdt(
        () -> {
          f.support.setOnTypingStateChanged(states::add);
          f.input.setText("hello");
          f.support.onUserEdit(false);
          assertTrue(states.contains("active"));
          assertFalse(f.banner.isVisible());
        });
  }

  @Test
  void bufferSwitchWithNonEmptyDraftEmitsPaused() throws Exception {
    Fixture f = newFixture();
    List<String> states = new ArrayList<>();
    onEdt(
        () -> {
          f.support.setOnTypingStateChanged(states::add);
          f.input.setText("still drafting");
          f.support.onUserEdit(false);

          f.support.flushTypingForBufferSwitch();
          assertEquals("paused", states.get(states.size() - 1));
        });
  }

  @Test
  void bufferSwitchWithBlankDraftEmitsDone() throws Exception {
    Fixture f = newFixture();
    List<String> states = new ArrayList<>();
    onEdt(
        () -> {
          f.support.setOnTypingStateChanged(states::add);
          f.input.setText("hello");
          f.support.onUserEdit(false);

          f.input.setText("");
          f.support.flushTypingForBufferSwitch();
          assertEquals("done", states.get(states.size() - 1));
        });
  }

  @Test
  void bufferSwitchWithSlashCommandDraftEmitsDone() throws Exception {
    Fixture f = newFixture();
    List<String> states = new ArrayList<>();
    onEdt(
        () -> {
          f.support.setOnTypingStateChanged(states::add);
          f.input.setText("hello");
          f.support.onUserEdit(false);

          f.input.setText("/whois alice");
          f.support.flushTypingForBufferSwitch();
          assertEquals("done", states.get(states.size() - 1));
        });
  }

  private static Fixture newFixture() throws Exception {
    return newFixture(() -> null);
  }

  private static Fixture newFixture(java.util.function.Supplier<UiSettings> settingsSupplier)
      throws Exception {
    final Fixture[] out = new Fixture[1];
    onEdt(
        () -> {
          JTextField input = new JTextField();
          JPanel banner = new JPanel();
          banner.setVisible(false);
          JLabel label = new JLabel();
          TypingDotsIndicator dots = new TypingDotsIndicator();
          TypingSignalIndicator signal = new TypingSignalIndicator();
          MessageInputTypingSupport support =
              new MessageInputTypingSupport(
                  input,
                  banner,
                  label,
                  dots,
                  signal,
                  settingsSupplier != null ? settingsSupplier : () -> null,
                  new NoOpHooks());
          out[0] = new Fixture(support, input, banner, label, dots, signal);
        });
    return out[0];
  }

  private static UiSettings defaultSettings() {
    return new UiSettings(
        "darcula",
        "Monospaced",
        12,
        true,
        false,
        false,
        0,
        0,
        true,
        false,
        false,
        true,
        true,
        true,
        "HH:mm:ss",
        true,
        100,
        200,
        true,
        "#6AA2FF",
        true,
        7,
        6,
        30,
        5);
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
      JTextField input,
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
