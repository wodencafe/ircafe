package cafe.woden.ircclient.ui.chat.view;

import cafe.woden.ircclient.ui.WrapTextPane;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.util.ChatFindBarDecorator;
import cafe.woden.ircclient.ui.util.ChatTranscriptContextMenuDecorator;
import cafe.woden.ircclient.ui.util.ChatTranscriptMouseDecorator;
import cafe.woden.ircclient.ui.util.CloseableScope;
import cafe.woden.ircclient.ui.util.FollowTailScrollDecorator;
import cafe.woden.ircclient.ui.util.ViewportWrapRevalidateDecorator;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Point;
import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URI;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import java.awt.Rectangle;
import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyledDocument;
import javax.swing.text.Utilities;
import javax.swing.text.Element;
 

/**
 * Reusable chat transcript view: shows a StyledDocument, clickable links,
 * and smart "follow tail" scrolling.
 */
public abstract class ChatViewPanel extends JPanel implements Scrollable {

  protected final WrapTextPane chat = new WrapTextPane();
  protected final JScrollPane scroll = new JScrollPane(chat);

  private final CloseableScope decorators = new CloseableScope();

  private final ChatFindBarDecorator findBar;

  private final UiSettingsBus settingsBus;

  private StyledDocument currentDocument;

  private final FollowTailScrollDecorator followTailScroll;

  private final ChatTranscriptMouseDecorator transcriptMouse;

  private final Cursor textCursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
  private final Cursor handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);


  private final PropertyChangeListener settingsListener = this::onSettingsChanged;

  protected ChatViewPanel(UiSettingsBus settingsBus) {
    super(new BorderLayout());
    this.settingsBus = settingsBus;

    chat.setEditable(false);

    // We always want wrapping; never show a horizontal scrollbar in the transcript view.
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    // Force a re-layout on resize so wrapping recalculates immediately when the window shrinks/grows.
    decorators.add(ViewportWrapRevalidateDecorator.decorate(scroll.getViewport(), chat));

    // Apply initial font if settings bus is present.
    if (this.settingsBus != null) {
      applySettings(this.settingsBus.get());
    } else {
      chat.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    }

    // In-panel find UI + keybindings (Ctrl+F).
    this.findBar = decorators.add(ChatFindBarDecorator.install(this, chat, () -> currentDocument));

    // Right-click context menu: default Copy / Select All / Find Text; URL-specific options on links.
    decorators.add(ChatTranscriptContextMenuDecorator.decorate(
        chat,
        this::urlAt,
        this::openUrl,
        this::openFindBar
    ));

    // Follow-tail scroll behavior is implemented as a decorator so the view stays lean.
    this.followTailScroll = decorators.add(new FollowTailScrollDecorator(
        scroll,
        this::isFollowTail,
        this::setFollowTail,
        this::getSavedScrollValue,
        this::setSavedScrollValue
    ));

    add(scroll, BorderLayout.CENTER);


    // Hover + click behavior extracted into a decorator.
    this.transcriptMouse = decorators.add(ChatTranscriptMouseDecorator.decorate(
        chat,
        handCursor,
        textCursor,
        this::urlAt,
        this::channelAt,
        this::nickAt,
        this::openUrl,
        this::onChannelClicked,
        this::onNickClicked,
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

  /**
   * Shows the find bar and focuses it.
   */
  public void openFindBar() {
    findBar.open();
  }

  /**
   * Toggles the find bar. If already visible, hide it.
   */
  public void toggleFindBar() {
    findBar.toggle();
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

  /**
   * Called on non-link clicks. Subclasses can use this to "activate" a target.
   */
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
