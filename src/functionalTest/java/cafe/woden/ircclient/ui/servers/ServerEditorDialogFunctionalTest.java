package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ServerEditorDialogFunctionalTest {

  @Test
  void validationTlsSaslProxyInteractionsAndSaveWork() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "dialog UI requires a display");

    ServerEditorDialog dialog = onEdtCall(() -> new ServerEditorDialog(null, "Add Server", null));
    JCheckBox tlsBox = readField(dialog, "tlsBox", JCheckBox.class);
    JTextField portField = readField(dialog, "portField", JTextField.class);
    JCheckBox saslEnabledBox = readField(dialog, "saslEnabledBox", JCheckBox.class);
    JComboBox<?> saslMechanism = readField(dialog, "saslMechanism", JComboBox.class);
    JTextField saslUserField = readField(dialog, "saslUserField", JTextField.class);
    JPasswordField saslPassField = readField(dialog, "saslPassField", JPasswordField.class);
    JCheckBox proxyOverrideBox = readField(dialog, "proxyOverrideBox", JCheckBox.class);
    JCheckBox proxyEnabledBox = readField(dialog, "proxyEnabledBox", JCheckBox.class);
    JTextField proxyHostField = readField(dialog, "proxyHostField", JTextField.class);
    JTextField proxyPortField = readField(dialog, "proxyPortField", JTextField.class);
    JTextField idField = readField(dialog, "idField", JTextField.class);
    JTextField hostField = readField(dialog, "hostField", JTextField.class);
    JTextField nickField = readField(dialog, "nickField", JTextField.class);
    JButton saveBtn = readField(dialog, "saveBtn", JButton.class);

    try {
      onEdt(() -> assertEquals("6697", portField.getText(), "tls default should set secure port"));

      onEdt(tlsBox::doClick);
      onEdt(() -> assertEquals("6667", portField.getText(), "plain mode should use 6667"));

      onEdt(saslEnabledBox::doClick);
      onEdt(() -> saslMechanism.setSelectedItem("EXTERNAL"));
      onEdt(
          () -> {
            assertTrue(saslUserField.isEnabled(), "EXTERNAL keeps username optional");
            assertFalse(saslPassField.isEnabled(), "EXTERNAL disables secret field");
          });

      onEdt(() -> saslMechanism.setSelectedItem("SCRAM-SHA-256"));
      onEdt(() -> assertTrue(saslPassField.isEnabled(), "SCRAM requires secret field"));

      onEdt(proxyOverrideBox::doClick);
      if (!onEdtCall(proxyEnabledBox::isSelected)) {
        onEdt(proxyEnabledBox::doClick);
      }
      onEdt(
          () -> {
            assertTrue(proxyHostField.isEnabled(), "proxy host should be editable when enabled");
            assertTrue(proxyPortField.isEnabled(), "proxy port should be editable when enabled");
          });

      onEdt(
          () -> {
            idField.setText("libera");
            hostField.setText("irc.libera.chat");
            portField.setText("6697");
            nickField.setText("ircafe");
            if (saslEnabledBox.isSelected()) {
              saslEnabledBox.doClick();
            }
          });
      waitFor(() -> onEdtBoolean(saveBtn::isEnabled), Duration.ofSeconds(2));

      onEdt(saveBtn::doClick);

      Optional<?> result = readField(dialog, "result", Optional.class);
      assertTrue(result.isPresent(), "save should produce a server result");
      Object server = result.get();
      Method id = server.getClass().getMethod("id");
      assertEquals("libera", id.invoke(server));
    } finally {
      onEdt(dialog::dispose);
      flushEdt();
    }
  }

  @Test
  void cancelLeavesEmptyResult() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "dialog UI requires a display");
    ServerEditorDialog dialog = onEdtCall(() -> new ServerEditorDialog(null, "Add Server", null));
    JButton cancelBtn = readField(dialog, "cancelBtn", JButton.class);
    try {
      onEdt(cancelBtn::doClick);
      Optional<?> result = readField(dialog, "result", Optional.class);
      assertTrue(result.isEmpty());
    } finally {
      onEdt(dialog::dispose);
      flushEdt();
    }
  }

  private static <T> T readField(Object target, String fieldName, Class<T> type) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return type.cast(field.get(target));
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

  private static <T> T onEdtCall(ThrowingSupplier<T> supplier) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      return supplier.get();
    }
    AtomicReference<T> out = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            out.set(supplier.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    return out.get();
  }

  private static boolean onEdtBoolean(ThrowingBooleanSupplier supplier) {
    try {
      return onEdtCall(supplier::getAsBoolean);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }

  @FunctionalInterface
  private interface ThrowingBooleanSupplier {
    boolean getAsBoolean() throws Exception;
  }
}
