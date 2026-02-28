package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.ui.SwingEdt;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.settings.EmbedCardStyle;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ChatLinkPreviewComponent extends JPanel {

  private static final Logger log = LoggerFactory.getLogger(ChatLinkPreviewComponent.class);

  private static final int FALLBACK_MAX_W = 420;
  private static final int WIDTH_MARGIN_PX = 32;
  private static final int SUBS_DISPOSE_DELAY_MS = 1200;

  private static final int THUMB_SIZE = 96;
  private static final int IMDB_THUMB_W = 96;
  private static final int IMDB_THUMB_H = 144;
  private static final int YT_THUMB_W = 160;
  private static final int YT_THUMB_H = 90;
  private static final int X_THUMB_W = 160;
  private static final int X_THUMB_H = 90;
  private static final int INSTAGRAM_THUMB_W = 320;
  private static final int INSTAGRAM_THUMB_H = 320;
  private static final int IMGUR_THUMB_W = 320;
  private static final int IMGUR_THUMB_H = 320;
  private static final int MAX_TITLE_LINES = 2;
  private static final int DEFAULT_MAX_DESC_LINES = 3;
  private static final int WIKIPEDIA_MAX_DESC_LINES = 10;
  private static final int YOUTUBE_MAX_DESC_LINES = 8;
  private static final int SLASHDOT_MAX_DESC_LINES = 10;
  private static final int IMDB_CREDITS_MAX_LINES = 4;
  private static final int IMDB_MAX_DESC_LINES = 30;
  private static final int X_MAX_DESC_LINES = 8;
  private static final int GITHUB_MAX_DESC_LINES = 6;
  private static final int INSTAGRAM_MAX_DESC_LINES = 28;
  private static final int IMGUR_MAX_DESC_LINES = 24;
  private static final int NEWS_MAX_DESC_LINES = 22;

  // Default style (kept as the current look).
  private static final Insets OUTER_PAD_EXPANDED_DEFAULT = new Insets(4, 0, 8, 0);
  private static final Insets OUTER_PAD_COLLAPSED_DEFAULT = new Insets(2, 0, 4, 0);
  private static final Insets CARD_PAD_EXPANDED_DEFAULT = new Insets(10, 12, 10, 12);
  private static final Insets CARD_PAD_COLLAPSED_DEFAULT = new Insets(6, 12, 6, 12);
  private static final int CARD_CORNER_ARC_DEFAULT = 18;
  private static final int EXT_MEDIA_CARD_CHROME_PX = 28;
  private static final int COLLAPSE_ICON_SIZE = 12;
  private static final Icon COLLAPSED_ICON = SvgIcons.action("play", COLLAPSE_ICON_SIZE);
  private static final Icon EXPANDED_ICON = SvgIcons.action("arrow-down", COLLAPSE_ICON_SIZE);

  private final String url;
  private final String serverId;
  private final LinkPreviewFetchService fetch;
  private final ImageFetchService imageFetch;
  private final boolean collapsedByDefault;
  private final EmbedCardStyle cardStyle;
  private final int mediaMaxWidthPx;
  private final int mediaMaxHeightPx;

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

  private boolean slashdotExtended;

  private boolean youtubeExtended;
  private String youtubeVideoId;

  private boolean xExtended;
  private boolean githubExtended;

  private boolean imdbExtended;
  private boolean rtExtended;
  private boolean instagramExtended;
  private boolean imgurExtended;
  private boolean newsExtended;

  private JLabel imdbMeta;
  private JTextArea imdbCredits;
  private JSeparator imdbSeparator;
  private JTextArea imdbSummary;
  private String imdbCreditsText;

  private String fullDescText;
  private boolean thumbnailBesideText = true;

  private boolean collapsed;

  private Disposable sub;
  private Disposable thumbSub;
  private javax.swing.Timer deferredDisposeTimer;

  private volatile int lastMaxW = -1;
  // Layout in a JTextPane can be finicky. We keep a small cache key so we can avoid
  // unnecessary relayout, but still relayout when the collapsed state changes.
  private volatile boolean lastCollapsedLayout = false;
  private java.awt.Component resizeListeningOn;
  private final java.awt.event.ComponentListener resizeListener =
      new java.awt.event.ComponentAdapter() {
        @Override
        public void componentResized(java.awt.event.ComponentEvent e) {
          SwingUtilities.invokeLater(ChatLinkPreviewComponent.this::layoutForCurrentWidth);
        }
      };

  ChatLinkPreviewComponent(
      String serverId,
      String url,
      LinkPreviewFetchService fetch,
      ImageFetchService imageFetch,
      boolean collapsedByDefault,
      EmbedCardStyle cardStyle,
      int mediaMaxWidthPx,
      int mediaMaxHeightPx) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    this.serverId = serverId;
    this.url = url;
    this.fetch = fetch;
    this.imageFetch = imageFetch;
    this.collapsedByDefault = collapsedByDefault;
    this.cardStyle = cardStyle != null ? cardStyle : EmbedCardStyle.DEFAULT;
    this.mediaMaxWidthPx = Math.max(0, mediaMaxWidthPx);
    this.mediaMaxHeightPx = Math.max(0, mediaMaxHeightPx);
    this.collapsed = collapsedByDefault;

    setOpaque(false);
    Insets outer = outerPadExpanded();
    setBorder(BorderFactory.createEmptyBorder(outer.top, outer.left, outer.bottom, outer.right));

    status.setOpaque(false);
    Insets statusPad = statusPadding();
    status.setBorder(
        BorderFactory.createEmptyBorder(
            statusPad.top, statusPad.left, statusPad.bottom, statusPad.right));
    status.setForeground(metaTextColor());
    add(status);

    beginLoad();
  }

  private void beginLoad() {
    if (fetch == null) {
      status.setText("(link previews disabled)");
      return;
    }

    sub =
        fetch
            .fetch(serverId, url)
            .observeOn(SwingEdt.scheduler())
            .subscribe(
                this::renderPreview,
                err -> {
                  status.setText("");
                  status.setText("(preview failed)");
                  status.setToolTipText(url + System.lineSeparator() + err.getMessage());
                });
  }

  private void renderPreview(LinkPreview p) {
    removeAll();

    // Main card container.
    card = createCardPanel();
    card.setBorder(buildCardBorder(collapsed));

    String targetUrl = safe(p.url()) != null ? p.url() : url;
    wikiExtended = WikipediaPreviewUtil.isWikipediaArticleUrl(targetUrl);

    slashdotExtended = SlashdotPreviewUtil.isSlashdotStoryUrl(targetUrl);

    youtubeVideoId = YouTubePreviewUtil.extractVideoId(targetUrl);
    youtubeExtended = youtubeVideoId != null;

    xExtended = XPreviewUtil.isXStatusUrl(targetUrl);
    githubExtended = GitHubPreviewUtil.isGitHubUrl(targetUrl);
    imdbExtended = ImdbPreviewUtil.isImdbTitleUrl(targetUrl);
    rtExtended = RottenTomatoesPreviewUtil.isRottenTomatoesTitleUrl(targetUrl);
    String previewSite = safe(p.siteName());
    String previewTitle = safe(p.title());
    boolean instagramByTarget = InstagramPreviewUtil.isInstagramPostUrl(targetUrl);
    boolean instagramByOriginal = InstagramPreviewUtil.isInstagramPostUrl(url);
    boolean instagramBySite = previewSite != null && previewSite.equalsIgnoreCase("instagram");
    boolean instagramByTitle =
        previewTitle != null && previewTitle.toLowerCase(Locale.ROOT).contains("instagram");
    instagramExtended =
        instagramByTarget || instagramByOriginal || instagramBySite || instagramByTitle;
    boolean imgurByTarget = ImgurPreviewUtil.isImgurUrl(targetUrl);
    boolean imgurByOriginal = ImgurPreviewUtil.isImgurUrl(url);
    boolean imgurBySite = previewSite != null && previewSite.equalsIgnoreCase("imgur");
    boolean imgurByDesc = ImgurPreviewUtil.looksLikeImgurDescription(safe(p.description()));
    imgurExtended =
        !instagramExtended && (imgurByTarget || imgurByOriginal || imgurBySite || imgurByDesc);
    boolean newsByTarget = NewsPreviewUtil.isLikelyNewsArticleUrl(targetUrl);
    boolean newsBySite = NewsPreviewUtil.isLikelyNewsSiteName(previewSite);
    newsExtended = !instagramExtended && !imgurExtended && (newsByTarget || newsBySite);
    // Header: collapse button + site + title.
    header = new JPanel(new BorderLayout(headerGap(), 0));
    header.setOpaque(false);

    collapseBtn = new JButton();
    collapseBtn.setFocusable(false);
    collapseBtn.setBorderPainted(false);
    collapseBtn.setContentAreaFilled(false);
    collapseBtn.setOpaque(false);
    collapseBtn.setMargin(new Insets(0, 0, 0, 0));
    collapseBtn.setToolTipText(collapsed ? "Expand preview" : "Collapse preview");
    collapseBtn.addActionListener(
        e -> {
          collapsed = !collapsed;
          applyCollapsedState();
          layoutForCurrentWidth();
        });

    JPanel headerText = new JPanel();
    headerText.setOpaque(false);
    headerText.setLayout(new javax.swing.BoxLayout(headerText, javax.swing.BoxLayout.Y_AXIS));
    headerText.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));

    String siteName = safe(p.siteName());
    if (siteName == null || siteName.isBlank()) {
      siteName = hostFromUrl(targetUrl);
    }
    site = new JLabel(sanitizeDisplayText(siteName));
    site.setOpaque(false);
    site.setForeground(siteTextColor());
    Font siteFont = site.getFont();
    try {
      site.setFont(siteFont.deriveFont(Font.BOLD, Math.max(10f, siteFont.getSize2D() - 1f)));
    } catch (Exception ignored) {
    }

    String titleText = safe(p.title());
    if (titleText == null || titleText.isBlank()) {
      titleText = targetUrl;
    }
    title = textArea(sanitizeDisplayText(titleText), true);
    title.setForeground(titleTextColor());
    try {
      Font titleFont = title.getFont();
      title.setFont(
          titleFont.deriveFont(titleFont.getStyle() | Font.BOLD, titleFont.getSize2D() + 1f));
    } catch (Exception ignored) {
    }

    headerText.add(site);
    if (!title.getText().isBlank()) {
      headerText.add(title);
    }

    header.add(collapseBtn, BorderLayout.WEST);
    header.add(headerText, BorderLayout.CENTER);

    // Body: optional thumbnail + description.
    body =
        new JPanel(
            new BorderLayout(
                (instagramExtended || imgurExtended) ? 0 : bodyHorizontalGap(),
                (instagramExtended || imgurExtended) ? bodyVerticalGap() : 0));
    body.setOpaque(false);

    thumb = new JLabel();
    thumbHost = thumb;
    thumbnailBesideText = !(instagramExtended || imgurExtended);
    boolean tallPoster = imdbExtended || rtExtended;
    int thumbW =
        instagramExtended
            ? INSTAGRAM_THUMB_W
            : (imgurExtended
                ? IMGUR_THUMB_W
                : (youtubeExtended
                    ? YT_THUMB_W
                    : (xExtended ? X_THUMB_W : (tallPoster ? IMDB_THUMB_W : THUMB_SIZE))));
    int thumbH =
        instagramExtended
            ? INSTAGRAM_THUMB_H
            : (imgurExtended
                ? IMGUR_THUMB_H
                : (youtubeExtended
                    ? YT_THUMB_H
                    : (xExtended ? X_THUMB_H : (tallPoster ? IMDB_THUMB_H : THUMB_SIZE))));
    setThumbHostSize(thumbW, thumbH);
    thumb.setOpaque(true);
    thumb.setBackground(thumbnailBackgroundColor());
    thumb.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
    thumb.setVerticalAlignment(javax.swing.SwingConstants.CENTER);
    thumb.setBorder(
        BorderFactory.createCompoundBorder(
            new javax.swing.border.LineBorder(borderColor(cardStyle), 1, true),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)));

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
      badge.setBorder(
          BorderFactory.createCompoundBorder(
              new javax.swing.border.LineBorder(new Color(255, 255, 255, 70), 1, true),
              BorderFactory.createEmptyBorder(2, 6, 2, 6)));
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

    String rawDesc = safe(p.description());

    if (p.imageUrl() != null && !p.imageUrl().isBlank()) {
      body.add(
          thumbHost, (instagramExtended || imgurExtended) ? BorderLayout.NORTH : BorderLayout.WEST);
      installImageOpenBehavior(p.imageUrl());
      loadThumbnail(p.imageUrl());
    }

    if ((imdbExtended || rtExtended) && rawDesc != null && !rawDesc.isBlank()) {
      ImdbDescParts parts = splitImdbDesc(rawDesc);

      JPanel imdbCenter = new JPanel();
      imdbCenter.setOpaque(false);
      imdbCenter.setLayout(new BoxLayout(imdbCenter, BoxLayout.Y_AXIS));
      // With BoxLayout.Y_AXIS, components default to alignmentX=0.5 (center).
      // For single-line labels (like the IMDb meta line), that makes them appear
      // horizontally "indented" (centered within the available width). Force left alignment.
      imdbCenter.setAlignmentX(LEFT_ALIGNMENT);

      if (parts.meta() != null && !parts.meta().isBlank()) {
        imdbMeta = new JLabel(parts.meta());
        imdbMeta.setOpaque(false);
        imdbMeta.setAlignmentX(LEFT_ALIGNMENT);
        imdbMeta.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        try {
          imdbMeta.setFont(
              imdbMeta.getFont().deriveFont(imdbMeta.getFont().getStyle() | Font.BOLD));
        } catch (Exception ignored) {
        }
        // Wrap in a BorderLayout anchored WEST to avoid BoxLayout centering quirks.
        JPanel metaWrap = new JPanel(new BorderLayout());
        metaWrap.setOpaque(false);
        metaWrap.setAlignmentX(LEFT_ALIGNMENT);
        metaWrap.add(imdbMeta, BorderLayout.WEST);
        imdbCenter.add(metaWrap);
      }

      imdbCreditsText = parts.credits();
      if (imdbCreditsText != null && !imdbCreditsText.isBlank()) {
        imdbCredits = textArea(imdbCreditsText, false);
        imdbCredits.setAlignmentX(LEFT_ALIGNMENT);
        imdbCenter.add(Box.createVerticalStrut(2));
        imdbCenter.add(imdbCredits);
      }

      String summaryText = parts.summary();
      fullDescText = summaryText;
      imdbSummary = textArea(summaryText, false);
      desc = imdbSummary;
      if (desc != null) desc.setAlignmentX(LEFT_ALIGNMENT);

      if (desc != null && !desc.getText().isBlank()) {
        // Horizontal line above the summary.
        if (imdbMeta != null || imdbCredits != null) {
          imdbSeparator = new JSeparator();
          imdbSeparator.setForeground(separatorColor());
          imdbSeparator.setBackground(separatorColor());
          JPanel sepWrap = new JPanel(new BorderLayout());
          sepWrap.setOpaque(false);
          sepWrap.setAlignmentX(LEFT_ALIGNMENT);
          sepWrap.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
          sepWrap.add(imdbSeparator, BorderLayout.CENTER);
          imdbCenter.add(sepWrap);
        }
        imdbCenter.add(desc);
      }

      if (imdbCenter.getComponentCount() > 0) {
        body.add(imdbCenter, BorderLayout.CENTER);
      }

    } else if (slashdotExtended && rawDesc != null && !rawDesc.isBlank()) {
      SlashdotDescParts parts = splitSlashdotDesc(rawDesc);

      JPanel sdCenter = new JPanel();
      sdCenter.setOpaque(false);
      sdCenter.setLayout(new BoxLayout(sdCenter, BoxLayout.Y_AXIS));
      sdCenter.setAlignmentX(LEFT_ALIGNMENT);

      if (parts.submitter() != null && !parts.submitter().isBlank()) {
        JLabel submitter = keyValueLabel("Submitter", parts.submitter());
        submitter.setAlignmentX(LEFT_ALIGNMENT);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        wrap.add(submitter, BorderLayout.WEST);
        sdCenter.add(wrap);
      }

      if (parts.date() != null && !parts.date().isBlank()) {
        JLabel date = keyValueLabel("Date", parts.date());
        date.setAlignmentX(LEFT_ALIGNMENT);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        wrap.add(date, BorderLayout.WEST);
        sdCenter.add(wrap);
      }

      String summaryText = parts.summary();
      fullDescText = summaryText;
      desc = textArea(summaryText, false);
      if (desc != null) desc.setAlignmentX(LEFT_ALIGNMENT);

      if (desc != null && !desc.getText().isBlank()) {
        if (sdCenter.getComponentCount() > 0) {
          JSeparator sep = new JSeparator();
          sep.setForeground(separatorColor());
          sep.setBackground(separatorColor());
          JPanel sepWrap = new JPanel(new BorderLayout());
          sepWrap.setOpaque(false);
          sepWrap.setAlignmentX(LEFT_ALIGNMENT);
          sepWrap.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
          sepWrap.add(sep, BorderLayout.CENTER);
          sdCenter.add(sepWrap);
        }
        sdCenter.add(desc);
      }

      if (sdCenter.getComponentCount() > 0) {
        body.add(sdCenter, BorderLayout.CENTER);
      }
    } else if (instagramExtended && rawDesc != null && !rawDesc.isBlank()) {
      InstagramDescParts parts = splitInstagramDesc(rawDesc);

      JPanel igCenter = new JPanel();
      igCenter.setOpaque(false);
      igCenter.setLayout(new BoxLayout(igCenter, BoxLayout.Y_AXIS));
      igCenter.setAlignmentX(LEFT_ALIGNMENT);

      if (parts.author() != null && !parts.author().isBlank()) {
        JLabel author = keyValueLabel("Author", parts.author());
        author.setAlignmentX(LEFT_ALIGNMENT);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        wrap.add(author, BorderLayout.WEST);
        igCenter.add(wrap);
      }

      if (parts.date() != null && !parts.date().isBlank()) {
        JLabel date = keyValueLabel("Date", parts.date());
        date.setAlignmentX(LEFT_ALIGNMENT);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        wrap.add(date, BorderLayout.WEST);
        igCenter.add(wrap);
      }

      String summaryText = parts.caption();
      fullDescText = summaryText;
      desc = textArea(summaryText, false);
      if (desc != null) desc.setAlignmentX(LEFT_ALIGNMENT);

      if (desc != null && !desc.getText().isBlank()) {
        if (igCenter.getComponentCount() > 0) {
          JSeparator sep = new JSeparator();
          sep.setForeground(separatorColor());
          sep.setBackground(separatorColor());
          JPanel sepWrap = new JPanel(new BorderLayout());
          sepWrap.setOpaque(false);
          sepWrap.setAlignmentX(LEFT_ALIGNMENT);
          sepWrap.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
          sepWrap.add(sep, BorderLayout.CENTER);
          igCenter.add(sepWrap);
        }
        igCenter.add(desc);
      }

      if (igCenter.getComponentCount() > 0) {
        body.add(igCenter, BorderLayout.CENTER);
      }
    } else if (imgurExtended && rawDesc != null && !rawDesc.isBlank()) {
      ImgurDescParts parts = splitImgurDesc(rawDesc);

      JPanel imgurCenter = new JPanel();
      imgurCenter.setOpaque(false);
      imgurCenter.setLayout(new BoxLayout(imgurCenter, BoxLayout.Y_AXIS));
      imgurCenter.setAlignmentX(LEFT_ALIGNMENT);

      if (parts.submitter() != null && !parts.submitter().isBlank()) {
        JLabel submitter = keyValueLabel("Submitter", parts.submitter());
        submitter.setAlignmentX(LEFT_ALIGNMENT);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        wrap.add(submitter, BorderLayout.WEST);
        imgurCenter.add(wrap);
      }

      if (parts.date() != null && !parts.date().isBlank()) {
        JLabel date = keyValueLabel("Date", parts.date());
        date.setAlignmentX(LEFT_ALIGNMENT);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        wrap.add(date, BorderLayout.WEST);
        imgurCenter.add(wrap);
      }

      String summaryText = parts.caption();
      fullDescText = summaryText;
      desc = textArea(summaryText, false);
      if (desc != null) desc.setAlignmentX(LEFT_ALIGNMENT);

      if (desc != null && !desc.getText().isBlank()) {
        if (imgurCenter.getComponentCount() > 0) {
          JSeparator sep = new JSeparator();
          sep.setForeground(separatorColor());
          sep.setBackground(separatorColor());
          JPanel sepWrap = new JPanel(new BorderLayout());
          sepWrap.setOpaque(false);
          sepWrap.setAlignmentX(LEFT_ALIGNMENT);
          sepWrap.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
          sepWrap.add(sep, BorderLayout.CENTER);
          imgurCenter.add(sepWrap);
        }
        imgurCenter.add(desc);
      }

      if (imgurCenter.getComponentCount() > 0) {
        body.add(imgurCenter, BorderLayout.CENTER);
      }
    } else if (newsExtended && rawDesc != null && !rawDesc.isBlank()) {
      NewsDescParts parts = splitNewsDesc(rawDesc);

      JPanel newsCenter = new JPanel();
      newsCenter.setOpaque(false);
      newsCenter.setLayout(new BoxLayout(newsCenter, BoxLayout.Y_AXIS));
      newsCenter.setAlignmentX(LEFT_ALIGNMENT);

      if (parts.author() != null && !parts.author().isBlank()) {
        JLabel author = keyValueLabel("Author", parts.author());
        author.setAlignmentX(LEFT_ALIGNMENT);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        wrap.add(author, BorderLayout.WEST);
        newsCenter.add(wrap);
      }

      if (parts.date() != null && !parts.date().isBlank()) {
        JLabel date = keyValueLabel("Date", parts.date());
        date.setAlignmentX(LEFT_ALIGNMENT);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        wrap.add(date, BorderLayout.WEST);
        newsCenter.add(wrap);
      }

      if (parts.publisher() != null && !parts.publisher().isBlank()) {
        JLabel publisher = keyValueLabel("Publisher", parts.publisher());
        publisher.setAlignmentX(LEFT_ALIGNMENT);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        wrap.add(publisher, BorderLayout.WEST);
        newsCenter.add(wrap);
      }

      String summaryText = parts.summary();
      fullDescText = summaryText;
      desc = textArea(summaryText, false);
      if (desc != null) desc.setAlignmentX(LEFT_ALIGNMENT);

      if (desc != null && !desc.getText().isBlank()) {
        if (newsCenter.getComponentCount() > 0) {
          JSeparator sep = new JSeparator();
          sep.setForeground(separatorColor());
          sep.setBackground(separatorColor());
          JPanel sepWrap = new JPanel(new BorderLayout());
          sepWrap.setOpaque(false);
          sepWrap.setAlignmentX(LEFT_ALIGNMENT);
          sepWrap.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
          sepWrap.add(sep, BorderLayout.CENTER);
          newsCenter.add(sepWrap);
        }
        newsCenter.add(desc);
      }

      if (newsCenter.getComponentCount() > 0) {
        body.add(newsCenter, BorderLayout.CENTER);
      }
    } else {
      fullDescText = rawDesc;
      desc = textArea(fullDescText, false);
      if (desc != null && !desc.getText().isBlank()) {
        body.add(desc, BorderLayout.CENTER);
      }
    }

    if (body.getComponentCount() > 0) {
      body.setBorder(BorderFactory.createEmptyBorder(bodyTopGap(), 0, 0, 0));
    }
    styleTextContent();
    card.add(header, BorderLayout.NORTH);
    card.add(body, BorderLayout.CENTER);

    // Interactions (open link, copy link)
    card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    card.setToolTipText(targetUrl);
    installPopup(card, targetUrl);
    card.addMouseListener(
        new java.awt.event.MouseAdapter() {
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
      collapseBtn.setText("");
      collapseBtn.setIcon(collapsed ? COLLAPSED_ICON : EXPANDED_ICON);
      collapseBtn.setToolTipText(collapsed ? "Expand preview" : "Collapse preview");
    }
    if (body != null) {
      body.setVisible(!collapsed);
    }

    // When embedded into a JTextPane StyledDocument, Swing may keep reserving the prior
    // preferred height unless we recompute our preferred size after collapsing.
    applyBordersForState();
    if (card != null) {
      card.repaint();
    }
    lastMaxW = -1; // bust width/height cache
    layoutForCurrentWidth();
  }

  private void loadThumbnail(String imageUrl) {
    if (imageFetch == null) return;
    if (thumbSub != null && !thumbSub.isDisposed()) {
      thumbSub.dispose();
      thumbSub = null;
    }

    thumbSub =
        imageFetch
            .fetch(serverId, imageUrl)
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
                      if (instagramExtended || imgurExtended) {
                        int inlineW =
                            EmbedHostLayoutUtil.computeMaxInlineWidth(
                                this, FALLBACK_MAX_W, WIDTH_MARGIN_PX, 220);
                        if (inlineW > 0) {
                          // Keep vertical media previews inside the card's usable content width.
                          maxW = Math.max(120, inlineW - EXT_MEDIA_CARD_CHROME_PX);
                        }
                      }
                      maxW = effectiveThumbnailMaxWidth(maxW, mediaMaxWidthPx);
                      java.awt.image.BufferedImage scaled =
                          ImageScaleUtil.scaleDownToFit(
                              img, maxW, effectiveThumbnailMaxHeight(mediaMaxHeightPx));
                      if (instagramExtended || imgurExtended) {
                        // Portrait photos can exceed the initial placeholder height.
                        // Grow the host to the scaled image so it doesn't look cramped/cropped.
                        setThumbHostSize(scaled.getWidth(), scaled.getHeight());
                      }
                      thumb.setIcon(new javax.swing.ImageIcon(scaled));
                      if (instagramExtended || imgurExtended) {
                        lastMaxW = -1;
                        layoutForCurrentWidth();
                      }
                    } else {
                      log.warn("Thumbnail decode produced no image for {}", imageUrl);
                      dropThumbnailPlaceholder();
                    }
                  } catch (Exception ex) {
                    log.warn("Thumbnail decode failed for {}: {}", imageUrl, ex.toString());
                    dropThumbnailPlaceholder();
                  }
                },
                err -> {
                  log.warn("Thumbnail download failed for {}: {}", imageUrl, err.toString());
                  dropThumbnailPlaceholder();
                });
  }

  private void dropThumbnailPlaceholder() {
    try {
      if (body == null || thumbHost == null) return;
      // Only drop if the icon isn't set (otherwise we'd hide a successfully loaded image).
      if (thumb != null && thumb.getIcon() != null) return;
      if (thumbHost.getParent() != body) return;

      SwingUtilities.invokeLater(
          () -> {
            try {
              if (thumb != null && thumb.getIcon() != null) return;
              if (body == null || thumbHost == null) return;
              if (thumbHost.getParent() != body) return;
              body.remove(thumbHost);
              body.revalidate();
              body.repaint();
              lastMaxW = -1; // bust cached layout
              layoutForCurrentWidth();
            } catch (Exception ignored) {
            }
          });
    } catch (Exception ignored) {
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    cancelDeferredSubDispose();
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
    scheduleDeferredSubDispose();
    // DO NOT dispose the fetch subscriptions here.
    // JTextPane/StyledDocument may call removeNotify() during view rebuilds and scrolling,
    // which would prevent the preview from ever completing and updating the UI.
    super.removeNotify();
  }

  private void layoutForCurrentWidth() {
    if (card == null) return;
    int maxW =
        EmbedHostLayoutUtil.computeMaxInlineWidth(this, FALLBACK_MAX_W, WIDTH_MARGIN_PX, 220);
    if (maxW <= 0) maxW = FALLBACK_MAX_W;
    if (instagramExtended || imgurExtended) {
      maxW = effectiveExtendedCardMaxWidth(maxW, mediaMaxWidthPx);
    }

    if (Math.abs(maxW - lastMaxW) < 4 && lastMaxW > 0 && collapsed == lastCollapsedLayout) return;
    lastMaxW = maxW;
    lastCollapsedLayout = collapsed;

    // Clamp header/title and body/desc widths.
    int headerInnerW = Math.max(160, maxW - 34);
    if (title != null) {
      title.setSize(new Dimension(headerInnerW, Short.MAX_VALUE));
      clampLines(title, MAX_TITLE_LINES);
    }

    int descInnerW = maxW;
    if (thumbnailBesideText && thumbHost != null && body != null && thumbHost.getParent() == body) {
      int tw = THUMB_SIZE;
      try {
        Dimension tps = thumbHost.getPreferredSize();
        if (tps != null && tps.width > 0) tw = tps.width;
      } catch (Exception ignored) {
        // best effort
      }
      descInnerW = Math.max(120, maxW - tw - 20);
    }

    int maxDescLines;
    if (wikiExtended) {
      maxDescLines = WIKIPEDIA_MAX_DESC_LINES;
    } else if (slashdotExtended) {
      maxDescLines = SLASHDOT_MAX_DESC_LINES;
    } else if (youtubeExtended) {
      maxDescLines = YOUTUBE_MAX_DESC_LINES;
    } else if (imdbExtended || rtExtended) {
      maxDescLines = IMDB_MAX_DESC_LINES;
    } else if (xExtended) {
      maxDescLines = X_MAX_DESC_LINES;
    } else if (githubExtended) {
      maxDescLines = GITHUB_MAX_DESC_LINES;
    } else if (imgurExtended) {
      maxDescLines = IMGUR_MAX_DESC_LINES;
    } else if (instagramExtended) {
      maxDescLines = INSTAGRAM_MAX_DESC_LINES;
    } else if (newsExtended) {
      maxDescLines = NEWS_MAX_DESC_LINES;
    } else {
      maxDescLines = DEFAULT_MAX_DESC_LINES;
    }

    if ((imdbExtended || rtExtended) && imdbCredits != null) {
      imdbCredits.setSize(new Dimension(descInnerW, Short.MAX_VALUE));

      if (imdbCreditsText != null && !imdbCreditsText.isBlank()) {
        String clampedCredits =
            PreviewTextUtil.clampToLines(
                imdbCreditsText, imdbCredits, descInnerW, IMDB_CREDITS_MAX_LINES);
        if (clampedCredits != null && !clampedCredits.equals(imdbCredits.getText())) {
          imdbCredits.setText(clampedCredits);
        }
      }

      clampLines(imdbCredits, IMDB_CREDITS_MAX_LINES);
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

  static int effectiveThumbnailMaxWidth(int requestedWidthPx, int configuredMaxWidthPx) {
    int width = requestedWidthPx > 0 ? requestedWidthPx : THUMB_SIZE;
    if (configuredMaxWidthPx > 0) {
      width = Math.min(width, configuredMaxWidthPx);
    }
    return Math.max(1, width);
  }

  static int effectiveThumbnailMaxHeight(int configuredMaxHeightPx) {
    return Math.max(0, configuredMaxHeightPx);
  }

  static int effectiveExtendedCardMaxWidth(int requestedCardWidthPx, int configuredMaxWidthPx) {
    int width = Math.max(1, requestedCardWidthPx);
    if (configuredMaxWidthPx > 0) {
      width = Math.min(width, Math.max(1, configuredMaxWidthPx + EXT_MEDIA_CARD_CHROME_PX));
    }
    return width;
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

  private void styleTextContent() {
    Color bodyColor = bodyTextColor();
    Color metaColor = metaTextColor();
    if (site != null) {
      site.setForeground(siteTextColor());
    }
    if (title != null) {
      title.setForeground(titleTextColor());
    }
    if (desc != null) {
      desc.setForeground(bodyColor);
    }
    if (imdbMeta != null) {
      imdbMeta.setForeground(metaColor);
    }
    if (imdbCredits != null) {
      imdbCredits.setForeground(metaColor);
    }
    if (imdbSummary != null) {
      imdbSummary.setForeground(bodyColor);
    }
    if (imdbSeparator != null) {
      Color sepColor = separatorColor();
      imdbSeparator.setForeground(sepColor);
      imdbSeparator.setBackground(sepColor);
    }
  }

  private Color cardBackgroundColor() {
    Color base = baseBackgroundColor();
    return switch (cardStyle) {
      case MINIMAL -> {
        if (isDark(base)) yield blend(base, Color.WHITE, collapsed ? 0.01 : 0.03);
        yield blend(base, Color.BLACK, collapsed ? 0.005 : 0.015);
      }
      case GLASSY -> {
        if (isDark(base)) yield blend(base, Color.WHITE, collapsed ? 0.10 : 0.16);
        yield blend(base, Color.BLACK, collapsed ? 0.04 : 0.07);
      }
      case DENSER -> {
        if (isDark(base)) yield blend(base, Color.WHITE, collapsed ? 0.05 : 0.08);
        yield blend(base, Color.BLACK, collapsed ? 0.015 : 0.03);
      }
      case DEFAULT -> {
        if (isDark(base)) yield blend(base, Color.WHITE, collapsed ? 0.06 : 0.10);
        yield blend(base, Color.BLACK, collapsed ? 0.02 : 0.04);
      }
    };
  }

  private static ImdbDescParts splitImdbDesc(String rawDesc) {
    if (rawDesc == null) return new ImdbDescParts(null, null, null);
    String t = rawDesc.strip();
    if (t.isEmpty()) return new ImdbDescParts(null, null, null);

    String[] lines = t.split("\\R");
    String meta = lines.length > 0 ? safe(lines[0]) : null;

    StringBuilder credits = new StringBuilder();
    StringBuilder summary = new StringBuilder();

    for (int i = 1; i < lines.length; i++) {
      String line = lines[i] == null ? "" : lines[i].strip();
      if (line.startsWith("Director:")
          || line.startsWith("Cast:")
          || line.startsWith("Creator:")
          || line.startsWith("Creators:")) {
        if (!credits.isEmpty()) credits.append("\n");
        credits.append(line);
        continue;
      }

      if (line.isBlank()) continue;
      if (!summary.isEmpty()) summary.append("\n");
      summary.append(line);
    }

    String creditsText = credits.isEmpty() ? null : credits.toString();
    String summaryText = summary.isEmpty() ? null : summary.toString();
    return new ImdbDescParts(safe(meta), safe(creditsText), safe(summaryText));
  }

  private record ImdbDescParts(String meta, String credits, String summary) {}

  private static SlashdotDescParts splitSlashdotDesc(String rawDesc) {
    if (rawDesc == null) return new SlashdotDescParts(null, null, null);
    String t = rawDesc.strip();
    if (t.isEmpty()) return new SlashdotDescParts(null, null, null);

    String[] lines = t.split("\\R");
    String submitter = null;
    String date = null;
    StringBuilder summary = new StringBuilder();

    int i = 0;
    // Parse optional "Submitter:" / "Date:" block at the top.
    for (; i < lines.length; i++) {
      String line = lines[i] == null ? "" : lines[i].strip();
      if (line.isBlank()) {
        // Blank line can separate meta from summary; stop once we've found any meta.
        if (submitter != null || date != null) {
          i++;
          break;
        }
        continue;
      }

      String lower = line.toLowerCase(java.util.Locale.ROOT);
      if (lower.startsWith("submitter:")) {
        submitter = safe(line.substring(line.indexOf(':') + 1));
        continue;
      }
      if (lower.startsWith("date:")) {
        date = safe(line.substring(line.indexOf(':') + 1));
        continue;
      }

      // First non-meta line => start of summary.
      break;
    }

    for (; i < lines.length; i++) {
      String line = lines[i] == null ? "" : lines[i].strip();
      if (line.isBlank()) continue;
      if (!summary.isEmpty()) summary.append("\n");
      summary.append(line);
    }

    String summaryText = summary.isEmpty() ? null : summary.toString();
    return new SlashdotDescParts(safe(submitter), safe(date), safe(summaryText));
  }

  private record SlashdotDescParts(String submitter, String date, String summary) {}

  private static NewsDescParts splitNewsDesc(String rawDesc) {
    if (rawDesc == null) return new NewsDescParts(null, null, null, null);
    String t = rawDesc.strip();
    if (t.isEmpty()) return new NewsDescParts(null, null, null, null);

    String[] lines = t.split("\\R");
    String author = null;
    String date = null;
    String publisher = null;
    StringBuilder summary = new StringBuilder();

    int i = 0;
    for (; i < lines.length; i++) {
      String line = lines[i] == null ? "" : lines[i].strip();
      if (line.isBlank()) {
        if (author != null || date != null || publisher != null) {
          i++;
          break;
        }
        continue;
      }

      String lower = line.toLowerCase(Locale.ROOT);
      if (lower.startsWith("author:")) {
        author = safe(line.substring(line.indexOf(':') + 1));
        continue;
      }
      if (lower.startsWith("date:")) {
        date = safe(line.substring(line.indexOf(':') + 1));
        continue;
      }
      if (lower.startsWith("publisher:")) {
        publisher = safe(line.substring(line.indexOf(':') + 1));
        continue;
      }
      break;
    }

    boolean started = false;
    for (; i < lines.length; i++) {
      String line = lines[i] == null ? "" : lines[i];
      if (!started && line.isBlank()) continue;
      started = true;
      if (summary.length() > 0) summary.append("\n");
      summary.append(line.strip());
    }

    String summaryText = summary.isEmpty() ? null : summary.toString();
    if (summaryText != null) {
      summaryText = summaryText.replaceFirst("(?is)^summary:\\s*", "");
    }
    return new NewsDescParts(safe(author), safe(date), safe(publisher), safe(summaryText));
  }

  private record NewsDescParts(String author, String date, String publisher, String summary) {}

  private static InstagramDescParts splitInstagramDesc(String rawDesc) {
    if (rawDesc == null) return new InstagramDescParts(null, null, null);
    String t = rawDesc.strip();
    if (t.isEmpty()) return new InstagramDescParts(null, null, null);

    String[] lines = t.split("\\R");
    String author = null;
    String date = null;
    StringBuilder caption = new StringBuilder();

    int i = 0;
    // Parse optional "Author:" / "Date:" block at the top.
    for (; i < lines.length; i++) {
      String line = lines[i] == null ? "" : lines[i].strip();
      if (line.isBlank()) {
        if (author != null || date != null) {
          i++;
          break;
        }
        continue;
      }

      String lower = line.toLowerCase(Locale.ROOT);
      if (lower.startsWith("author:")) {
        author = safe(line.substring(line.indexOf(':') + 1));
        continue;
      }
      if (lower.startsWith("date:")) {
        date = safe(line.substring(line.indexOf(':') + 1));
        continue;
      }

      // First non-meta line => start of caption.
      break;
    }

    boolean started = false;
    for (; i < lines.length; i++) {
      String line = lines[i] == null ? "" : lines[i];
      // Keep paragraph breaks, but don't start with empty lines.
      if (!started && line.isBlank()) continue;
      started = true;
      if (caption.length() > 0) caption.append("\n");
      caption.append(line.strip());
    }

    String captionText = caption.isEmpty() ? null : caption.toString();
    if (captionText != null) {
      captionText = captionText.replaceFirst("(?is)^summary:\\s*", "");
    }
    return new InstagramDescParts(safe(author), safe(date), safe(captionText));
  }

  private record InstagramDescParts(String author, String date, String caption) {}

  private static ImgurDescParts splitImgurDesc(String rawDesc) {
    if (rawDesc == null) return new ImgurDescParts(null, null, null);
    String t = rawDesc.strip();
    if (t.isEmpty()) return new ImgurDescParts(null, null, null);

    String[] lines = t.split("\\R");
    String submitter = null;
    String date = null;
    StringBuilder caption = new StringBuilder();

    int i = 0;
    for (; i < lines.length; i++) {
      String line = lines[i] == null ? "" : lines[i].strip();
      if (line.isBlank()) {
        if (submitter != null || date != null) {
          i++;
          break;
        }
        continue;
      }

      String lower = line.toLowerCase(Locale.ROOT);
      if (lower.startsWith("submitter:")) {
        submitter = safe(line.substring(line.indexOf(':') + 1));
        continue;
      }
      if (lower.startsWith("author:")) {
        submitter = safe(line.substring(line.indexOf(':') + 1));
        continue;
      }
      if (lower.startsWith("date:")) {
        date = safe(line.substring(line.indexOf(':') + 1));
        continue;
      }
      break;
    }

    boolean started = false;
    for (; i < lines.length; i++) {
      String line = lines[i] == null ? "" : lines[i];
      if (!started && line.isBlank()) continue;
      started = true;
      if (caption.length() > 0) caption.append("\n");
      caption.append(line.strip());
    }

    String captionText = caption.isEmpty() ? null : caption.toString();
    if (captionText != null) {
      captionText = captionText.replaceFirst("(?is)^summary:\\s*", "");
    }
    return new ImgurDescParts(safe(submitter), safe(date), safe(captionText));
  }

  private record ImgurDescParts(String submitter, String date, String caption) {}

  private JLabel keyValueLabel(String key, String value) {
    String k = sanitizeDisplayText(key == null ? "" : key);
    String v = sanitizeDisplayText(value == null ? "" : value);
    k = k.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    v = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    JLabel l = new JLabel("<html><b>" + k + ":</b> " + v + "</html>");
    l.setOpaque(false);
    l.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
    l.setForeground(metaTextColor());
    return l;
  }

  private JTextArea textArea(String text, boolean bold) {
    JTextArea ta = new JTextArea(sanitizeDisplayText(text == null ? "" : text));
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
    ta.setForeground(bold ? titleTextColor() : bodyTextColor());
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
    resizeListeningOn =
        EmbedHostLayoutUtil.hookResizeListener(this, resizeListener, resizeListeningOn);
  }

  private void unhookResizeListener() {
    resizeListeningOn = EmbedHostLayoutUtil.unhookResizeListener(resizeListener, resizeListeningOn);
  }

  private void disposeSubs() {
    cancelDeferredSubDispose();
    if (sub != null && !sub.isDisposed()) {
      sub.dispose();
    }
    sub = null;
    if (thumbSub != null && !thumbSub.isDisposed()) {
      thumbSub.dispose();
    }
    thumbSub = null;
  }

  private void scheduleDeferredSubDispose() {
    if (deferredDisposeTimer == null) {
      deferredDisposeTimer =
          new javax.swing.Timer(
              SUBS_DISPOSE_DELAY_MS,
              e -> {
                if (isDisplayable()) return;
                disposeSubs();
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

  private void setThumbHostSize(int w, int h) {
    int cw = Math.max(1, w);
    int ch = Math.max(1, h);
    Dimension d = new Dimension(cw, ch);
    if (thumb != null) {
      thumb.setPreferredSize(d);
      thumb.setMinimumSize(d);
      thumb.setMaximumSize(d);
    }
    if (thumbHost != null) {
      thumbHost.setPreferredSize(d);
      thumbHost.setMinimumSize(d);
      thumbHost.setMaximumSize(d);
    }
  }

  private static String sanitizeDisplayText(String value) {
    if (value == null || value.isEmpty()) return value;
    StringBuilder out = new StringBuilder(value.length());
    value
        .codePoints()
        .forEach(
            cp -> {
              if (cp == '\n' || cp == '\t') {
                out.appendCodePoint(cp);
                return;
              }
              if (Character.isISOControl(cp)) return;
              int type = Character.getType(cp);
              if (type == Character.PRIVATE_USE || type == Character.UNASSIGNED) return;
              if (isVariationSelector(cp)) return;
              out.appendCodePoint(cp);
            });
    return out.toString();
  }

  private static boolean isVariationSelector(int cp) {
    return (cp >= 0xFE00 && cp <= 0xFE0F) || (cp >= 0xE0100 && cp <= 0xE01EF);
  }

  private void installImageOpenBehavior(String imageUrl) {
    String u = safe(imageUrl);
    if (u == null || u.isBlank()) return;

    java.awt.event.MouseAdapter openImage =
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
              openUrl(u);
              e.consume();
            }
          }
        };

    if (thumb != null) {
      thumb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      thumb.setToolTipText(u);
      thumb.addMouseListener(openImage);
    }
    if (thumbHost != null && thumbHost != thumb) {
      thumbHost.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      if (thumbHost instanceof javax.swing.JComponent jc) {
        jc.setToolTipText(u);
      }
      thumbHost.addMouseListener(openImage);
    }
  }

  private void installPopup(JPanel p, String targetUrl) {
    JPopupMenu menu = new JPopupMenu();

    JMenuItem open = new JMenuItem("Open link");
    open.addActionListener(e -> openUrl(targetUrl));

    JMenuItem copy = new JMenuItem("Copy link");
    copy.addActionListener(
        e -> {
          try {
            java.awt.Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(targetUrl), null);
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
    Insets outer = collapsed ? outerPadCollapsed() : outerPadExpanded();
    setBorder(BorderFactory.createEmptyBorder(outer.top, outer.left, outer.bottom, outer.right));
    if (card != null) {
      card.setBorder(buildCardBorder(collapsed));
    }
  }

  private javax.swing.border.Border buildCardBorder(boolean collapsed) {
    return buildCardBorder(cardStyle, collapsed);
  }

  static javax.swing.border.Border buildCardBorder(EmbedCardStyle style, boolean collapsed) {
    Insets pad = cardPadding(style, collapsed);
    return BorderFactory.createCompoundBorder(
        new javax.swing.border.LineBorder(borderColor(style), 1, true),
        BorderFactory.createEmptyBorder(pad.top, pad.left, pad.bottom, pad.right));
  }

  private static Insets cardPadding(EmbedCardStyle style, boolean collapsed) {
    EmbedCardStyle s = style != null ? style : EmbedCardStyle.DEFAULT;
    return switch (s) {
      case MINIMAL -> collapsed ? new Insets(3, 8, 3, 8) : new Insets(6, 8, 6, 8);
      case GLASSY -> collapsed ? new Insets(7, 14, 7, 14) : new Insets(12, 14, 12, 14);
      case DENSER -> collapsed ? new Insets(2, 8, 2, 8) : new Insets(5, 8, 5, 8);
      case DEFAULT ->
          collapsed
              ? new Insets(
                  CARD_PAD_COLLAPSED_DEFAULT.top,
                  CARD_PAD_COLLAPSED_DEFAULT.left,
                  CARD_PAD_COLLAPSED_DEFAULT.bottom,
                  CARD_PAD_COLLAPSED_DEFAULT.right)
              : new Insets(
                  CARD_PAD_EXPANDED_DEFAULT.top,
                  CARD_PAD_EXPANDED_DEFAULT.left,
                  CARD_PAD_EXPANDED_DEFAULT.bottom,
                  CARD_PAD_EXPANDED_DEFAULT.right);
    };
  }

  private static java.awt.Color borderColor(EmbedCardStyle style) {
    EmbedCardStyle s = style != null ? style : EmbedCardStyle.DEFAULT;
    java.awt.Color base = javax.swing.UIManager.getColor("Component.borderColor");
    if (base == null) {
      base = blend(foregroundColor(), baseBackgroundColor(), 0.35);
    }
    return switch (s) {
      case MINIMAL -> withAlpha(base, 105);
      case GLASSY ->
          withAlpha(
              blend(base, firstNonNullColor("Component.linkColor", "Component.focusColor"), 0.20),
              205);
      case DENSER -> withAlpha(base, 190);
      case DEFAULT -> withAlpha(base, 170);
    };
  }

  private static Color siteTextColor() {
    Color accent = javax.swing.UIManager.getColor("Component.linkColor");
    if (accent == null) accent = javax.swing.UIManager.getColor("Component.focusColor");
    if (accent == null) accent = foregroundColor();
    return withAlpha(accent, 220);
  }

  private static Color titleTextColor() {
    return foregroundColor();
  }

  private static Color bodyTextColor() {
    return blend(foregroundColor(), baseBackgroundColor(), 0.20);
  }

  private static Color metaTextColor() {
    return blend(foregroundColor(), baseBackgroundColor(), 0.38);
  }

  private static Color separatorColor() {
    return withAlpha(metaTextColor(), 140);
  }

  private static Color thumbnailBackgroundColor() {
    Color base = baseBackgroundColor();
    if (isDark(base)) {
      return blend(base, Color.WHITE, 0.10);
    }
    return blend(base, Color.BLACK, 0.03);
  }

  private static Color foregroundColor() {
    Color fg = javax.swing.UIManager.getColor("Label.foreground");
    if (fg == null) fg = Color.DARK_GRAY;
    return fg;
  }

  private static Color baseBackgroundColor() {
    Color bg = javax.swing.UIManager.getColor("TextPane.background");
    if (bg == null) bg = javax.swing.UIManager.getColor("Panel.background");
    if (bg == null) bg = Color.WHITE;
    return bg;
  }

  private static Color blend(Color a, Color b, double ratioB) {
    double clamped = Math.max(0d, Math.min(1d, ratioB));
    double ratioA = 1d - clamped;
    int r = (int) Math.round(a.getRed() * ratioA + b.getRed() * clamped);
    int g = (int) Math.round(a.getGreen() * ratioA + b.getGreen() * clamped);
    int bl = (int) Math.round(a.getBlue() * ratioA + b.getBlue() * clamped);
    return new Color(clampChannel(r), clampChannel(g), clampChannel(bl), 255);
  }

  private static Color withAlpha(Color c, int alpha) {
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), clampChannel(alpha));
  }

  private static int clampChannel(int value) {
    return Math.max(0, Math.min(255, value));
  }

  private static boolean isDark(Color c) {
    double luminance = (0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue()) / 255d;
    return luminance < 0.52;
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

  private Insets statusPadding() {
    return switch (cardStyle) {
      case MINIMAL -> new Insets(2, 6, 2, 6);
      case GLASSY -> new Insets(6, 10, 6, 10);
      case DENSER -> new Insets(3, 7, 3, 7);
      case DEFAULT -> new Insets(4, 8, 4, 8);
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
      case DEFAULT -> 10;
    };
  }

  private int bodyHorizontalGap() {
    return switch (cardStyle) {
      case MINIMAL -> 8;
      case GLASSY -> 14;
      case DENSER -> 8;
      case DEFAULT -> 12;
    };
  }

  private int bodyVerticalGap() {
    return switch (cardStyle) {
      case MINIMAL -> 6;
      case GLASSY -> 12;
      case DENSER -> 6;
      case DEFAULT -> 10;
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

  private static Color firstNonNullColor(String... keys) {
    if (keys != null) {
      for (String key : keys) {
        if (key == null || key.isBlank()) continue;
        Color c = javax.swing.UIManager.getColor(key);
        if (c != null) return c;
      }
    }
    return foregroundColor();
  }
}
