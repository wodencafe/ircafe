package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class ServerEditorAuthTabBuilderTest {

  @Test
  void buildInitializesHintLabelsAndAuthCards() {
    JLabel matrixAuthModeLabel = new JLabel("Matrix auth");
    JComboBox<ServerEditorMatrixAuthMode> matrixAuthModeCombo =
        new JComboBox<>(ServerEditorMatrixAuthMode.values());
    JLabel matrixAuthUserLabel = new JLabel("Username");
    JTextField matrixAuthUserField = new JTextField();
    JLabel serverPasswordLabel = new JLabel("Server password");
    JPasswordField serverPasswordField = new JPasswordField();
    JLabel authModeLabel = new JLabel("Method");
    JComboBox<ServerEditorAuthMode> authModeCombo = new JComboBox<>(ServerEditorAuthMode.values());
    JLabel matrixAuthHintLabel = new JLabel("stale");
    JPanel authModeCardPanel = new JPanel(new CardLayout());
    JLabel authDisabledHintLabel = new JLabel("stale");
    JTextField saslUserField = new JTextField();
    JPasswordField saslPasswordField = new JPasswordField();
    JComboBox<String> saslMechanismCombo = new JComboBox<>(new String[] {"PLAIN"});
    JCheckBox saslContinueOnFailureBox = new JCheckBox();
    JLabel saslHintLabel = new JLabel("stale");
    JTextField nickservServiceField = new JTextField();
    JPasswordField nickservPasswordField = new JPasswordField();
    JCheckBox nickservDelayJoinBox = new JCheckBox();
    JLabel nickservHintLabel = new JLabel("stale");

    JPanel panel =
        ServerEditorAuthTabBuilder.build(
            new ServerEditorAuthTabBuilder.AuthTabWidgets(
                matrixAuthModeLabel,
                matrixAuthModeCombo,
                matrixAuthUserLabel,
                matrixAuthUserField,
                serverPasswordLabel,
                serverPasswordField,
                authModeLabel,
                authModeCombo,
                matrixAuthHintLabel,
                authModeCardPanel,
                "auth-disabled",
                "auth-sasl",
                "auth-nickserv",
                "No authentication on connect.",
                authDisabledHintLabel,
                saslUserField,
                saslPasswordField,
                saslMechanismCombo,
                saslContinueOnFailureBox,
                saslHintLabel,
                nickservServiceField,
                nickservPasswordField,
                nickservDelayJoinBox,
                nickservHintLabel));

    assertEquals(" ", matrixAuthHintLabel.getText());
    assertEquals(" ", saslHintLabel.getText());
    assertEquals(" ", nickservHintLabel.getText());
    assertEquals(
        "foreground:$Label.disabledForeground",
        matrixAuthHintLabel.getClientProperty(FlatClientProperties.STYLE));
    assertEquals("<html>No authentication on connect.</html>", authDisabledHintLabel.getText());
    assertEquals(3, authModeCardPanel.getComponentCount());
    assertTrue(panel.isAncestorOf(authModeCardPanel));
  }
}
