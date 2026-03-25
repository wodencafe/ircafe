package cafe.woden.ircclient.ui.settings;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;

final class ImageEmbedControls {
  final JCheckBox enabled;
  final JCheckBox collapsed;
  final JSpinner maxWidth;
  final JSpinner maxHeight;
  final JCheckBox animateGifs;
  final JPanel panel;

  ImageEmbedControls(
      JCheckBox enabled,
      JCheckBox collapsed,
      JSpinner maxWidth,
      JSpinner maxHeight,
      JCheckBox animateGifs,
      JPanel panel) {
    this.enabled = enabled;
    this.collapsed = collapsed;
    this.maxWidth = maxWidth;
    this.maxHeight = maxHeight;
    this.animateGifs = animateGifs;
    this.panel = panel;
  }
}

final class LinkPreviewControls {
  final JCheckBox enabled;
  final JCheckBox collapsed;
  final JComboBox<EmbedCardStyle> cardStyle;
  final JPanel panel;

  LinkPreviewControls(
      JCheckBox enabled, JCheckBox collapsed, JComboBox<EmbedCardStyle> cardStyle, JPanel panel) {
    this.enabled = enabled;
    this.collapsed = collapsed;
    this.cardStyle = cardStyle;
    this.panel = panel;
  }
}
