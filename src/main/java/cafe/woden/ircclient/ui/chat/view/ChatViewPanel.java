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
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Point;
import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URI;
import java.util.Objects;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import java.awt.Rectangle;
import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyledDocument;
import javax.swing.text.Utilities;
import javax.swing.text.Element;

public abstract class ChatViewPanel extends JPanel implements Scrollable {

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
    try {
      Desktop.getDesktop().browse(new URI(url));
    } catch (Exception ignored) {
      // Best-effort.
    }
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
