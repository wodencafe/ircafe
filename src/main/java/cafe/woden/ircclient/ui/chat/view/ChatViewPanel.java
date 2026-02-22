package cafe.woden.ircclient.ui.chat.view;

import cafe.woden.ircclient.ui.WrapTextPane;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.util.ChatFindBarDecorator;
import cafe.woden.ircclient.ui.util.ChatAutoLoadOlderScrollDecorator;
import cafe.woden.ircclient.ui.util.ChatTranscriptContextMenuDecorator;
import cafe.woden.ircclient.ui.util.ChatTranscriptMouseDecorator;
import cafe.woden.ircclient.ui.util.CloseableScope;
import cafe.woden.ircclient.ui.util.FollowTailScrollDecorator;
import cafe.woden.ircclient.ui.util.ViewportWrapRevalidateDecorator;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.util.VirtualThreads;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Point;
import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import java.awt.Rectangle;
import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyledDocument;
import javax.swing.text.Utilities;
import javax.swing.text.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ChatViewPanel extends JPanel implements Scrollable {

  private static final Logger log = LoggerFactory.getLogger(ChatViewPanel.class);
  private static final Pattern URL_TOKEN_PATTERN =
      Pattern.compile("(?i)(https?://[^\\s<>\"\\u0000-\\u001F]+|www\\.[^\\s<>\"\\u0000-\\u001F]+)");

  protected final WrapTextPane chat = new WrapTextPane();
  protected final JScrollPane scroll = new JScrollPane(chat);

  private final CloseableScope decorators = new CloseableScope();

  private final ChatFindBarDecorator findBar;

  private final UiSettingsBus settingsBus;

  private StyledDocument currentDocument;

  private final FollowTailScrollDecorator followTailScroll;

  private final ChatTranscriptMouseDecorator transcriptMouse;

  private final ChatTranscriptContextMenuDecorator transcriptMenu;

  private final Cursor textCursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
  private final Cursor handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

  private final PropertyChangeListener settingsListener = this::onSettingsChanged;

  protected ChatViewPanel(UiSettingsBus settingsBus) {
    super(new BorderLayout());
    this.settingsBus = settingsBus;

    chat.setEditable(false);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    applyChatBackground();
    decorators.add(ViewportWrapRevalidateDecorator.decorate(scroll.getViewport(), chat));
    if (this.settingsBus != null) {
      applySettings(this.settingsBus.get());
    } else {
      chat.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    }
    this.findBar = decorators.add(ChatFindBarDecorator.install(this, chat, () -> currentDocument));
    this.transcriptMenu = decorators.add(ChatTranscriptContextMenuDecorator.decorate(
        chat,
        this::urlAt,
        this::nickAt,
        this::nickContextMenuFor,
        this::openUrl,
        this::openFindBar,
        this::currentProxyPlan,
        this::loadNewerHistoryContextActionVisible,
        this::loadAroundMessageContextActionVisible,
        this::onLoadNewerHistoryRequested,
        this::onLoadContextAroundMessageRequested,
        this::replyContextActionVisible,
        this::reactContextActionVisible,
        this::onReplyToMessageRequested,
        this::onReactToMessageRequested,
        this::editContextActionVisible,
        this::redactContextActionVisible,
        this::onEditMessageRequested,
        this::onRedactMessageRequested
    ));
    this.followTailScroll = decorators.add(new FollowTailScrollDecorator(
        scroll,
        this::isFollowTail,
        this::setFollowTail,
        this::getSavedScrollValue,
        this::setSavedScrollValue
    ));

    // "Infinite scroll" helper: if the user tries to wheel-scroll up past the very top,
    // trigger the embedded "Load older messages…" control (if present).
    decorators.add(ChatAutoLoadOlderScrollDecorator.decorate(scroll, chat));

    add(scroll, BorderLayout.CENTER);
    this.transcriptMouse = decorators.add(ChatTranscriptMouseDecorator.decorate(
        chat,
        handCursor,
        textCursor,
        this::urlAt,
        this::channelAt,
        this::nickAt,
        this::messageReferenceAt,
        this::openUrl,
        this::onChannelClicked,
        this::onNickClicked,
        this::onMessageReferenceClicked,
        this::onTranscriptClicked
    ));
  }

  @Override
  public void updateUI() {
    super.updateUI();
    // Keep the scrollpane/viewport/panel background matched to the transcript background.
    // This prevents the subtle "paper on cardboard" mismatch on many LAFs.
    SwingUtilities.invokeLater(this::applyChatBackground);
  }

  private void applyChatBackground() {
    try {
      Color bg = UIManager.getColor("TextPane.background");
      if (bg == null) {
        bg = chat.getBackground();
      }
      if (bg == null) {
        return;
      }

      chat.setBackground(bg);
      scroll.getViewport().setBackground(bg);
      scroll.setBackground(bg);
      setBackground(bg);
    } catch (Exception ignored) {
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (settingsBus != null) {
      settingsBus.addListener(settingsListener);
    }
  }

  @Override
  public void removeNotify() {
    if (settingsBus != null) {
      settingsBus.removeListener(settingsListener);
    }
    super.removeNotify();
  }

  public void openFindBar() {
    findBar.open();
  }

  public void toggleFindBar() {
    findBar.toggle();
  }

  public void findNextInTranscript() {
    findBar.findNext();
  }

  public void findPreviousInTranscript() {
    findBar.findPrevious();
  }

  protected void setTranscriptContextMenuActions(Runnable clearAction, Runnable reloadRecentAction) {
    try {
      if (transcriptMenu != null) {
        transcriptMenu.setClearAction(clearAction);
        transcriptMenu.setReloadRecentAction(reloadRecentAction);
      }
    } catch (Exception ignored) {
    }
  }

  private void onSettingsChanged(PropertyChangeEvent evt) {
    if (!UiSettingsBus.PROP_UI_SETTINGS.equals(evt.getPropertyName())) return;
    Object o = evt.getNewValue();
    if (o instanceof UiSettings s) {
      applySettings(s);
    }
  }

  private void applySettings(UiSettings s) {
    if (s == null) return;
    try {
      chat.setFont(new Font(s.chatFontFamily(), Font.PLAIN, s.chatFontSize()));
    } catch (Exception ignored) {
      chat.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    }
    chat.revalidate();
    chat.repaint();
  }

  protected void onTranscriptClicked() {
    // default: no-op
  }

  /**
   * Called when the user clicks a nick token in the transcript.
   *
   * @return true if the click was handled/consumed
   */
  protected boolean onNickClicked(String nick) {
    return false;
  }

  /**
   * Called when the user clicks a channel token (e.g. "#channel") in the transcript.
   *
   * @return true if the click was handled/consumed
   */
  protected boolean onChannelClicked(String channel) {
    return false;
  }

  /**
   * Called when the user clicks a reply/reference token linked to a message ID.
   *
   * @return true if the click was handled/consumed
   */
  protected boolean onMessageReferenceClicked(String messageId) {
    return false;
  }

  /**
   * Optional hook: if provided, this menu is shown when the user right-clicks a nick token
   * in the transcript. Return {@code null} (or an empty menu) to fall back to the default
   * transcript context menu.
   */
  protected JPopupMenu nickContextMenuFor(String nick) {
    return null;
  }

  /**
   * Optional hook: return the proxy plan for network operations initiated from this view
   * (e.g., "Save Link As...").
   *
   * <p>Return {@code null} to fall back to the global proxy settings.
   */
  protected ProxyPlan currentProxyPlan() {
    return null;
  }

  /** Whether the transcript context menu should show "Reply to Message…". */
  protected boolean replyContextActionVisible() {
    return false;
  }

  /** Whether the transcript context menu should show "React to Message…". */
  protected boolean reactContextActionVisible() {
    return false;
  }

  /** Whether the transcript context menu should show "Edit Message…". */
  protected boolean editContextActionVisible() {
    return false;
  }

  /** Whether the transcript context menu should show "Redact Message…". */
  protected boolean redactContextActionVisible() {
    return false;
  }

  /** Whether the transcript context menu should show "Load Newer History". */
  protected boolean loadNewerHistoryContextActionVisible() {
    return false;
  }

  /** Whether the transcript context menu should show "Load Context Around Message…". */
  protected boolean loadAroundMessageContextActionVisible() {
    return false;
  }

  /** Called by transcript context menu action "Load Newer History". */
  protected void onLoadNewerHistoryRequested() {
    // default: no-op
  }

  /** Called by transcript context menu action "Load Context Around Message…". */
  protected void onLoadContextAroundMessageRequested(String messageId) {
    // default: no-op
  }

  /** Called by transcript context menu action "Reply to Message…". */
  protected void onReplyToMessageRequested(String messageId) {
    // default: no-op
  }

  /** Called by transcript context menu action "React to Message…". */
  protected void onReactToMessageRequested(String messageId) {
    // default: no-op
  }

  /** Called by transcript context menu action "Edit Message…". */
  protected void onEditMessageRequested(String messageId) {
    // default: no-op
  }

  /** Called by transcript context menu action "Redact Message…". */
  protected void onRedactMessageRequested(String messageId) {
    // default: no-op
  }

  /**
   * Build a prefilled raw-line draft for replying to an IRCv3 message by msgid.
   *
   * <p>Returned text is intended for the input field and should be sent with {@code /quote}.
   */
  protected static String buildReplyPrefillDraft(String ircTarget, String messageId) {
    String target = Objects.toString(ircTarget, "").trim();
    String msgId = Objects.toString(messageId, "").trim();
    if (target.isEmpty() || msgId.isEmpty()) return "";
    String escapedMsgId = escapeIrcv3TagValue(msgId);
    return "/quote @+draft/reply=" + escapedMsgId + " PRIVMSG " + target + " :";
  }

  /**
   * Build a prefilled raw-line draft for reacting to an IRCv3 message by msgid.
   *
   * <p>Returned text is intended for the input field and should be sent with {@code /quote}.
   * The default reaction token is {@code :+1:}; users can edit it before sending.
   */
  protected static String buildReactPrefillDraft(String ircTarget, String messageId) {
    String target = Objects.toString(ircTarget, "").trim();
    String msgId = Objects.toString(messageId, "").trim();
    if (target.isEmpty() || msgId.isEmpty()) return "";
    String escapedMsgId = escapeIrcv3TagValue(msgId);
    return "/quote @+draft/react=:+1:;+draft/reply=" + escapedMsgId + " TAGMSG " + target;
  }

  /**
   * Build a raw command line that requests the latest/newer CHATHISTORY page for the active target.
   */
  protected static String buildChatHistoryLatestCommand() {
    return "/chathistory latest *";
  }

  /**
   * Build a raw command line that requests CHATHISTORY context around a specific IRCv3 message id.
   */
  protected static String buildChatHistoryAroundByMsgIdCommand(String messageId) {
    String msgId = Objects.toString(messageId, "").trim();
    if (msgId.isEmpty()) return "";
    for (int i = 0; i < msgId.length(); i++) {
      if (Character.isWhitespace(msgId.charAt(i))) return "";
    }
    return "/chathistory around msgid=" + msgId;
  }

  private static String escapeIrcv3TagValue(String value) {
    String raw = Objects.toString(value, "");
    if (raw.isEmpty()) return "";
    StringBuilder out = new StringBuilder(raw.length() + 8);
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      switch (c) {
        case ';' -> out.append("\\:");
        case ' ' -> out.append("\\s");
        case '\\' -> out.append("\\\\");
        case '\r' -> out.append("\\r");
        case '\n' -> out.append("\\n");
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  protected void setDocument(StyledDocument doc) {
    if (currentDocument == doc) return;

    currentDocument = doc;
    chat.setDocument(doc);

    // Let the decorator swap document listeners + restore scroll position.
    followTailScroll.onDocumentSwapped(doc);

    findBar.onDocumentSwapped();
  }

  protected void scrollToBottom() {
    followTailScroll.scrollToBottom();
  }

  protected void scrollToTranscriptOffset(int offset) {
    try {
      StyledDocument doc = currentDocument;
      int len = (doc != null) ? doc.getLength() : 0;
      int off = Math.max(0, Math.min(offset, len));
      chat.setCaretPosition(off);
      java.awt.geom.Rectangle2D r = chat.modelToView2D(off);
      if (r != null) {
        chat.scrollRectToVisible(r.getBounds());
      }
    } catch (Exception ignored) {
    }
  }

  protected boolean isTranscriptAtBottom() {
    try {
      JScrollBar bar = scroll.getVerticalScrollBar();
      if (bar == null) return true;
      int max = bar.getMaximum();
      int extent = bar.getModel().getExtent();
      int val = bar.getValue();
      return (val + extent) >= (max - 2);
    } catch (Exception ignored) {
      return true;
    }
  }

  protected void armTailPinOnNextAppendIfAtBottom() {
    followTailScroll.armTailPinIfAtBottomNow();
  }

  protected void updateScrollStateFromBar() {
    followTailScroll.updateScrollStateFromBar();
  }

  protected void restoreScrollState() {
    followTailScroll.restoreScrollState();
  }

  /**
   * Close any installed decorators/listeners owned by this view.
   * Subclasses should call this from their lifecycle shutdown (@PreDestroy).
   */
  protected void closeDecorators() {
    decorators.closeQuietly();
  }

  protected abstract boolean isFollowTail();
  protected abstract void setFollowTail(boolean followTail);
  protected abstract int getSavedScrollValue();
  protected abstract void setSavedScrollValue(int value);

  private String urlAt(Point p) {
    try {
      int pos = chat.viewToModel2D(p);
      if (pos < 0) return null;

      StyledDocument doc = (StyledDocument) chat.getDocument();
      int start = Utilities.getWordStart(chat, pos);
      int end = Utilities.getWordEnd(chat, pos);
      if (start < 0 || end <= start) return null;

      AttributeSet attrs = doc.getCharacterElement(start).getAttributes();
      Object url = attrs.getAttribute(ChatStyles.ATTR_URL);
      return url != null ? String.valueOf(url) : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private String nickAt(Point p) {
    try {
      int pos = chat.viewToModel2D(p);
      if (pos < 0) return null;

      StyledDocument doc = (StyledDocument) chat.getDocument();
      Element el = doc.getCharacterElement(pos);
      if (el == null) return null;

      AttributeSet attrs = el.getAttributes();
      Object marker = attrs.getAttribute(NickColorService.ATTR_NICK);
      if (marker == null) return null;

      int start = el.getStartOffset();
      int end = el.getEndOffset();
      if (end <= start) return null;

      String token = doc.getText(start, end - start);
      if (token == null) return null;
      token = token.trim();
      if (token.isEmpty()) return null;

      // Safety: strip any surrounding punctuation that might have been included in the element.
      int a = 0;
      int b = token.length();
      while (a < b && !isNickChar(token.charAt(a))) a++;
      while (b > a && !isNickChar(token.charAt(b - 1))) b--;
      if (b <= a) return null;

      return token.substring(a, b);
    } catch (Exception ignored) {
      return null;
    }
  }

  private String channelAt(Point p) {
    try {
      int pos = chat.viewToModel2D(p);
      if (pos < 0) return null;

      StyledDocument doc = (StyledDocument) chat.getDocument();
      Element el = doc.getCharacterElement(pos);
      if (el == null) return null;

      AttributeSet attrs = el.getAttributes();
      Object marker = attrs.getAttribute(ChatStyles.ATTR_CHANNEL);
      if (marker == null) return null;

      int start = el.getStartOffset();
      int end = el.getEndOffset();
      if (end <= start) return null;

      String token = doc.getText(start, end - start);
      if (token == null) return null;
      token = token.trim();
      if (token.isEmpty()) return null;

      // Renderer should have stored the exact channel token; be defensive anyway.
      int hash = token.indexOf('#');
      if (hash < 0) return null;
      token = token.substring(hash).trim();
      if (token.length() <= 1) return null;
      return token;
    } catch (Exception ignored) {
      return null;
    }
  }

  private String messageReferenceAt(Point p) {
    try {
      int pos = chat.viewToModel2D(p);
      if (pos < 0) return null;

      StyledDocument doc = (StyledDocument) chat.getDocument();
      Element el = doc.getCharacterElement(pos);
      if (el == null) return null;

      AttributeSet attrs = el.getAttributes();
      Object marker = attrs.getAttribute(ChatStyles.ATTR_MSG_REF);
      if (marker == null) return null;
      String msgId = String.valueOf(marker).trim();
      return msgId.isEmpty() ? null : msgId;
    } catch (Exception ignored) {
      return null;
    }
  }

  // Keep in sync with ChatRichTextRenderer#isNickChar.
  private static boolean isNickChar(char c) {
    return Character.isLetterOrDigit(c)
        || c == '[' || c == ']' || c == '\\' || c == '`'
        || c == '_' || c == '^' || c == '{' || c == '|' || c == '}'
        || c == '-';
  }
  private void openUrl(String url) {
    String raw = sanitizeUrlForBrowser(url);
    if (raw == null || raw.isBlank()) return;

    VirtualThreads.start("ircafe-open-url", () -> {
      // On Linux, prefer explicit browser executables before Desktop/xdg handlers.
      // Some desktop MIME associations can route URLs to non-browser apps.
      if (isLinux() && tryPlatformOpen(raw)) return;
      if (tryDesktopBrowse(raw)) return;
      if (!isLinux() && tryPlatformOpen(raw)) return;
      log.warn("[ircafe] Could not open URL in browser: {}", raw);
    });
  }

  private static boolean tryDesktopBrowse(String url) {
    try {
      if (!Desktop.isDesktopSupported()) return false;
      Desktop desktop = Desktop.getDesktop();
      if (desktop == null) return false;
      if (!desktop.isSupported(Desktop.Action.BROWSE)) return false;
      desktop.browse(new URI(url));
      return true;
    } catch (Exception e) {
      log.debug("[ircafe] Desktop browse failed for {}", url, e);
      return false;
    }
  }

  private static boolean tryPlatformOpen(String url) {
    if (isLinux()) {
      return tryKnownLinuxBrowser(url)
          || tryStart("xdg-open", url)
          || tryStart("gio", "open", url)
          || tryStart("sensible-browser", url)
          || tryStart("x-www-browser", url)
          || tryStart("gnome-open", url)
          || tryStart("kde-open", url);
    }
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac") || os.contains("darwin")) {
      return tryStart("open", url);
    }
    if (os.contains("win")) {
      return tryStart("rundll32", "url.dll,FileProtocolHandler", url)
          || tryStart("cmd", "/c", "start", "", url);
    }
    return false;
  }

  private static boolean tryKnownLinuxBrowser(String url) {
    List<String> browsers = List.of(
        "librewolf",
        "zen-browser",
        "firefox",
        "google-chrome",
        "chromium",
        "chromium-browser",
        "brave-browser",
        "microsoft-edge",
        "opera",
        "vivaldi");
    for (String browser : browsers) {
      if (tryStart(browser, url)) return true;
    }
    return false;
  }

  private static boolean isLinux() {
    String os = System.getProperty("os.name", "");
    return os.toLowerCase(Locale.ROOT).contains("linux");
  }

  private static boolean tryStart(String... cmd) {
    if (cmd == null || cmd.length == 0) return false;
    try {
      Process process = new ProcessBuilder(cmd)
          .redirectErrorStream(true)
          .start();
      if (process == null) return false;

      // If the command exits immediately with a non-zero status, treat it as a failure
      // so we can fall back to the next opener.
      try {
        if (process.waitFor(450, TimeUnit.MILLISECONDS)) {
          return process.exitValue() == 0;
        }
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }

      // Still running after a short window generally means launch succeeded.
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static String sanitizeUrlForBrowser(String rawUrl) {
    String s = Objects.toString(rawUrl, "").trim();
    if (s.isEmpty()) return null;

    // Be defensive: if a malformed token leaked extra text, pull the first URL-looking token.
    Matcher m = URL_TOKEN_PATTERN.matcher(s);
    if (m.find()) {
      s = Objects.toString(m.group(1), "").trim();
    }

    s = trimEdgeNoise(s);
    if (s.isEmpty()) return null;

    if (s.startsWith("<") && s.endsWith(">") && s.length() > 2) {
      s = s.substring(1, s.length() - 1).trim();
    }

    int ws = firstWhitespaceIndex(s);
    if (ws > 0) {
      // Be defensive if an invalid token accidentally captured trailing chat text.
      s = s.substring(0, ws).trim();
    }

    // Trim common trailing punctuation introduced by prose.
    while (!s.isEmpty()) {
      char c = s.charAt(s.length() - 1);
      if (c == '.'
          || c == ','
          || c == ')'
          || c == ']'
          || c == '}'
          || c == '>'
          || c == '!'
          || c == '?'
          || c == ';'
          || c == ':'
          || c == '\''
          || c == '"') {
        s = s.substring(0, s.length() - 1).trim();
        continue;
      }
      break;
    }
    s = trimEdgeNoise(s);

    if (s.isBlank()) return null;

    String lower = s.toLowerCase(Locale.ROOT);
    if (lower.startsWith("www.")) {
      s = "https://" + s;
      lower = s.toLowerCase(Locale.ROOT);
    }

    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
      return null;
    }

    try {
      URI uri = new URI(s);
      String scheme = Objects.toString(uri.getScheme(), "").toLowerCase(Locale.ROOT);
      if (!scheme.equals("http") && !scheme.equals("https")) return null;
      if (Objects.toString(uri.getHost(), "").isBlank()) return null;
      return uri.toASCIIString();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String trimEdgeNoise(String s) {
    if (s == null || s.isEmpty()) return "";
    int start = 0;
    int end = s.length();
    while (start < end) {
      char c = s.charAt(start);
      if (Character.isWhitespace(c) || Character.isISOControl(c)) {
        start++;
      } else {
        break;
      }
    }
    while (end > start) {
      char c = s.charAt(end - 1);
      if (Character.isWhitespace(c) || Character.isISOControl(c)) {
        end--;
      } else {
        break;
      }
    }
    return (start == 0 && end == s.length()) ? s : s.substring(start, end);
  }

  private static int firstWhitespaceIndex(String s) {
    if (s == null || s.isEmpty()) return -1;
    for (int i = 0; i < s.length(); i++) {
      if (Character.isWhitespace(s.charAt(i))) return i;
    }
    return -1;
  }

  // If the docking framework wraps a Dockable in a JScrollPane, we do NOT want a second set of scrollbars.
  // This panel contains its own internal JScrollPane (the transcript), so always track any outer viewport.
  @Override
  public Dimension getPreferredScrollableViewportSize() {
    // Reasonable default to avoid "infinite preferred height" from the transcript content.
    return new Dimension(640, 480);
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 16;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return Math.max(visibleRect.height - 16, 16);
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return true;
  }

}
