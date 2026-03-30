package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class ServerEditorAuthUiApplierTest {

  @Test
  void applyMatrixUiUpdatesVisibilityAndHintPresentation() {
    JLabel authModeLabel = new JLabel();
    JComboBox<String> authModeCombo = new JComboBox<>();
    JPanel authModeCardPanel = new JPanel();
    JLabel matrixAuthModeLabel = new JLabel();
    JComboBox<String> matrixAuthModeCombo = new JComboBox<>();
    JLabel matrixAuthHintLabel = new JLabel("stale");
    matrixAuthHintLabel.setToolTipText("stale");
    JLabel matrixAuthUserLabel = new JLabel();
    JTextField matrixAuthUserField = new JTextField();
    JLabel serverPasswordLabel = new JLabel("Server password");
    JPasswordField serverPasswordField = new JPasswordField();
    JPanel refreshTarget = new JPanel();

    ServerEditorAuthUiApplier.applyMatrixUi(
        new ServerEditorAuthUiPolicy.MatrixUiState(
            true,
            false,
            true,
            true,
            true,
            "Password",
            "matrix account password",
            "Username/password mode signs in via /login."),
        new ServerEditorAuthUiApplier.MatrixAuthWidgets(
            authModeLabel,
            authModeCombo,
            authModeCardPanel,
            matrixAuthModeLabel,
            matrixAuthModeCombo,
            matrixAuthHintLabel,
            matrixAuthUserLabel,
            matrixAuthUserField,
            serverPasswordLabel,
            serverPasswordField,
            refreshTarget));

    assertTrue(authModeLabel.isVisible());
    assertFalse(authModeCardPanel.isVisible());
    assertTrue(matrixAuthModeLabel.isVisible());
    assertTrue(matrixAuthUserField.isVisible());
    assertTrue(matrixAuthUserField.isEnabled());
    assertEquals("Password", serverPasswordLabel.getText());
    assertEquals(
        "matrix account password",
        serverPasswordField.getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT));
    assertTrue(matrixAuthHintLabel.getText().startsWith("<html>"));
    assertEquals(
        "Username/password mode signs in via /login.", matrixAuthHintLabel.getToolTipText());

    ServerEditorAuthUiApplier.applyMatrixUi(
        new ServerEditorAuthUiPolicy.MatrixUiState(true, true, false, false, false, "", "", null),
        new ServerEditorAuthUiApplier.MatrixAuthWidgets(
            authModeLabel,
            authModeCombo,
            authModeCardPanel,
            matrixAuthModeLabel,
            matrixAuthModeCombo,
            matrixAuthHintLabel,
            matrixAuthUserLabel,
            matrixAuthUserField,
            serverPasswordLabel,
            serverPasswordField,
            refreshTarget));

    assertFalse(matrixAuthModeLabel.isVisible());
    assertFalse(matrixAuthUserField.isVisible());
    assertEquals(" ", matrixAuthHintLabel.getText());
    assertNull(matrixAuthHintLabel.getToolTipText());
  }

  @Test
  void applySaslUiUpdatesFieldEnablementAndHint() {
    JComboBox<String> mechanismCombo = new JComboBox<>();
    JCheckBox continueOnFailureBox = new JCheckBox();
    JTextField userField = new JTextField();
    JPasswordField secretField = new JPasswordField();
    JLabel hintLabel = new JLabel();

    ServerEditorAuthUiApplier.applySaslUi(
        new ServerEditorAuthUiPolicy.SaslUiState(
            true, true, false, true, false, "(ignored)", "EXTERNAL uses TLS certs."),
        new ServerEditorAuthUiApplier.SaslWidgets(
            mechanismCombo, continueOnFailureBox, userField, secretField, hintLabel));

    assertTrue(mechanismCombo.isEnabled());
    assertFalse(continueOnFailureBox.isEnabled());
    assertTrue(userField.isEnabled());
    assertFalse(secretField.isEnabled());
    assertEquals("(ignored)", secretField.getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT));
    assertTrue(hintLabel.getText().startsWith("<html>"));
    assertEquals("EXTERNAL uses TLS certs.", hintLabel.getToolTipText());
  }

  @Test
  void applyNickservUiUpdatesEnablementAndHint() {
    JTextField serviceField = new JTextField();
    JPasswordField passwordField = new JPasswordField();
    JCheckBox delayJoinBox = new JCheckBox();
    JLabel hintLabel = new JLabel();

    ServerEditorAuthUiApplier.applyNickservUi(
        new ServerEditorAuthUiPolicy.NickservUiState(true, "NickServ identify runs after connect."),
        new ServerEditorAuthUiApplier.NickservWidgets(
            serviceField, passwordField, delayJoinBox, hintLabel));

    assertTrue(serviceField.isEnabled());
    assertTrue(passwordField.isEnabled());
    assertTrue(delayJoinBox.isEnabled());
    assertTrue(hintLabel.getText().startsWith("<html>"));
    assertEquals("NickServ identify runs after connect.", hintLabel.getToolTipText());
  }
}
