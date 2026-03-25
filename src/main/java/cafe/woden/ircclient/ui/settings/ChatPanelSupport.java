package cafe.woden.ircclient.ui.settings;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;

final class ChatPanelSupport {
  private ChatPanelSupport() {}

  static JPanel buildPanel(
      JCheckBox presenceFolds,
      JCheckBox ctcpRequestsInActiveTarget,
      JTextField defaultQuitMessage,
      SpellcheckControls spellcheck,
      NickColorControls nickColors,
      TimestampControls timestamps,
      OutgoingColorControls outgoing,
      JCheckBox outgoingDeliveryIndicators) {
    JPanel form =
        new JPanel(new MigLayout("insets 12, fill, wrap 1", "[grow,fill]", "[]10[grow,fill]"));
    form.add(PreferencesDialog.tabTitle("Chat"), "growx, wmin 0, wrap");

    JTabbedPane chatTabs = new JTabbedPane();
    chatTabs.addTab(
        "General",
        PreferencesDialog.padSubTab(
            buildGeneralSubTab(
                presenceFolds,
                ctcpRequestsInActiveTarget,
                defaultQuitMessage,
                nickColors,
                timestamps,
                outgoing,
                outgoingDeliveryIndicators)));
    chatTabs.addTab("Spellcheck", PreferencesDialog.padSubTab(buildSpellcheckSubTab(spellcheck)));
    form.add(chatTabs, "grow, push, wmin 0");
    return form;
  }

  private static JPanel buildGeneralSubTab(
      JCheckBox presenceFolds,
      JCheckBox ctcpRequestsInActiveTarget,
      JTextField defaultQuitMessage,
      NickColorControls nickColors,
      TimestampControls timestamps,
      OutgoingColorControls outgoing,
      JCheckBox outgoingDeliveryIndicators) {
    JPanel panel =
        new JPanel(
            new MigLayout(
                "insets 0, fillx, wrap 2", "[right]12[grow,fill]", "[]8[]6[]6[]6[]10[]6[]"));
    panel.setOpaque(false);

    panel.add(PreferencesDialog.sectionTitle("Display"), "span 2, growx, wmin 0, wrap");
    panel.add(new JLabel("Presence events"), "aligny top");
    panel.add(presenceFolds, "alignx left");

    panel.add(new JLabel("CTCP requests"), "aligny top");
    panel.add(ctcpRequestsInActiveTarget, "alignx left");

    panel.add(new JLabel("Nick colors"), "aligny top");
    panel.add(nickColors.panel, "growx, wmin 0");

    panel.add(new JLabel("Timestamps"), "aligny top");
    panel.add(timestamps.panel, "growx, wmin 0");

    panel.add(PreferencesDialog.sectionTitle("Your messages"), "span 2, growx, wmin 0, wrap");
    panel.add(new JLabel("Outgoing messages"), "aligny top");
    panel.add(outgoing.panel, "growx, wmin 0");
    panel.add(new JLabel("Delivery indicators"), "aligny top");
    panel.add(outgoingDeliveryIndicators, "alignx left");
    panel.add(new JLabel("Default /quit message"), "aligny top");
    panel.add(defaultQuitMessage, "growx, wmin 0");

    return panel;
  }

  private static JPanel buildSpellcheckSubTab(SpellcheckControls spellcheck) {
    JPanel panel = new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]8[]"));
    panel.setOpaque(false);
    panel.add(PreferencesDialog.sectionTitle("Input"), "growx, wmin 0, wrap");
    panel.add(spellcheck.panel, "growx, wmin 0, wrap");
    panel.add(
        PreferencesDialog.helpText("Spellcheck settings are scoped to the message input bar."),
        "growx, wmin 0, wrap");
    return panel;
  }
}
