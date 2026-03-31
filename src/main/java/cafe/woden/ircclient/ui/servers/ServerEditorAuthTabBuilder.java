package cafe.woden.ircclient.ui.servers;

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;

/** Builds the server-editor auth tab and its auth-mode cards from existing widgets. */
final class ServerEditorAuthTabBuilder {
  private ServerEditorAuthTabBuilder() {}

  static JPanel build(AuthTabWidgets widgets) {
    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 8, fillx, wrap 2, hidemode 3",
                "[right]12[grow,fill,min:0]",
                "[]6[]6[]6[]8[grow,fill,min:0]"));

    panel.add(widgets.matrixAuthModeLabel());
    panel.add(widgets.matrixAuthModeCombo(), "growx, wmin 0, wrap");
    panel.add(widgets.matrixAuthUserLabel());
    panel.add(widgets.matrixAuthUserField(), "growx, wmin 0, wrap");
    panel.add(widgets.serverPasswordLabel());
    panel.add(widgets.serverPasswordField(), "growx, wmin 0, wrap");
    panel.add(widgets.authModeLabel());
    panel.add(widgets.authModeCombo(), "growx, wmin 0, wrap");

    styleHint(widgets.matrixAuthHintLabel(), " ");
    panel.add(widgets.matrixAuthHintLabel(), "span 2, growx, wmin 0, wrap");

    widgets.authModeCardPanel().removeAll();
    widgets.authModeCardPanel().add(buildDisabledCard(widgets), widgets.disabledCardId());
    widgets.authModeCardPanel().add(buildSaslCard(widgets), widgets.saslCardId());
    widgets.authModeCardPanel().add(buildNickservCard(widgets), widgets.nickservCardId());
    panel.add(widgets.authModeCardPanel(), "span 2, grow, push, wmin 0");

    return panel;
  }

  private static JPanel buildDisabledCard(AuthTabWidgets widgets) {
    JPanel panel = new JPanel(new MigLayout("insets 6 0 0 0, fillx", "[grow,fill,min:0]", "[]"));
    styleHint(widgets.authDisabledHintLabel(), asHtml(widgets.authDisabledHintText()));
    panel.add(widgets.authDisabledHintLabel(), "growx, wmin 0");
    return panel;
  }

  private static JPanel buildSaslCard(AuthTabWidgets widgets) {
    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 2", "[right]12[grow,fill,min:0]", "[]6[]6[]6[]8[]push"));
    panel.add(new JLabel("Username"));
    panel.add(widgets.saslUserField(), "growx, wmin 0, wrap");
    panel.add(new JLabel("Secret"));
    panel.add(widgets.saslPasswordField(), "growx, wmin 0, wrap");
    panel.add(new JLabel("Mechanism"));
    panel.add(widgets.saslMechanismCombo(), "growx, wmin 0, wrap");
    panel.add(new JLabel("On failure"), "top");
    panel.add(widgets.saslContinueOnFailureBox(), "growx, wmin 0, wrap");

    styleHint(widgets.saslHintLabel(), " ");
    panel.add(widgets.saslHintLabel(), "span 2, growx, wmin 0, pushy");
    return panel;
  }

  private static JPanel buildNickservCard(AuthTabWidgets widgets) {
    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 2", "[right]12[grow,fill,min:0]", "[]6[]6[]8[]push"));
    panel.add(new JLabel("Service"));
    panel.add(widgets.nickservServiceField(), "growx, wmin 0, wrap");
    panel.add(new JLabel("Password"));
    panel.add(widgets.nickservPasswordField(), "growx, wmin 0, wrap");
    panel.add(new JLabel("Delay auto-join"), "top");
    panel.add(widgets.nickservDelayJoinBox(), "growx, wmin 0, wrap");

    styleHint(widgets.nickservHintLabel(), " ");
    panel.add(widgets.nickservHintLabel(), "span 2, growx, wmin 0, pushy");
    return panel;
  }

  private static void styleHint(JLabel label, String text) {
    label.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
    label.setText(text);
  }

  private static String asHtml(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    return "<html>" + escapeHtml(text) + "</html>";
  }

  private static String escapeHtml(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  record AuthTabWidgets(
      JLabel matrixAuthModeLabel,
      JComboBox<ServerEditorMatrixAuthMode> matrixAuthModeCombo,
      JLabel matrixAuthUserLabel,
      JTextField matrixAuthUserField,
      JLabel serverPasswordLabel,
      JPasswordField serverPasswordField,
      JLabel authModeLabel,
      JComboBox<ServerEditorAuthMode> authModeCombo,
      JLabel matrixAuthHintLabel,
      JPanel authModeCardPanel,
      String disabledCardId,
      String saslCardId,
      String nickservCardId,
      String authDisabledHintText,
      JLabel authDisabledHintLabel,
      JTextField saslUserField,
      JPasswordField saslPasswordField,
      JComboBox<String> saslMechanismCombo,
      JCheckBox saslContinueOnFailureBox,
      JLabel saslHintLabel,
      JTextField nickservServiceField,
      JPasswordField nickservPasswordField,
      JCheckBox nickservDelayJoinBox,
      JLabel nickservHintLabel) {}
}
