package cafe.woden.ircclient.ui.settings;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;

final class OutgoingColorControlsSupport {
  private OutgoingColorControlsSupport() {}

  static OutgoingColorControls buildControls(Window owner, UiSettings current) {
    JCheckBox outgoingColorEnabled = new JCheckBox("Use custom color for my outgoing messages");
    outgoingColorEnabled.setSelected(current.clientLineColorEnabled());
    outgoingColorEnabled.setToolTipText(
        "If enabled, IRCafe will render lines you send (locally echoed into chat) using a custom color.");

    JTextField outgoingColorHex =
        new JTextField(UiSettings.normalizeHexOrDefault(current.clientLineColor(), "#6AA2FF"), 10);
    outgoingColorHex.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "#RRGGBB");

    JLabel outgoingPreview = new JLabel();
    outgoingPreview.setOpaque(true);
    outgoingPreview.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
    outgoingPreview.setPreferredSize(new Dimension(120, 24));

    JButton outgoingPick = new JButton("Pick...");
    outgoingPick.addActionListener(
        e -> {
          Color currentColor = SettingsColorSupport.parseHexColor(outgoingColorHex.getText());
          if (currentColor == null) {
            currentColor = SettingsColorSupport.parseHexColor(current.clientLineColor());
          }
          if (currentColor == null) {
            currentColor = UIManager.getColor("Label.foreground");
          }
          if (currentColor == null) {
            currentColor = Color.WHITE;
          }

          Color chosen =
              SettingsColorPickerDialogSupport.showColorPickerDialog(
                  owner,
                  "Choose Outgoing Message Color",
                  currentColor,
                  SettingsColorSupport.preferredPreviewBackground());
          if (chosen != null) {
            outgoingColorHex.setText(SettingsColorSupport.toHex(chosen));
          }
        });

    JPanel outgoingColorPanel =
        new JPanel(
            new MigLayout("insets 0, fillx, wrap 3", "[grow,fill]8[nogrid]8[nogrid]", "[]4[]"));
    outgoingColorPanel.setOpaque(false);
    outgoingColorPanel.add(outgoingColorEnabled, "span 3, wrap");
    outgoingColorPanel.add(outgoingColorHex, "w 110!");
    outgoingColorPanel.add(outgoingPick);
    outgoingColorPanel.add(outgoingPreview);

    Runnable updateOutgoingColorUi =
        () -> {
          boolean enabled = outgoingColorEnabled.isSelected();
          outgoingColorHex.setEnabled(enabled);
          outgoingPick.setEnabled(enabled);

          if (!enabled) {
            outgoingPreview.setOpaque(false);
            outgoingPreview.setText("");
            outgoingPreview.repaint();
            return;
          }

          Color c = SettingsColorSupport.parseHexColor(outgoingColorHex.getText());
          if (c != null) {
            outgoingPreview.setOpaque(true);
            outgoingPreview.setBackground(c);
            outgoingPreview.setText(SettingsColorSupport.toHex(c));
          } else {
            outgoingPreview.setOpaque(false);
            outgoingPreview.setText("Invalid");
          }
          outgoingPreview.repaint();
        };

    outgoingColorEnabled.addActionListener(e -> updateOutgoingColorUi.run());
    outgoingColorHex
        .getDocument()
        .addDocumentListener(new PreferencesDialog.SimpleDocListener(updateOutgoingColorUi));
    updateOutgoingColorUi.run();

    return new OutgoingColorControls(
        outgoingColorEnabled, outgoingColorHex, outgoingPreview, outgoingColorPanel);
  }
}
