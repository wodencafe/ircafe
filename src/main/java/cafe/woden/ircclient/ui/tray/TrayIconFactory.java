package cafe.woden.ircclient.ui.tray;

import cafe.woden.ircclient.ui.icons.AppIcons;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Small programmatic tray icon.
 *
 * <p>We generate an icon instead of relying on external resources so the dev build "just works".
 * This can be swapped to a bundled PNG/ICO later.
 */
final class TrayIconFactory {

  private TrayIconFactory() {
  }

  static Image createDefaultTrayImage() {
    int size = 16;
    BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      // Background badge
      g.setColor(new Color(0x5A3CFF));
      g.fillRoundRect(1, 1, size - 2, size - 2, 6, 6);

      // Thin border for contrast on light trays
      g.setColor(new Color(0x2B1C7A));
      g.setStroke(new BasicStroke(1f));
      g.drawRoundRect(1, 1, size - 3, size - 3, 6, 6);

      // "#" glyph
      g.setColor(Color.WHITE);
      Font font = new Font("SansSerif", Font.BOLD, 12);
      g.setFont(font);
      FontMetrics fm = g.getFontMetrics();
      String text = "#";
      int x = (size - fm.stringWidth(text)) / 2;
      int y = (size - fm.getHeight()) / 2 + fm.getAscent() - 1;
      g.drawString(text, x, y);
    } finally {
      g.dispose();
    }
    return img;
  }

  static InputStream createDefaultTrayIconPngStream() {
    // Prefer bundled tray icon (looks consistent with the app icon).
    InputStream bundled = AppIcons.trayPngStream();
    if (bundled != null) {
      return bundled;
    }

    try {
      BufferedImage img = (BufferedImage) createDefaultTrayImage();
      ByteArrayOutputStream out = new ByteArrayOutputStream(8 * 1024);
      ImageIO.write(img, "png", out);
      return new ByteArrayInputStream(out.toByteArray());
    } catch (Exception e) {
      // ByteArrayInputStream doesn't really need closing, and this is best-effort.
      return new ByteArrayInputStream(new byte[0]);
    }
  }
}
