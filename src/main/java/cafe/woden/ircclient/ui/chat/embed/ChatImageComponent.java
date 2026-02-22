package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.ui.SwingEdt;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.reactivex.rxjava3.disposables.Disposable;
import java.beans.PropertyChangeListener;
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

final class ChatImageComponent extends JPanel {

  // Fallback width if we can't determine the transcript viewport width yet.
  private static final int FALLBACK_MAX_W = 360;
  // Subtract some breathing room so we don't force horizontal scrolling.
  private static final int WIDTH_MARGIN_PX = 32;

  private final String url;
  private final String serverId;
  private final ImageFetchService fetch;
  private final UiSettingsBus uiSettingsBus;

  // Coordinates "only newest GIF animates" within a transcript.
  private final GifAnimationCoordinator gifCoordinator;
  private final long embedSeq;

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
  private volatile int lastMaxH = -1;

  private Disposable sub;

  private boolean settingsListenerInstalled;
  private final PropertyChangeListener settingsListener = evt -> {
    if (evt == null) return;
    if (!UiSettingsBus.PROP_UI_SETTINGS.equals(evt.getPropertyName())) return;
    // Re-render when the user changes max-width, font, etc.
    SwingUtilities.invokeLater(this::renderForCurrentWidth);
  };

  private java.awt.Component resizeListeningOn;
  private boolean visibilityListenerInstalled;
  private final java.awt.event.HierarchyListener visibilityListener = e -> {
    if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) return;
    SwingUtilities.invokeLater(this::syncGifPlaybackState);
  };
  private final java.awt.event.ComponentListener resizeListener = new java.awt.event.ComponentAdapter() {
    @Override
    public void componentResized(java.awt.event.ComponentEvent e) {
      // Debounce-ish: schedule one repaint on EDT.
      SwingUtilities.invokeLater(ChatImageComponent.this::renderForCurrentWidth);
    }
  };

  private AnimatedGifPlayer gifPlayer;

  // When this component is an animated GIF, only the newest GIF in the transcript should animate.
  private boolean gifAnimationAllowed = true;

  // Global user setting: enable/disable animated GIF playback.
  private boolean gifAnimationEnabled = true;
  private boolean lastAnimateSetting = true;
  private java.util.List<javax.swing.ImageIcon> gifFrames = java.util.List.of();
  private int[] gifDelaysMs = new int[0];

  ChatImageComponent(
      String serverId,
      String url,
      ImageFetchService fetch,
      boolean collapsedByDefault,
      UiSettingsBus uiSettingsBus,
      GifAnimationCoordinator gifCoordinator,
      long embedSeq) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    this.serverId = serverId;
    this.url = url;
    this.fetch = fetch;
    this.uiSettingsBus = uiSettingsBus;
    this.gifCoordinator = gifCoordinator;
    this.embedSeq = embedSeq;
    this.collapsedByDefault = collapsedByDefault;
    this.collapsed = collapsedByDefault;

    setOpaque(false);

    renderScaffold();
    beginLoad();
  }

  /**
   * Called by {@link GifAnimationCoordinator} to enforce "only newest GIF animates".
   * Must be invoked on the Swing EDT.
   */
  void setGifAnimationAllowed(boolean allowed) {
    if (this.gifAnimationAllowed == allowed) return;
    this.gifAnimationAllowed = allowed;

    if (gifPlayer != null) {
      syncGifPlaybackState();
      if (!shouldAnimateGifNow() && gifFrames != null && !gifFrames.isEmpty()) {
        imageLabel.setIcon(gifFrames.get(0));
        setLabelPreferredSize(gifFrames.get(0));
      }
      revalidate();
      repaint();
    }
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
      syncGifPlaybackState();
      if (!shouldAnimateGifNow() && gifFrames != null && !gifFrames.isEmpty()) {
        imageLabel.setIcon(gifFrames.get(0));
        setLabelPreferredSize(gifFrames.get(0));
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

    sub = fetch.fetch(serverId, url)
        .observeOn(SwingEdt.scheduler())
        .subscribe(
            b -> {
              bytes = b;
              try {
                decoded = ImageDecodeUtil.decode(url, b);

                // Confirm/deny GIF-ness so the coordinator can enforce "only newest GIF animates".
                if (gifCoordinator != null) {
                  if (decoded instanceof AnimatedGifDecoded) {
                    gifCoordinator.registerAnimatedGif(embedSeq, this);
                  } else {
                    gifCoordinator.rejectGifHint(embedSeq);
                  }
                }

                imageLabel.setText("");
                if (!collapsed) {
                  renderForCurrentWidth();
                }
              } catch (Exception ex) {
                decoded = null;
                if (gifCoordinator != null) {
                  gifCoordinator.rejectGifHint(embedSeq);
                }
                imageLabel.setText("(image decode failed)");
                imageLabel.setToolTipText(url + System.lineSeparator() + ex.getMessage());
              }
            },
            err -> {
              decoded = null;
              if (gifCoordinator != null) {
                gifCoordinator.rejectGifHint(embedSeq);
              }
              imageLabel.setText("(image failed to load)");
              imageLabel.setToolTipText(url + System.lineSeparator() + err.getMessage());
            }
        );
  }

  @Override
  public void addNotify() {
    super.addNotify();

    hookVisibilityListener();
    hookResizeListener();
    hookSettingsListener();

    // If Swing re-adds this component during view rebuilds and scrolling, and we haven't loaded yet,
    // (re)start the fetch. (Do NOT cancel on removeNotify; see removeNotify below.)
    if (bytes == null || bytes.length == 0) {
      if (sub == null || sub.isDisposed()) {
        beginLoad();
      }
    }

    syncGifPlaybackState();
  }

  @Override
  public void removeNotify() {
    // DO NOT dispose the fetch subscription here.
    // JTextPane/StyledDocument may call removeNotify() during view rebuilds and scrolling,
    // which would prevent the image from ever completing and updating the UI.

    if (gifPlayer != null) {
      gifPlayer.stop();
    }
    unhookVisibilityListener();
    unhookResizeListener();
    unhookSettingsListener();
    super.removeNotify();
  }

  private void renderForCurrentWidth() {
    if (collapsed) return;

    DecodedImage d = decoded;
    if (d == null) return;

    int maxW = EmbedHostLayoutUtil.computeMaxInlineWidth(this, FALLBACK_MAX_W, WIDTH_MARGIN_PX, 96);
    if (maxW <= 0) maxW = FALLBACK_MAX_W;

    int capW = 0;
    int capH = 0;
    boolean animateGifs = true;
    if (uiSettingsBus != null) {
      try {
        UiSettings s = uiSettingsBus.get();
        if (s != null) {
          capW = s.imageEmbedsMaxWidthPx();
          capH = s.imageEmbedsMaxHeightPx();
          animateGifs = s.imageEmbedsAnimateGifs();
        }
      } catch (Exception ignored) {
      }
    }
    this.gifAnimationEnabled = animateGifs;

    if (capW > 0) {
      maxW = Math.min(maxW, capW);
    }

    int maxH = (capH > 0) ? capH : 0;

    // Avoid re-scaling on every tiny jitter.
    if (Math.abs(maxW - lastMaxW) < 4 && lastMaxW > 0 && maxH == lastMaxH && animateGifs == lastAnimateSetting) {
      return;
    }
    lastMaxW = maxW;
    lastMaxH = maxH;
    lastAnimateSetting = animateGifs;

    if (d instanceof AnimatedGifDecoded gif) {
      // Build scaled frame icons.
      java.util.List<javax.swing.ImageIcon> icons = new java.util.ArrayList<>(gif.frames().size());
      for (java.awt.image.BufferedImage frame : gif.frames()) {
        java.awt.image.BufferedImage scaled = ImageScaleUtil.scaleDownToFit(frame, maxW, maxH);
        icons.add(new javax.swing.ImageIcon(scaled));
      }

      this.gifFrames = java.util.List.copyOf(icons);
      this.gifDelaysMs = gif.delaysMs();

      if (gifPlayer == null) {
        gifPlayer = new AnimatedGifPlayer(imageLabel);
      }
      gifPlayer.setFrames(icons, gif.delaysMs());
      syncGifPlaybackState();
      if (!shouldAnimateGifNow() && !icons.isEmpty()) {
        imageLabel.setIcon(icons.get(0));
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
      java.awt.image.BufferedImage scaled = ImageScaleUtil.scaleDownToFit(st.image(), maxW, maxH);
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

  private void hookVisibilityListener() {
    if (visibilityListenerInstalled) return;
    addHierarchyListener(visibilityListener);
    visibilityListenerInstalled = true;
  }

  private void unhookVisibilityListener() {
    if (!visibilityListenerInstalled) return;
    removeHierarchyListener(visibilityListener);
    visibilityListenerInstalled = false;
  }

  private void unhookResizeListener() {
    resizeListeningOn = EmbedHostLayoutUtil.unhookResizeListener(resizeListener, resizeListeningOn);
  }

  private void hookSettingsListener() {
    if (uiSettingsBus == null) return;
    if (settingsListenerInstalled) return;
    uiSettingsBus.addListener(settingsListener);
    settingsListenerInstalled = true;
  }

  private void unhookSettingsListener() {
    if (uiSettingsBus == null) return;
    if (!settingsListenerInstalled) return;
    uiSettingsBus.removeListener(settingsListener);
    settingsListenerInstalled = false;
  }

  private boolean shouldAnimateGifNow() {
    if (gifPlayer == null) return false;
    if (collapsed) return false;
    if (!gifAnimationAllowed || !gifAnimationEnabled) return false;
    return isShowing();
  }

  private void syncGifPlaybackState() {
    if (gifPlayer == null) return;
    if (shouldAnimateGifNow()) {
      gifPlayer.start();
    } else {
      gifPlayer.stop();
    }
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
