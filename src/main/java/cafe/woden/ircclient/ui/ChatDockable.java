package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.WrapTextPane;
import io.github.andrewauclair.moderndocking.Dockable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

  public ChatDockable() {
    super(new BorderLayout());

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

    // Clickable links
    chat.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        String url = urlAt(e.getPoint());
        chat.setCursor(url != null ? handCursor : normalCursor);
      }
    });

    chat.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e)) return;
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

      insertRichText(doc, text, textAttr);
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

  private void insertRichText(StyledDocument doc, String text, AttributeSet base) throws BadLocationException {
    if (text == null || text.isEmpty()) return;

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

      if (!parts.trailing.isEmpty()) {
        insertWithMentions(doc, parts.trailing, base);
      }

      idx = end;
    }

    if (idx < text.length()) {
      insertWithMentions(doc, text.substring(idx), base);
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
      if (!Desktop.isDesktopSupported()) throw new UnsupportedOperationException("Desktop not supported");
      Desktop.getDesktop().browse(new URI(url));
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
