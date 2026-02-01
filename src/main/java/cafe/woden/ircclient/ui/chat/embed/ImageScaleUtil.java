package cafe.woden.ircclient.ui.chat.embed;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Small helpers for scaling images with decent quality. */
final class ImageScaleUtil {

  private ImageScaleUtil() {}

  static BufferedImage scaleDownToWidth(BufferedImage src, int maxW) {
    if (src == null) return null;
    int w = src.getWidth();
    int h = src.getHeight();
    if (w <= 0 || h <= 0) return src;
    if (maxW <= 0) return src;
    if (w <= maxW) return src;

    double scale = (double) maxW / (double) w;
    int tw = Math.max(1, maxW);
    int th = Math.max(1, (int) Math.round(h * scale));

    BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.drawImage(src, 0, 0, tw, th, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  /**
   * Scale the image down to fit within the given bounds while preserving aspect ratio.
   *
   * <p>Either bound may be <= 0 to indicate "no cap" for that dimension.
   */
  static BufferedImage scaleDownToFit(BufferedImage src, int maxW, int maxH) {
    if (src == null) return null;
    int w = src.getWidth();
    int h = src.getHeight();
    if (w <= 0 || h <= 0) return src;

    double scaleW = (maxW > 0) ? ((double) maxW / (double) w) : 1.0;
    double scaleH = (maxH > 0) ? ((double) maxH / (double) h) : 1.0;
    double scale = Math.min(1.0, Math.min(scaleW, scaleH));
    if (scale >= 1.0) return src;

    int tw = Math.max(1, (int) Math.round(w * scale));
    int th = Math.max(1, (int) Math.round(h * scale));

    BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.drawImage(src, 0, 0, tw, th, null);
    } finally {
      g.dispose();
    }
    return out;
  }
}
