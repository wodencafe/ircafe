package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import cafe.woden.ircclient.app.api.QuasselNetworkManagerAction;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.bus.ActiveInputRouter;
import cafe.woden.ircclient.ui.bus.OutboundLineBus;
import cafe.woden.ircclient.ui.bus.TargetActivationBus;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import cafe.woden.ircclient.ui.shell.StatusBar;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class SwingUiPortQuasselPromptsFunctionalTest {

  @Test
  void quasselCoreSetupPromptCollectsValuesFromDialog() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "dialog UI requires a display");

    SwingUiPort ui = newUi();
    QuasselCoreControlPort.QuasselCoreSetupPrompt prompt =
        new QuasselCoreControlPort.QuasselCoreSetupPrompt(
            "quassel",
            "setup required",
            List.of("SQLite"),
            List.of("Database"),
            Map.of("field", "value"));

    AtomicReference<Optional<QuasselCoreControlPort.QuasselCoreSetupRequest>> result =
        new AtomicReference<>(Optional.empty());
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread caller =
        new Thread(
            () -> {
              try {
                result.set(ui.promptQuasselCoreSetup("quassel", prompt));
              } catch (Throwable t) {
                error.set(t);
              }
            },
            "quassel-setup-dialog-caller");
    caller.setDaemon(true);
    caller.start();

    JDialog dialog = waitForDialog("Quassel Core Setup - quassel", Duration.ofSeconds(8));
    onEdt(
        () -> {
          JTextField adminUser =
              findComponentNextToLabel(dialog, "Admin user", JTextField.class, f -> true);
          JPasswordField adminPassword =
              findComponentNextToLabel(dialog, "Admin password", JPasswordField.class, f -> true);
          assertNotNull(adminUser, "admin user field should be visible");
          assertNotNull(adminPassword, "admin password field should be visible");
          adminUser.setText("admin");
          adminPassword.setText("secret");
          clickButton(dialog, "OK");
        });

    joinCaller(caller, error);

    Optional<QuasselCoreControlPort.QuasselCoreSetupRequest> request = result.get();
    assertTrue(request.isPresent(), "setup prompt should return request after confirmation");
    assertEquals("admin", request.orElseThrow().adminUser());
    assertEquals("secret", request.orElseThrow().adminPassword());
    assertEquals("SQLite", request.orElseThrow().storageBackend());
    assertEquals("Database", request.orElseThrow().authenticator());
  }

  @Test
  void quasselCoreSetupPromptCancelReturnsEmpty() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "dialog UI requires a display");

    SwingUiPort ui = newUi();
    QuasselCoreControlPort.QuasselCoreSetupPrompt prompt =
        new QuasselCoreControlPort.QuasselCoreSetupPrompt(
            "quassel", "setup required", List.of("SQLite"), List.of("Database"), Map.of());

    AtomicReference<Optional<QuasselCoreControlPort.QuasselCoreSetupRequest>> result =
        new AtomicReference<>(Optional.empty());
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread caller =
        new Thread(
            () -> {
              try {
                result.set(ui.promptQuasselCoreSetup("quassel", prompt));
              } catch (Throwable t) {
                error.set(t);
              }
            },
            "quassel-setup-dialog-cancel-caller");
    caller.setDaemon(true);
    caller.start();

    JDialog dialog = waitForDialog("Quassel Core Setup - quassel", Duration.ofSeconds(8));
    onEdt(() -> clickButton(dialog, "Cancel"));

    joinCaller(caller, error);
    assertTrue(result.get().isEmpty(), "cancel should return empty setup request");
  }

  @Test
  void quasselNetworkManagerPromptConnectReturnsSelectedNetworkToken() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "dialog UI requires a display");

    SwingUiPort ui = newUi();
    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks =
        List.of(
            new QuasselCoreControlPort.QuasselCoreNetworkSummary(
                2, "Libera", false, true, 4, "irc.libera.chat", 6697, true, Map.of()));

    AtomicReference<Optional<QuasselNetworkManagerAction>> result =
        new AtomicReference<>(Optional.empty());
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread caller =
        new Thread(
            () -> {
              try {
                result.set(ui.promptQuasselNetworkManagerAction("quassel", networks));
              } catch (Throwable t) {
                error.set(t);
              }
            },
            "quassel-network-manager-caller");
    caller.setDaemon(true);
    caller.start();

    JDialog dialog = waitForDialog("Quassel Network Manager - quassel", Duration.ofSeconds(8));
    onEdt(() -> clickButton(dialog, "Connect"));

    joinCaller(caller, error);

    Optional<QuasselNetworkManagerAction> action = result.get();
    assertTrue(action.isPresent(), "network manager should return a selected action");
    assertEquals(QuasselNetworkManagerAction.Operation.CONNECT, action.orElseThrow().operation());
    assertEquals("2", action.orElseThrow().networkIdOrName());
  }

  @Test
  void quasselNetworkManagerPromptCloseReturnsEmpty() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "dialog UI requires a display");

    SwingUiPort ui = newUi();
    List<QuasselCoreControlPort.QuasselCoreNetworkSummary> networks =
        List.of(
            new QuasselCoreControlPort.QuasselCoreNetworkSummary(
                2, "Libera", false, true, 4, "irc.libera.chat", 6697, true, Map.of()));

    AtomicReference<Optional<QuasselNetworkManagerAction>> result =
        new AtomicReference<>(Optional.empty());
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread caller =
        new Thread(
            () -> {
              try {
                result.set(ui.promptQuasselNetworkManagerAction("quassel", networks));
              } catch (Throwable t) {
                error.set(t);
              }
            },
            "quassel-network-manager-close-caller");
    caller.setDaemon(true);
    caller.start();

    JDialog dialog = waitForDialog("Quassel Network Manager - quassel", Duration.ofSeconds(8));
    onEdt(() -> clickButton(dialog, "Close"));

    joinCaller(caller, error);
    assertTrue(result.get().isEmpty(), "close should return empty network-manager action");
  }

  private static SwingUiPort newUi() {
    return new SwingUiPort(
        mock(ServerTreeDockable.class),
        mock(ChatDockable.class),
        mock(ChatTranscriptStore.class),
        mock(MentionPatternRegistry.class),
        mock(NotificationStore.class),
        mock(UserListDockable.class),
        mock(StatusBar.class),
        mock(ConnectButton.class),
        mock(DisconnectButton.class),
        new TargetActivationBus(),
        new OutboundLineBus(),
        mock(ChatDockManager.class),
        new ActiveInputRouter());
  }

  private static JDialog waitForDialog(String title, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      for (Window window : Window.getWindows()) {
        if (!(window instanceof JDialog dialog)) continue;
        if (!dialog.isShowing()) continue;
        if (title.equals(dialog.getTitle())) return dialog;
      }
      Thread.sleep(25);
    }
    throw new AssertionError("Timed out waiting for dialog: " + title);
  }

  private static void joinCaller(Thread caller, AtomicReference<Throwable> error) throws Exception {
    caller.join(Duration.ofSeconds(10));
    if (caller.isAlive()) {
      throw new AssertionError("Dialog caller thread timed out");
    }
    Throwable err = error.get();
    if (err != null) {
      throw new AssertionError("Dialog caller failed", err);
    }
  }

  private static <T extends Component> T findComponentNextToLabel(
      Component root, String labelText, Class<T> type, Predicate<T> predicate) {
    if (!(root instanceof Container container)) return null;
    Component[] components = container.getComponents();
    for (int i = 0; i < components.length; i++) {
      Component component = components[i];
      if (component instanceof JLabel label && labelText.equals(label.getText())) {
        if (i + 1 >= components.length) continue;
        Component candidate = components[i + 1];
        if (!type.isInstance(candidate)) continue;
        T casted = type.cast(candidate);
        if (predicate == null || predicate.test(casted)) {
          return casted;
        }
      }
    }
    for (Component child : components) {
      T found = findComponentNextToLabel(child, labelText, type, predicate);
      if (found != null) return found;
    }
    return null;
  }

  private static void clickButton(Container root, String text) {
    JButton button = findButton(root, text);
    if (button == null) {
      throw new AssertionError("Could not find button: " + text);
    }
    button.doClick();
  }

  private static JButton findButton(Component root, String text) {
    if (root == null || text == null) return null;
    if (root instanceof JButton button && text.equals(button.getText())) {
      return button;
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      JButton found = findButton(child, text);
      if (found != null) return found;
    }
    return null;
  }

  private static void onEdt(ThrowingRunnable runnable) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      runnable.run();
      return;
    }
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
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
