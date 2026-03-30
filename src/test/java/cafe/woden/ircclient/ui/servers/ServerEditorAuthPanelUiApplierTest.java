package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.CardLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class ServerEditorAuthPanelUiApplierTest {

  private static final ServerEditorBackendProfiles BACKEND_PROFILES =
      ServerEditorBackendProfiles.builtIns();

  @Test
  void applyUpdatesMatrixProfileUsingEffectiveDisabledAuthMode() {
    JComboBox<ServerEditorAuthMode> authModeCombo = new JComboBox<>(ServerEditorAuthMode.values());
    authModeCombo.setSelectedItem(ServerEditorAuthMode.NICKSERV);
    JPanel authModeCardPanel = new JPanel(new CardLayout());
    JPanel disabledCard = new JPanel();
    JPanel saslCard = new JPanel();
    JPanel nickservCard = new JPanel();
    authModeCardPanel.add(disabledCard, "auth-disabled");
    authModeCardPanel.add(saslCard, "auth-sasl");
    authModeCardPanel.add(nickservCard, "auth-nickserv");

    JLabel authModeLabel = new JLabel();
    JLabel matrixAuthModeLabel = new JLabel();
    JComboBox<ServerEditorMatrixAuthMode> matrixAuthModeCombo =
        new JComboBox<>(ServerEditorMatrixAuthMode.values());
    JLabel matrixAuthHintLabel = new JLabel();
    JLabel matrixAuthUserLabel = new JLabel();
    JTextField matrixAuthUserField = new JTextField();
    JLabel serverPasswordLabel = new JLabel("Credential");
    JPasswordField serverPasswordField = new JPasswordField();
    JPanel refreshTarget = new JPanel();

    JComboBox<String> saslMechanism = new JComboBox<>(new String[] {"PLAIN"});
    JCheckBox saslContinueOnFailureBox = new JCheckBox();
    JTextField saslUserField = new JTextField();
    JPasswordField saslPassField = new JPasswordField();
    JLabel saslHintLabel = new JLabel();

    JTextField nickservServiceField = new JTextField();
    JPasswordField nickservPassField = new JPasswordField();
    JCheckBox nickservDelayJoinBox = new JCheckBox();
    JLabel nickservHintLabel = new JLabel();

    ServerEditorAuthPanelUiApplier.apply(
        new ServerEditorAuthPanelUiApplier.RefreshRequest(
            BACKEND_PROFILES.profileForBackendId("matrix"),
            ServerEditorAuthMode.NICKSERV,
            ServerEditorMatrixAuthMode.USERNAME_PASSWORD,
            "PLAIN"),
        new ServerEditorAuthPanelUiApplier.AuthPanelWidgets(
            new ServerEditorAuthModeUiApplier.AuthModeWidgets(
                authModeCombo, authModeCardPanel, "auth-disabled", "auth-sasl", "auth-nickserv"),
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
                refreshTarget),
            new ServerEditorAuthUiApplier.SaslWidgets(
                saslMechanism,
                saslContinueOnFailureBox,
                saslUserField,
                saslPassField,
                saslHintLabel),
            new ServerEditorAuthUiApplier.NickservWidgets(
                nickservServiceField, nickservPassField, nickservDelayJoinBox, nickservHintLabel)));

    assertFalse(authModeCombo.isEnabled());
    assertEquals(ServerEditorAuthMode.DISABLED, authModeCombo.getSelectedItem());
    assertFalse(authModeLabel.isVisible());
    assertFalse(authModeCardPanel.isVisible());
    assertTrue(matrixAuthModeLabel.isVisible());
    assertTrue(matrixAuthUserField.isVisible());
    assertTrue(matrixAuthUserField.isEnabled());
    assertEquals("Password", serverPasswordLabel.getText());
    assertEquals(
        "matrix account password",
        serverPasswordField.getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT));
    assertFalse(saslMechanism.isEnabled());
    assertFalse(saslUserField.isEnabled());
    assertFalse(nickservServiceField.isEnabled());
    assertFalse(nickservPassField.isEnabled());
  }

  @Test
  void applyUpdatesIrcNickservFlowAndHidesMatrixControls() {
    JComboBox<ServerEditorAuthMode> authModeCombo = new JComboBox<>(ServerEditorAuthMode.values());
    authModeCombo.setSelectedItem(ServerEditorAuthMode.DISABLED);
    JPanel authModeCardPanel = new JPanel(new CardLayout());
    JPanel disabledCard = new JPanel();
    JPanel saslCard = new JPanel();
    JPanel nickservCard = new JPanel();
    authModeCardPanel.add(disabledCard, "auth-disabled");
    authModeCardPanel.add(saslCard, "auth-sasl");
    authModeCardPanel.add(nickservCard, "auth-nickserv");

    JLabel authModeLabel = new JLabel();
    JLabel matrixAuthModeLabel = new JLabel();
    JComboBox<ServerEditorMatrixAuthMode> matrixAuthModeCombo =
        new JComboBox<>(ServerEditorMatrixAuthMode.values());
    JLabel matrixAuthHintLabel = new JLabel("stale");
    JLabel matrixAuthUserLabel = new JLabel();
    JTextField matrixAuthUserField = new JTextField();
    JLabel serverPasswordLabel = new JLabel("Server password");
    JPasswordField serverPasswordField = new JPasswordField();
    JPanel refreshTarget = new JPanel();

    JComboBox<String> saslMechanism = new JComboBox<>(new String[] {"PLAIN"});
    JCheckBox saslContinueOnFailureBox = new JCheckBox();
    JTextField saslUserField = new JTextField();
    JPasswordField saslPassField = new JPasswordField();
    JLabel saslHintLabel = new JLabel();

    JTextField nickservServiceField = new JTextField();
    JPasswordField nickservPassField = new JPasswordField();
    JCheckBox nickservDelayJoinBox = new JCheckBox();
    JLabel nickservHintLabel = new JLabel();

    ServerEditorAuthPanelUiApplier.apply(
        new ServerEditorAuthPanelUiApplier.RefreshRequest(
            BACKEND_PROFILES.profileForBackendId("irc"),
            ServerEditorAuthMode.NICKSERV,
            ServerEditorMatrixAuthMode.ACCESS_TOKEN,
            "PLAIN"),
        new ServerEditorAuthPanelUiApplier.AuthPanelWidgets(
            new ServerEditorAuthModeUiApplier.AuthModeWidgets(
                authModeCombo, authModeCardPanel, "auth-disabled", "auth-sasl", "auth-nickserv"),
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
                refreshTarget),
            new ServerEditorAuthUiApplier.SaslWidgets(
                saslMechanism,
                saslContinueOnFailureBox,
                saslUserField,
                saslPassField,
                saslHintLabel),
            new ServerEditorAuthUiApplier.NickservWidgets(
                nickservServiceField, nickservPassField, nickservDelayJoinBox, nickservHintLabel)));

    assertTrue(authModeCombo.isEnabled());
    assertEquals(ServerEditorAuthMode.NICKSERV, authModeCombo.getSelectedItem());
    assertTrue(authModeLabel.isVisible());
    assertTrue(authModeCardPanel.isVisible());
    assertFalse(disabledCard.isVisible());
    assertFalse(saslCard.isVisible());
    assertTrue(nickservCard.isVisible());
    assertFalse(matrixAuthModeLabel.isVisible());
    assertFalse(matrixAuthUserField.isVisible());
    assertEquals(" ", matrixAuthHintLabel.getText());
    assertFalse(saslMechanism.isEnabled());
    assertFalse(saslUserField.isEnabled());
    assertTrue(nickservServiceField.isEnabled());
    assertTrue(nickservPassField.isEnabled());
    assertTrue(nickservDelayJoinBox.isEnabled());
  }
}
