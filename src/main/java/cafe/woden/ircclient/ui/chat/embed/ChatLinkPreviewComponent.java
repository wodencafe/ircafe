package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.ui.SwingEdt;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;

/**
 * Inline link preview component inserted into the chat transcript StyledDocument.
 */
final class ChatLinkPreviewComponent extends JPanel {

  private static final int FALLBACK_MAX_W = 420;
  private static final int WIDTH_MARGIN_PX = 32;

  private static final int THUMB_SIZE = 96;
  private static final int YT_THUMB_W = 160;
  private static final int YT_THUMB_H = 90;
  private static final int X_THUMB_W = 160;
  private static final int X_THUMB_H = 90;
  private static final int MAX_TITLE_LINES = 2;
  private static final int DEFAULT_MAX_DESC_LINES = 3;
  private static final int WIKIPEDIA_MAX_DESC_LINES = 10;
  private static final int YOUTUBE_MAX_DESC_LINES = 8;
  private static final int X_MAX_DESC_LINES = 8;
  private static final int GITHUB_MAX_DESC_LINES = 6;

  // Padding tweaks for collapsed vs expanded previews.
  private static final Insets OUTER_PAD_EXPANDED = new Insets(2, 0, 6, 0);
  private static final Insets OUTER_PAD_COLLAPSED = new Insets(1, 0, 2, 0);

  private static final Insets CARD_PAD_EXPANDED = new Insets(8, 10, 8, 10);
  private static final Insets CARD_PAD_COLLAPSED = new Insets(4, 10, 4, 10);

  private final String url;
  private final LinkPreviewFetchService fetch;
  private final ImageFetchService imageFetch;
  private final boolean collapsedByDefault;

  private final JLabel status = new JLabel("Loading preview…");

  private JPanel card;
  private JPanel header;
  private JPanel body;

  private JButton collapseBtn;
  private JLabel thumb;
  private java.awt.Component thumbHost;
  private JTextArea title;
  private JTextArea desc;
  private JLabel site;

  private boolean wikiExtended;

  private boolean youtubeExtended;
  private String youtubeVideoId;

  private boolean xExtended;
  private boolean githubExtended;

  private String fullDescText;

  private boolean collapsed;

  private Disposable sub;
  private Disposable thumbSub;

  private volatile int lastMaxW = -1;
  // Layout in a JTextPane can be finicky. We keep a small cache key so we can avoid
  // unnecessary relayout, but still relayout when the collapsed state changes.
  private volatile boolean lastCollapsedLayout = false;
  private java.awt.Component resizeListeningOn;
  private final java.awt.event.ComponentListener resizeListener = new java.awt.event.ComponentAdapter() {
    @Override
    public void componentResized(java.awt.event.ComponentEvent e) {
      SwingUtilities.invokeLater(ChatLinkPreviewComponent.this::layoutForCurrentWidth);
    }
  };

  ChatLinkPreviewComponent(String url, LinkPreviewFetchService fetch, ImageFetchService imageFetch, boolean collapsedByDefault) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    this.url = url;
    this.fetch = fetch;
    this.imageFetch = imageFetch;
    this.collapsedByDefault = collapsedByDefault;
    this.collapsed = collapsedByDefault;

    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(
        OUTER_PAD_EXPANDED.top, OUTER_PAD_EXPANDED.left,
        OUTER_PAD_EXPANDED.bottom, OUTER_PAD_EXPANDED.right));

    status.setOpaque(false);
    status.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    add(status);

    beginLoad();
  }

  private void beginLoad() {
    if (fetch == null) {
      status.setText("(link previews disabled)");
      return;
    }

    sub = fetch.fetch(url)
        .observeOn(SwingEdt.scheduler())
        .subscribe(
            this::renderPreview,
            err -> {
              status.setText("");
              status.setText("(preview failed)");
              status.setToolTipText(url + System.lineSeparator() + err.getMessage());
            }
        );
  }

  private void renderPreview(LinkPreview p) {
    removeAll();

    // Main card container.
    card = new JPanel(new BorderLayout());
    card.setOpaque(true);
    card.setBorder(buildCardBorder(collapsed));

    String targetUrl = safe(p.url()) != null ? p.url() : url;
    wikiExtended = WikipediaPreviewUtil.isWikipediaArticleUrl(targetUrl);

    youtubeVideoId = YouTubePreviewUtil.extractVideoId(targetUrl);
    youtubeExtended = youtubeVideoId != null;

    xExtended = XPreviewUtil.isXStatusUrl(targetUrl);
    githubExtended = GitHubPreviewUtil.isGitHubUrl(targetUrl);
    // Header: collapse button + site + title.
    header = new JPanel(new BorderLayout(8, 0));
    header.setOpaque(false);

    collapseBtn = new JButton();
    collapseBtn.setFocusable(false);
    collapseBtn.setBorderPainted(false);
    collapseBtn.setContentAreaFilled(false);
    collapseBtn.setOpaque(false);
    collapseBtn.setMargin(new Insets(0, 0, 0, 0));
    collapseBtn.setToolTipText(collapsed ? "Expand preview" : "Collapse preview");
    collapseBtn.addActionListener(e -> {
      collapsed = !collapsed;
      applyCollapsedState();
      layoutForCurrentWidth();
    });

    JPanel headerText = new JPanel();
    headerText.setOpaque(false);
    headerText.setLayout(new javax.swing.BoxLayout(headerText, javax.swing.BoxLayout.Y_AXIS));

    String siteName = safe(p.siteName());
    if (siteName == null || siteName.isBlank()) {
      siteName = hostFromUrl(targetUrl);
    }
    site = new JLabel(siteName);
    site.setOpaque(false);

    String titleText = safe(p.title());
    if (titleText == null || titleText.isBlank()) {
      titleText = targetUrl;
    }
    title = textArea(titleText, true);

    headerText.add(site);
    if (!title.getText().isBlank()) {
      headerText.add(title);
    }

    header.add(collapseBtn, BorderLayout.WEST);
    header.add(headerText, BorderLayout.CENTER);

    // Body: optional thumbnail + description.
    body = new JPanel(new BorderLayout(10, 0));
    body.setOpaque(false);

    thumb = new JLabel();
    thumbHost = thumb;
    int thumbW = youtubeExtended ? YT_THUMB_W : (xExtended ? X_THUMB_W : THUMB_SIZE);
    int thumbH = youtubeExtended ? YT_THUMB_H : (xExtended ? X_THUMB_H : THUMB_SIZE);
    thumb.setPreferredSize(new Dimension(thumbW, thumbH));
    thumb.setMinimumSize(new Dimension(thumbW, thumbH));
    thumb.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

    // Badge for multi-media posts (e.g., Mastodon with multiple attachments).
    int extraMedia = Math.max(0, p.mediaCount() - 1);
    if (extraMedia > 0) {
      JPanel wrap = new JPanel();
      wrap.setOpaque(false);
      wrap.setLayout(new OverlayLayout(wrap));
      Dimension tdim = new Dimension(thumbW, thumbH);
      wrap.setPreferredSize(tdim);
      wrap.setMinimumSize(tdim);
      wrap.setMaximumSize(tdim);

      // Keep the thumbnail centered; badge pinned to bottom-right.
      thumb.setAlignmentX(0.5f);
      thumb.setAlignmentY(0.5f);

      JLabel badge = new JLabel("+" + extraMedia + " more");
      badge.setOpaque(true);
      badge.setForeground(Color.WHITE);
      badge.setBackground(new Color(0, 0, 0, 170));
      badge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
      try {
        badge.setFont(badge.getFont().deriveFont(badge.getFont().getStyle() | Font.BOLD));
      } catch (Exception ignored) {
      }
      badge.setAlignmentX(1.0f);
      badge.setAlignmentY(1.0f);

      wrap.add(thumb);
      wrap.add(badge);
      thumbHost = wrap;
    }

    fullDescText = safe(p.description());
    desc = textArea(fullDescText, false);

    if (p.imageUrl() != null && !p.imageUrl().isBlank()) {
      body.add(thumbHost, BorderLayout.WEST);
      loadThumbnail(p.imageUrl());
    }

    if (!desc.getText().isBlank()) {
      body.add(desc, BorderLayout.CENTER);
    }

    card.add(header, BorderLayout.NORTH);
    card.add(body, BorderLayout.CENTER);

    // Interactions (open link, copy link)
    card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    card.setToolTipText(targetUrl);
    installPopup(card, targetUrl);
    card.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
          openUrl(targetUrl);
        }
      }
    });

    add(card);

    // Apply collapse after the UI exists.
    collapsed = collapsedByDefault;
    applyCollapsedState();

    layoutForCurrentWidth();
    revalidate();
    repaint();
  }

  private void applyCollapsedState() {
    if (collapseBtn != null) {
      collapseBtn.setText(collapsed ? "▸" : "▾");
      collapseBtn.setToolTipText(collapsed ? "Expand preview" : "Collapse preview");
    }
    if (body != null) {
      body.setVisible(!collapsed);
    }

    // When embedded into a JTextPane StyledDocument, Swing may keep reserving the prior
    // preferred height unless we recompute our preferred size after collapsing.
    applyBordersForState();
    lastMaxW = -1; // bust width/height cache
    layoutForCurrentWidth();
  }

  private void loadThumbnail(String imageUrl) {
    if (imageFetch == null) return;
    if (thumbSub != null && !thumbSub.isDisposed()) {
      thumbSub.dispose();
      thumbSub = null;
    }

    thumbSub = imageFetch.fetch(imageUrl)
        .observeOn(SwingEdt.scheduler())
        .subscribe(
            bytes -> {
              try {
                DecodedImage d = ImageDecodeUtil.decode(imageUrl, bytes);
                java.awt.image.BufferedImage img = null;
                if (d instanceof StaticImageDecoded st) {
                  img = st.image();
                } else if (d instanceof AnimatedGifDecoded gif && !gif.frames().isEmpty()) {
                  img = gif.frames().get(0);
                }
                if (img != null) {
                  int maxW = THUMB_SIZE;
                  try {
                    Dimension dsz = thumb != null ? thumb.getPreferredSize() : null;
                    if (dsz != null && dsz.width > 0) maxW = dsz.width;
                  } catch (Exception ignored2) {
                    // best effort
                  }
                  java.awt.image.BufferedImage scaled = ImageScaleUtil.scaleDownToWidth(img, maxW);
                  thumb.setIcon(new javax.swing.ImageIcon(scaled));
                }
              } catch (Exception ignored) {
                // ignore thumbnail failures
              }
            },
            err -> {
              // ignore thumbnail failures
            }
        );
  }

  @Override
  public void addNotify() {
    super.addNotify();
    hookResizeListener();

    // IMPORTANT: JTextPane/StyledDocument may temporarily remove and re-add embedded components
    // during view rebuilds and scrolling. If we cancel the fetch on removeNotify(), the preview
    // can get stuck on "Loading preview…" forever. If we haven't rendered yet and the subscription
    // is missing/disposed, (re)start the load.
    if (card == null) {
      if (sub == null || sub.isDisposed()) {
        beginLoad();
      }
    }
  }

  @Override
  public void removeNotify() {
    unhookResizeListener();
    // DO NOT dispose the fetch subscriptions here.
    // JTextPane/StyledDocument may call removeNotify() during view rebuilds and scrolling,
    // which would prevent the preview from ever completing and updating the UI.
    super.removeNotify();
  }

  private void layoutForCurrentWidth() {
    if (card == null) return;
    int maxW = EmbedHostLayoutUtil.computeMaxInlineWidth(this, FALLBACK_MAX_W, WIDTH_MARGIN_PX, 220);
    if (maxW <= 0) maxW = FALLBACK_MAX_W;

    if (Math.abs(maxW - lastMaxW) < 4 && lastMaxW > 0 && collapsed == lastCollapsedLayout) return;
    lastMaxW = maxW;
    lastCollapsedLayout = collapsed;

    // Clamp header/title and body/desc widths.
    int headerInnerW = Math.max(160, maxW - 28);
    if (title != null) {
      title.setSize(new Dimension(headerInnerW, Short.MAX_VALUE));
      clampLines(title, MAX_TITLE_LINES);
    }

    int descInnerW = maxW;
    if (thumbHost != null && body != null && thumbHost.getParent() == body) {
      int tw = THUMB_SIZE;
      try {
        Dimension tps = thumbHost.getPreferredSize();
        if (tps != null && tps.width > 0) tw = tps.width;
      } catch (Exception ignored) {
        // best effort
      }
      descInnerW = Math.max(120, maxW - tw - 14);
    }

    int maxDescLines;
    if (wikiExtended) {
      maxDescLines = WIKIPEDIA_MAX_DESC_LINES;
    } else if (youtubeExtended) {
      maxDescLines = YOUTUBE_MAX_DESC_LINES;
    } else if (xExtended) {
      maxDescLines = X_MAX_DESC_LINES;
    } else if (githubExtended) {
      maxDescLines = GITHUB_MAX_DESC_LINES;
    } else {
      maxDescLines = DEFAULT_MAX_DESC_LINES;
    }

    if (desc != null) {
      desc.setSize(new Dimension(descInnerW, Short.MAX_VALUE));

      // Prefer an end-of-sentence clamp instead of a hard visual clip.
      if (fullDescText != null && !fullDescText.isBlank()) {
        String clamped = PreviewTextUtil.clampToLines(fullDescText, desc, descInnerW, maxDescLines);
        if (clamped != null && !clamped.equals(desc.getText())) {
          desc.setText(clamped);
        }
      }

      clampLines(desc, maxDescLines);
    }

    // We want to clamp the width, but NOT lock the height.
    // If we set a preferred size with an expanded height, a later collapse may stay blank
    // but still reserve the same vertical space.
    card.setPreferredSize(null); // clear any prior fixed preferred size
    Dimension pref = card.getPreferredSize();
    card.setPreferredSize(new Dimension(maxW, pref.height));

    revalidate();
    repaint();
    EmbedHostLayoutUtil.requestHostReflow(this);
  }

  private static JTextArea textArea(String text, boolean bold) {
    JTextArea ta = new JTextArea(text == null ? "" : text);
    ta.setOpaque(false);
    ta.setEditable(false);
    ta.setFocusable(false);
    ta.setLineWrap(true);
    ta.setWrapStyleWord(true);
    ta.setBorder(null);
    ta.setHighlighter(null);
    Font f = ta.getFont();
    if (bold) {
      ta.setFont(f.deriveFont(f.getStyle() | Font.BOLD));
    }
    return ta;
  }

  private static void clampLines(JTextArea ta, int maxLines) {
    if (ta == null || maxLines <= 0) return;
    int lh = ta.getFontMetrics(ta.getFont()).getHeight();
    int maxH = lh * maxLines + 4;
    Dimension pref = ta.getPreferredSize();
    if (pref.height > maxH) {
      ta.setPreferredSize(new Dimension(pref.width, maxH));
      ta.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxH));
    }
  }

  private void hookResizeListener() {
    resizeListeningOn = EmbedHostLayoutUtil.hookResizeListener(this, resizeListener, resizeListeningOn);
  }

  private void unhookResizeListener() {
    resizeListeningOn = EmbedHostLayoutUtil.unhookResizeListener(resizeListener, resizeListeningOn);
  }

  private void disposeSubs() {
    if (sub != null && !sub.isDisposed()) {
      sub.dispose();
      sub = null;
    }
    if (thumbSub != null && !thumbSub.isDisposed()) {
      thumbSub.dispose();
      thumbSub = null;
    }
  }

  private void installPopup(JPanel p, String targetUrl) {
    JPopupMenu menu = new JPopupMenu();

    JMenuItem open = new JMenuItem("Open link");
    open.addActionListener(e -> openUrl(targetUrl));

    JMenuItem copy = new JMenuItem("Copy link");
    copy.addActionListener(e -> {
      try {
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(targetUrl), null);
      } catch (Exception ignored) {
      }
    });

    menu.add(open);
    menu.add(copy);

    p.setComponentPopupMenu(menu);
  }

  private void openUrl(String u) {
    if (u == null || u.isBlank()) return;
    try {
      java.awt.Desktop.getDesktop().browse(new URI(u));
    } catch (Exception ignored) {
    }
  }

  private static String safe(String s) {
    if (s == null) return null;
    String t = s.strip();
    return t.isEmpty() ? null : t;
  }

  private static String hostFromUrl(String u) {
    if (u == null) return "";
    try {
      URI uri = new URI(u);
      String host = uri.getHost();
      if (host == null && !Objects.equals(uri.getScheme(), null)) {
        // Some URIs might be missing //, try again.
        uri = new URI(uri.getScheme() + "://" + uri.getSchemeSpecificPart());
        host = uri.getHost();
      }
      if (host == null) return u;
      return host.startsWith("www.") ? host.substring(4) : host;
    } catch (Exception e) {
      return u;
    }
  }


  private void applyBordersForState() {
    Insets outer = collapsed ? OUTER_PAD_COLLAPSED : OUTER_PAD_EXPANDED;
    setBorder(BorderFactory.createEmptyBorder(outer.top, outer.left, outer.bottom, outer.right));
    if (card != null) {
      card.setBorder(buildCardBorder(collapsed));
    }
  }

  private static javax.swing.border.Border buildCardBorder(boolean collapsed) {
    Insets pad = collapsed ? CARD_PAD_COLLAPSED : CARD_PAD_EXPANDED;
    return BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 4, 1, 1, borderColor()),
        BorderFactory.createEmptyBorder(pad.top, pad.left, pad.bottom, pad.right)
    );
  }

  private static java.awt.Color borderColor() {
    java.awt.Color base = javax.swing.UIManager.getColor("Component.borderColor");
    if (base != null) return base;
    java.awt.Color fg = javax.swing.UIManager.getColor("Label.foreground");
    if (fg == null) fg = java.awt.Color.GRAY;
    return new java.awt.Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 120);
  }
}
