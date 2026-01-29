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
import javax.swing.text.Highlighter;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultCaret;

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

/**
 * Reusable chat transcript view: shows a StyledDocument, clickable links,
 * and smart "follow tail" scrolling.
 */
public abstract class ChatViewPanel extends JPanel implements Scrollable {

  protected final WrapTextPane chat = new WrapTextPane();
  protected final JScrollPane scroll = new JScrollPane(chat);

  private final FindBar findBar = new FindBar();

  private final UiSettingsBus settingsBus;

  private boolean programmaticScroll = false;
  private StyledDocument currentDocument;

  // Track prior scrollbar state so we can keep "follow tail" enabled when the transcript grows.
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

    // Hidden find bar (Ctrl+F)
    findBar.setVisible(false);
    add(findBar, BorderLayout.NORTH);


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
        setCursor((urlAt(e.getPoint()) != null
            || channelAt(e.getPoint()) != null
            || nickAt(e.getPoint()) != null) ? handCursor : textCursor);
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

        String channel = channelAt(e.getPoint());
        if (channel != null && onChannelClicked(channel)) {
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

  /**
   * Shows the find bar and focuses it.
   */
  public void openFindBar() {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    findBar.open(focusOwner);
  }

  /**
   * Toggles the find bar. If already visible, hide it.
   */
  public void toggleFindBar() {
    if (findBar.isVisible()) {
      findBar.close();
    } else {
      openFindBar();
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

    if (currentDocument != null) {
      currentDocument.removeDocumentListener(docListener);
    }

    currentDocument = doc;
    chat.setDocument(doc);

    if (currentDocument != null) {
      currentDocument.addDocumentListener(docListener);
    }

    restoreScrollState();

    findBar.onDocumentSwapped();
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
      // Enable follow tail
      setFollowTail(true);
    } else if (isFollowTail()) {
      if (val < lastBarValue) {
        setFollowTail(false);
      }
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


  /**
   * Simple in-panel find UI (hidden by default; opened via Ctrl+F).
   */
  private final class FindBar extends JPanel {

    private final JTextField field = new JTextField(28);
    private final JCheckBox matchCase = new JCheckBox("Aa");
    private final JLabel status = new JLabel("");

    private final JButton prev = new JButton("Prev");
    private final JButton next = new JButton("Next");
    private final JButton close = new JButton("×");

    private Component restoreFocusTo;

    private String cachedNeedle = null;
    private boolean cachedMatchCase = false;
    private int cachedDocLength = -1;
    private int[] cachedMatchStarts = new int[0];

    private Object currentHighlightTag;

    private FindBar() {
      super(new BorderLayout(10, 0));
      setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

      JLabel findLabel = new JLabel("Find:");

      // Left side: label + field + options + status
      JPanel left = new JPanel();
      left.setOpaque(false);
      left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
      left.add(findLabel);
      left.add(Box.createHorizontalStrut(8));
      left.add(field);
      left.add(Box.createHorizontalStrut(8));
      matchCase.setToolTipText("Match case");
      left.add(matchCase);
      left.add(Box.createHorizontalStrut(10));
      left.add(status);

      // Right side: navigation buttons
      JPanel right = new JPanel();
      right.setOpaque(false);
      right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
      right.add(prev);
      right.add(Box.createHorizontalStrut(6));
      right.add(next);
      right.add(Box.createHorizontalStrut(8));
      close.setMargin(new java.awt.Insets(2, 10, 2, 10));
      right.add(close);

      add(left, BorderLayout.CENTER);
      add(right, BorderLayout.EAST);

      // Wire actions
      prev.addActionListener(e -> find(false));
      next.addActionListener(e -> find(true));
      close.addActionListener(e -> close());

      // Enter = next, Shift+Enter = prev, Esc = close
      field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "findNext");
      field.getActionMap().put("findNext", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) {
          find(true);
        }
      });

      field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "findPrev");
      field.getActionMap().put("findPrev", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) {
          find(false);
        }
      });

      field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "findClose");
      field.getActionMap().put("findClose", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) {
          close();
        }
      });

      // Changing query/options invalidates cache; we only scan on user action (Next/Prev/Enter).
      field.getDocument().addDocumentListener(new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent e) { invalidateCache(); }
        @Override public void removeUpdate(DocumentEvent e) { invalidateCache(); }
        @Override public void changedUpdate(DocumentEvent e) { invalidateCache(); }
      });
      matchCase.addActionListener(e -> invalidateCache());

      // Subtle status styling if a theme provides one
      try {
        java.awt.Color fg = UIManager.getColor("Label.disabledForeground");
        if (fg != null) status.setForeground(fg);
      } catch (Exception ignored) {
      }
    }

    void open(Component focusOwner) {
      this.restoreFocusTo = focusOwner;

      if (!isVisible()) {
        setVisible(true);
        revalidate();
        repaint();
      }

      // Helpful default: if the user selected a token in the transcript, seed the find box.
      String sel = chat.getSelectedText();
      if (sel != null) {
        sel = sel.trim();
        if (!sel.isBlank() && !sel.contains("\n") && !sel.contains("\r")) {
          field.setText(sel);
          invalidateCache();
        }
      }

      SwingUtilities.invokeLater(() -> {
        field.requestFocusInWindow();
        field.selectAll();
      });
    }

    void onDocumentSwapped() {
      invalidateCache();
    }

    private void close() {
      setVisible(false);
      status.setText("");
      clearHighlight();

      // Clear selection highlight.
      try {
        int cpos = chat.getCaretPosition();
        chat.select(cpos, cpos);
      } catch (Exception ignored) {
      }

      // Return focus to wherever the user was when they opened find.
      Component c = restoreFocusTo;
      restoreFocusTo = null;
      if (c != null && c.isShowing()) {
        c.requestFocusInWindow();
      } else {
        chat.requestFocusInWindow();
      }
    }

    private void clearHighlight() {
      try {
        if (currentHighlightTag != null) {
          chat.getHighlighter().removeHighlight(currentHighlightTag);
        }
      } catch (Exception ignored) {
      } finally {
        currentHighlightTag = null;
      }
    }

    private void invalidateCache() {
      cachedNeedle = null;
      cachedDocLength = -1;
      cachedMatchStarts = new int[0];
      status.setText("");
    }

    private void ensureCache() {
      String needle = field.getText();
      if (needle == null) needle = "";
      needle = needle.trim();

      boolean mc = matchCase.isSelected();
      int docLen = currentDocument != null ? currentDocument.getLength() : 0;

      if (needle.isEmpty()) {
        cachedNeedle = needle;
        cachedMatchCase = mc;
        cachedDocLength = docLen;
        cachedMatchStarts = new int[0];
        return;
      }

      if (needle.equals(cachedNeedle) && mc == cachedMatchCase && docLen == cachedDocLength) {
        return;
      }

      cachedNeedle = needle;
      cachedMatchCase = mc;
      cachedDocLength = docLen;

      try {
        StyledDocument doc = currentDocument;
        if (doc == null) {
          cachedMatchStarts = new int[0];
          return;
        }

        String text = doc.getText(0, doc.getLength());
        String hay = mc ? text : text.toLowerCase();
        String ned = mc ? needle : needle.toLowerCase();

        int n = ned.length();
        int at = 0;
        int[] tmp = new int[32];
        int size = 0;
        while (at >= 0) {
          at = hay.indexOf(ned, at);
          if (at < 0) break;
          if (size == tmp.length) {
            tmp = Arrays.copyOf(tmp, tmp.length * 2);
          }
          tmp[size++] = at;
          at = at + Math.max(1, n);
        }
        cachedMatchStarts = Arrays.copyOf(tmp, size);
      } catch (Exception ignored) {
        cachedMatchStarts = new int[0];
      }
    }

    private void find(boolean forward) {
      ensureCache();
      String needle = cachedNeedle;
      if (needle == null || needle.isBlank()) {
        status.setText("Type to search…");
        return;
      }

      int[] starts = cachedMatchStarts;
      if (starts.length == 0) {
        status.setText("No matches");
        java.awt.Toolkit.getDefaultToolkit().beep();
        return;
      }

      int n = needle.length();
      int startPos;
      if (forward) {
        startPos = Math.max(chat.getSelectionEnd(), chat.getCaretPosition());
        int idx = Arrays.binarySearch(starts, startPos);
        if (idx < 0) idx = -idx - 1;
        if (idx >= starts.length) idx = 0; // wrap
        selectMatch(starts[idx], n);
        status.setText((idx + 1) + "/" + starts.length);
      } else {
        startPos = Math.min(chat.getSelectionStart(), chat.getCaretPosition()) - 1;
        if (startPos < 0) startPos = -1;
        int idx = Arrays.binarySearch(starts, startPos);
        if (idx < 0) idx = -idx - 2; // last <= startPos
        if (idx < 0) idx = starts.length - 1; // wrap
        selectMatch(starts[idx], n);
        status.setText((idx + 1) + "/" + starts.length);
      }
    }

    private void selectMatch(int start, int len) {
      try {
        int docLen = currentDocument != null ? currentDocument.getLength() : 0;
        int end = Math.min(start + len, docLen);

        clearHighlight();

        // Select the match in the transcript.
        chat.setCaretPosition(start);
        chat.moveCaretPosition(end);

        // Ensure selection remains visible even while focus stays in the find field.
        try {
          if (chat.getCaret() instanceof DefaultCaret dc) {
            dc.setSelectionVisible(true);
            dc.setVisible(true);
          }
        } catch (Exception ignored) {
        }

        // Add an explicit highlight so the match is obvious even without focus.
        try {
          Highlighter.HighlightPainter painter =
              new DefaultHighlighter.DefaultHighlightPainter(chat.getSelectionColor());
          currentHighlightTag = chat.getHighlighter().addHighlight(start, end, painter);
        } catch (Exception ignored) {
          currentHighlightTag = null;
        }

        // Ensure the selection is visible in the viewport.
        java.awt.Rectangle r = chat.modelToView2D(start).getBounds();
        if (r != null) {
          r.grow(0, 16);
          chat.scrollRectToVisible(r);
        }
      } catch (Exception ignored) {
        
      }
    }
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
