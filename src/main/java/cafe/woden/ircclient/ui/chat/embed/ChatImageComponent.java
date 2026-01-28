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
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

/**
 * Inline image preview component inserted into the chat transcript StyledDocument.
 *
 */
final class ChatImageComponent extends JPanel {

  // Fallback width if we can't determine the transcript viewport width yet.
  private static final int FALLBACK_MAX_W = 360;
  // Subtract some breathing room so we don't force horizontal scrolling.
  private static final int WIDTH_MARGIN_PX = 32;

  private final String url;
  private final ImageFetchService fetch;

  private final JLabel label = new JLabel("Loading image…");
  private volatile byte[] bytes;
  private volatile DecodedImage decoded;
  private volatile int lastMaxW = -1;

  private Disposable sub;

  private java.awt.Component resizeListeningOn;
  private final java.awt.event.ComponentListener resizeListener = new java.awt.event.ComponentAdapter() {
    @Override
    public void componentResized(java.awt.event.ComponentEvent e) {
      // Debounce-ish: schedule one repaint on EDT.
      SwingUtilities.invokeLater(ChatImageComponent.this::renderForCurrentWidth);
    }
  };

  private AnimatedGifPlayer gifPlayer;

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
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
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
              try {
                decoded = ImageDecodeUtil.decode(url, b);
                label.setText("");
                renderForCurrentWidth();
              } catch (Exception ex) {
                decoded = null;
                label.setText("(image decode failed)");
                label.setToolTipText(url + "\n" + ex);
              }
            },
            err -> {
              decoded = null;
              label.setText("(image failed to load)");
              label.setToolTipText(url + "\n" + err);
            }
        );
  }

  @Override
  public void addNotify() {
    super.addNotify();

    hookResizeListener();

    // If Swing re-adds this component during view rebuilds and we haven't loaded yet,
    // (re)start the fetch. (Do NOT cancel on removeNotify; see removeNotify below.)
    if (bytes == null || bytes.length == 0) {
      if (sub == null || sub.isDisposed()) {
        beginLoad();
      }
    }

    if (gifPlayer != null) {
      gifPlayer.start();
    }
  }

  @Override
  public void removeNotify() {
    // DO NOT dispose the fetch subscription here.
    // JTextPane/StyledDocument may call removeNotify() during view rebuilds and scrolling,
    // which would prevent the image from ever completing and updating the UI.

    if (gifPlayer != null) {
      gifPlayer.stop();
    }
    unhookResizeListener();
    super.removeNotify();
  }

  private void renderForCurrentWidth() {
    DecodedImage d = decoded;
    if (d == null) return;

    int maxW = computeMaxInlineWidth();
    if (maxW <= 0) maxW = FALLBACK_MAX_W;

    // Avoid re-scaling on every tiny jitter.
    if (Math.abs(maxW - lastMaxW) < 4 && lastMaxW > 0) {
      return;
    }
    lastMaxW = maxW;

    if (d instanceof AnimatedGifDecoded gif) {
      // Build scaled frame icons.
      java.util.List<javax.swing.ImageIcon> icons = new java.util.ArrayList<>(gif.frames().size());
      for (java.awt.image.BufferedImage frame : gif.frames()) {
        java.awt.image.BufferedImage scaled = ImageScaleUtil.scaleDownToWidth(frame, maxW);
        icons.add(new javax.swing.ImageIcon(scaled));
      }

      if (gifPlayer == null) {
        gifPlayer = new AnimatedGifPlayer(label);
      }
      gifPlayer.setFrames(icons, gif.delaysMs());
      gifPlayer.start();

      // Set sizing based on the first frame.
      if (!icons.isEmpty()) {
        setLabelPreferredSize(icons.get(0));
      }
      revalidate();
      repaint();
      return;
    }

    if (gifPlayer != null) {
      gifPlayer.stop();
      gifPlayer = null;
    }

    if (d instanceof StaticImageDecoded st) {
      java.awt.image.BufferedImage scaled = ImageScaleUtil.scaleDownToWidth(st.image(), maxW);
      javax.swing.ImageIcon icon = new javax.swing.ImageIcon(scaled);
      label.setIcon(icon);
      label.setText("");
      setLabelPreferredSize(icon);
      revalidate();
      repaint();
    }
  }

  private void setLabelPreferredSize(javax.swing.ImageIcon icon) {
    int w = icon.getIconWidth();
    int h = icon.getIconHeight();
    if (w > 0 && h > 0) {
      label.setPreferredSize(new Dimension(w, h));
    }
  }

  private int computeMaxInlineWidth() {
    // Try to find the transcript viewport width (JScrollPane -> JViewport -> JTextPane).
    JTextPane pane = (JTextPane) SwingUtilities.getAncestorOfClass(JTextPane.class, this);
    if (pane != null) {
      int w = pane.getVisibleRect().width;
      if (w <= 0) {
        w = pane.getWidth();
      }
      if (w > 0) {
        return Math.max(96, w - WIDTH_MARGIN_PX);
      }
    }

    JScrollPane scroller = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
    if (scroller != null) {
      int w = scroller.getViewport().getExtentSize().width;
      if (w > 0) {
        return Math.max(96, w - WIDTH_MARGIN_PX);
      }
    }

    return FALLBACK_MAX_W;
  }

  private void hookResizeListener() {
    // Listen on the ancestor text pane (or scroll pane) so we can rescale on resize.
    java.awt.Component target = (java.awt.Component) SwingUtilities.getAncestorOfClass(JTextPane.class, this);
    if (target == null) {
      target = (java.awt.Component) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
    }
    if (target == null) return;

    if (resizeListeningOn == target) return;
    unhookResizeListener();
    resizeListeningOn = target;
    target.addComponentListener(resizeListener);
  }

  private void unhookResizeListener() {
    if (resizeListeningOn != null) {
      try {
        resizeListeningOn.removeComponentListener(resizeListener);
      } catch (Exception ignored) {
      }
      resizeListeningOn = null;
    }
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
}
