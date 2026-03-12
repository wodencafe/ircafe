package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.util.EmojiFontSupport;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import org.junit.jupiter.api.Test;

class WrapTextPaneEmojiRenderTest {

  @Test
  void paintsEmojiRunsWithVisiblePixels() throws Exception {
    AtomicReference<BufferedImage> rendered = new AtomicReference<>();

    SwingUtilities.invokeAndWait(
        () -> {
          try {
            WrapTextPane pane = new WrapTextPane();
            pane.setOpaque(true);
            pane.setBackground(Color.WHITE);
            pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

            DefaultStyledDocument doc = new DefaultStyledDocument();
            pane.setDocument(doc);

            SimpleAttributeSet attrs = new SimpleAttributeSet();
            EmojiFontSupport.applyEmojiRunFont(attrs);
            doc.insertString(0, "😀", attrs);

            pane.setSize(48, 32);
            pane.doLayout();
            rendered.set(paint(pane, 48, 32));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });

    assertTrue(nonWhitePixels(rendered.get()) > 30);
  }

  private static BufferedImage paint(WrapTextPane pane, int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, width, height);
      pane.paint(g);
      return image;
    } finally {
      g.dispose();
    }
  }

  private static int nonWhitePixels(BufferedImage image) {
    int count = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) & 0x00FFFFFF) != 0x00FFFFFF) {
          count++;
        }
      }
    }
    return count;
  }
}
