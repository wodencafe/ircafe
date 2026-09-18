package cafe.woden.ircclient.irc.pircbotx.client;

import cafe.woden.ircclient.IrcSwingApp;
import cafe.woden.ircclient.ui.shell.MainFrame;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/** Test-only child process that captures the real application after the launcher stages a scene. */
public final class Ircv3ShowcaseCaptureClient {
  private Ircv3ShowcaseCaptureClient() {}

  public static void main(String[] args) throws Exception {
    Path runDir = Path.of(System.getProperty("ircafe.runtime-config")).toAbsolutePath().getParent();
    try {
      IrcSwingApp.main(args);
      long deadline = System.nanoTime() + Duration.ofSeconds(150).toNanos();
      while (!Files.exists(runDir.resolve("capture.request"))) {
        if (System.nanoTime() >= deadline) {
          throw new IllegalStateException("Timed out waiting for the showcase capture request.");
        }
        Thread.sleep(50);
      }

      AtomicReference<BufferedImage> result = new AtomicReference<>();
      SwingUtilities.invokeAndWait(() -> result.set(renderClient()));
      // Encode and write off the EDT; the launcher owns this child process's lifetime.
      if (!ImageIO.write(result.get(), "png", runDir.resolve("capture.png").toFile())) {
        throw new IllegalStateException("No PNG writer is available.");
      }
      Files.writeString(runDir.resolve("capture.done"), "complete");
    } catch (Exception failure) {
      Files.writeString(runDir.resolve("capture.error"), failure.toString());
      throw failure;
    }
  }

  private static BufferedImage renderClient() {
    for (Frame frame : Frame.getFrames()) {
      if (!(frame instanceof MainFrame mainFrame) || !frame.isShowing()) continue;
      JRootPane root = mainFrame.getRootPane();
      if (root.getWidth() <= 0 || root.getHeight() <= 0) {
        throw new IllegalStateException("The IRCafe window has no drawable content.");
      }
      BufferedImage image =
          new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
      Graphics2D graphics = image.createGraphics();
      try {
        root.printAll(graphics);
      } finally {
        graphics.dispose();
      }
      return image;
    }
    throw new IllegalStateException("The IRCafe main window is not showing.");
  }
}
