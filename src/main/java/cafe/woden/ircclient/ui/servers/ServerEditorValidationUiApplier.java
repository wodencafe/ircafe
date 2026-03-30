package cafe.woden.ircclient.ui.servers;

import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JComponent;

/** Applies server-editor validation state to Swing widgets. */
final class ServerEditorValidationUiApplier {
  private static final String OUTLINE_PROP = "JComponent.outline";
  private static final String OUTLINE_ERROR = "error";
  private static final String OUTLINE_WARNING = "warning";
  private static final String OUTLINE_SUCCESS = "success";

  private ServerEditorValidationUiApplier() {}

  static void apply(ServerEditorValidationPolicy.ValidationState state, ValidationWidgets widgets) {
    setError(widgets.idField(), state.connectionValidation().idBad());
    setError(widgets.hostField(), state.connectionValidation().hostBad());
    setError(widgets.portField(), state.connectionValidation().portBad());
    setError(widgets.serverPasswordField(), state.matrixValidation().credentialBad());

    if (state.matrixValidation().applicable()) {
      setError(widgets.matrixAuthUserField(), state.matrixValidation().usernameBad());
    } else {
      clearOutline(widgets.matrixAuthUserField());
    }

    setError(widgets.loginField(), false);
    setError(widgets.nickField(), state.connectionValidation().nickBad());

    if (!state.saslValidation().applicable()) {
      clearOutline(widgets.saslUserField());
      clearOutline(widgets.saslSecretField());
    } else {
      setError(widgets.saslUserField(), state.saslValidation().userBad());
      setError(widgets.saslSecretField(), state.saslValidation().secretBad());
    }

    if (!state.nickservValidation().applicable()) {
      clearOutline(widgets.nickservServiceField());
      clearOutline(widgets.nickservPasswordField());
    } else {
      setError(widgets.nickservServiceField(), false);
      setError(widgets.nickservPasswordField(), state.nickservValidation().passwordBad());
    }

    if (!state.proxyValidation().applicable()) {
      clearOutline(widgets.proxyHostField());
      clearOutline(widgets.proxyPortField());
      clearOutline(widgets.proxyUserField());
      clearOutline(widgets.proxyPasswordField());
      clearOutline(widgets.proxyConnectTimeoutField());
      clearOutline(widgets.proxyReadTimeoutField());
    } else {
      setWarning(
          widgets.proxyConnectTimeoutField(), state.proxyValidation().connectTimeoutWarning());
      setWarning(widgets.proxyReadTimeoutField(), state.proxyValidation().readTimeoutWarning());

      if (!state.proxyValidation().proxyDetailsApplicable()) {
        clearOutline(widgets.proxyHostField());
        clearOutline(widgets.proxyPortField());
        clearOutline(widgets.proxyUserField());
        clearOutline(widgets.proxyPasswordField());
      } else {
        setError(widgets.proxyHostField(), state.proxyValidation().hostBad());
        setError(widgets.proxyPortField(), state.proxyValidation().portBad());
        setWarning(widgets.proxyUserField(), state.proxyValidation().authMismatch());
        setWarning(widgets.proxyPasswordField(), state.proxyValidation().authMismatch());
      }
    }

    widgets.saveButton().setEnabled(state.saveEnabled());
    widgets
        .saveButton()
        .setToolTipText(state.saveEnabled() ? null : "Fix highlighted fields to enable Save.");
  }

  static void clearOutline(JComponent component) {
    component.putClientProperty(OUTLINE_PROP, null);
  }

  static void setSuccess(JComponent component, boolean on) {
    Object current = component.getClientProperty(OUTLINE_PROP);
    if (on) {
      if (current == null) {
        component.putClientProperty(OUTLINE_PROP, OUTLINE_SUCCESS);
      }
    } else if (Objects.equals(current, OUTLINE_SUCCESS)) {
      clearOutline(component);
    }
  }

  private static void setError(JComponent component, boolean on) {
    component.putClientProperty(OUTLINE_PROP, on ? OUTLINE_ERROR : null);
  }

  private static void setWarning(JComponent component, boolean on) {
    component.putClientProperty(OUTLINE_PROP, on ? OUTLINE_WARNING : null);
  }

  record ValidationWidgets(
      JComponent idField,
      JComponent hostField,
      JComponent portField,
      JComponent serverPasswordField,
      JComponent matrixAuthUserField,
      JComponent loginField,
      JComponent nickField,
      JComponent saslUserField,
      JComponent saslSecretField,
      JComponent nickservServiceField,
      JComponent nickservPasswordField,
      JComponent proxyHostField,
      JComponent proxyPortField,
      JComponent proxyUserField,
      JComponent proxyPasswordField,
      JComponent proxyConnectTimeoutField,
      JComponent proxyReadTimeoutField,
      JButton saveButton) {}
}
