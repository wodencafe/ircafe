package cafe.woden.ircclient.ui.servers;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Container;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Applies auth-related UI state to the server-editor Swing components. */
final class ServerEditorAuthUiApplier {
  private ServerEditorAuthUiApplier() {}

  static void applyMatrixUi(
      ServerEditorAuthUiPolicy.MatrixUiState state, MatrixAuthWidgets widgets) {
    widgets.authModeLabel().setVisible(state.authModeControlsVisible());
    widgets.authModeCombo().setVisible(state.authModeControlsVisible());
    widgets.authModeCardPanel().setVisible(state.authModeCardVisible());
    widgets.matrixAuthModeLabel().setVisible(state.matrixAuthControlsVisible());
    widgets.matrixAuthModeCombo().setVisible(state.matrixAuthControlsVisible());
    widgets.matrixAuthHintLabel().setVisible(state.matrixAuthControlsVisible());
    widgets.matrixAuthUserLabel().setVisible(state.matrixAuthUserVisible());
    widgets.matrixAuthUserField().setVisible(state.matrixAuthUserVisible());
    widgets.matrixAuthUserField().setEnabled(state.matrixAuthUserEnabled());

    if (!state.matrixAuthControlsVisible()) {
      widgets.matrixAuthHintLabel().setText(" ");
      widgets.matrixAuthHintLabel().setToolTipText(null);
      refresh(widgets.refreshTarget());
      return;
    }

    widgets.serverPasswordLabel().setText(state.serverPasswordLabel());
    widgets
        .serverPasswordField()
        .putClientProperty(
            FlatClientProperties.PLACEHOLDER_TEXT, state.serverPasswordPlaceholder());
    widgets.matrixAuthHintLabel().setText(asHtml(state.hint()));
    widgets.matrixAuthHintLabel().setToolTipText(state.hint());
    refresh(widgets.refreshTarget());
  }

  static void applySaslUi(ServerEditorAuthUiPolicy.SaslUiState state, SaslWidgets widgets) {
    widgets.mechanismCombo().setEnabled(state.mechanismEnabled());
    widgets.continueOnFailureBox().setEnabled(state.continueOnFailureEnabled());
    widgets.userField().setEnabled(state.userEnabled());
    widgets.secretField().setEnabled(state.secretEnabled());
    widgets.hintLabel().setText(state.hintVisible() ? asHtml(state.hint()) : " ");
    widgets.hintLabel().setToolTipText(state.hint());
    widgets
        .secretField()
        .putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, state.secretPlaceholder());
  }

  static void applyNickservUi(
      ServerEditorAuthUiPolicy.NickservUiState state, NickservWidgets widgets) {
    widgets.serviceField().setEnabled(state.enabled());
    widgets.passwordField().setEnabled(state.enabled());
    widgets.delayJoinBox().setEnabled(state.enabled());
    widgets.hintLabel().setText(state.enabled() ? asHtml(state.hint()) : " ");
    widgets.hintLabel().setToolTipText(state.hint());
  }

  private static void refresh(Container target) {
    if (target != null) {
      target.revalidate();
      target.repaint();
    }
  }

  private static String asHtml(String text) {
    return "<html>" + escapeHtml(text) + "</html>";
  }

  private static String escapeHtml(String text) {
    if (text == null || text.isEmpty()) return "";
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  record MatrixAuthWidgets(
      JLabel authModeLabel,
      JComboBox<?> authModeCombo,
      JPanel authModeCardPanel,
      JLabel matrixAuthModeLabel,
      JComboBox<?> matrixAuthModeCombo,
      JLabel matrixAuthHintLabel,
      JLabel matrixAuthUserLabel,
      JTextField matrixAuthUserField,
      JLabel serverPasswordLabel,
      JTextField serverPasswordField,
      Container refreshTarget) {}

  record SaslWidgets(
      JComboBox<?> mechanismCombo,
      JCheckBox continueOnFailureBox,
      JTextField userField,
      JPasswordField secretField,
      JLabel hintLabel) {}

  record NickservWidgets(
      JTextField serviceField,
      JPasswordField passwordField,
      JCheckBox delayJoinBox,
      JLabel hintLabel) {}
}
