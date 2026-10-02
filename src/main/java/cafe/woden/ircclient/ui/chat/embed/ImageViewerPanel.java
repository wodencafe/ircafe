package cafe.woden.ircclient.ui.chat.embed;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JPanel;

/** Paints the original image at a size that fits the current viewer, retaining GIF animation. */
final class ImageViewerPanel extends JPanel {

  private ImageIcon image;

  ImageViewerPanel() {
    setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
  }

  void setImage(ImageIcon image) {
    this.image = image;
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (image == null || image.getIconWidth() <= 0 || image.getIconHeight() <= 0) return;

    Insets insets = getInsets();
    int availableWidth = getWidth() - insets.left - insets.right;
    int availableHeight = getHeight() - insets.top - insets.bottom;
    if (availableWidth <= 0 || availableHeight <= 0) return;

    double scale =
        Math.min(
            1.0,
            Math.min(
                (double) availableWidth / image.getIconWidth(),
                (double) availableHeight / image.getIconHeight()));
    int width = Math.max(1, (int) Math.floor(image.getIconWidth() * scale));
    int height = Math.max(1, (int) Math.floor(image.getIconHeight() * scale));
    int x = insets.left + (availableWidth - width) / 2;
    int y = insets.top + (availableHeight - height) / 2;

    Graphics2D scaled = (Graphics2D) g.create();
    try {
      scaled.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      // Passing this component as observer lets animated GIF frames trigger repainting.
      scaled.drawImage(image.getImage(), x, y, width, height, this);
    } finally {
      scaled.dispose();
    }
  }
}
