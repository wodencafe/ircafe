package cafe.woden.ircclient.ui.settings;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;

final class EmbedsAndPreviewsPanelSupport {
  private EmbedsAndPreviewsPanelSupport() {}

  static JPanel buildPanel(
      ImageEmbedControls image, LinkPreviewControls links, JButton advancedPolicyButton) {
    JPanel form =
        new JPanel(
            new MigLayout(
                "insets 12, fillx, wrap 2", "[right]12[grow,fill]", "[]10[]6[]10[]6[]10[]"));

    form.add(PreferencesDialog.tabTitle("Embeds & Previews"), "span 2, growx, wmin 0, wrap");
    form.add(PreferencesDialog.sectionTitle("Inline images"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("Direct image links"), "aligny top");
    form.add(image.panel, "growx");

    form.add(PreferencesDialog.sectionTitle("Link previews"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("OpenGraph cards"), "aligny top");
    form.add(links.panel, "growx");

    form.add(PreferencesDialog.sectionTitle("Access policy"), "span 2, growx, wmin 0, wrap");
    form.add(new JLabel("Advanced matching rules"), "aligny top");
    JPanel buttonRow = new JPanel(new MigLayout("insets 0", "[]", "[]"));
    buttonRow.setOpaque(false);
    if (advancedPolicyButton != null) {
      buttonRow.add(advancedPolicyButton);
    }
    form.add(buttonRow, "growx");

    return form;
  }
}
