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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.LabelView;
import javax.swing.text.Position;
import javax.swing.text.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Paints emoji-tagged styled-document runs using bundled image assets instead of font glyphs. */
final class EmojiInlineView extends LabelView {
  private static final Logger log = LoggerFactory.getLogger(EmojiInlineView.class);
  private static final ImageSource BUNDLED_IMAGES =
      new ImageSource() {
        @Override
        public EmojiImageSupport.CachedImage lookup(String text, int size) {
          return EmojiImageSupport.cachedImageFor(text, size);
        }

        @Override
        public void load(String text, int size) {
          EmojiImageSupport.imageFor(text, size);
        }
      };

  interface ImageSource {
    EmojiImageSupport.CachedImage lookup(String text, int size);

    void load(String text, int size);
  }

  private final ImageSource images;
  private String cachedText = "";
  private List<String> cachedClusters = List.of();
  private SwingWorker<Void, Void> imageWorker;
  private Timer retryTimer;

  EmojiInlineView(Element elem) {
    this(elem, BUNDLED_IMAGES);
  }

  EmojiInlineView(Element elem, ImageSource images) {
    super(elem);
    this.images = Objects.requireNonNull(images, "images");
  }

  @Override
  public void setParent(View parent) {
    if (parent == null) {
      SwingWorker<Void, Void> worker = imageWorker;
      imageWorker = null;
      if (worker != null) worker.cancel(true);
      if (retryTimer != null) retryTimer.stop();
    }
    super.setParent(parent);
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
      boolean needsLoad = false;

      for (String cluster : clusters) {
        EmojiImageSupport.CachedImage cached = images.lookup(cluster, boxSize);
        needsLoad |= !cached.resolved();
        BufferedImage image = cached.image();
        if (image != null) {
          g2.drawImage(image, x, y, null);
          x += boxSize;
          continue;
        }

        g2.setFont(font);
        g2.setColor(getForeground());
        g2.drawString(cluster, x, baseline);
        // Keep layout stable while the image is loading (and for unsupported emoji).
        x += boxSize;
      }
      if (needsLoad) requestImages(clusters, boxSize);
    } finally {
      g2.dispose();
    }
  }

  private void requestImages(List<String> clusters, int size) {
    if (imageWorker != null || getParent() == null || getContainer() == null) return;
    if (retryTimer != null && retryTimer.isRunning()) return;
    List<String> requests = clusters.stream().distinct().toList();
    SwingWorker<Void, Void> worker =
        new SwingWorker<>() {
          @Override
          protected Void doInBackground() {
            for (String text : requests) {
              if (isCancelled()) break;
              images.load(text, size);
            }
            return null;
          }

          @Override
          protected void done() {
            if (imageWorker != this) return;
            imageWorker = null;
            try {
              get();
              repaintIfAttached();
            } catch (CancellationException ignored) {
              // Detached views cancel their load; they must not repaint their former container.
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            } catch (ExecutionException ex) {
              log.warn("[ircafe] loading inline emoji images failed", ex.getCause());
            }
          }
        };
    imageWorker = worker;
    if (!EmojiImageSupport.executeLoad(worker)) {
      imageWorker = null;
      worker.cancel(false);
      if (retryTimer == null) {
        retryTimer = new Timer(100, event -> repaintIfAttached());
        retryTimer.setRepeats(false);
      }
      retryTimer.restart();
    }
  }

  private void repaintIfAttached() {
    if (getParent() != null && getContainer() != null && getContainer().isDisplayable()) {
      getContainer().repaint();
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
    return Math.clamp(Math.round(size * 1.25f), 12, 256);
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
