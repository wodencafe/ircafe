package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.util.EmojiImageSupport;
import cafe.woden.ircclient.ui.util.EmojiTextSupport;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.LabelView;
import javax.swing.text.Position;

/** Paints emoji-tagged styled-document runs using bundled image assets instead of font glyphs. */
final class EmojiInlineView extends LabelView {

  private String cachedText = "";
  private List<String> cachedClusters = List.of();

  EmojiInlineView(Element elem) {
    super(elem);
  }

  @Override
  public float getPreferredSpan(int axis) {
    if (axis == X_AXIS) {
      List<String> clusters = emojiClusters();
      if (clusters.isEmpty()) {
        return super.getPreferredSpan(axis);
      }
      return clusters.size() * emojiBoxSize();
    }
    if (axis == Y_AXIS) {
      return Math.max(super.getPreferredSpan(axis), emojiBoxSize());
    }
    return super.getPreferredSpan(axis);
  }

  @Override
  public void paint(Graphics g, Shape allocation) {
    List<String> clusters = emojiClusters();
    if (clusters.isEmpty()) {
      super.paint(g, allocation);
      return;
    }

    Rectangle bounds = allocation instanceof Rectangle r ? r : allocation.getBounds();
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      Font font = getFont();
      FontMetrics metrics = getFontMetrics(font);
      int boxSize = emojiBoxSize();
      int x = bounds.x;
      int y = bounds.y + Math.max(0, (bounds.height - boxSize) / 2);
      int baseline = bounds.y + metrics.getAscent();

      for (String cluster : clusters) {
        BufferedImage image = EmojiImageSupport.imageFor(cluster, boxSize);
        if (image != null) {
          g2.drawImage(image, x, y, null);
          x += boxSize;
          continue;
        }

        g2.setFont(font);
        g2.drawString(cluster, x, baseline);
        x += metrics.stringWidth(cluster);
      }
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Shape modelToView(int pos, Shape allocation, Position.Bias bias)
      throws BadLocationException {
    Rectangle bounds = allocation instanceof Rectangle r ? r : allocation.getBounds();
    int start = getStartOffset();
    int end = getEndOffset();
    int len = Math.max(1, end - start);
    int rel = Math.max(0, Math.min(len, pos - start));
    int x = bounds.x + Math.round(getPreferredSpan(X_AXIS) * (rel / (float) len));
    return new Rectangle(x, bounds.y, 1, bounds.height);
  }

  @Override
  public int viewToModel(float x, float y, Shape allocation, Position.Bias[] biasReturn) {
    Rectangle bounds = allocation instanceof Rectangle r ? r : allocation.getBounds();
    int start = getStartOffset();
    int end = getEndOffset();
    int len = Math.max(1, end - start);
    float width = Math.max(1f, getPreferredSpan(X_AXIS));
    float frac = Math.max(0f, Math.min(1f, (x - bounds.x) / width));
    if (biasReturn != null && biasReturn.length > 0) {
      biasReturn[0] = Position.Bias.Forward;
    }
    return start + Math.round(frac * len);
  }

  private FontMetrics getFontMetrics(Font font) {
    if (getContainer() != null) {
      return getContainer().getFontMetrics(font);
    }
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      return g.getFontMetrics(font);
    } finally {
      g.dispose();
    }
  }

  private int emojiBoxSize() {
    Font font = getFont();
    float size = font != null ? font.getSize2D() : 12f;
    return Math.max(12, Math.round(size * 1.25f));
  }

  private List<String> emojiClusters() {
    String text = currentText();
    if (!Objects.equals(cachedText, text)) {
      cachedText = text;
      cachedClusters =
          EmojiTextSupport.clusters(text).stream().map(EmojiTextSupport.Cluster::text).toList();
    }
    return cachedClusters;
  }

  private String currentText() {
    int start = getStartOffset();
    int end = getEndOffset();
    if (end <= start) {
      return "";
    }
    try {
      return getDocument().getText(start, end - start);
    } catch (BadLocationException ignored) {
      return "";
    }
  }
}
