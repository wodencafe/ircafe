package cafe.woden.ircclient.ui.chat.embed;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Simple modal viewer for an embedded image.
 *
 * <p>We keep it intentionally lightweight: no JavaFX, no WebView.
 * Swing's {@link javax.swing.ImageIcon} will animate GIFs automatically.
 */
final class ImageViewerDialog {

  private ImageViewerDialog() {}

  static void show(Window parent, String url, byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      // If we don't have bytes, fall back to browser.
      try {
        Desktop.getDesktop().browse(new URI(url));
      } catch (Exception ignored) {
      }
      return;
    }

    JDialog dlg = new JDialog(parent, "Image", JDialog.ModalityType.APPLICATION_MODAL);
    dlg.setLayout(new BorderLayout(8, 8));

    JLabel img = new JLabel(new javax.swing.ImageIcon(bytes));
    img.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    JScrollPane scroller = new JScrollPane(img);
    dlg.add(scroller, BorderLayout.CENTER);

    JPanel buttons = new JPanel();
    JButton openExternal = new JButton("Open externally");
    JButton openBrowser = new JButton("Open in browser");
    JButton copy = new JButton("Copy URL");
    JButton close = new JButton("Close");

    openExternal.addActionListener(e -> {
      try {
        File f = writeTempFile(url, bytes);
        Desktop.getDesktop().open(f);
      } catch (Exception ignored) {
      }
    });

    openBrowser.addActionListener(e -> {
      try {
        Desktop.getDesktop().browse(new URI(url));
      } catch (Exception ignored) {
      }
    });

    copy.addActionListener(e -> {
      try {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
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
    dlg.setVisible(true);
  }

  private static File writeTempFile(String url, byte[] bytes) throws IOException {
    String ext = extensionFromUrl(url);
    File f = Files.createTempFile("ircafe-image-", ext).toFile();
    Files.write(f.toPath(), bytes);
    f.deleteOnExit();
    return f;
  }

  private static String extensionFromUrl(String url) {
    try {
      String p = URI.create(url).getPath();
      if (p == null) return ".img";
      p = p.toLowerCase(Locale.ROOT);
      if (p.endsWith(".png")) return ".png";
      if (p.endsWith(".jpg")) return ".jpg";
      if (p.endsWith(".jpeg")) return ".jpeg";
      if (p.endsWith(".gif")) return ".gif";
    } catch (Exception ignored) {
    }
    return ".img";
  }

  static Window windowOf(java.awt.Component c) {
    if (c == null) return null;
    return SwingUtilities.getWindowAncestor(c);
  }
}
