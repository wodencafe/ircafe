package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.CardLayout;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class ServerEditorAuthModeUiApplierTest {

  @Test
  void applySelectsEffectiveAuthModeAndVisibleCard() {
    JComboBox<ServerEditorAuthMode> authModeCombo = new JComboBox<>(ServerEditorAuthMode.values());
    authModeCombo.setSelectedItem(ServerEditorAuthMode.DISABLED);

    JPanel authModeCardPanel = new JPanel(new CardLayout());
    JPanel disabledCard = new JPanel();
    JPanel saslCard = new JPanel();
    JPanel nickservCard = new JPanel();
    authModeCardPanel.add(disabledCard, "auth-disabled");
    authModeCardPanel.add(saslCard, "auth-sasl");
    authModeCardPanel.add(nickservCard, "auth-nickserv");

    ServerEditorAuthModeUiApplier.apply(
        new ServerEditorAuthModePresentationPolicy.AuthModePresentationState(
            true,
            ServerEditorAuthMode.NICKSERV,
            ServerEditorAuthModePresentationPolicy.ServerEditorAuthCard.NICKSERV),
        new ServerEditorAuthModeUiApplier.AuthModeWidgets(
            authModeCombo, authModeCardPanel, "auth-disabled", "auth-sasl", "auth-nickserv"));

    assertTrue(authModeCombo.isEnabled());
    assertEquals(ServerEditorAuthMode.NICKSERV, authModeCombo.getSelectedItem());
    assertFalse(disabledCard.isVisible());
    assertFalse(saslCard.isVisible());
    assertTrue(nickservCard.isVisible());
  }

  @Test
  void applyCanDisableComboAndShowDisabledCard() {
    JComboBox<ServerEditorAuthMode> authModeCombo = new JComboBox<>(ServerEditorAuthMode.values());
    authModeCombo.setSelectedItem(ServerEditorAuthMode.SASL);

    JPanel authModeCardPanel = new JPanel(new CardLayout());
    JPanel disabledCard = new JPanel();
    JPanel saslCard = new JPanel();
    JPanel nickservCard = new JPanel();
    authModeCardPanel.add(disabledCard, "auth-disabled");
    authModeCardPanel.add(saslCard, "auth-sasl");
    authModeCardPanel.add(nickservCard, "auth-nickserv");

    ServerEditorAuthModeUiApplier.apply(
        new ServerEditorAuthModePresentationPolicy.AuthModePresentationState(
            false,
            ServerEditorAuthMode.DISABLED,
            ServerEditorAuthModePresentationPolicy.ServerEditorAuthCard.DISABLED),
        new ServerEditorAuthModeUiApplier.AuthModeWidgets(
            authModeCombo, authModeCardPanel, "auth-disabled", "auth-sasl", "auth-nickserv"));

    assertFalse(authModeCombo.isEnabled());
    assertEquals(ServerEditorAuthMode.DISABLED, authModeCombo.getSelectedItem());
    assertTrue(disabledCard.isVisible());
    assertFalse(saslCard.isVisible());
    assertFalse(nickservCard.isVisible());
  }
}
