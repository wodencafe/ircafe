package cafe.woden.ircclient.ui.chat.embed;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.madgag.gif.fmsware.AnimatedGifEncoder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ImageViewerDialogFunctionalTest {

  @ParameterizedTest
  @CsvSource({"800, 400, 400, 200", "400, 800, 150, 300", "800, 800, 300, 300", "100, 50, 100, 50"})
  void imageFitsBothDimensionsAndStaysCentered(
      int imageWidth, int imageHeight, int expectedWidth, int expectedHeight) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          ImageViewerPanel panel = new ImageViewerPanel();
          panel.setImage(new ImageIcon(solidImage(imageWidth, imageHeight, Color.RED)));
          panel.setSize(416, 316);
          assertEquals(
              new Rectangle(
                  8 + (400 - expectedWidth) / 2,
                  8 + (300 - expectedHeight) / 2,
                  expectedWidth,
                  expectedHeight),
              paintedBounds(panel, Color.RED));

          panel.setSize(116, 116);
          Rectangle smaller = paintedBounds(panel, Color.RED);
          assertTrue(smaller.width <= 100 && smaller.height <= 100);

          panel.setSize(imageWidth + 16, imageHeight + 16);
          assertEquals(
              new Rectangle(8, 8, imageWidth, imageHeight), paintedBounds(panel, Color.RED));
        });
  }

  @Test
  void viewerLoadsImageResizesMaximizesAndCloses() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless(), "viewer window requires a display");
    assumeTrue(
        Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH),
        "window manager must support maximize");
    ByteArrayOutputStream encoded = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(solidImage(1600, 800, Color.RED), "png", encoded));
    AtomicReference<JFrame> viewer = new AtomicReference<>();
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            JFrame frame =
                ImageViewerDialog.createWindow(
                    null, "https://example.org/image.png", encoded.toByteArray(), List.of());
            frame.setSize(640, 400);
            frame.setVisible(true);
            viewer.set(frame);
            assertTrue(frame.isResizable());
            assertEquals(JFrame.DISPOSE_ON_CLOSE, frame.getDefaultCloseOperation());
          });
      awaitOnEdt(() -> assertImageFits(viewer.get(), Color.RED));
      SwingUtilities.invokeAndWait(() -> viewer.get().setSize(480, 320));
      awaitOnEdt(() -> assertImageFits(viewer.get(), Color.RED));
      Rectangle normalBounds = viewer.get().getBounds();
      SwingUtilities.invokeAndWait(() -> viewer.get().setExtendedState(Frame.MAXIMIZED_BOTH));
      awaitOnEdt(
          () -> {
            assertEquals(Frame.MAXIMIZED_BOTH, viewer.get().getExtendedState());
            assertTrue(viewer.get().getWidth() > normalBounds.width);
            assertTrue(viewer.get().getHeight() > normalBounds.height);
            assertImageFits(viewer.get(), Color.RED);
          });
      SwingUtilities.invokeAndWait(
          () -> {
            JPanel buttons = (JPanel) viewer.get().getContentPane().getComponent(1);
            ((JButton) buttons.getComponent(3)).doClick();
            assertFalse(viewer.get().isDisplayable());
          });
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            if (viewer.get() != null) viewer.get().dispose();
          });
    }
  }

  @Test
  void scaledGifKeepsAnimating() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless(), "animated viewer requires a display");
    ByteArrayOutputStream encoded = new ByteArrayOutputStream();
    AnimatedGifEncoder encoder = new AnimatedGifEncoder();
    assertTrue(encoder.start(encoded));
    encoder.setRepeat(0);
    encoder.setDelay(300);
    assertTrue(encoder.addFrame(solidImage(1600, 800, Color.RED)));
    assertTrue(encoder.addFrame(solidImage(1600, 800, Color.BLUE)));
    assertTrue(encoder.finish());
    AtomicReference<JFrame> viewer = new AtomicReference<>();
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            JFrame frame =
                ImageViewerDialog.createWindow(
                    null, "https://example.org/image.gif", encoded.toByteArray(), List.of());
            frame.setSize(640, 400);
            frame.setVisible(true);
            viewer.set(frame);
          });
      awaitOnEdt(() -> assertImageFits(viewer.get(), Color.RED));
      awaitOnEdt(() -> assertImageFits(viewer.get(), Color.BLUE));
      awaitOnEdt(() -> assertImageFits(viewer.get(), Color.RED));
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            if (viewer.get() != null) viewer.get().dispose();
          });
    }
  }

  @Test
  void closingParentDisposesViewerAndRemovesListener() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless(), "viewer window requires a display");
    AtomicReference<JFrame> parent = new AtomicReference<>();
    AtomicReference<JFrame> viewer = new AtomicReference<>();
    ByteArrayOutputStream encoded = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(solidImage(20, 20, Color.RED), "png", encoded));
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            JFrame frame = new JFrame();
            frame.pack();
            parent.set(frame);
            viewer.set(
                ImageViewerDialog.createWindow(
                    frame, "https://example.org/image.png", encoded.toByteArray(), List.of()));
            assertEquals(1, frame.getWindowListeners().length);
            frame.dispose();
          });
      awaitOnEdt(
          () -> {
            assertFalse(viewer.get().isDisplayable());
            assertEquals(0, parent.get().getWindowListeners().length);
          });
    } finally {
      SwingUtilities.invokeAndWait(
          () -> {
            if (viewer.get() != null) viewer.get().dispose();
            if (parent.get() != null) parent.get().dispose();
          });
    }
  }

  private static void assertImageFits(JFrame viewer, Color color) {
    ImageViewerPanel panel =
        (ImageViewerPanel)
            ((BorderLayout) viewer.getContentPane().getLayout())
                .getLayoutComponent(BorderLayout.CENTER);
    Rectangle painted = paintedBounds(panel, color);
    assertTrue(painted.width > 0 && painted.height > 0, "image should be visible");
    assertTrue(painted.x >= 8 && painted.y >= 8);
    assertTrue(painted.x + painted.width <= panel.getWidth() - 8);
    assertTrue(painted.y + painted.height <= panel.getHeight() - 8);
    assertTrue(Math.abs(painted.width - 2 * painted.height) <= 1, "preserve aspect ratio");
    assertTrue(Math.abs(2 * painted.x + painted.width - panel.getWidth()) <= 1);
    assertTrue(Math.abs(2 * painted.y + painted.height - panel.getHeight()) <= 1);
  }

  private static Rectangle paintedBounds(ImageViewerPanel panel, Color color) {
    BufferedImage rendered =
        new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = rendered.createGraphics();
    try {
      panel.paint(graphics);
    } finally {
      graphics.dispose();
    }
    Rectangle bounds = new Rectangle();
    for (int y = 0; y < rendered.getHeight(); y++) {
      for (int x = 0; x < rendered.getWidth(); x++) {
        if (rendered.getRGB(x, y) == color.getRGB()) {
          if (bounds.isEmpty()) bounds.setBounds(x, y, 1, 1);
          else bounds.add(new Rectangle(x, y, 1, 1));
        }
      }
    }
    return bounds;
  }

  private static BufferedImage solidImage(int width, int height, Color color) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(color);
      graphics.fillRect(0, 0, width, height);
    } finally {
      graphics.dispose();
    }
    return image;
  }

  private static void awaitOnEdt(Runnable assertion) {
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              try {
                SwingUtilities.invokeAndWait(assertion);
              } catch (InvocationTargetException ex) {
                if (ex.getCause() instanceof AssertionError error) throw error;
                throw ex;
              }
            });
  }
}
