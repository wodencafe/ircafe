package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.ui.SwingEdt;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * Inline image preview component inserted into the chat transcript StyledDocument.
 */
final class ChatImageComponent extends JPanel {

  // Fallback width if we can't determine the transcript viewport width yet.
  private static final int FALLBACK_MAX_W = 360;
  // Subtract some breathing room so we don't force horizontal scrolling.
  private static final int WIDTH_MARGIN_PX = 32;

  private final String url;
  private final ImageFetchService fetch;

  private final boolean collapsedByDefault;
  private boolean collapsed;

  private final JLabel imageLabel = new JLabel("Loading image…");

  private JPanel card;
  private JPanel header;
  private JPanel body;
  private JButton collapseBtn;
  private JLabel headerTitle;

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

  ChatImageComponent(String url, ImageFetchService fetch, boolean collapsedByDefault) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    this.url = url;
    this.fetch = fetch;
    this.collapsedByDefault = collapsedByDefault;
    this.collapsed = collapsedByDefault;

    setOpaque(false);

    renderScaffold();
    beginLoad();
  }

  private void renderScaffold() {
    removeAll();

    card = new JPanel(new BorderLayout());
    card.setOpaque(true);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(borderColor()),
        BorderFactory.createEmptyBorder(8, 8, 8, 8)
    ));

    header = buildHeader();
    body = buildBody();

    card.add(header, BorderLayout.NORTH);
    card.add(body, BorderLayout.CENTER);

    installPopup(card);

    add(card);

    applyCollapsedState();

    revalidate();
    repaint();
  }

  private JPanel buildHeader() {
    JPanel h = new JPanel(new BorderLayout(8, 0));
    h.setOpaque(false);

    collapseBtn = new JButton();
    collapseBtn.setFocusable(false);
    collapseBtn.setBorderPainted(false);
    collapseBtn.setContentAreaFilled(false);
    collapseBtn.setMargin(new Insets(0, 0, 0, 0));
    collapseBtn.addActionListener(e -> {
      collapsed = !collapsed;
      applyCollapsedState();
      if (!collapsed) {
        renderForCurrentWidth();
      }
    });

    headerTitle = new JLabel(displayTitle(url));
    headerTitle.setToolTipText(url);

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    left.setOpaque(false);
    left.add(collapseBtn);

    h.add(left, BorderLayout.WEST);
    h.add(headerTitle, BorderLayout.CENTER);

    return h;
  }

  private JPanel buildBody() {
    JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    b.setOpaque(false);

    imageLabel.setOpaque(false);
    imageLabel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    imageLabel.setToolTipText(url);

    imageLabel.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
          openViewer();
        }
      }
    });

    b.add(imageLabel);
    return b;
  }

  private void applyCollapsedState() {
    if (collapseBtn != null) {
      collapseBtn.setText(collapsed ? "▸" : "▾");
      collapseBtn.setToolTipText(collapsed ? "Expand image" : "Collapse image");
    }

    if (body != null) {
      body.setVisible(!collapsed);
    }

    if (gifPlayer != null) {
      if (collapsed) {
        gifPlayer.stop();
      } else {
        gifPlayer.start();
      }
    }

    revalidate();
    repaint();
  }

  private void beginLoad() {
    if (fetch == null) {
      imageLabel.setText("(image embeds disabled)");
      return;
    }

    sub = fetch.fetch(url)
        .observeOn(SwingEdt.scheduler())
        .subscribe(
            b -> {
              bytes = b;
              try {
                decoded = ImageDecodeUtil.decode(url, b);
                imageLabel.setText("");
                if (!collapsed) {
                  renderForCurrentWidth();
                }
              } catch (Exception ex) {
                decoded = null;
                imageLabel.setText("(image decode failed)");
                imageLabel.setToolTipText(url + System.lineSeparator() + ex.getMessage());
              }
            },
            err -> {
              decoded = null;
              imageLabel.setText("(image failed to load)");
              imageLabel.setToolTipText(url + System.lineSeparator() + err.getMessage());
            }
        );
  }

  @Override
  public void addNotify() {
    super.addNotify();

    hookResizeListener();

    // If Swing re-adds this component during view rebuilds and scrolling, and we haven't loaded yet,
    // (re)start the fetch. (Do NOT cancel on removeNotify; see removeNotify below.)
    if (bytes == null || bytes.length == 0) {
      if (sub == null || sub.isDisposed()) {
        beginLoad();
      }
    }

    if (gifPlayer != null && !collapsed) {
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
    if (collapsed) return;

    DecodedImage d = decoded;
    if (d == null) return;

    int maxW = EmbedHostLayoutUtil.computeMaxInlineWidth(this, FALLBACK_MAX_W, WIDTH_MARGIN_PX, 96);
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
        gifPlayer = new AnimatedGifPlayer(imageLabel);
      }
      gifPlayer.setFrames(icons, gif.delaysMs());
      if (!collapsed) {
        gifPlayer.start();
      }

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
      imageLabel.setIcon(icon);
      imageLabel.setText("");
      setLabelPreferredSize(icon);
      revalidate();
      repaint();
    }
  }

  private void setLabelPreferredSize(javax.swing.ImageIcon icon) {
    int w = icon.getIconWidth();
    int h = icon.getIconHeight();
    if (w > 0 && h > 0) {
      imageLabel.setPreferredSize(new Dimension(w, h));
    }
  }

  private void hookResizeListener() {
    resizeListeningOn = EmbedHostLayoutUtil.hookResizeListener(this, resizeListener, resizeListeningOn);
  }

  private void unhookResizeListener() {
    resizeListeningOn = EmbedHostLayoutUtil.unhookResizeListener(resizeListener, resizeListeningOn);
  }

  private void installPopup(JPanel target) {
    JPopupMenu menu = new JPopupMenu();

    JMenuItem view = new JMenuItem("View image");
    view.addActionListener(e -> openViewer());

    JMenuItem openBrowser = new JMenuItem("Open image link");
    openBrowser.addActionListener(e -> openInBrowser());

    JMenuItem copyUrl = new JMenuItem("Copy URL");
    copyUrl.addActionListener(e -> {
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
    });

    menu.add(view);
    menu.add(openBrowser);
    menu.add(copyUrl);

    target.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mousePressed(java.awt.event.MouseEvent e) {
        if (e.isPopupTrigger()) menu.show(target, e.getX(), e.getY());
      }

      @Override
      public void mouseReleased(java.awt.event.MouseEvent e) {
        if (e.isPopupTrigger()) menu.show(target, e.getX(), e.getY());
      }
    });
  }

  private void openInBrowser() {
    try {
      java.awt.Desktop.getDesktop().browse(new URI(url));
    } catch (Exception ignored) {
    }
  }

  private void openViewer() {
    byte[] b = bytes;
    if (b == null || b.length == 0) {
      openInBrowser();
      return;
    }

    Window w = SwingUtilities.getWindowAncestor(this);
    if (w == null) return;

    ImageViewerDialog.show(w, url, b);
  }

  private static java.awt.Color borderColor() {
    java.awt.Color c = javax.swing.UIManager.getColor("Component.borderColor");
    if (c == null) c = javax.swing.UIManager.getColor("Separator.foreground");
    if (c == null) c = new java.awt.Color(100, 100, 100, 120);
    return c;
  }

  private static String displayTitle(String url) {
    try {
      URI u = new URI(url);
      String host = u.getHost();
      if (host != null && !host.isBlank()) {
        String path = u.getPath();
        if (path != null && path.length() > 1) {
          int idx = path.lastIndexOf('/');
          if (idx >= 0 && idx < path.length() - 1) {
            String last = path.substring(idx + 1);
            if (!last.isBlank()) return host + " / " + last;
          }
        }
        return host;
      }
    } catch (Exception ignored) {
    }
    return url;
  }
}
