package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.embed.*;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maintains a StyledDocument buffer per target and provides rich rendering:
 * - word/line wrap
 * - clickable URLs
 * - nick mention highlighting
 * - notice/status/error styling
 * - follow-tail scrolling (auto-scroll only when user is at bottom)
 */
@Component
@Lazy
public class ChatDockable extends JPanel implements Dockable {
  public static final String ID = "chat";

  private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

  // URL detection: http(s)://... or www....
  private static final Pattern URL_PATTERN =
      Pattern.compile("(?i)\\b((?:https?://|www\\.)[^\\s<>]+)");

  // IRC-ish nick characters, used to avoid highlighting inside words.
  private static final String NICK_CHARS = "[A-Za-z0-9\\[\\]\\\\`_\\^\\{\\|\\}-]";

  private static final String ATTR_URL = "chat.url";

  private final WrapTextPane chat = new WrapTextPane();
  private final JScrollPane scroll = new JScrollPane(chat);

  private final Map<String, StyledDocument> buffers = new HashMap<>();
  private final Map<String, Boolean> followTailByTarget = new HashMap<>();
  private final Map<String, Integer> scrollValueByTarget = new HashMap<>();

  private String activeTarget = "status";

  private volatile String currentNick = "";
  private volatile Pattern mentionPattern = null;

  // Styles
  private final SimpleAttributeSet tsStyle;
  private final SimpleAttributeSet fromStyle;
  private final SimpleAttributeSet msgStyle;
  private final SimpleAttributeSet noticeFromStyle;
  private final SimpleAttributeSet noticeMsgStyle;
  private final SimpleAttributeSet statusStyle;
  private final SimpleAttributeSet errorStyle;
  private final SimpleAttributeSet linkStyle;
  private final SimpleAttributeSet mentionStyle;

  private boolean programmaticScroll = false;
  private Cursor normalCursor;
  private Cursor handCursor;

  // Embed support
  private final EmbedFetcher embedFetcher;
  private final EmbedRenderer embedRenderer;
  private final EmbedSettings embedSettings;
  private final CompositeDisposable embedDisposables = new CompositeDisposable();

  // Track embeds by URL for click handling: url -> EmbedResult
  private final Map<String, EmbedResult> embedResults = new HashMap<>();

  // Custom attribute for embed URLs
  private static final String ATTR_EMBED_URL = "chat.embed.url";

  @Autowired
  public ChatDockable(
      @Autowired(required = false) EmbedFetcher embedFetcher,
      @Autowired(required = false) EmbedRenderer embedRenderer,
      @Autowired(required = false) EmbedSettings embedSettings
  ) {
    super(new BorderLayout());

    this.embedFetcher = embedFetcher;
    this.embedRenderer = embedRenderer;
    this.embedSettings = embedSettings != null ? embedSettings : EmbedSettings.defaults();

    chat.setEditable(false);
    chat.setOpaque(true);

    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    add(scroll, BorderLayout.CENTER);

    normalCursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
    handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

    // Styles based on current Look & Feel colors
    Color fg = uiColor("TextPane.foreground", Color.BLACK);
    Color bg = uiColor("TextPane.background", Color.WHITE);
    Color dim = uiColor("Label.disabledForeground", new Color(128, 128, 128));
    Color link = uiColor("Component.linkColor", uiColor("Link.foreground", new Color(0, 102, 204)));
    Color selBg = uiColor("TextPane.selectionBackground", uiColor("TextArea.selectionBackground", new Color(60, 120, 200)));

    Color noticeFg = blend(fg, dim, 0.6f);
    Color statusFg = blend(fg, dim, 0.35f);
    Color errFg = uiColor("Component.errorForeground", new Color(220, 80, 80));

    // Mention highlight: soften selection background against the chat bg
    Color mentionBg = blend(selBg, bg, 0.45f);

    tsStyle = attrs(dim, false, false, false, null);
    fromStyle = attrs(fg, true, false, false, null);
    msgStyle = attrs(fg, false, false, false, null);

    noticeFromStyle = attrs(noticeFg, true, false, false, null);
    noticeMsgStyle = attrs(noticeFg, false, true, false, null);

    statusStyle = attrs(statusFg, false, true, false, null);
    errorStyle = attrs(errFg, true, false, false, null);

    linkStyle = attrs(link, false, false, true, null);
    mentionStyle = attrs(fg, true, false, false, mentionBg);

    // Follow-tail tracking (only for the active buffer)
    scroll.getVerticalScrollBar().addAdjustmentListener(e -> {
      if (programmaticScroll) return;
      if (activeTarget == null) return;
      updateFollowTailFromScrollbar();
    });

    // Clickable links and embeds
    chat.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        String url = urlAt(e.getPoint());
        String embedUrl = embedUrlAt(e.getPoint());
        chat.setCursor((url != null || embedUrl != null) ? handCursor : normalCursor);
      }
    });

    chat.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) return;

        // Check for embed click first
        String embedUrl = embedUrlAt(e.getPoint());
        if (embedUrl != null) {
          handleEmbedClick(embedUrl);
          return;
        }

        // Check for regular URL click
        String url = urlAt(e.getPoint());
        if (url == null) return;
        openUrl(url);
      }
    });

    ensureBuffer("status");
    setActiveTarget("status");
  }

  public void setCurrentNick(String nick) {
    String n = nick == null ? "" : nick.trim();
    currentNick = n;
    if (n.isEmpty()) {
      mentionPattern = null;
      return;
    }

    // Avoid highlighting inside words: (?<!nickChars)NICK(?!nickChars)
    String quoted = Pattern.quote(n);
    String regex = "(?i)(?<!" + NICK_CHARS + ")" + quoted + "(?!" + NICK_CHARS + ")";
    mentionPattern = Pattern.compile(regex);
  }

  public void ensureBuffer(String target) {
    buffers.computeIfAbsent(target, k -> new DefaultStyledDocument());
    followTailByTarget.putIfAbsent(target, true);
    scrollValueByTarget.putIfAbsent(target, 0);
  }

  public void setActiveTarget(String target) {
    if (target == null || target.isBlank()) return;

    // Save old scroll state
    if (activeTarget != null) saveScrollState(activeTarget);

    ensureBuffer(target);
    activeTarget = target;
    chat.setDocument(buffers.get(target));

    // Restore scroll after document swap/layout
    SwingUtilities.invokeLater(() -> restoreScrollState(target));
  }

  public String getActiveTarget() {
    return activeTarget;
  }

  /** Regular chat message (styled, URLs clickable, mentions highlighted). */
  public void append(String target, String from, String text) {
    appendLine(target, Objects.toString(from, ""), Objects.toString(text, ""), fromStyle, msgStyle);
  }

  /** Server notice (styled distinctively). */
  public void appendNotice(String target, String from, String text) {
    appendLine(target, Objects.toString(from, ""), Objects.toString(text, ""), noticeFromStyle, noticeMsgStyle);
  }

  /** Status/info line (eg connect/join). */
  public void appendStatus(String target, String from, String text) {
    appendLine(target, Objects.toString(from, ""), Objects.toString(text, ""), statusStyle, statusStyle);
  }

  /** Error line. */
  public void appendError(String target, String from, String text) {
    appendLine(target, Objects.toString(from, ""), Objects.toString(text, ""), errorStyle, errorStyle);
  }

  private void appendLine(
      String target,
      String from,
      String text,
      AttributeSet fromAttr,
      AttributeSet textAttr
  ) {
    ensureBuffer(target);
    StyledDocument doc = buffers.get(target);

    String ts = TS_FMT.format(LocalTime.now());

    try {
      doc.insertString(doc.getLength(), "[" + ts + "] ", tsStyle);

      if (!from.isBlank()) {
        doc.insertString(doc.getLength(), from, fromAttr);
        doc.insertString(doc.getLength(), ": ", textAttr);
      }

      insertRichText(doc, text, textAttr, target);
      doc.insertString(doc.getLength(), "\n", textAttr);
    } catch (BadLocationException ignored) {
    }

    if (target.equals(activeTarget)) {
      if (followTailByTarget.getOrDefault(target, true)) {
        scrollToBottom();
      } else {
        // Keep the current scroll position cached
        saveScrollState(target);
      }
    }
  }

  private void insertRichText(StyledDocument doc, String text, AttributeSet base, String target) throws BadLocationException {
    if (text == null || text.isEmpty()) return;

    List<String> urlsToEmbed = new ArrayList<>();
    Matcher m = URL_PATTERN.matcher(text);
    int idx = 0;

    while (m.find()) {
      int start = m.start(1);
      int end = m.end(1);
      if (start > idx) {
        insertWithMentions(doc, text.substring(idx, start), base);
      }

      String rawUrl = m.group(1);
      UrlParts parts = splitUrlTrailingPunct(rawUrl);
      String display = parts.url;
      String open = normalizeUrl(display);

      SimpleAttributeSet a = new SimpleAttributeSet(linkStyle);
      a.addAttribute(ATTR_URL, open);
      doc.insertString(doc.getLength(), display, a);

      // Check if URL should be embedded
      if (shouldEmbed(open)) {
        urlsToEmbed.add(open);
      }

      if (!parts.trailing.isEmpty()) {
        insertWithMentions(doc, parts.trailing, base);
      }

      idx = end;
    }

    if (idx < text.length()) {
      insertWithMentions(doc, text.substring(idx), base);
    }

    // Process embeds for detected URLs
    for (String url : urlsToEmbed) {
      processEmbed(doc, url, target);
    }
  }

  private void insertWithMentions(StyledDocument doc, String text, AttributeSet base) throws BadLocationException {
    if (text == null || text.isEmpty()) return;

    Pattern p = mentionPattern;
    if (p == null || currentNick.isBlank()) {
      doc.insertString(doc.getLength(), text, base);
      return;
    }

    Matcher m = p.matcher(text);
    int idx = 0;

    while (m.find()) {
      int start = m.start();
      int end = m.end();
      if (start > idx) {
        doc.insertString(doc.getLength(), text.substring(idx, start), base);
      }
      doc.insertString(doc.getLength(), text.substring(start, end), mentionStyle);
      idx = end;
    }

    if (idx < text.length()) {
      doc.insertString(doc.getLength(), text.substring(idx), base);
    }
  }

  // ==================== Embed Support ====================

  private boolean shouldEmbed(String url) {
    if (embedFetcher == null || embedSettings == null || !embedSettings.enabled()) {
      return false;
    }
    EmbedType type = embedFetcher.classifyUrl(url);
    return type != EmbedType.NONE;
  }

  private void processEmbed(StyledDocument doc, String url, String target) {
    if (embedFetcher == null) return;

    // Fetch embed asynchronously
    Disposable disposable = embedFetcher.fetch(url)
        .observeOn(SwingEdt.scheduler())
        .subscribe(
            result -> insertEmbedImage(doc, url, target, result),
            error -> { /* Silently ignore embed errors */ }
        );

    embedDisposables.add(disposable);
  }

  private void insertEmbedImage(StyledDocument doc, String url, String target, EmbedResult result) {
    // Store result for click handling
    embedResults.put(url, result);

    try {
      // Get the image to display
      ImageIcon icon = createEmbedIcon(result);
      if (icon == null) return;

      // Insert newline, indent, image, and trailing newline
      doc.insertString(doc.getLength(), "\n    ", msgStyle);

      // Create style with the icon and embed URL attribute
      SimpleAttributeSet attrs = new SimpleAttributeSet();
      StyleConstants.setIcon(attrs, icon);
      attrs.addAttribute(ATTR_EMBED_URL, url);
      doc.insertString(doc.getLength(), " ", attrs);

      // Add newline after embed so next message starts on new line
      doc.insertString(doc.getLength(), "\n", msgStyle);

    } catch (BadLocationException e) {
      // Ignore
    }

    // Scroll if following tail
    if (target.equals(activeTarget) && followTailByTarget.getOrDefault(target, true)) {
      SwingUtilities.invokeLater(this::scrollToBottom);
    }
  }

  private ImageIcon createEmbedIcon(EmbedResult result) {
    return switch (result) {
      case EmbedResult.ImageEmbed img -> new ImageIcon(img.thumbnail());
      case EmbedResult.VideoEmbed vid -> {
        if (vid.thumbnail() != null) {
          yield createVideoThumbnailIcon(vid.thumbnail());
        }
        // Create placeholder for direct video files
        yield createVideoPlaceholderIcon(vid.url());
      }
      case EmbedResult.LinkPreview link -> {
        if (link.ogImage() != null) {
          yield new ImageIcon(link.ogImage());
        }
        yield null;
      }
      default -> null;
    };
  }

  private ImageIcon createVideoPlaceholderIcon(String url) {
    int w = embedSettings.maxThumbnailWidth();
    int h = (int) (w * 9.0 / 16.0); // 16:9 aspect ratio

    java.awt.image.BufferedImage placeholder = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    java.awt.Graphics2D g2 = placeholder.createGraphics();
    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

    // Dark background
    g2.setColor(new Color(40, 40, 40));
    g2.fillRect(0, 0, w, h);

    // Border
    g2.setColor(new Color(80, 80, 80));
    g2.drawRect(0, 0, w - 1, h - 1);

    // Play button
    int centerX = w / 2;
    int centerY = h / 2;
    int radius = 30;

    g2.setColor(new Color(0, 0, 0, 180));
    g2.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
    g2.setColor(Color.WHITE);
    g2.setStroke(new java.awt.BasicStroke(2));
    g2.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

    // Play triangle
    int triSize = 15;
    int[] xPoints = {centerX - triSize/2, centerX - triSize/2, centerX + triSize};
    int[] yPoints = {centerY - triSize, centerY + triSize, centerY};
    g2.fillPolygon(xPoints, yPoints, 3);

    // Filename at bottom
    String filename = url;
    int lastSlash = url.lastIndexOf('/');
    if (lastSlash >= 0 && lastSlash < url.length() - 1) {
      filename = url.substring(lastSlash + 1);
    }
    if (filename.length() > 40) {
      filename = filename.substring(0, 37) + "...";
    }

    g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
    java.awt.FontMetrics fm = g2.getFontMetrics();
    int textX = (w - fm.stringWidth(filename)) / 2;
    g2.setColor(new Color(180, 180, 180));
    g2.drawString(filename, textX, h - 10);

    g2.dispose();
    return new ImageIcon(placeholder);
  }

  private ImageIcon createVideoThumbnailIcon(java.awt.image.BufferedImage thumbnail) {
    // Draw play button overlay on video thumbnail
    int w = thumbnail.getWidth();
    int h = thumbnail.getHeight();
    java.awt.image.BufferedImage withOverlay = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    java.awt.Graphics2D g2 = withOverlay.createGraphics();
    g2.drawImage(thumbnail, 0, 0, null);

    // Draw play button circle
    int centerX = w / 2;
    int centerY = h / 2;
    int radius = Math.min(w, h) / 8;
    g2.setColor(new Color(0, 0, 0, 180));
    g2.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
    g2.setColor(Color.WHITE);
    g2.setStroke(new java.awt.BasicStroke(2));
    g2.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

    // Draw play triangle
    int triSize = radius / 2;
    int[] xPoints = {centerX - triSize/2, centerX - triSize/2, centerX + triSize};
    int[] yPoints = {centerY - triSize, centerY + triSize, centerY};
    g2.fillPolygon(xPoints, yPoints, 3);

    g2.dispose();
    return new ImageIcon(withOverlay);
  }

  private String embedUrlAt(Point p) {
    int pos = chat.viewToModel2D(p);
    if (pos < 0) return null;

    Document d = chat.getDocument();
    if (!(d instanceof StyledDocument sd)) return null;

    // Check positions around the click - icons are single chars but span many pixels
    // We need to find if we're clicking within the visual bounds of an icon
    for (int offset = 0; offset >= -5; offset--) {
      int checkPos = pos + offset;
      if (checkPos < 0) continue;

      Element el = sd.getCharacterElement(checkPos);
      if (el == null) continue;

      Object v = el.getAttributes().getAttribute(ATTR_EMBED_URL);
      if (v instanceof String s) {
        // Verify the click is within this element's visual bounds
        try {
          Rectangle2D rect = chat.modelToView2D(checkPos);
          if (rect != null) {
            // Get icon width from the element
            Icon icon = StyleConstants.getIcon(el.getAttributes());
            if (icon != null) {
              Rectangle2D iconBounds = new Rectangle2D.Double(
                  rect.getX(), rect.getY(), icon.getIconWidth(), icon.getIconHeight());
              if (iconBounds.contains(p)) {
                return s;
              }
            }
          }
        } catch (BadLocationException ignored) {}
      }
    }

    return null;
  }

  private void handleEmbedClick(String embedUrl) {
    EmbedResult result = embedResults.get(embedUrl);
    if (result == null) return;

    switch (result) {
      case EmbedResult.ImageEmbed img -> openImageViewer(img);
      case EmbedResult.VideoEmbed vid -> openVideoPlayer(embedUrl, result);
      case EmbedResult.LinkPreview link -> VideoPlayerDialog.openUrlInBrowser(link.url());
      default -> {}
    }
  }

  private void openImageViewer(EmbedResult.ImageEmbed img) {
    java.awt.image.BufferedImage viewImage = img.original();
    if (viewImage == null) viewImage = img.thumbnail();
    if (viewImage == null) return;

    JDialog dialog = new JDialog(
        (Frame) SwingUtilities.getWindowAncestor(this),
        "Image Viewer",
        true
    );

    // Create scalable image panel
    final java.awt.image.BufferedImage finalImage = viewImage;
    JPanel imagePanel = new JPanel() {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        double imgRatio = (double) finalImage.getWidth() / finalImage.getHeight();
        double panelRatio = (double) getWidth() / getHeight();

        int drawW, drawH;
        if (panelRatio > imgRatio) {
          drawH = getHeight();
          drawW = (int) (drawH * imgRatio);
        } else {
          drawW = getWidth();
          drawH = (int) (drawW / imgRatio);
        }

        int x = (getWidth() - drawW) / 2;
        int y = (getHeight() - drawH) / 2;
        g2.drawImage(finalImage, x, y, drawW, drawH, null);
      }
    };
    imagePanel.setBackground(Color.BLACK);
    dialog.add(imagePanel);

    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    int maxW = (int) (screen.width * 0.9);
    int maxH = (int) (screen.height * 0.9);
    int w = Math.min(viewImage.getWidth() + 50, maxW);
    int h = Math.min(viewImage.getHeight() + 50, maxH);

    dialog.setSize(w, h);
    dialog.setLocationRelativeTo(this);
    dialog.setVisible(true);
  }

  private void openVideoPlayer(String url, EmbedResult result) {
    // For YouTube/Vimeo, just open in browser directly (VLCJ can't play them)
    if (VideoPlayerDialog.isStreamingServiceUrl(url)) {
      VideoPlayerDialog.openUrlInBrowser(url);
      return;
    }

    // For direct video files, use the VLCJ player
    String title = null;
    if (result instanceof EmbedResult.VideoEmbed vid) {
      title = vid.title();
    }

    Window parentWindow = SwingUtilities.getWindowAncestor(this);
    VideoPlayerDialog dialog = new VideoPlayerDialog(parentWindow, url, title);
    dialog.setVisible(true);
    dialog.play();
  }

  /**
   * Clean up embed resources. Should be called when the component is disposed.
   */
  public void disposeEmbeds() {
    embedDisposables.clear();
    embedResults.clear();
  }

  // ==================== End Embed Support ====================

  private String urlAt(Point p) {
    int pos = chat.viewToModel2D(p);
    if (pos < 0) return null;

    Document d = chat.getDocument();
    if (!(d instanceof StyledDocument sd)) return null;

    Element el = sd.getCharacterElement(pos);
    if (el == null) return null;

    Object v = el.getAttributes().getAttribute(ATTR_URL);
    return (v instanceof String s) ? s : null;
  }

  private void openUrl(String url) {
    try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(new URI(url));
      } else {
        // Fallback for Linux - try xdg-open
        Runtime.getRuntime().exec(new String[]{"xdg-open", url});
      }
    } catch (Exception ex) {
      // Fallback: copy to clipboard and notify
      try {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
      } catch (Exception ignored) {}
      appendStatus("status", "(link)", "Could not open link; copied to clipboard: " + url);
    }
  }

  private void updateFollowTailFromScrollbar() {
    var bar = scroll.getVerticalScrollBar();
    var model = bar.getModel();

    int value = model.getValue();
    int extent = model.getExtent();
    int max = model.getMaximum();

    boolean atBottom = (value + extent) >= (max - 2);
    followTailByTarget.put(activeTarget, atBottom);
    scrollValueByTarget.put(activeTarget, value);
  }

  private void saveScrollState(String target) {
    if (target == null) return;
    var model = scroll.getVerticalScrollBar().getModel();
    scrollValueByTarget.put(target, model.getValue());

    int value = model.getValue();
    int extent = model.getExtent();
    int max = model.getMaximum();
    boolean atBottom = (value + extent) >= (max - 2);
    followTailByTarget.put(target, atBottom);
  }

  private void restoreScrollState(String target) {
    boolean follow = followTailByTarget.getOrDefault(target, true);
    if (follow) {
      scrollToBottom();
      return;
    }

    Integer v = scrollValueByTarget.get(target);
    if (v == null) v = 0;

    programmaticScroll = true;
    try {
      var bar = scroll.getVerticalScrollBar();
      var model = bar.getModel();
      int clamped = Math.max(model.getMinimum(), Math.min(v, model.getMaximum()));
      bar.setValue(clamped);
    } finally {
      programmaticScroll = false;
    }
  }

  private void scrollToBottom() {
    programmaticScroll = true;
    try {
      var bar = scroll.getVerticalScrollBar();
      bar.setValue(bar.getMaximum());
      scrollValueByTarget.put(activeTarget, bar.getValue());
      followTailByTarget.put(activeTarget, true);
    } finally {
      programmaticScroll = false;
    }
  }

  private static class UrlParts {
    final String url;
    final String trailing;
    UrlParts(String url, String trailing) {
      this.url = url;
      this.trailing = trailing;
    }
  }

  private static UrlParts splitUrlTrailingPunct(String raw) {
    if (raw == null) return new UrlParts("", "");
    String url = raw;
    StringBuilder trailing = new StringBuilder();

    // Common punctuation that appears after URLs in chat.
    String punct = ".,;:!?)]}";

    while (!url.isEmpty()) {
      char last = url.charAt(url.length() - 1);
      if (punct.indexOf(last) >= 0) {
        trailing.insert(0, last);
        url = url.substring(0, url.length() - 1);
      } else {
        break;
      }
    }

    return new UrlParts(url, trailing.toString());
  }

  private static String normalizeUrl(String display) {
    if (display == null || display.isBlank()) return "";
    String d = display.trim();
    if (d.regionMatches(true, 0, "www.", 0, 4)) {
      return "http://" + d;
    }
    return d;
  }

  private static Color uiColor(String key, Color fallback) {
    Color c = UIManager.getColor(key);
    return c != null ? c : fallback;
  }

  private static SimpleAttributeSet attrs(Color fg, boolean bold, boolean italic, boolean underline, Color bg) {
    SimpleAttributeSet a = new SimpleAttributeSet();
    if (fg != null) StyleConstants.setForeground(a, fg);
    if (bg != null) StyleConstants.setBackground(a, bg);
    StyleConstants.setBold(a, bold);
    StyleConstants.setItalic(a, italic);
    StyleConstants.setUnderline(a, underline);
    return a;
  }

  /** Linear blend of colors (t=0 -> a, t=1 -> b). */
  private static Color blend(Color a, Color b, float t) {
    if (a == null) return b;
    if (b == null) return a;
    t = Math.max(0f, Math.min(1f, t));
    int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
    int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
    int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
    int al = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
    return new Color(r, g, bl, al);
  }

  @Override public String getPersistentID() { return ID; }
  @Override public String getTabText() { return "Chat"; }
}
