package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import java.awt.Component;
import java.awt.Container;
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
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
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
    JPasswordField serverPassField = readField(dialog, "serverPassField", JPasswordField.class);
    JButton saveBtn = readField(dialog, "saveBtn", JButton.class);

    try {
      JTabbedPane tabs = onEdtCall(() -> findDescendant(dialog, JTabbedPane.class));
      assertNotNull(tabs, "server dialog should contain tabs");
      int authTabIndex = onEdtCall(() -> tabs.indexOfTab("Auth"));
      int connectionTabIndex = onEdtCall(() -> tabs.indexOfTab("Connection"));
      assertTrue(authTabIndex >= 0, "auth tab should be present");
      assertTrue(connectionTabIndex >= 0, "connection tab should be present");
      Component authTabComponent = onEdtCall(() -> tabs.getComponentAt(authTabIndex));
      Component connectionTabComponent = onEdtCall(() -> tabs.getComponentAt(connectionTabIndex));
      assertTrue(
          onEdtCall(() -> isDescendant(authTabComponent, serverPassField)),
          "server/core password field should be on Auth tab");
      assertFalse(
          onEdtCall(() -> isDescendant(connectionTabComponent, serverPassField)),
          "server/core password field should not live on Connection tab");

      onEdt(() -> assertEquals("6697", portField.getText(), "tls default should set secure port"));
      onEdt(
          () ->
              assertEquals(
                  IrcProperties.Server.Backend.IRC,
                  backendCombo.getSelectedItem(),
                  "new servers should default to IRC backend"));
      onEdt(
          () -> {
            assertTrue(serverPassField.isVisible(), "auth password field should be visible on IRC");
            assertTrue(
                serverPassField.getPreferredSize().width > 100,
                "auth password field should have usable width");
          });

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

  @Test
  void matrixBackendUsesAccessTokenAndDisablesIrcAuthMode() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "dialog UI requires a display");

    ServerEditorDialog dialog = onEdtCall(() -> new ServerEditorDialog(null, "Add Server", null));
    JTextField idField = readField(dialog, "idField", JTextField.class);
    JTextField hostField = readField(dialog, "hostField", JTextField.class);
    JTextField portField = readField(dialog, "portField", JTextField.class);
    JPasswordField serverPassField = readField(dialog, "serverPassField", JPasswordField.class);
    JComboBox<?> backendCombo = readField(dialog, "backendCombo", JComboBox.class);
    JComboBox<?> authModeCombo = readField(dialog, "authModeCombo", JComboBox.class);
    JComboBox<?> matrixAuthModeCombo = readField(dialog, "matrixAuthModeCombo", JComboBox.class);
    JCheckBox tlsBox = readField(dialog, "tlsBox", JCheckBox.class);
    JLabel hostLabel = readField(dialog, "hostLabel", JLabel.class);
    JLabel serverPasswordLabel = readField(dialog, "serverPasswordLabel", JLabel.class);
    JButton saveBtn = readField(dialog, "saveBtn", JButton.class);

    try {
      onEdt(
          () -> {
            idField.setText("matrix");
            hostField.setText("https://matrix.example.org");
            backendCombo.setSelectedItem(IrcProperties.Server.Backend.MATRIX);
          });
      onEdt(
          () -> {
            assertEquals("Homeserver", hostLabel.getText());
            assertEquals("Access token", serverPasswordLabel.getText());
            assertEquals(0, matrixAuthModeCombo.getSelectedIndex());
            assertEquals("443", portField.getText(), "matrix backend should default to TLS 443");
            assertFalse(authModeCombo.isEnabled(), "Matrix backend should disable IRC auth mode");
            assertFalse(
                authModeCombo.isVisible(), "Matrix backend should hide IRC auth method row");
            assertFalse(saveBtn.isEnabled(), "matrix backend should require access token");
          });

      onEdt(tlsBox::doClick);
      onEdt(() -> assertEquals("80", portField.getText(), "matrix plain mode should use 80"));
      onEdt(tlsBox::doClick);
      onEdt(() -> assertEquals("443", portField.getText(), "matrix TLS mode should use 443"));

      onEdt(() -> serverPassField.setText("matrix-access-token"));
      waitFor(() -> onEdtBoolean(saveBtn::isEnabled), Duration.ofSeconds(2));
      onEdt(saveBtn::doClick);

      Optional<?> result = readField(dialog, "result", Optional.class);
      assertTrue(result.isPresent(), "save should produce a server result");
      Object server = result.get();
      Method backend = server.getClass().getMethod("backend");
      assertEquals(IrcProperties.Server.Backend.MATRIX, backend.invoke(server));
      Method serverPassword = server.getClass().getMethod("serverPassword");
      assertEquals("matrix-access-token", serverPassword.invoke(server));

      Method saslMethod = server.getClass().getMethod("sasl");
      Object sasl = saslMethod.invoke(server);
      Method saslEnabled = sasl.getClass().getMethod("enabled");
      assertEquals(Boolean.FALSE, saslEnabled.invoke(sasl));

      Method nickservMethod = server.getClass().getMethod("nickserv");
      Object nickserv = nickservMethod.invoke(server);
      Method nickservEnabled = nickserv.getClass().getMethod("enabled");
      assertEquals(Boolean.FALSE, nickservEnabled.invoke(nickserv));
    } finally {
      onEdt(dialog::dispose);
      flushEdt();
    }
  }

  @Test
  void matrixBackendSupportsUsernamePasswordAuthMode() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "dialog UI requires a display");

    ServerEditorDialog dialog = onEdtCall(() -> new ServerEditorDialog(null, "Add Server", null));
    JTextField idField = readField(dialog, "idField", JTextField.class);
    JTextField hostField = readField(dialog, "hostField", JTextField.class);
    JPasswordField serverPassField = readField(dialog, "serverPassField", JPasswordField.class);
    JTextField matrixAuthUserField = readField(dialog, "matrixAuthUserField", JTextField.class);
    JComboBox<?> backendCombo = readField(dialog, "backendCombo", JComboBox.class);
    JComboBox<?> matrixAuthModeCombo = readField(dialog, "matrixAuthModeCombo", JComboBox.class);
    JLabel serverPasswordLabel = readField(dialog, "serverPasswordLabel", JLabel.class);
    JButton saveBtn = readField(dialog, "saveBtn", JButton.class);

    try {
      onEdt(
          () -> {
            idField.setText("matrix");
            hostField.setText("https://matrix.example.org");
            backendCombo.setSelectedItem(IrcProperties.Server.Backend.MATRIX);
            matrixAuthModeCombo.setSelectedIndex(1);
          });

      onEdt(() -> assertEquals("Password", serverPasswordLabel.getText()));
      onEdt(() -> assertFalse(saveBtn.isEnabled(), "matrix password mode requires both fields"));

      onEdt(() -> matrixAuthUserField.setText("alice"));
      onEdt(() -> assertFalse(saveBtn.isEnabled(), "matrix password mode requires password"));
      onEdt(() -> serverPassField.setText("matrix-password"));
      waitFor(() -> onEdtBoolean(saveBtn::isEnabled), Duration.ofSeconds(2));

      onEdt(saveBtn::doClick);

      Optional<?> result = readField(dialog, "result", Optional.class);
      assertTrue(result.isPresent(), "save should produce a server result");
      Object server = result.get();

      Method backend = server.getClass().getMethod("backend");
      assertEquals(IrcProperties.Server.Backend.MATRIX, backend.invoke(server));
      Method serverPassword = server.getClass().getMethod("serverPassword");
      assertEquals("", serverPassword.invoke(server));
      Method login = server.getClass().getMethod("login");
      assertEquals("alice", login.invoke(server));

      Method saslMethod = server.getClass().getMethod("sasl");
      Object sasl = saslMethod.invoke(server);
      Method saslEnabled = sasl.getClass().getMethod("enabled");
      Method saslUser = sasl.getClass().getMethod("username");
      Method saslPassword = sasl.getClass().getMethod("password");
      Method saslMechanism = sasl.getClass().getMethod("mechanism");
      assertEquals(Boolean.TRUE, saslEnabled.invoke(sasl));
      assertEquals("alice", saslUser.invoke(sasl));
      assertEquals("matrix-password", saslPassword.invoke(sasl));
      assertEquals("MATRIX_PASSWORD", saslMechanism.invoke(sasl));
    } finally {
      onEdt(dialog::dispose);
      flushEdt();
    }
  }

  @Test
  void proxyTabUsesScrollPaneToAvoidClipping() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "dialog UI requires a display");

    ServerEditorDialog dialog = onEdtCall(() -> new ServerEditorDialog(null, "Add Server", null));
    try {
      JTabbedPane tabs = onEdtCall(() -> findDescendant(dialog, JTabbedPane.class));
      assertNotNull(tabs, "server dialog should contain tabs");

      int proxyTabIndex = onEdtCall(() -> tabs.indexOfTab("Proxy"));
      assertTrue(proxyTabIndex >= 0, "proxy tab should be present");

      Component proxyTabComponent = onEdtCall(() -> tabs.getComponentAt(proxyTabIndex));
      assertTrue(
          proxyTabComponent instanceof JScrollPane,
          "proxy tab should be scrollable so fields do not clip");

      JScrollPane proxyScroll = (JScrollPane) proxyTabComponent;
      assertEquals(
          JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
          onEdtCall(proxyScroll::getHorizontalScrollBarPolicy));
      assertNotNull(onEdtCall(() -> proxyScroll.getViewport().getView()));
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

  private static <T extends Component> T findDescendant(Container root, Class<T> type) {
    if (root == null || type == null) return null;
    for (Component child : root.getComponents()) {
      if (type.isInstance(child)) return type.cast(child);
      if (child instanceof Container nested) {
        T found = findDescendant(nested, type);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static boolean isDescendant(Component root, Component target) {
    if (root == null || target == null) return false;
    if (root == target) return true;
    if (!(root instanceof Container container)) return false;
    for (Component child : container.getComponents()) {
      if (isDescendant(child, target)) return true;
    }
    return false;
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
