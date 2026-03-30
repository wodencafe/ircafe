package cafe.woden.ircclient.ui.servers;

import java.awt.CardLayout;
import java.util.Objects;
import javax.swing.JComboBox;
import javax.swing.JPanel;

/** Applies auth-mode presentation state to combo/card widgets in the server editor. */
final class ServerEditorAuthModeUiApplier {
  private ServerEditorAuthModeUiApplier() {}

  static void apply(
      ServerEditorAuthModePresentationPolicy.AuthModePresentationState state,
      AuthModeWidgets widgets) {
    widgets.authModeCombo().setEnabled(state.authModeEnabled());
    if (!Objects.equals(widgets.authModeCombo().getSelectedItem(), state.authMode())) {
      widgets.authModeCombo().setSelectedItem(state.authMode());
    }

    CardLayout cardLayout = (CardLayout) widgets.authModeCardPanel().getLayout();
    cardLayout.show(widgets.authModeCardPanel(), cardId(state.authCard(), widgets));
  }

  private static String cardId(
      ServerEditorAuthModePresentationPolicy.ServerEditorAuthCard authCard,
      AuthModeWidgets widgets) {
    return switch (authCard) {
      case SASL -> widgets.saslCardId();
      case NICKSERV -> widgets.nickservCardId();
      case DISABLED -> widgets.disabledCardId();
    };
  }

  record AuthModeWidgets(
      JComboBox<ServerEditorAuthMode> authModeCombo,
      JPanel authModeCardPanel,
      String disabledCardId,
      String saslCardId,
      String nickservCardId) {}
}
