package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.NickColorSettings;
import cafe.woden.ircclient.ui.nickcolors.NickColorOverridesDialog;
import cafe.woden.ircclient.ui.util.MouseWheelDecorator;
import java.awt.Window;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.miginfocom.swing.MigLayout;

final class NickColorControlsSupport {
  private NickColorControlsSupport() {}

  static NickColorControls buildControls(
      Window owner,
      List<AutoCloseable> closeables,
      NickColorService nickColorService,
      NickColorOverridesDialog nickColorOverridesDialog,
      NickColorSettings current) {
    boolean enabledSeed = current == null || current.enabled();
    double minContrastSeed = current != null ? current.minContrast() : 3.0;
    if (minContrastSeed <= 0) minContrastSeed = 3.0;

    JCheckBox enabled = new JCheckBox("Color nicknames (channels and PMs)");
    enabled.setSelected(enabledSeed);
    enabled.setToolTipText(
        "When enabled, IRCafe renders nicknames in deterministic colors (per nick),\n"
            + "adjusted to meet a minimum contrast ratio against the chat background.");

    JSpinner minContrast = doubleSpinner(minContrastSeed, 1.0, 21.0, 0.5, closeables);
    minContrast.setToolTipText(
        "Minimum contrast ratio against the chat background (WCAG-style).\n"
            + "Higher values are safer for readability but may push colors toward lighter/darker extremes.");

    JButton overrides = new JButton("Edit overrides...");
    overrides.setToolTipText(
        "Open the per-nick override editor. Overrides take precedence over the palette.");

    NickColorPreviewPanel preview = new NickColorPreviewPanel(nickColorService);

    Runnable updatePreview =
        () -> {
          boolean previewEnabled = enabled.isSelected();
          double minContrastValue = ((Number) minContrast.getValue()).doubleValue();
          if (minContrastValue <= 0) minContrastValue = 3.0;
          minContrast.setEnabled(previewEnabled);
          preview.updatePreview(previewEnabled, minContrastValue);
        };

    enabled.addActionListener(e -> updatePreview.run());
    minContrast.addChangeListener(e -> updatePreview.run());

    overrides.addActionListener(
        e -> {
          if (nickColorOverridesDialog != null) {
            nickColorOverridesDialog.open(owner);
          }
          updatePreview.run();
        });

    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[grow,fill]8[nogrid]", "[]6[]6[]6[]"));
    panel.setOpaque(false);
    panel.add(enabled, "span 2, wrap");
    panel.add(new JLabel("Minimum contrast ratio:"));
    panel.add(minContrast, "w 110!, wrap");
    panel.add(overrides, "span 2, alignx left, wrap");
    panel.add(
        PreferencesDialog.helpText(
            "Tip: If nick colors look too similar to the background, increase the contrast ratio.\n"
                + "Overrides always win over the palette."),
        "span 2, growx, wmin 0, wrap");
    panel.add(new JLabel("Preview:"), "span 2, wrap");
    panel.add(preview, "span 2, growx");
    updatePreview.run();

    return new NickColorControls(enabled, minContrast, overrides, panel);
  }

  private static JSpinner doubleSpinner(
      double value, double min, double max, double step, List<AutoCloseable> closeables) {
    JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
    AutoCloseable decoration = MouseWheelDecorator.decorateNumberSpinner(spinner);
    if (decoration != null) closeables.add(decoration);
    return spinner;
  }
}
