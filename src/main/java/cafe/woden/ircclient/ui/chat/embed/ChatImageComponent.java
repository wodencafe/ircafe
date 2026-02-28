package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.ui.SwingEdt;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.settings.EmbedCardStyle;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.beans.PropertyChangeListener;
import java.net.URI;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

final class ChatImageComponent extends JPanel {

  // Fallback width if we can't determine the transcript viewport width yet.
  private static final int FALLBACK_MAX_W = 360;
  // Keep large original payloads from piling up across many embeds.
  // When this threshold is exceeded we keep the rendered preview and reopen via browser on click.
  private static final int MAX_RETAINED_ORIGINAL_BYTES = 2 * 1024 * 1024;
  // Subtract some breathing room so we don't force horizontal scrolling.
  private static final int WIDTH_MARGIN_PX = 32;
  private static final int SUBS_DISPOSE_DELAY_MS = 1200;
  private static final int COLLAPSE_ICON_SIZE = 12;
  private static final Icon COLLAPSED_ICON = SvgIcons.action("play", COLLAPSE_ICON_SIZE);
  private static final Icon EXPANDED_ICON = SvgIcons.action("arrow-down", COLLAPSE_ICON_SIZE);
  private static final Insets OUTER_PAD_EXPANDED_DEFAULT = new Insets(4, 0, 8, 0);
  private static final Insets OUTER_PAD_COLLAPSED_DEFAULT = new Insets(2, 0, 4, 0);
  private static final Insets CARD_PAD_EXPANDED_DEFAULT = new Insets(10, 12, 10, 12);
  private static final Insets CARD_PAD_COLLAPSED_DEFAULT = new Insets(6, 12, 6, 12);
  private static final int CARD_CORNER_ARC_DEFAULT = 18;

  private final String url;
  private final String serverId;
  private final ImageFetchService fetch;
  private final UiSettingsBus uiSettingsBus;
  private final EmbedCardStyle cardStyle;

  // Coordinates "only newest GIF animates" within a transcript.
  private final GifAnimationCoordinator gifCoordinator;
  private final long embedSeq;

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
  private javax.swing.Timer deferredDisposeTimer;

  private boolean settingsListenerInstalled;
  private final PropertyChangeListener settingsListener =
      evt -> {
        if (evt == null) return;
        if (!UiSettingsBus.PROP_UI_SETTINGS.equals(evt.getPropertyName())) return;
        // Re-render when the user changes max-width, font, etc.
        SwingUtilities.invokeLater(this::renderForCurrentWidth);
      };

  private java.awt.Component resizeListeningOn;
  private boolean visibilityListenerInstalled;
  private final java.awt.event.HierarchyListener visibilityListener =
      e -> {
        if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) == 0) return;
        SwingUtilities.invokeLater(this::syncGifPlaybackState);
      };
  private final java.awt.event.ComponentListener resizeListener =
      new java.awt.event.ComponentAdapter() {
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

  ChatImageComponent(
      String serverId,
      String url,
      ImageFetchService fetch,
      boolean collapsedByDefault,
      UiSettingsBus uiSettingsBus,
      EmbedCardStyle cardStyle,
      GifAnimationCoordinator gifCoordinator,
      long embedSeq) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    this.serverId = serverId;
    this.url = url;
    this.fetch = fetch;
    this.uiSettingsBus = uiSettingsBus;
    this.cardStyle = cardStyle != null ? cardStyle : EmbedCardStyle.DEFAULT;
    this.gifCoordinator = gifCoordinator;
    this.embedSeq = embedSeq;

    this.collapsed = collapsedByDefault;

    setOpaque(false);
    Insets outer = outerPadExpanded();
    setBorder(BorderFactory.createEmptyBorder(outer.top, outer.left, outer.bottom, outer.right));

    renderScaffold();
    beginLoad();
  }

  /**
   * Called by {@link GifAnimationCoordinator} to enforce "only newest GIF animates". Must be
   * invoked on the Swing EDT.
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

    card = createCardPanel();
    card.setBorder(buildCardBorder(collapsed));

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
    JPanel h = new JPanel(new BorderLayout(headerGap(), 0));
    h.setOpaque(false);

    collapseBtn = new JButton();
    collapseBtn.setFocusable(false);
    collapseBtn.setBorderPainted(false);
    collapseBtn.setContentAreaFilled(false);
    collapseBtn.setMargin(new Insets(0, 0, 0, 0));
    collapseBtn.addActionListener(
        e -> {
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
    b.setBorder(BorderFactory.createEmptyBorder(bodyTopGap(), 0, 0, 0));

    imageLabel.setOpaque(false);
    Insets mediaPad = mediaPadding();
    imageLabel.setBorder(
        BorderFactory.createCompoundBorder(
            new javax.swing.border.LineBorder(borderColor(), 1, true),
            BorderFactory.createEmptyBorder(
                mediaPad.top, mediaPad.left, mediaPad.bottom, mediaPad.right)));
    imageLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    imageLabel.setToolTipText(url);

    imageLabel.addMouseListener(
        new java.awt.event.MouseAdapter() {
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
      collapseBtn.setText("");
      collapseBtn.setIcon(collapsed ? COLLAPSED_ICON : EXPANDED_ICON);
      collapseBtn.setToolTipText(collapsed ? "Expand image" : "Collapse image");
    }

    if (body != null) {
      body.setVisible(!collapsed);
    }
    applyBorderForState();

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

    sub =
        fetch
            .fetch(serverId, url)
            .observeOn(SwingEdt.scheduler())
            .subscribe(
                b -> {
                  bytes = b;
                  try {
                    decoded = ImageDecodeUtil.decode(url, b);

                    // Confirm/deny GIF-ness so the coordinator can enforce "only newest GIF
                    // animates".
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
                    if (b.length > MAX_RETAINED_ORIGINAL_BYTES) {
                      bytes = null;
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
                });
  }

  @Override
  public void addNotify() {
    super.addNotify();
    cancelDeferredSubDispose();

    hookVisibilityListener();
    hookResizeListener();
    hookSettingsListener();

    // If Swing re-adds this component during view rebuilds and scrolling, and we haven't loaded
    // yet,
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
    scheduleDeferredSubDispose();

    if (gifPlayer != null) {
      gifPlayer.stop();
    }
    unhookVisibilityListener();
    unhookResizeListener();
    unhookSettingsListener();
    super.removeNotify();
  }

  private void scheduleDeferredSubDispose() {
    if (deferredDisposeTimer == null) {
      deferredDisposeTimer =
          new javax.swing.Timer(
              SUBS_DISPOSE_DELAY_MS,
              e -> {
                if (isDisplayable()) return;
                disposeLoadSubscription();
              });
      deferredDisposeTimer.setRepeats(false);
    }
    deferredDisposeTimer.restart();
  }

  private void cancelDeferredSubDispose() {
    if (deferredDisposeTimer != null) {
      deferredDisposeTimer.stop();
    }
  }

  private void disposeLoadSubscription() {
    cancelDeferredSubDispose();
    if (sub != null && !sub.isDisposed()) {
      sub.dispose();
    }
    sub = null;
  }

  private void renderForCurrentWidth() {
    if (collapsed) return;

    DecodedImage d = decoded;
    if (d == null) {
      byte[] raw = bytes;
      if (raw == null || raw.length == 0) return;
      try {
        d = ImageDecodeUtil.decode(url, raw);
        decoded = d;
      } catch (Exception ignored) {
        imageLabel.setText("(image decode failed)");
        return;
      }
    }
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
    if (Math.abs(maxW - lastMaxW) < 4
        && lastMaxW > 0
        && maxH == lastMaxH
        && animateGifs == lastAnimateSetting) {
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
      decoded = null;
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
    resizeListeningOn =
        EmbedHostLayoutUtil.hookResizeListener(this, resizeListener, resizeListeningOn);
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
    copyUrl.addActionListener(
        e -> {
          Toolkit.getDefaultToolkit()
              .getSystemClipboard()
              .setContents(new StringSelection(url), null);
        });

    menu.add(view);
    menu.add(openBrowser);
    menu.add(copyUrl);

    target.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mousePressed(java.awt.event.MouseEvent e) {
            if (e.isPopupTrigger()) {
              PopupMenuThemeSupport.prepareForDisplay(menu);
              menu.show(target, e.getX(), e.getY());
            }
          }

          @Override
          public void mouseReleased(java.awt.event.MouseEvent e) {
            if (e.isPopupTrigger()) {
              PopupMenuThemeSupport.prepareForDisplay(menu);
              menu.show(target, e.getX(), e.getY());
            }
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

  private JPanel createCardPanel() {
    JPanel panel =
        new JPanel(new BorderLayout()) {
          @Override
          protected void paintComponent(java.awt.Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
              g2.setRenderingHint(
                  RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
              g2.setColor(cardBackgroundColor());
              g2.fillRoundRect(
                  0,
                  0,
                  Math.max(0, getWidth() - 1),
                  Math.max(0, getHeight() - 1),
                  cardCornerArc(),
                  cardCornerArc());
            } finally {
              g2.dispose();
            }
          }
        };
    panel.setOpaque(false);
    return panel;
  }

  private void applyBorderForState() {
    Insets outer = collapsed ? outerPadCollapsed() : outerPadExpanded();
    setBorder(BorderFactory.createEmptyBorder(outer.top, outer.left, outer.bottom, outer.right));
    if (card != null) {
      card.setBorder(buildCardBorder(collapsed));
    }
  }

  private javax.swing.border.Border buildCardBorder(boolean collapsedState) {
    Insets pad = cardPadding(collapsedState);
    return BorderFactory.createCompoundBorder(
        new javax.swing.border.LineBorder(borderColor(), 1, true),
        BorderFactory.createEmptyBorder(pad.top, pad.left, pad.bottom, pad.right));
  }

  private java.awt.Color borderColor() {
    java.awt.Color base = javax.swing.UIManager.getColor("Component.borderColor");
    if (base == null) {
      base = blend(foregroundColor(), baseBackgroundColor(), 0.35);
    }
    return switch (cardStyle) {
      case MINIMAL -> withAlpha(base, 105);
      case GLASSY ->
          withAlpha(
              blend(base, firstNonNullColor("Component.linkColor", "Component.focusColor"), 0.20),
              205);
      case DENSER -> withAlpha(base, 190);
      case DEFAULT -> withAlpha(base, 170);
    };
  }

  private java.awt.Color cardBackgroundColor() {
    java.awt.Color base = baseBackgroundColor();
    return switch (cardStyle) {
      case MINIMAL -> {
        if (isDark(base)) yield blend(base, java.awt.Color.WHITE, collapsed ? 0.01 : 0.03);
        yield blend(base, java.awt.Color.BLACK, collapsed ? 0.005 : 0.015);
      }
      case GLASSY -> {
        if (isDark(base)) yield blend(base, java.awt.Color.WHITE, collapsed ? 0.10 : 0.16);
        yield blend(base, java.awt.Color.BLACK, collapsed ? 0.04 : 0.07);
      }
      case DENSER -> {
        if (isDark(base)) yield blend(base, java.awt.Color.WHITE, collapsed ? 0.05 : 0.08);
        yield blend(base, java.awt.Color.BLACK, collapsed ? 0.015 : 0.03);
      }
      case DEFAULT -> {
        if (isDark(base)) yield blend(base, java.awt.Color.WHITE, collapsed ? 0.06 : 0.10);
        yield blend(base, java.awt.Color.BLACK, collapsed ? 0.02 : 0.04);
      }
    };
  }

  private Insets outerPadExpanded() {
    return switch (cardStyle) {
      case MINIMAL -> new Insets(1, 0, 4, 0);
      case GLASSY -> new Insets(6, 0, 10, 0);
      case DENSER -> new Insets(2, 0, 5, 0);
      case DEFAULT ->
          new Insets(
              OUTER_PAD_EXPANDED_DEFAULT.top,
              OUTER_PAD_EXPANDED_DEFAULT.left,
              OUTER_PAD_EXPANDED_DEFAULT.bottom,
              OUTER_PAD_EXPANDED_DEFAULT.right);
    };
  }

  private Insets outerPadCollapsed() {
    return switch (cardStyle) {
      case MINIMAL -> new Insets(0, 0, 2, 0);
      case GLASSY -> new Insets(3, 0, 6, 0);
      case DENSER -> new Insets(1, 0, 3, 0);
      case DEFAULT ->
          new Insets(
              OUTER_PAD_COLLAPSED_DEFAULT.top,
              OUTER_PAD_COLLAPSED_DEFAULT.left,
              OUTER_PAD_COLLAPSED_DEFAULT.bottom,
              OUTER_PAD_COLLAPSED_DEFAULT.right);
    };
  }

  private Insets cardPadding(boolean collapsedState) {
    if (collapsedState) {
      return switch (cardStyle) {
        case MINIMAL -> new Insets(3, 8, 3, 8);
        case GLASSY -> new Insets(7, 14, 7, 14);
        case DENSER -> new Insets(2, 8, 2, 8);
        case DEFAULT ->
            new Insets(
                CARD_PAD_COLLAPSED_DEFAULT.top,
                CARD_PAD_COLLAPSED_DEFAULT.left,
                CARD_PAD_COLLAPSED_DEFAULT.bottom,
                CARD_PAD_COLLAPSED_DEFAULT.right);
      };
    }
    return switch (cardStyle) {
      case MINIMAL -> new Insets(6, 8, 6, 8);
      case GLASSY -> new Insets(12, 14, 12, 14);
      case DENSER -> new Insets(5, 8, 5, 8);
      case DEFAULT ->
          new Insets(
              CARD_PAD_EXPANDED_DEFAULT.top,
              CARD_PAD_EXPANDED_DEFAULT.left,
              CARD_PAD_EXPANDED_DEFAULT.bottom,
              CARD_PAD_EXPANDED_DEFAULT.right);
    };
  }

  private Insets mediaPadding() {
    return switch (cardStyle) {
      case MINIMAL -> new Insets(1, 1, 1, 1);
      case GLASSY -> new Insets(3, 3, 3, 3);
      case DENSER -> new Insets(1, 1, 1, 1);
      case DEFAULT -> new Insets(2, 2, 2, 2);
    };
  }

  private int cardCornerArc() {
    return switch (cardStyle) {
      case MINIMAL -> 10;
      case GLASSY -> 24;
      case DENSER -> 12;
      case DEFAULT -> CARD_CORNER_ARC_DEFAULT;
    };
  }

  private int headerGap() {
    return switch (cardStyle) {
      case MINIMAL -> 8;
      case GLASSY -> 12;
      case DENSER -> 6;
      case DEFAULT -> 8;
    };
  }

  private int bodyTopGap() {
    return switch (cardStyle) {
      case MINIMAL -> 4;
      case GLASSY -> 10;
      case DENSER -> 5;
      case DEFAULT -> 8;
    };
  }

  private static java.awt.Color firstNonNullColor(String... keys) {
    if (keys != null) {
      for (String key : keys) {
        if (key == null || key.isBlank()) continue;
        java.awt.Color c = javax.swing.UIManager.getColor(key);
        if (c != null) return c;
      }
    }
    return foregroundColor();
  }

  private static java.awt.Color foregroundColor() {
    java.awt.Color fg = javax.swing.UIManager.getColor("Label.foreground");
    if (fg == null) fg = java.awt.Color.DARK_GRAY;
    return fg;
  }

  private static java.awt.Color baseBackgroundColor() {
    java.awt.Color bg = javax.swing.UIManager.getColor("TextPane.background");
    if (bg == null) bg = javax.swing.UIManager.getColor("Panel.background");
    if (bg == null) bg = java.awt.Color.WHITE;
    return bg;
  }

  private static java.awt.Color blend(java.awt.Color a, java.awt.Color b, double ratioB) {
    double clamped = Math.max(0d, Math.min(1d, ratioB));
    double ratioA = 1d - clamped;
    int r = (int) Math.round(a.getRed() * ratioA + b.getRed() * clamped);
    int g = (int) Math.round(a.getGreen() * ratioA + b.getGreen() * clamped);
    int bl = (int) Math.round(a.getBlue() * ratioA + b.getBlue() * clamped);
    return new java.awt.Color(clampChannel(r), clampChannel(g), clampChannel(bl), 255);
  }

  private static java.awt.Color withAlpha(java.awt.Color c, int alpha) {
    return new java.awt.Color(c.getRed(), c.getGreen(), c.getBlue(), clampChannel(alpha));
  }

  private static int clampChannel(int value) {
    return Math.max(0, Math.min(255, value));
  }

  private static boolean isDark(java.awt.Color c) {
    double luminance = (0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue()) / 255d;
    return luminance < 0.52;
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
