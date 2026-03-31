package cafe.woden.ircclient.ui.servers;

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JTextField;

/** Applies backend-profile-driven presentation state to the server-editor widgets. */
final class ServerEditorBackendPresentationApplier {
  private ServerEditorBackendPresentationApplier() {}

  static void apply(
      ServerEditorBackendPresentationPolicy.BackendPresentationState state,
      BackendWidgets widgets) {
    widgets.hostLabel().setText(state.hostLabel());
    widgets.serverPasswordLabel().setText(state.serverPasswordLabel());
    widgets.nickLabel().setText(state.nickLabel());
    widgets.loginLabel().setText(state.loginLabel());
    widgets.realNameLabel().setText(state.realNameLabel());
    widgets.tlsToggle().setText(state.tlsToggleLabel());
    widgets.connectionHintLabel().setText(state.connectionHint());
    widgets.authDisabledHintLabel().setText(asHtml(state.authDisabledHint()));
    applyPlaceholder(widgets.serverPasswordField(), state.serverPasswordPlaceholder());
    applyPlaceholder(widgets.hostField(), state.hostPlaceholder());
    applyPlaceholder(widgets.loginField(), state.loginPlaceholder());
    applyPlaceholder(widgets.nickField(), state.nickPlaceholder());
    applyPlaceholder(widgets.realNameField(), state.realNamePlaceholder());
  }

  private static void applyPlaceholder(JTextField field, String placeholder) {
    field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
  }

  private static String asHtml(String text) {
    return "<html>" + escapeHtml(text) + "</html>";
  }

  private static String escapeHtml(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  record BackendWidgets(
      JLabel hostLabel,
      JLabel serverPasswordLabel,
      JLabel nickLabel,
      JLabel loginLabel,
      JLabel realNameLabel,
      AbstractButton tlsToggle,
      JLabel connectionHintLabel,
      JLabel authDisabledHintLabel,
      JTextField serverPasswordField,
      JTextField hostField,
      JTextField loginField,
      JTextField nickField,
      JTextField realNameField) {}
}
