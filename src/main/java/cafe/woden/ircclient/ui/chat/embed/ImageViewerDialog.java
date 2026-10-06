package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.ui.localization.UiMessages;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Simple maximizable viewer for an embedded image.
 *
 * <p>We keep it intentionally lightweight: no JavaFX, no WebView. Swing's {@link
 * javax.swing.ImageIcon} will animate GIFs automatically.
 */
final class ImageViewerDialog {

  private static final UiMessages MESSAGES = UiMessages.bundledDefaults();

  private ImageViewerDialog() {}

  static void show(Window parent, String url, byte[] bytes) {
    show(parent, url, bytes, List.of());
  }

  static void show(
      Window parent,
      String url,
      byte[] bytes,
      List<? extends cafe.woden.ircclient.ui.chat.embed.spi.ImageUrlExtensionProvider>
          extensionProviders) {
    if (bytes == null || bytes.length == 0) {
      // If we don't have bytes, fall back to browser.
      try {
        Desktop.getDesktop().browse(new URI(url));
      } catch (Exception ignored) {
      }
      return;
    }

    createWindow(parent, url, bytes, extensionProviders).setVisible(true);
  }

  static JFrame createWindow(
      Window parent,
      String url,
      byte[] bytes,
      List<? extends cafe.woden.ircclient.ui.chat.embed.spi.ImageUrlExtensionProvider>
          extensionProviders) {
    JFrame dlg = new JFrame(message("imageViewer.title"));
    dlg.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    dlg.setLayout(new BorderLayout(8, 8));

    ImageViewerPanel img = new ImageViewerPanel();
    dlg.add(img, BorderLayout.CENTER);

    SwingWorker<ImageIcon, Void> loader =
        new SwingWorker<>() {
          @Override
          protected ImageIcon doInBackground() {
            // ImageIcon retains GIF animation; static formats use the ImageIO plugins.
            if (!ImageDecodeUtil.looksLikeGif(url, bytes)) {
              try {
                java.awt.image.BufferedImage bi =
                    javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                if (bi != null) return new ImageIcon(bi);
              } catch (IOException ignored) {
              }
            }
            return new ImageIcon(bytes);
          }

          @Override
          protected void done() {
            if (isCancelled() || !dlg.isDisplayable()) return;
            try {
              img.setImage(get());
            } catch (Exception ignored) {
            }
          }
        };

    WindowAdapter parentListener =
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent e) {
            dlg.dispose();
          }
        };
    if (parent != null) {
      dlg.setIconImages(parent.getIconImages());
      parent.addWindowListener(parentListener);
    }
    dlg.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent e) {
            loader.cancel(true);
            img.setImage(null);
            if (parent != null) parent.removeWindowListener(parentListener);
          }
        });

    JPanel buttons = new JPanel();
    JButton openExternal = new JButton(message("imageViewer.button.openExternally"));
    JButton openBrowser = new JButton(message("imageViewer.button.openInBrowser"));
    JButton copy = new JButton(message("imageViewer.button.copyUrl"));
    JButton close = new JButton(message("common.button.close"));

    openExternal.addActionListener(
        e -> {
          try {
            File f = writeTempFile(url, bytes, extensionProviders);
            Desktop.getDesktop().open(f);
          } catch (Exception ignored) {
          }
        });

    openBrowser.addActionListener(
        e -> {
          try {
            Desktop.getDesktop().browse(new URI(url));
          } catch (Exception ignored) {
          }
        });

    copy.addActionListener(
        e -> {
          try {
            Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(url), null);
          } catch (Exception ignored) {
          }
        });

    close.addActionListener(e -> dlg.dispose());

    buttons.add(openExternal);
    buttons.add(openBrowser);
    buttons.add(copy);
    buttons.add(close);
    dlg.add(buttons, BorderLayout.SOUTH);

    dlg.setPreferredSize(new Dimension(900, 650));
    dlg.pack();
    dlg.setLocationRelativeTo(parent);
    loader.execute();
    return dlg;
  }

  private static String message(String key, Object... args) {
    return MESSAGES.text(key, args);
  }

  private static File writeTempFile(
      String url,
      byte[] bytes,
      List<? extends cafe.woden.ircclient.ui.chat.embed.spi.ImageUrlExtensionProvider>
          extensionProviders)
      throws IOException {
    String ext = ImageFileExtensionSupport.extensionFromUrl(url, extensionProviders);
    File f = Files.createTempFile("ircafe-image-", ext).toFile();
    Files.write(f.toPath(), bytes);
    f.deleteOnExit();
    return f;
  }

  static Window windowOf(java.awt.Component c) {
    if (c == null) return null;
    return SwingUtilities.getWindowAncestor(c);
  }
}
