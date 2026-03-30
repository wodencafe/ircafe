package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class ServerEditorBackendPresentationApplierTest {

  private static final ServerEditorBackendProfiles BACKEND_PROFILES =
      ServerEditorBackendProfiles.builtIns();

  @Test
  void applyUpdatesLabelsHintsAndPlaceholders() {
    JLabel hostLabel = new JLabel();
    JLabel serverPasswordLabel = new JLabel();
    JLabel nickLabel = new JLabel();
    JLabel loginLabel = new JLabel();
    JLabel realNameLabel = new JLabel();
    JCheckBox tlsToggle = new JCheckBox();
    JLabel connectionHintLabel = new JLabel();
    JLabel authDisabledHintLabel = new JLabel();
    JPasswordField serverPasswordField = new JPasswordField();
    JTextField hostField = new JTextField();
    JTextField loginField = new JTextField();
    JTextField nickField = new JTextField();
    JTextField realNameField = new JTextField();

    ServerEditorBackendPresentationApplier.apply(
        ServerEditorBackendPresentationPolicy.presentationState(
            BACKEND_PROFILES.profileForBackendId("quassel-core")),
        new ServerEditorBackendPresentationApplier.BackendWidgets(
            hostLabel,
            serverPasswordLabel,
            nickLabel,
            loginLabel,
            realNameLabel,
            tlsToggle,
            connectionHintLabel,
            authDisabledHintLabel,
            serverPasswordField,
            hostField,
            loginField,
            nickField,
            realNameField));

    assertEquals("Host", hostLabel.getText());
    assertEquals("Core password", serverPasswordLabel.getText());
    assertEquals("Use TLS (SSL)", tlsToggle.getText());
    assertEquals(
        "Quassel backend logs into Quassel Core here (default ports: 4242 plain, 4243 TLS)."
            + " Core password can be blank before initial setup. SASL/NickServ below are ignored.",
        connectionHintLabel.getText());
    assertEquals(
        "<html>Quassel backend does not run direct IRC SASL/NickServ auth from IRCafe."
            + " Configure upstream network auth inside Quassel Core.</html>",
        authDisabledHintLabel.getText());
    assertEquals(
        "(optional until core is configured)",
        serverPasswordField.getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT));
    assertEquals(
        "quassel.example.net", hostField.getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT));
    assertEquals(
        "quassel-user", loginField.getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT));
    assertEquals(
        "display nick (optional)",
        nickField.getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT));
    assertEquals(
        "display name (optional)",
        realNameField.getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT));
  }

  @Test
  void applyEscapesHtmlInAuthHint() {
    JLabel authDisabledHintLabel = new JLabel();

    ServerEditorBackendPresentationApplier.apply(
        new ServerEditorBackendPresentationPolicy.BackendPresentationState(
            "Host",
            "Password",
            "Nick",
            "Login",
            "Real name",
            "TLS",
            "Hint",
            "Use <custom> auth & fallback",
            "",
            "",
            "",
            "",
            ""),
        new ServerEditorBackendPresentationApplier.BackendWidgets(
            new JLabel(),
            new JLabel(),
            new JLabel(),
            new JLabel(),
            new JLabel(),
            new JCheckBox(),
            new JLabel(),
            authDisabledHintLabel,
            new JPasswordField(),
            new JTextField(),
            new JTextField(),
            new JTextField(),
            new JTextField()));

    assertEquals(
        "<html>Use &lt;custom&gt; auth &amp; fallback</html>", authDisabledHintLabel.getText());
  }
}
