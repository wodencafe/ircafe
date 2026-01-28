package cafe.woden.ircclient.ui.chat.view;

import cafe.woden.ircclient.ui.WrapTextPane;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Point;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URI;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentAdapter;
import java.awt.Rectangle;
import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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

  private final UiSettingsBus settingsBus;

  private boolean programmaticScroll = false;
  private StyledDocument currentDocument;

  // Track prior scrollbar state so we can keep "follow tail" enabled when the transcript grows.
  // Without this, growing content can emit scrollbar adjustment events that look like the
  // user scrolled away from the bottom, which disables auto-scroll.
  private int lastBarMax = -1;
  private int lastBarExtent = 0;
  private int lastBarValue = 0;

  private final Cursor textCursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR);
  private final Cursor handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

  private final DocumentListener docListener = new DocumentListener() {
    @Override public void insertUpdate(DocumentEvent e) { maybeAutoScroll(); }
    @Override public void removeUpdate(DocumentEvent e) { maybeAutoScroll(); }
    @Override public void changedUpdate(DocumentEvent e) { maybeAutoScroll(); }
  };

  private final PropertyChangeListener settingsListener = this::onSettingsChanged;

  protected ChatViewPanel(UiSettingsBus settingsBus) {
    super(new BorderLayout());
    this.settingsBus = settingsBus;

    chat.setEditable(false);


    // We always want wrapping; never show a horizontal scrollbar in the transcript view.
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    // Force a re-layout on resize so wrapping recalculates immediately when the window shrinks/grows.
    scroll.getViewport().addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        chat.revalidate();
        chat.repaint();
      }
    });

    // Apply initial font if settings bus is present.
    if (this.settingsBus != null) {
      applySettings(this.settingsBus.get());
    } else {
      chat.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    }

    add(scroll, BorderLayout.CENTER);

    // Follow tail detection per view.
    scroll.getVerticalScrollBar().addAdjustmentListener(e -> {
      if (programmaticScroll) return;
      updateScrollStateFromBar();
    });

    chat.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        // Prefer link cursor, otherwise use nick cursor if it's a colored nick token.
        setCursor((urlAt(e.getPoint()) != null || nickAt(e.getPoint()) != null) ? handCursor : textCursor);
      }
    });

    chat.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) return;
        String url = urlAt(e.getPoint());
        if (url != null) {
          openUrl(url);
          return;
        }

        String nick = nickAt(e.getPoint());
        if (nick != null && onNickClicked(nick)) {
          return;
        }

        onTranscriptClicked();
      }
    });
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

  protected void setDocument(StyledDocument doc) {
    if (currentDocument == doc) return;

    if (currentDocument != null) {
      currentDocument.removeDocumentListener(docListener);
    }

    currentDocument = doc;
    chat.setDocument(doc);

    if (currentDocument != null) {
      currentDocument.addDocumentListener(docListener);
    }

    restoreScrollState();
  }

  private void captureBarState() {
    JScrollBar bar = scroll.getVerticalScrollBar();
    lastBarMax = bar.getMaximum();
    lastBarExtent = bar.getModel().getExtent();
    lastBarValue = bar.getValue();
  }

  private void maybeAutoScroll() {
    if (!isFollowTail()) return;
    SwingUtilities.invokeLater(this::scrollToBottom);
  }

  protected void scrollToBottom() {
    JScrollBar bar = scroll.getVerticalScrollBar();
    try {
      programmaticScroll = true;
      // Clamp-to-bottom: setting to maximum will be constrained by the model to (max - extent).
      bar.setValue(bar.getMaximum());
      captureBarState();
    } finally {
      programmaticScroll = false;
    }
  }

  protected void updateScrollStateFromBar() {
    JScrollBar bar = scroll.getVerticalScrollBar();
    int max = bar.getMaximum();
    int extent = bar.getModel().getExtent();
    int val = bar.getValue();

    boolean atBottomNow = (val + extent) >= (max - 2);

    if (atBottomNow) {
      // If the user reaches the bottom, we (re)enable follow-tail.
      setFollowTail(true);
    } else if (isFollowTail()) {
      // IMPORTANT: Do NOT disable follow-tail just because the transcript grew.
      // Only disable it when the user actively scrolls upward.
      if (val < lastBarValue) {
        setFollowTail(false);
      }
      // If the doc grows and the scrollbar's maximum changes, 'val' may stay the same;
      // in that case we keep follow-tail enabled.
    }

    setSavedScrollValue(val);

    lastBarMax = max;
    lastBarExtent = extent;
    lastBarValue = val;
  }

  protected void restoreScrollState() {
    if (isFollowTail()) {
      SwingUtilities.invokeLater(this::scrollToBottom);
      return;
    }

    int saved = getSavedScrollValue();
    SwingUtilities.invokeLater(() -> {
      JScrollBar bar = scroll.getVerticalScrollBar();
      try {
        programmaticScroll = true;
        bar.setValue(Math.min(saved, Math.max(0, bar.getMaximum())));
        captureBarState();
      } finally {
        programmaticScroll = false;
      }
    });
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
