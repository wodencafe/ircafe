package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
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
    JComboBox<?> backendCombo = readField(dialog, "backendCombo", JComboBox.class);
    JComboBox<?> authModeCombo = readField(dialog, "authModeCombo", JComboBox.class);
    JComboBox<?> saslMechanism = readField(dialog, "saslMechanism", JComboBox.class);
    JTextField saslUserField = readField(dialog, "saslUserField", JTextField.class);
    JPasswordField saslPassField = readField(dialog, "saslPassField", JPasswordField.class);
    JCheckBox saslContinueOnFailureBox =
        readField(dialog, "saslContinueOnFailureBox", JCheckBox.class);
    JCheckBox proxyOverrideBox = readField(dialog, "proxyOverrideBox", JCheckBox.class);
    JCheckBox proxyEnabledBox = readField(dialog, "proxyEnabledBox", JCheckBox.class);
    JTextField proxyHostField = readField(dialog, "proxyHostField", JTextField.class);
    JTextField proxyPortField = readField(dialog, "proxyPortField", JTextField.class);
    JTextField idField = readField(dialog, "idField", JTextField.class);
    JTextField hostField = readField(dialog, "hostField", JTextField.class);
    JTextField nickField = readField(dialog, "nickField", JTextField.class);
    JTextField loginField = readField(dialog, "loginField", JTextField.class);
    JTextField serverPassField = readField(dialog, "serverPassField", JTextField.class);
    JButton saveBtn = readField(dialog, "saveBtn", JButton.class);

    try {
      onEdt(() -> assertEquals("6697", portField.getText(), "tls default should set secure port"));
      onEdt(
          () ->
              assertEquals(
                  IrcProperties.Server.Backend.IRC,
                  backendCombo.getSelectedItem(),
                  "new servers should default to IRC backend"));

      onEdt(tlsBox::doClick);
      onEdt(() -> assertEquals("6667", portField.getText(), "plain mode should use 6667"));

      onEdt(() -> authModeCombo.setSelectedIndex(1)); // SASL
      onEdt(() -> saslMechanism.setSelectedItem("EXTERNAL"));
      onEdt(
          () -> {
            assertTrue(saslUserField.isEnabled(), "EXTERNAL keeps username optional");
            assertFalse(saslPassField.isEnabled(), "EXTERNAL disables secret field");
          });

      onEdt(() -> saslMechanism.setSelectedItem("SCRAM-SHA-256"));
      onEdt(() -> assertTrue(saslPassField.isEnabled(), "SCRAM requires secret field"));

      int authModePrefBeforeAuto = onEdtCall(() -> authModeCombo.getPreferredSize().width);
      onEdt(() -> saslMechanism.setSelectedItem("AUTO"));
      int authModePrefAfterAuto = onEdtCall(() -> authModeCombo.getPreferredSize().width);
      assertTrue(
          authModePrefAfterAuto >= authModePrefBeforeAuto,
          "auth method combo should not shrink when SASL mechanism switches to AUTO");

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
            authModeCombo.setSelectedIndex(1); // SASL
            saslMechanism.setSelectedItem("SCRAM-SHA-256");
            saslUserField.setText("ircafe-user");
            saslPassField.setText("sasl-secret");
            saslContinueOnFailureBox.setSelected(true);
          });
      onEdt(() -> backendCombo.setSelectedItem(IrcProperties.Server.Backend.QUASSEL_CORE));
      onEdt(
          () -> {
            assertEquals(
                "4242",
                portField.getText(),
                "quassel backend should auto-select core default port when auto-port is enabled");
            assertFalse(
                authModeCombo.isEnabled(), "Quassel backend should disable direct auth mode");
            assertTrue(
                saveBtn.isEnabled(),
                "Quassel profiles should save without pre-existing core credentials");
            loginField.setText("core-user");
            serverPassField.setText("core-secret");
          });
      waitFor(() -> onEdtBoolean(saveBtn::isEnabled), Duration.ofSeconds(2));

      onEdt(saveBtn::doClick);

      Optional<?> result = readField(dialog, "result", Optional.class);
      assertTrue(result.isPresent(), "save should produce a server result");
      Object server = result.get();
      Method id = server.getClass().getMethod("id");
      assertEquals("libera", id.invoke(server));
      Method backend = server.getClass().getMethod("backend");
      assertEquals(IrcProperties.Server.Backend.QUASSEL_CORE, backend.invoke(server));
      Method login = server.getClass().getMethod("login");
      assertEquals("core-user", login.invoke(server));
      Method serverPassword = server.getClass().getMethod("serverPassword");
      assertEquals("core-secret", serverPassword.invoke(server));
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
