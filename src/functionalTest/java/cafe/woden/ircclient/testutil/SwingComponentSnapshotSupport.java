package cafe.woden.ircclient.testutil;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.imageio.ImageIO;

/** Renders Swing component trees into PNG snapshots for functional-test artifacts. */
public final class SwingComponentSnapshotSupport {

  private static final int DEFAULT_CAPTURE_WIDTH = 1180;
  private static final int DEFAULT_CAPTURE_HEIGHT = 760;

  private SwingComponentSnapshotSupport() {}

  public static BufferedImage capture(Component component) {
    Component root = Objects.requireNonNull(component, "component");
    Dimension size = sanitizeSize(root.getSize(), root.getPreferredSize());
    if (root.getWidth() != size.width || root.getHeight() != size.height) {
      root.setSize(size);
    }
    layoutRecursively(root);

    BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      g.setColor(resolveBackground(root));
      g.fillRect(0, 0, size.width, size.height);
      root.printAll(g);
      return image;
    } finally {
      g.dispose();
    }
  }

  public static void writePng(Path path, BufferedImage image) throws IOException {
    Path out = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    BufferedImage rendered = Objects.requireNonNull(image, "image");
    Path parent = out.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    if (!ImageIO.write(rendered, "png", out.toFile())) {
      throw new IOException("No PNG writer available for: " + out);
    }
  }

  private static Dimension sanitizeSize(Dimension actual, Dimension preferred) {
    int width = actual != null ? actual.width : 0;
    int height = actual != null ? actual.height : 0;
    if (width > 0 && height > 0) {
      return new Dimension(width, height);
    }
    int prefWidth = preferred != null ? preferred.width : 0;
    int prefHeight = preferred != null ? preferred.height : 0;
    return new Dimension(
        Math.max(1, prefWidth > 0 ? prefWidth : DEFAULT_CAPTURE_WIDTH),
        Math.max(1, prefHeight > 0 ? prefHeight : DEFAULT_CAPTURE_HEIGHT));
  }

  private static void layoutRecursively(Component component) {
    if (component == null) return;
    component.invalidate();
    component.doLayout();
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        layoutRecursively(child);
      }
      container.validate();
    }
  }

  private static Color resolveBackground(Component component) {
    Color bg = component == null ? null : component.getBackground();
    if (bg == null) {
      bg = Color.WHITE;
    }
    return bg;
  }
}
