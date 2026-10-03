package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.ui.util.EmojiFontSupport;
import cafe.woden.ircclient.ui.util.EmojiImageSupport;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.ViewFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
class EmojiInlineViewFunctionalTest {
  @Test
  void paintsFallbackWithoutWaitingAndRepaintsTheLoadedImageOnEdt() throws Exception {
    ControlledImages images = new ControlledImages(false);
    AtomicReference<RecordingPane> pane = new AtomicReference<>();
    AtomicReference<EmojiInlineView> view = new AtomicReference<>();
    AtomicReference<BufferedImage> fallback = new AtomicReference<>();
    CountDownLatch repainted = new CountDownLatch(1);
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            pane.set(pane(images, view));
            fallback.set(paint(pane.get()));
            paint(pane.get());
            pane.get().onRepaint =
                () -> {
                  assertTrue(SwingUtilities.isEventDispatchThread());
                  repainted.countDown();
                };
          });
      assertTrue(images.started.await(2, TimeUnit.SECONDS));
      assertEquals(1, images.loads.get(), "repeated paints must share the pending view load");
      assertTrue(hasInk(fallback.get()), "text fallback remains visible while decoding is blocked");
      assertFalse(hasMagenta(fallback.get()));
      CountDownLatch edtResponsive = new CountDownLatch(1);
      SwingUtilities.invokeLater(edtResponsive::countDown);
      assertTrue(edtResponsive.await(2, TimeUnit.SECONDS), "decoding must not hold the EDT");
      images.release.countDown();
      assertTrue(repainted.await(5, TimeUnit.SECONDS));
      SwingUtilities.invokeAndWait(
          () ->
              assertTrue(hasMagenta(paint(pane.get())), "completion must paint the loaded asset"));
      assertEquals(1, images.loads.get());
    } finally {
      images.release.countDown();
      detach(view);
    }
  }

  @Test
  void detachedViewCancelsItsLoadAndDoesNotRepaintItsFormerContainer() throws Exception {
    ControlledImages images = new ControlledImages(false);
    AtomicReference<EmojiInlineView> view = new AtomicReference<>();
    AtomicReference<RecordingPane> pane = new AtomicReference<>();
    AtomicInteger repaints = new AtomicInteger();
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            pane.set(pane(images, view));
            paint(pane.get());
          });
      assertTrue(images.started.await(2, TimeUnit.SECONDS));
      SwingUtilities.invokeAndWait(
          () -> {
            pane.get().onRepaint = repaints::incrementAndGet;
            view.get().setParent(null);
          });
      assertTrue(images.interrupted.await(2, TimeUnit.SECONDS), "detaching cancels decoding");
      SwingUtilities.invokeAndWait(() -> assertEquals(0, repaints.get()));
    } finally {
      images.release.countDown();
      detach(view);
    }
  }

  @Test
  void unsupportedEmojiKeepsItsFallbackWithoutResubmittingOnPaint() throws Exception {
    ControlledImages images = new ControlledImages(true);
    AtomicReference<EmojiInlineView> view = new AtomicReference<>();
    AtomicReference<RecordingPane> pane = new AtomicReference<>();
    CountDownLatch repainted = new CountDownLatch(1);
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            pane.set(pane(images, view));
            paint(pane.get());
            pane.get().onRepaint = repainted::countDown;
          });
      assertTrue(images.started.await(2, TimeUnit.SECONDS));
      images.release.countDown();
      assertTrue(repainted.await(5, TimeUnit.SECONDS));
      SwingUtilities.invokeAndWait(
          () -> {
            assertTrue(hasInk(paint(pane.get())));
            paint(pane.get());
          });
      assertEquals(1, images.loads.get());
    } finally {
      images.release.countDown();
      detach(view);
    }
  }

  private static RecordingPane pane(
      ControlledImages images, AtomicReference<EmojiInlineView> view) {
    RecordingPane pane = new RecordingPane();
    StyledEditorKit kit = new StyledEditorKit();
    ViewFactory delegate = kit.getViewFactory();
    pane.setEditorKit(
        new StyledEditorKit() {
          @Override
          public ViewFactory getViewFactory() {
            return element -> {
              if (!EmojiFontSupport.isEmojiRun(element.getAttributes())) {
                return delegate.create(element);
              }
              EmojiInlineView emojiView = new EmojiInlineView(element, images);
              view.set(emojiView);
              return emojiView;
            };
          }
        });
    pane.setBackground(Color.WHITE);
    pane.setForeground(Color.BLACK);
    SimpleAttributeSet attributes = new SimpleAttributeSet();
    EmojiFontSupport.applyEmojiRunFont(attributes);
    try {
      pane.getStyledDocument().insertString(0, "😀", attributes);
    } catch (javax.swing.text.BadLocationException ex) {
      throw new AssertionError(ex);
    }
    pane.setSize(120, 64);
    return pane;
  }

  private static BufferedImage paint(JTextPane pane) {
    BufferedImage image = new BufferedImage(120, 64, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
      pane.paint(graphics);
    } finally {
      graphics.dispose();
    }
    return image;
  }

  private static boolean hasInk(BufferedImage image) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (image.getRGB(x, y) != Color.WHITE.getRGB()) return true;
      }
    }
    return false;
  }

  private static boolean hasMagenta(BufferedImage image) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if (image.getRGB(x, y) == Color.MAGENTA.getRGB()) return true;
      }
    }
    return false;
  }

  private static void detach(AtomicReference<EmojiInlineView> view) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          if (view.get() != null) view.get().setParent(null);
        });
  }

  private static final class RecordingPane extends JTextPane {
    Runnable onRepaint;

    @Override
    public boolean isDisplayable() {
      // Exercise the completion path while rendering offscreen in headless functional runs.
      return true;
    }

    @Override
    public void repaint() {
      if (onRepaint != null) onRepaint.run();
      super.repaint();
    }
  }

  private static final class ControlledImages implements EmojiInlineView.ImageSource {
    final CountDownLatch started = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final CountDownLatch interrupted = new CountDownLatch(1);
    final AtomicInteger loads = new AtomicInteger();
    final boolean unsupported;
    volatile EmojiImageSupport.CachedImage cached = new EmojiImageSupport.CachedImage(false, null);

    ControlledImages(boolean unsupported) {
      this.unsupported = unsupported;
    }

    @Override
    public EmojiImageSupport.CachedImage lookup(String text, int size) {
      return cached;
    }

    @Override
    public void load(String text, int size) {
      assertFalse(SwingUtilities.isEventDispatchThread());
      loads.incrementAndGet();
      started.countDown();
      try {
        if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("load timed out");
      } catch (InterruptedException ex) {
        interrupted.countDown();
        Thread.currentThread().interrupt();
        return;
      }
      BufferedImage image = null;
      if (!unsupported) {
        image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
          graphics.setColor(Color.MAGENTA);
          graphics.fillRect(0, 0, size, size);
        } finally {
          graphics.dispose();
        }
      }
      cached = new EmojiImageSupport.CachedImage(true, image);
    }
  }
}
