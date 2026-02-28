package cafe.woden.ircclient.ui.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.ui.CommandHistoryStore;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.Component;
import java.awt.Container;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MessageInputPanelFunctionalTest {

  @Test
  void clickingSendPublishesOutboundMessage() throws Exception {
    UiSettingsBus settingsBus = mock(UiSettingsBus.class);
    when(settingsBus.get()).thenReturn(null);
    CommandHistoryStore historyStore = mock(CommandHistoryStore.class);

    MessageInputPanel panel = new MessageInputPanel(settingsBus, historyStore);
    CopyOnWriteArrayList<String> outbound = new CopyOnWriteArrayList<>();
    Disposable subscription = panel.outboundMessages().subscribe(outbound::add);

    try {
      JTextField input = findFirst(panel, JTextField.class);
      JButton send = findNamedButton(panel, "messageSendButton");
      assertNotNull(input, "message input field should be present");
      assertNotNull(send, "send button should be present");

      onEdt(() -> input.setText("hello functional smoke"));
      flushEdt();

      onEdt(() -> send.doClick());
      flushEdt();

      waitFor(() -> outbound.size() == 1, Duration.ofSeconds(3));
      assertEquals("hello functional smoke", outbound.getFirst());
      onEdt(() -> assertTrue(input.getText().isEmpty(), "input should clear after send"));
    } finally {
      subscription.dispose();
      flushEdt();
    }
  }

  private static JButton findNamedButton(Component root, String name) {
    if (root == null || name == null) return null;
    if (root instanceof JButton button && name.equals(button.getName())) {
      return button;
    }
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      JButton found = findNamedButton(child, name);
      if (found != null) return found;
    }
    return null;
  }

  private static <T extends Component> T findFirst(Component root, Class<T> type) {
    if (root == null || type == null) return null;
    if (type.isInstance(root)) return type.cast(root);
    if (!(root instanceof Container container)) return null;
    for (Component child : container.getComponents()) {
      T found = findFirst(child, type);
      if (found != null) return found;
    }
    return null;
  }

  private static void waitFor(BooleanSupplier condition, Duration timeout) throws Exception {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      flushEdt();
      if (condition.getAsBoolean()) return;
      Thread.sleep(25);
    }
    flushEdt();
    assertTrue(condition.getAsBoolean(), "Timed out waiting for condition");
  }

  private static void flushEdt() throws Exception {
    if (SwingUtilities.isEventDispatchThread()) return;
    SwingUtilities.invokeAndWait(() -> {});
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
