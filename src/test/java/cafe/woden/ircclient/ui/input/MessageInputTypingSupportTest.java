package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsTestFixtures;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MessageInputTypingSupportTest {

  @Test
  void userEditGlowsVioletUntilActiveSendCompletes() throws Exception {
    Fixture f = newFixture();
    onEdt(
        () -> {
          try {
            f.support.setTypingSignalAvailable(true);
            f.input.setText("draft");
            f.support.onUserEdit(false);
            f.clock.addAndGet(300);
            assertViolet(f);
            // Throttled edits must preserve the pending animation.
            f.input.setText("draft more");
            f.support.onUserEdit(false);
            assertViolet(f);
            f.support.onLocalTypingIndicatorSent("active");
            f.clock.addAndGet(300);
            var blue = f.signal.debugArrowColorForTest();
            assertTrue(blue.getBlue() > blue.getGreen());
            assertTrue(blue.getGreen() > blue.getRed());
          } finally {
            f.support.onRemoveNotify();
          }
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"submit", "clear", "switch", "draft", "remove", "fail", "slash"})
  void pendingSignalReturnsToGreenWhenCanceledOrFailed(String action) throws Exception {
    Fixture f = newFixture();
    onEdt(
        () -> {
          try {
            f.support.setTypingSignalAvailable(true);
            f.input.setText("draft");
            f.support.onUserEdit(false);
            f.clock.addAndGet(300);
            assertViolet(f);
            switch (action) {
              case "submit" -> f.support.onMessageSubmitted();
              case "clear" -> {
                f.input.setText("");
                f.support.onUserEdit(false);
              }
              case "switch" -> f.support.flushTypingForBufferSwitch();
              case "draft" -> f.support.onDraftTextSetProgrammatically();
              case "remove" -> f.support.onRemoveNotify();
              case "fail" -> f.support.onLocalTypingIndicatorFailed("active");
              case "slash" -> {
                f.input.setText("/whois alice");
                f.support.onUserEdit(false);
              }
              default -> throw new AssertionError(action);
            }
            if (!"fail".equals(action)) f.support.onLocalTypingIndicatorSent("active");
            f.clock.addAndGet(500);
            assertEquals(0x35C86E, rgbHex(f.signal.debugArrowColorForTest()));
          } finally {
            f.support.onRemoveNotify();
          }
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"send", "signal"})
  void disabledTypingSettingsSuppressPendingGlow(String disabledSetting) throws Exception {
    UiSettings settings = defaultSettings();
    UiSettings disabled =
        "send".equals(disabledSetting)
            ? settings.withTypingIndicatorsEnabled(false)
            : settings.withTypingIndicatorsSendSignalEnabled(false);
    Fixture f = newFixture(() -> disabled);
    onEdt(
        () -> {
          try {
            f.support.setTypingSignalAvailable(true);
            var fallback = f.signal.debugArrowColorForTest();
            f.input.setText("draft");
            f.support.onUserEdit(false);
            f.clock.addAndGet(300);
            assertEquals(fallback, f.signal.debugArrowColorForTest());
            assertEquals(0f, f.signal.debugArrowGlowForTest());
          } finally {
            f.support.onRemoveNotify();
          }
        });
  }

  @Test
  void availabilityDiscoveredDuringSendStartsPendingGlow() throws Exception {
    Fixture f = newFixture();
    onEdt(
        () -> {
          try {
            f.support.setOnTypingStateChanged(state -> f.support.setTypingSignalAvailable(true));
            f.input.setText("draft");
            f.support.onUserEdit(false);
            f.clock.addAndGet(300);
            assertViolet(f);
          } finally {
            f.support.onRemoveNotify();
          }
        });
  }

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
  void localTypingSignalTelemetryFallsBackToThemeAwareArrowWhenUnavailable() throws Exception {
    int fallbackArrow = unavailableFallbackArrowColorHex();
    Fixture f = newFixture();
    onEdt(
        () -> {
          f.support.setTypingSignalAvailable(false);
          f.support.onLocalTypingIndicatorSent("active");
          assertTrue(f.signal.isVisible());
          assertEquals(fallbackArrow, rgbHex(f.signal.debugArrowColorForTest()));
          assertEquals(0f, f.signal.debugArrowGlowForTest());
          assertTrue(f.signal.isArrowVisible());

          f.support.setTypingSignalAvailable(true);
          f.input.setText("hello");
          f.support.onUserEdit(false);
          f.support.onLocalTypingIndicatorSent("active");
          assertTrue(f.signal.isVisible());
          assertTrue(f.signal.isArrowVisible());
          assertNotEquals(fallbackArrow, rgbHex(f.signal.debugArrowColorForTest()));
          assertTrue(f.signal.debugArrowGlowForTest() > 0f);
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
  void transcriptDisplayToggleOffSuppressesIncomingTypingBanner() throws Exception {
    AtomicReference<UiSettings> settings =
        new AtomicReference<>(defaultSettings().withTypingIndicatorsTranscriptEnabled(false));
    Fixture f = newFixture(settings::get);
    onEdt(
        () -> {
          f.support.showRemoteTypingIndicator("alice", "active");
          assertFalse(f.banner.isVisible());
          assertEquals("", f.label.getText());
        });
  }

  @Test
  void sendSignalDisplayToggleOffUsesThemeAwareArrowFallback() throws Exception {
    int fallbackArrow = unavailableFallbackArrowColorHex();
    AtomicReference<UiSettings> settings =
        new AtomicReference<>(defaultSettings().withTypingIndicatorsSendSignalEnabled(false));
    Fixture f = newFixture(settings::get);
    onEdt(
        () -> {
          f.support.setTypingSignalAvailable(true);
          f.support.onLocalTypingIndicatorSent("active");
          assertTrue(f.signal.isVisible());
          assertEquals(fallbackArrow, rgbHex(f.signal.debugArrowColorForTest()));
          assertEquals(0f, f.signal.debugArrowGlowForTest());
          assertTrue(f.signal.isArrowVisible());
        });
  }

  @Test
  void sendToggleStillEmitsWhenReceiveToggleIsOff() throws Exception {
    AtomicReference<UiSettings> settings =
        new AtomicReference<>(
            defaultSettings()
                .withTypingIndicatorsEnabled(true)
                .withTypingIndicatorsReceiveEnabled(false));
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
  void messageSubmissionResetsTypingWithoutDoneAndNextDraftEmitsActive() throws Exception {
    Fixture f = newFixture();
    List<String> states = new ArrayList<>();
    onEdt(
        () -> {
          try {
            f.support.setOnTypingStateChanged(states::add);
            f.support.setTypingSignalAvailable(true);
            f.input.setText("hello");
            f.support.onUserEdit(false);
            f.support.onLocalTypingIndicatorSent("active");
            f.clock.addAndGet(300);
            assertNotEquals(0x35C86E, rgbHex(f.signal.debugArrowColorForTest()));
            f.support.onMessageSubmitted();
            f.support.onLocalTypingIndicatorSent("active");
            f.clock.addAndGet(500);
            assertEquals(0x35C86E, rgbHex(f.signal.debugArrowColorForTest()));
            f.input.setText("");
            f.support.onUserEdit(true);
            f.support.flushTypingDone();
            assertEquals(List.of("active"), states);

            f.input.setText("another message");
            f.support.onUserEdit(false);
            assertEquals(List.of("active", "active"), states);
          } finally {
            f.support.onRemoveNotify();
          }
        });
  }

  @Test
  void latePausedAcknowledgementDoesNotInterruptResumedTyping() throws Exception {
    Fixture f = newFixture();
    onEdt(
        () -> {
          try {
            f.support.setTypingSignalAvailable(true);
            f.input.setText("draft");
            f.support.onUserEdit(false);
            f.support.onLocalTypingIndicatorSent("active");
            f.clock.addAndGet(300);
            f.support.flushTypingForBufferSwitch();
            f.support.onUserEdit(false);
            f.support.onLocalTypingIndicatorSent("active");
            f.support.onLocalTypingIndicatorSent("paused");
            f.clock.addAndGet(500);
            assertTrue(f.signal.debugArrowColorForTest().getBlue() > 200);
          } finally {
            f.support.onRemoveNotify();
          }
        });
  }

  @Test
  void bufferSwitchReturnsArrowToGreenWithoutSendAcknowledgement() throws Exception {
    Fixture f = newFixture();
    onEdt(
        () -> {
          try {
            f.support.setTypingSignalAvailable(true);
            f.input.setText("unfinished draft");
            f.support.onUserEdit(false);
            f.support.onLocalTypingIndicatorSent("active");
            f.clock.addAndGet(300);
            assertNotEquals(0x35C86E, rgbHex(f.signal.debugArrowColorForTest()));
            f.support.flushTypingForBufferSwitch();
            f.clock.addAndGet(500);
            assertEquals(0x35C86E, rgbHex(f.signal.debugArrowColorForTest()));
          } finally {
            f.support.onRemoveNotify();
          }
        });
  }

  @Test
  void clearingDraftWithoutSendingEmitsDoneOnce() throws Exception {
    Fixture f = newFixture();
    List<String> states = new ArrayList<>();
    onEdt(
        () -> {
          try {
            f.support.setOnTypingStateChanged(states::add);
            f.input.setText("draft");
            f.support.onUserEdit(false);
            f.input.setText("");
            f.support.onUserEdit(false);
            f.support.flushTypingDone();
            assertEquals(List.of("active", "done"), states);
          } finally {
            f.support.onRemoveNotify();
          }
        });
  }

  @Test
  void meCommandDraftEmitsActiveTypingState() throws Exception {
    Fixture f = newFixture();
    List<String> states = new ArrayList<>();
    onEdt(
        () -> {
          f.support.setOnTypingStateChanged(states::add);
          f.input.setText("/me waves");
          f.support.onUserEdit(false);
          assertEquals("active", states.get(states.size() - 1));
        });
  }

  @Test
  void nonMeSlashCommandDraftEmitsDoneTypingState() throws Exception {
    Fixture f = newFixture();
    List<String> states = new ArrayList<>();
    onEdt(
        () -> {
          f.support.setOnTypingStateChanged(states::add);
          f.input.setText("hello");
          f.support.onUserEdit(false);
          f.input.setText("/whois alice");
          f.support.onUserEdit(false);
          assertEquals("done", states.get(states.size() - 1));
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

  @Test
  void bufferSwitchWithMeCommandDraftEmitsPaused() throws Exception {
    Fixture f = newFixture();
    List<String> states = new ArrayList<>();
    onEdt(
        () -> {
          f.support.setOnTypingStateChanged(states::add);
          f.input.setText("/me waves");
          f.support.onUserEdit(false);

          f.support.flushTypingForBufferSwitch();
          assertEquals("paused", states.get(states.size() - 1));
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
          AtomicLong clock = new AtomicLong();
          TypingSignalIndicator signal = new TypingSignalIndicator(clock::get);
          MessageInputTypingSupport support =
              new MessageInputTypingSupport(
                  input,
                  banner,
                  label,
                  dots,
                  signal,
                  settingsSupplier != null ? settingsSupplier : () -> null,
                  new NoOpHooks());
          out[0] = new Fixture(support, input, banner, label, dots, signal, clock);
        });
    return out[0];
  }

  private static UiSettings defaultSettings() {
    return UiSettingsTestFixtures.legacyBuilder()
        .imageEmbedsEnabled(false)
        .linkPreviewsEnabled(false)
        .build();
  }

  private static void onEdt(Runnable r) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    SwingUtilities.invokeAndWait(r);
  }

  private static int rgbHex(java.awt.Color color) {
    return color == null ? 0 : (color.getRGB() & 0xFFFFFF);
  }

  private static void assertViolet(Fixture f) {
    var violet = f.signal.debugArrowColorForTest();
    assertTrue(violet.getBlue() > violet.getRed());
    assertTrue(violet.getRed() > violet.getGreen());
    assertTrue(f.signal.debugArrowGlowForTest() > 0.2f);
  }

  private static int unavailableFallbackArrowColorHex()
      throws InvocationTargetException, InterruptedException {
    final int[] out = new int[1];
    onEdt(
        () -> {
          TypingSignalIndicator indicator = new TypingSignalIndicator();
          indicator.setAvailable(false);
          out[0] = rgbHex(indicator.debugArrowColorForTest());
        });
    return out[0];
  }

  private record Fixture(
      MessageInputTypingSupport support,
      JTextField input,
      JPanel banner,
      JLabel label,
      TypingDotsIndicator dots,
      TypingSignalIndicator signal,
      AtomicLong clock) {}

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
