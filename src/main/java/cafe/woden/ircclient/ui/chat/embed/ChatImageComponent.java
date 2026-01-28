package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.ui.SwingEdt;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;

/**
 * Inline image preview component inserted into the chat transcript StyledDocument.
 */
final class ChatImageComponent extends JPanel {

  private static final int THUMB_MAX_W = 360;
  private static final int THUMB_MAX_H = 280;

  private final String url;
  private final ImageFetchService fetch;

  private final JLabel label = new JLabel("Loading image…");
  private volatile byte[] bytes;
  private Disposable sub;

  ChatImageComponent(String url, ImageFetchService fetch) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    this.url = url;
    this.fetch = fetch;

    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(2, 0, 6, 0));

    label.setOpaque(false);
    label.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    label.setToolTipText(url);
    add(label);

    installPopup(label);

    label.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (javax.swing.SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
          openViewer();
        }
      }
    });

    beginLoad();
  }

  private void beginLoad() {
    if (fetch == null) {
      label.setText("(image embeds disabled)");
      return;
    }
    sub = fetch.fetch(url)
        .observeOn(SwingEdt.scheduler())
        .subscribe(
            b -> {
              bytes = b;
              setIconFromBytes(b);
            },
            err -> {
              label.setText("(image failed to load)");
              label.setToolTipText(url + "\n" + err);
            }
        );
  }

  @Override
  public void addNotify() {
    super.addNotify();
    // IMPORTANT:
    // When Swing lays out/rebuilds JTextPane views, it can temporarily remove and
    // re-add embedded components. If we cancel the fetch on removeNotify(), the
    // component can get "stuck" in the loading state forever.
    //
    // So: if we get re-added and we haven't loaded yet, (re)start the fetch.
    if (bytes == null || bytes.length == 0) {
      if (sub == null || sub.isDisposed()) {
        beginLoad();
      }
    }
  }

  @Override
  public void removeNotify() {
    // DO NOT dispose the subscription here.
    // JTextPane/StyledDocument may call removeNotify() during view rebuilds and scrolling,
    // which would prevent the image from ever completing and updating the UI.
    super.removeNotify();
  }

  private void setIconFromBytes(byte[] b) {
    javax.swing.ImageIcon icon = new javax.swing.ImageIcon(b);
    if (!isGif(url)) {
      icon = scaleToThumb(icon);
    }
    label.setText("");
    label.setIcon(icon);

    int w = icon.getIconWidth();
    int h = icon.getIconHeight();
    if (w > 0 && h > 0) {
      label.setPreferredSize(new Dimension(w, h));
    }
    revalidate();
    repaint();
  }

  private javax.swing.ImageIcon scaleToThumb(javax.swing.ImageIcon icon) {
    int w = icon.getIconWidth();
    int h = icon.getIconHeight();
    if (w <= 0 || h <= 0) return icon;
    if (w <= THUMB_MAX_W && h <= THUMB_MAX_H) return icon;

    double sx = (double) THUMB_MAX_W / (double) w;
    double sy = (double) THUMB_MAX_H / (double) h;
    double s = Math.min(sx, sy);
    int nw = Math.max(1, (int) Math.round(w * s));
    int nh = Math.max(1, (int) Math.round(h * s));

    java.awt.Image scaled = icon.getImage().getScaledInstance(nw, nh, java.awt.Image.SCALE_SMOOTH);
    return new javax.swing.ImageIcon(scaled);
  }

  private void openViewer() {
    byte[] b = bytes;
    if (b != null && b.length > 0) {
      Window parent = ImageViewerDialog.windowOf(this);
      ImageViewerDialog.show(parent, url, b);
      return;
    }

    // Not loaded yet: just open the link.
    try {
      Desktop.getDesktop().browse(new URI(url));
    } catch (Exception ignored) {
    }
  }

  private void installPopup(JLabel target) {
    JPopupMenu menu = new JPopupMenu();

    JMenuItem enlarge = new JMenuItem("Enlarge");
    enlarge.addActionListener(e -> openViewer());
    menu.add(enlarge);

    JMenuItem openBrowser = new JMenuItem("Open in browser");
    openBrowser.addActionListener(e -> {
      try {
        Desktop.getDesktop().browse(new URI(url));
      } catch (Exception ignored) {
      }
    });
    menu.add(openBrowser);

    JMenuItem openExternal = new JMenuItem("Open externally");
    openExternal.addActionListener(e -> {
      try {
        byte[] b = bytes;
        if (b != null && b.length > 0) {
          FileUtil.openBytesWithDesktop(url, b);
        } else {
          Desktop.getDesktop().browse(new URI(url));
        }
      } catch (Exception ignored) {
      }
    });
    menu.add(openExternal);

    JMenuItem copy = new JMenuItem("Copy URL");
    copy.addActionListener(e -> {
      try {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
      } catch (Exception ignored) {
      }
    });
    menu.add(copy);

    target.setComponentPopupMenu(menu);
  }

  private static boolean isGif(String url) {
    if (url == null) return false;
    try {
      String p = URI.create(url).getPath();
      if (p == null) return false;
      return p.toLowerCase(Locale.ROOT).endsWith(".gif");
    } catch (Exception ignored) {
      return url.toLowerCase(Locale.ROOT).contains(".gif");
    }
  }
}
