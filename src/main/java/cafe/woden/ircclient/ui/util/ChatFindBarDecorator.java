package cafe.woden.ircclient.ui.util;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.StyledDocument;

/** Adds an in-panel Find UI (Ctrl+F) to a chat transcript component. */
public final class ChatFindBarDecorator implements AutoCloseable {

  private static final String ACTION_FIND_TOGGLE = "cafe.woden.find.toggle";
  private static final String ACTION_FIND_CLOSE = "cafe.woden.find.close";
  private static final KeyStroke KS_CTRL_F =
      KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
  private static final KeyStroke KS_ESC =
      KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);

  private final JComponent host;
  private final JTextPane transcript;
  private final Supplier<StyledDocument> documentSupplier;
  private final FindBar bar;

  private ChatFindBarDecorator(
      JComponent host,
      JTextPane transcript,
      Supplier<StyledDocument> documentSupplier
  ) {
    this.host = Objects.requireNonNull(host, "host");
    this.transcript = Objects.requireNonNull(transcript, "transcript");
    this.documentSupplier = Objects.requireNonNull(documentSupplier, "documentSupplier");
    this.bar = new FindBar();

    bar.setVisible(false);

    // Host is expected to be BorderLayout (ChatViewPanel), but fall back gracefully.
    try {
      host.add(bar, BorderLayout.NORTH);
    } catch (Exception ignored) {
      host.add(bar);
    }

    installKeybindings();
  }

  public static ChatFindBarDecorator install(
      JComponent host,
      JTextPane transcript,
      Supplier<StyledDocument> documentSupplier
  ) {
    return new ChatFindBarDecorator(host, transcript, documentSupplier);
  }

  public void open() {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    bar.open(focusOwner);
  }

  public void toggle() {
    if (bar.isVisible()) {
      bar.close();
    } else {
      open();
    }
  }

  public void findNext() {
    if (!bar.isVisible()) {
      open();
    }
    bar.find(true);
  }

  public void findPrevious() {
    if (!bar.isVisible()) {
      open();
    }
    bar.find(false);
  }

  public void onDocumentSwapped() {
    bar.onDocumentSwapped();
  }

  private void installKeybindings() {
    JComponent bindingTarget = host;

    bindingTarget.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KS_CTRL_F, ACTION_FIND_TOGGLE);
    bindingTarget.getActionMap().put(ACTION_FIND_TOGGLE, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        toggle();
      }
    });

    // Esc closes find bar if visible (even if transcript has focus).
    bindingTarget.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KS_ESC, ACTION_FIND_CLOSE);
    bindingTarget.getActionMap().put(ACTION_FIND_CLOSE, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (bar.isVisible()) {
          bar.close();
        }
      }
    });
  }

  private void uninstallKeybindings() {
    JComponent bindingTarget = host;
    bindingTarget.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).remove(KS_CTRL_F);
    bindingTarget.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).remove(KS_ESC);
    bindingTarget.getActionMap().remove(ACTION_FIND_TOGGLE);
    bindingTarget.getActionMap().remove(ACTION_FIND_CLOSE);
  }

  @Override
  public void close() {
    try {
      bar.close();
    } catch (Exception ignored) {
    }
    try {
      uninstallKeybindings();
    } catch (Exception ignored) {
    }
    try {
      host.remove(bar);
      host.revalidate();
      host.repaint();
    } catch (Exception ignored) {
    }
  }

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

      // Enter = next, Shift+Enter = prev, Esc = close (field-focused)
      field.getInputMap(JComponent.WHEN_FOCUSED)
          .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "findNext");
      field.getActionMap().put("findNext", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) {
          find(true);
        }
      });

      field.getInputMap(JComponent.WHEN_FOCUSED)
          .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "findPrev");
      field.getActionMap().put("findPrev", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) {
          find(false);
        }
      });

      field.getInputMap(JComponent.WHEN_FOCUSED)
          .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "findClose");
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
        host.revalidate();
        host.repaint();
      }

      // Helpful default: if the user selected a token in the transcript, seed the find box.
      String sel = transcript.getSelectedText();
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
      clearHighlight();
      status.setText("");
    }

    private void close() {
      setVisible(false);
      status.setText("");
      clearHighlight();

      // Clear selection highlight.
      try {
        int cpos = transcript.getCaretPosition();
        transcript.select(cpos, cpos);
      } catch (Exception ignored) {
      }

      // Return focus to wherever the user was when they opened find.
      Component c = restoreFocusTo;
      restoreFocusTo = null;
      if (c != null && c.isShowing()) {
        c.requestFocusInWindow();
      } else {
        transcript.requestFocusInWindow();
      }
    }

    private void clearHighlight() {
      try {
        if (currentHighlightTag != null) {
          transcript.getHighlighter().removeHighlight(currentHighlightTag);
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
      StyledDocument doc = documentSupplier.get();
      int docLen = doc != null ? doc.getLength() : 0;

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
        Toolkit.getDefaultToolkit().beep();
        return;
      }

      int n = needle.length();
      int startPos;
      if (forward) {
        startPos = Math.max(transcript.getSelectionEnd(), transcript.getCaretPosition());
        int idx = Arrays.binarySearch(starts, startPos);
        if (idx < 0) idx = -idx - 1;
        if (idx >= starts.length) idx = 0; // wrap
        selectMatch(starts[idx], n);
        status.setText((idx + 1) + "/" + starts.length);
      } else {
        startPos = Math.min(transcript.getSelectionStart(), transcript.getCaretPosition()) - 1;
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
        StyledDocument doc = documentSupplier.get();
        int docLen = doc != null ? doc.getLength() : 0;
        int end = Math.min(start + len, docLen);

        clearHighlight();

        // Select the match in the transcript.
        transcript.setCaretPosition(start);
        transcript.moveCaretPosition(end);

        // Ensure selection remains visible even while focus stays in the find field.
        try {
          if (transcript.getCaret() instanceof DefaultCaret dc) {
            dc.setSelectionVisible(true);
            dc.setVisible(true);
          }
        } catch (Exception ignored) {
        }

        // Add an explicit highlight so the match is obvious even without focus.
        try {
          Highlighter.HighlightPainter painter =
              new DefaultHighlighter.DefaultHighlightPainter(transcript.getSelectionColor());
          currentHighlightTag = transcript.getHighlighter().addHighlight(start, end, painter);
        } catch (Exception ignored) {
          currentHighlightTag = null;
        }

        // Ensure the selection is visible in the viewport.
        java.awt.Rectangle r = transcript.modelToView2D(start).getBounds();
        if (r != null) {
          r.grow(0, 16);
          transcript.scrollRectToVisible(r);
        }
      } catch (Exception ignored) {
      }
    }
  }
}
