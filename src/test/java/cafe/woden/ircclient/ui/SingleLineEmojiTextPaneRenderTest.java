package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.util.EmojiFontSupport;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;
import org.junit.jupiter.api.Test;

class SingleLineEmojiTextPaneRenderTest {

  @Test
  void caretLayoutDoesNotWrapLongDraftAtCurrentComponentWidth() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            SingleLineEmojiTextPane pane = new SingleLineEmojiTextPane();
            pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            pane.setText("hello");
            pane.setSize(120, pane.getPreferredSize().height);
            double firstLineY = pane.modelToView2D(0).getY();
            int firstLineHeight = pane.getPreferredSize().height;
            pane.setText("hello alice, this draft is wider than the input viewport");
            var caretBounds = pane.modelToView2D(pane.getDocument().getLength());
            assertEquals(
                firstLineY, caretBounds.getY(), "completion caret must stay on the first line");
            assertEquals(firstLineHeight, pane.getPreferredSize().height);
          } catch (javax.swing.text.BadLocationException e) {
            throw new RuntimeException(e);
          }
        });
  }

  @Test
  void paintsEmojiRunsWithVisiblePixels() throws Exception {
    AtomicReference<BufferedImage> rendered = new AtomicReference<>();

    SwingUtilities.invokeAndWait(
        () -> {
          try {
            SingleLineEmojiTextPane pane = new SingleLineEmojiTextPane();
            pane.setOpaque(true);
            pane.setBackground(Color.WHITE);
            pane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

            StyledDocument doc = pane.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            EmojiFontSupport.applyEmojiRunFont(attrs);
            doc.insertString(0, "😀", attrs);

            pane.setSize(160, 32);
            pane.doLayout();
            rendered.set(paint(pane, 160, 32));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });

    assertTrue(nonWhitePixels(rendered.get()) > 30);
  }

  private static BufferedImage paint(SingleLineEmojiTextPane pane, int width, int height) {
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
