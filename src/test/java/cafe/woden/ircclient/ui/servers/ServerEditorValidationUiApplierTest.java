package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class ServerEditorValidationUiApplierTest {

  @Test
  void applyDecoratesErrorsWarningsAndSaveState() {
    JTextField idField = new JTextField();
    JTextField hostField = new JTextField();
    JTextField portField = new JTextField();
    JPasswordField serverPasswordField = new JPasswordField();
    JTextField matrixAuthUserField = new JTextField();
    JTextField loginField = new JTextField();
    JTextField nickField = new JTextField();
    JTextField saslUserField = new JTextField();
    JPasswordField saslSecretField = new JPasswordField();
    JTextField nickservServiceField = new JTextField();
    JPasswordField nickservPasswordField = new JPasswordField();
    JTextField proxyHostField = new JTextField();
    JTextField proxyPortField = new JTextField();
    JTextField proxyUserField = new JTextField();
    JPasswordField proxyPasswordField = new JPasswordField();
    JTextField proxyConnectTimeoutField = new JTextField();
    JTextField proxyReadTimeoutField = new JTextField();
    JButton saveButton = new JButton();

    ServerEditorValidationUiApplier.apply(
        new ServerEditorValidationPolicy.ValidationState(
            new ServerEditorConnectionPolicy.ConnectionValidation(true, false, true, false),
            new ServerEditorAuthPolicy.MatrixValidation(true, true, true),
            ServerEditorAuthMode.SASL,
            new ServerEditorAuthPolicy.SaslValidation(true, true, true),
            new ServerEditorAuthPolicy.NickservValidation(true, true),
            new ServerEditorProxyValidationPolicy.ProxyValidation(
                true, true, true, true, true, false, true),
            false),
        new ServerEditorValidationUiApplier.ValidationWidgets(
            idField,
            hostField,
            portField,
            serverPasswordField,
            matrixAuthUserField,
            loginField,
            nickField,
            saslUserField,
            saslSecretField,
            nickservServiceField,
            nickservPasswordField,
            proxyHostField,
            proxyPortField,
            proxyUserField,
            proxyPasswordField,
            proxyConnectTimeoutField,
            proxyReadTimeoutField,
            saveButton));

    assertEquals("error", idField.getClientProperty("JComponent.outline"));
    assertNull(hostField.getClientProperty("JComponent.outline"));
    assertEquals("error", portField.getClientProperty("JComponent.outline"));
    assertEquals("error", serverPasswordField.getClientProperty("JComponent.outline"));
    assertEquals("error", matrixAuthUserField.getClientProperty("JComponent.outline"));
    assertEquals("error", saslUserField.getClientProperty("JComponent.outline"));
    assertEquals("error", saslSecretField.getClientProperty("JComponent.outline"));
    assertEquals("error", nickservPasswordField.getClientProperty("JComponent.outline"));
    assertEquals("error", proxyHostField.getClientProperty("JComponent.outline"));
    assertEquals("error", proxyPortField.getClientProperty("JComponent.outline"));
    assertEquals("warning", proxyUserField.getClientProperty("JComponent.outline"));
    assertEquals("warning", proxyPasswordField.getClientProperty("JComponent.outline"));
    assertEquals("warning", proxyConnectTimeoutField.getClientProperty("JComponent.outline"));
    assertFalse(saveButton.isEnabled());
    assertEquals("Fix highlighted fields to enable Save.", saveButton.getToolTipText());
  }

  @Test
  void applyClearsInactiveSectionsAndAllowsSave() {
    JTextField matrixAuthUserField = new JTextField();
    matrixAuthUserField.putClientProperty("JComponent.outline", "error");
    JTextField saslUserField = new JTextField();
    saslUserField.putClientProperty("JComponent.outline", "error");
    JPasswordField saslSecretField = new JPasswordField();
    saslSecretField.putClientProperty("JComponent.outline", "error");
    JTextField nickservServiceField = new JTextField();
    nickservServiceField.putClientProperty("JComponent.outline", "error");
    JPasswordField nickservPasswordField = new JPasswordField();
    nickservPasswordField.putClientProperty("JComponent.outline", "error");
    JTextField proxyHostField = new JTextField();
    proxyHostField.putClientProperty("JComponent.outline", "error");
    JTextField proxyPortField = new JTextField();
    proxyPortField.putClientProperty("JComponent.outline", "error");
    JTextField proxyUserField = new JTextField();
    proxyUserField.putClientProperty("JComponent.outline", "warning");
    JPasswordField proxyPasswordField = new JPasswordField();
    proxyPasswordField.putClientProperty("JComponent.outline", "warning");
    JTextField proxyConnectTimeoutField = new JTextField();
    proxyConnectTimeoutField.putClientProperty("JComponent.outline", "warning");
    JTextField proxyReadTimeoutField = new JTextField();
    proxyReadTimeoutField.putClientProperty("JComponent.outline", "warning");
    JButton saveButton = new JButton();

    ServerEditorValidationUiApplier.apply(
        new ServerEditorValidationPolicy.ValidationState(
            new ServerEditorConnectionPolicy.ConnectionValidation(false, false, false, false),
            new ServerEditorAuthPolicy.MatrixValidation(false, false, false),
            ServerEditorAuthMode.DISABLED,
            new ServerEditorAuthPolicy.SaslValidation(false, false, false),
            new ServerEditorAuthPolicy.NickservValidation(false, false),
            new ServerEditorProxyValidationPolicy.ProxyValidation(
                false, false, false, false, false, false, false),
            true),
        new ServerEditorValidationUiApplier.ValidationWidgets(
            new JTextField(),
            new JTextField(),
            new JTextField(),
            new JPasswordField(),
            matrixAuthUserField,
            new JTextField(),
            new JTextField(),
            saslUserField,
            saslSecretField,
            nickservServiceField,
            nickservPasswordField,
            proxyHostField,
            proxyPortField,
            proxyUserField,
            proxyPasswordField,
            proxyConnectTimeoutField,
            proxyReadTimeoutField,
            saveButton));

    assertNull(matrixAuthUserField.getClientProperty("JComponent.outline"));
    assertNull(saslUserField.getClientProperty("JComponent.outline"));
    assertNull(saslSecretField.getClientProperty("JComponent.outline"));
    assertNull(nickservServiceField.getClientProperty("JComponent.outline"));
    assertNull(nickservPasswordField.getClientProperty("JComponent.outline"));
    assertNull(proxyHostField.getClientProperty("JComponent.outline"));
    assertNull(proxyPortField.getClientProperty("JComponent.outline"));
    assertNull(proxyUserField.getClientProperty("JComponent.outline"));
    assertNull(proxyPasswordField.getClientProperty("JComponent.outline"));
    assertNull(proxyConnectTimeoutField.getClientProperty("JComponent.outline"));
    assertNull(proxyReadTimeoutField.getClientProperty("JComponent.outline"));
    assertTrue(saveButton.isEnabled());
    assertNull(saveButton.getToolTipText());
  }
}
