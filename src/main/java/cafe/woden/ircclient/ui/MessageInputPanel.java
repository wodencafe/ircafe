package cafe.woden.ircclient.ui;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.AutoCompletionEvent;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import javax.swing.*;
import java.lang.reflect.Field;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.AbstractDocument;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
public class MessageInputPanel extends JPanel {
  public static final String ID = "input";
  private final JTextField input = new JTextField();
  private final JButton send = new JButton("Send");
  private final JLabel completionHint = new JLabel();
  private final UndoManager undo = new UndoManager();
  private static final int UNDO_GROUP_WINDOW_MS = 800;
  private final Timer undoGroupTimer = new Timer(UNDO_GROUP_WINDOW_MS, e -> endCompoundEdit());
  private CompoundEdit activeCompoundEdit;
  private DocumentEvent.EventType activeCompoundType;
  private long lastUndoEditAtMs;
  private boolean programmaticEdit;
  private final Action undoAction = new AbstractAction("Undo") {
    @Override public void actionPerformed(ActionEvent e) {
      try {
        endCompoundEdit();
        if (undo.canUndo()) undo.undo();
      } catch (CannotUndoException ignored) {
      }
      updateUndoRedoActions();
    }
  };
  private final Action redoAction = new AbstractAction("Redo") {
    @Override public void actionPerformed(ActionEvent e) {
      try {
        endCompoundEdit();
        if (undo.canRedo()) undo.redo();
      } catch (CannotRedoException ignored) {
      }
      updateUndoRedoActions();
    }
  };
  /**
   * Completion provider for nick auto-complete.
   *
   * NOTE: DefaultCompletionProvider sorts its list on every addCompletion(), which becomes
   * catastrophically slow when we rebuild completions for large channels.
   */
  private final FastCompletionProvider completionProvider = new FastCompletionProvider();
  private final AutoCompletion autoCompletion = new AutoCompletion(completionProvider);
  private volatile List<String> nickSnapshot = List.of();
  private final FlowableProcessor<String> outbound = PublishProcessor.<String>create().toSerialized();
  private volatile Runnable onActivated = () -> {};
  private volatile Consumer<String> onDraftChanged = t -> {};
  private final UiSettingsBus settingsBus;
  private final CommandHistoryStore historyStore;
  private int historyOffset = -1;
  private String historyScratch = null;
  private String historySearchPrefix = null;
  private InputMap historyInputMap;
  private final PropertyChangeListener settingsListener = this::onSettingsChanged;
  public MessageInputPanel(UiSettingsBus settingsBus, CommandHistoryStore historyStore) {
    super(new BorderLayout(8, 0));
    this.settingsBus = settingsBus;
    this.historyStore = historyStore;
    undoGroupTimer.setRepeats(false);
    setPreferredSize(new Dimension(10, 34));
    completionHint.setText(" ");
    completionHint.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
    completionHint.setHorizontalAlignment(SwingConstants.RIGHT);
    completionHint.setToolTipText("Press Tab for nick completion");
    JPanel right = new JPanel(new BorderLayout(6, 0));
    right.setOpaque(false);
    right.add(completionHint, BorderLayout.CENTER);
    right.add(send, BorderLayout.EAST);
    add(input, BorderLayout.CENTER);
    add(right, BorderLayout.EAST);
    installInputContextMenu();
    input.setFocusTraversalKeysEnabled(false);
    installUndoRedoKeybindings();
    autoCompletion.setAutoActivationEnabled(false);
    autoCompletion.setParameterAssistanceEnabled(false);
    autoCompletion.setShowDescWindow(false);
    autoCompletion.setAutoCompleteSingleChoices(true);
    autoCompletion.setTriggerKey(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
    autoCompletion.install(input);

    // Completion popup should keep its expected Up/Down navigation.
    // We temporarily un-bind history arrow keys while the popup is visible.
    autoCompletion.addAutoCompletionListener(e -> {
      if (e.getEventType() == AutoCompletionEvent.Type.POPUP_SHOWN) {
        setHistoryArrowKeysEnabled(false);
      } else if (e.getEventType() == AutoCompletionEvent.Type.POPUP_HIDDEN) {
        setHistoryArrowKeysEnabled(true);
      }
    });

    installHistoryKeybindings();
    input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
      @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateHint(); fireDraftChanged(); maybeExitHistoryBrowseOnUserEdit(); }
      @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateHint(); fireDraftChanged(); maybeExitHistoryBrowseOnUserEdit(); }
      @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateHint(); fireDraftChanged(); maybeExitHistoryBrowseOnUserEdit(); }
    });

    input.getDocument().addUndoableEditListener(e -> {
      if (programmaticEdit) return;
      handleUndoableEdit(e.getEdit());
    });

    input.addCaretListener(e -> updateHint());
    applySettings(settingsBus.get());
    input.addActionListener(e -> emit());
    send.addActionListener(e -> emit());

    // Mark this input surface as "active" when the user interacts with it.
    FocusAdapter focusAdapter = new FocusAdapter() {
      @Override public void focusGained(FocusEvent e) { fireActivated(); }
      @Override public void focusLost(FocusEvent e) { endCompoundEdit(); }
    };
    input.addFocusListener(focusAdapter);
    send.addFocusListener(focusAdapter);

    MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override public void mousePressed(MouseEvent e) { endCompoundEdit(); fireActivated(); }
    };
    input.addMouseListener(mouseAdapter);
    send.addMouseListener(mouseAdapter);
    addMouseListener(mouseAdapter);

    // UX polish: ESC cancels history browse and restores the original draft.
    // We keep this conditional so ESC can continue to dismiss completion popups when not browsing history.
    input.addKeyListener(new java.awt.event.KeyAdapter() {
      @Override public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        // End the current undo group when the user moves the caret around.
        if (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_HOME || code == KeyEvent.VK_END) {
          endCompoundEdit();
          return;
        }
        if (code != KeyEvent.VK_ESCAPE) return;
        if (historyOffset < 0) return;
        exitHistoryBrowse(true);
        e.consume();
      }
    });
  }

  private void handleUndoableEdit(UndoableEdit edit) {
    if (edit == null) return;

    DocumentEvent.EventType type = null;
    int len = 1;
    int offset = -1;
    if (edit instanceof AbstractDocument.DefaultDocumentEvent dde) {
      type = dde.getType();
      len = Math.max(0, dde.getLength());
      offset = dde.getOffset();
    }

    long now = System.currentTimeMillis();

    // Only group small insert/remove operations. Everything else is treated as its own step.
    boolean groupable = (type == DocumentEvent.EventType.INSERT || type == DocumentEvent.EventType.REMOVE) && len == 1;
    if (!groupable) {
      endCompoundEdit();
      undo.addEdit(edit);
      updateUndoRedoActions();
      return;
    }

    boolean expired = activeCompoundEdit != null && (now - lastUndoEditAtMs) > UNDO_GROUP_WINDOW_MS;
    boolean typeChanged = activeCompoundEdit != null && !java.util.Objects.equals(activeCompoundType, type);
    if (expired || typeChanged) {
      endCompoundEdit();
    }

    if (activeCompoundEdit == null) {
      CompoundEdit ce = new CompoundEdit();
      ce.addEdit(edit);
      // Add the compound as the undo step immediately so redo gets cleared right away.
      boolean accepted = undo.addEdit(ce);
      if (!accepted) {
        // Fallback: no grouping for this edit.
        undo.addEdit(edit);
        updateUndoRedoActions();
        return;
      }
      activeCompoundEdit = ce;
      activeCompoundType = type;
    } else {
      activeCompoundEdit.addEdit(edit);
    }

    lastUndoEditAtMs = now;
    undoGroupTimer.restart();
    updateUndoRedoActions();

    // If the user typed whitespace, end the group so the next word becomes a new undo step.
    if (type == DocumentEvent.EventType.INSERT && offset >= 0) {
      try {
        String inserted = input.getDocument().getText(offset, len);
        if (inserted != null && inserted.chars().anyMatch(Character::isWhitespace)) {
          endCompoundEdit();
        }
      } catch (Exception ignored) {
      }
    }
  }

  private void endCompoundEdit() {
    undoGroupTimer.stop();
    if (activeCompoundEdit != null) {
      try {
        activeCompoundEdit.end();
      } catch (Exception ignored) {
      }
      activeCompoundEdit = null;
      activeCompoundType = null;
      lastUndoEditAtMs = 0;
    }
    // Ending a compound edit is what makes it undoable. Make sure UI state tracks that immediately
    // (otherwise Ctrl+Z can appear "dead" until the next document event).
    updateUndoRedoActions();
  }

  private void discardAllUndoEdits() {
    undoGroupTimer.stop();
    if (activeCompoundEdit != null) {
      try {
        activeCompoundEdit.end();
      } catch (Exception ignored) {
      }
      activeCompoundEdit = null;
      activeCompoundType = null;
      lastUndoEditAtMs = 0;
    }
    undo.discardAllEdits();
    updateUndoRedoActions();
  }

  private void installUndoRedoKeybindings() {
    int menuMask;
    try {
      menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    } catch (Exception e) {
      menuMask = KeyEvent.CTRL_DOWN_MASK;
    }
    InputMap im = input.getInputMap(JComponent.WHEN_FOCUSED);
    ActionMap am = input.getActionMap();
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask), "ircafe.undo");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuMask), "ircafe.redo");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | KeyEvent.SHIFT_DOWN_MASK), "ircafe.redo");
    am.put("ircafe.undo", undoAction);
    am.put("ircafe.redo", redoAction);
    updateUndoRedoActions();
  }

  private void installHistoryKeybindings() {
    // Use an overlay InputMap so we can temporarily disable Up/Down history keys
    // and fall back to whatever bindings the auto-completion popup installed.
    InputMap base = input.getInputMap(JComponent.WHEN_FOCUSED);
    historyInputMap = new InputMap();
    historyInputMap.setParent(base);
    input.setInputMap(JComponent.WHEN_FOCUSED, historyInputMap);

    InputMap im = historyInputMap;
    ActionMap am = input.getActionMap();

    am.put("ircafe.historyPrev", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) {
        browseHistoryPrev();
      }
    });
    am.put("ircafe.historyNext", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) {
        browseHistoryNext();
      }
    });
    am.put("ircafe.historyCancel", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) {
        if (historyOffset < 0) {
          Toolkit.getDefaultToolkit().beep();
          return;
        }
        exitHistoryBrowse(true);
      }
    });
    am.put("ircafe.clearInput", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) {
        if (!input.isEnabled() || !input.isEditable()) return;
        // Exit history-browse mode first so draft tracking stays sane.
        historyOffset = -1;
        historyScratch = null;
        historySearchPrefix = null;
        input.setText("");
      }
    });

    // Primary: Up/Down (classic IRC client behavior).
    setHistoryArrowKeysEnabled(true);

    // Shell-style alternatives that don't collide with common app shortcuts (Cmd+P is Print on macOS).
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK), "ircafe.historyPrev");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK), "ircafe.historyNext");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.ALT_DOWN_MASK), "ircafe.historyPrev");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.ALT_DOWN_MASK), "ircafe.historyNext");

    // Cancel history browse / restore draft. (ESC is handled conditionally via KeyListener.)
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_G, KeyEvent.CTRL_DOWN_MASK), "ircafe.historyCancel");

    // Clear the current input line (undoable).
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), "ircafe.clearInput");

// WeeChat-style filter toggles:
//  - Alt+= toggles global filtering (/filter toggle)
//  - Alt+- toggles filtering for current buffer (/filter toggle @)
am.put("ircafe.filterToggleGlobal", new AbstractAction() {
  @Override public void actionPerformed(ActionEvent e) {
    outbound.onNext("/filter toggle");
  }
});
am.put("ircafe.filterToggleBuffer", new AbstractAction() {
  @Override public void actionPerformed(ActionEvent e) {
    outbound.onNext("/filter toggle @");
  }
});

// On US keyboards, the "+=" key is VK_EQUALS (with Shift producing '+').
im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.ALT_DOWN_MASK), "ircafe.filterToggleGlobal");
im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.ALT_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "ircafe.filterToggleGlobal");
im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.ALT_DOWN_MASK), "ircafe.filterToggleBuffer");

  }

  private void setHistoryArrowKeysEnabled(boolean enabled) {
    if (historyInputMap == null) return;
    KeyStroke up = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0);
    KeyStroke down = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0);
    if (enabled) {
      historyInputMap.put(up, "ircafe.historyPrev");
      historyInputMap.put(down, "ircafe.historyNext");
    } else {
      historyInputMap.remove(up);
      historyInputMap.remove(down);
    }
  }

  private void browseHistoryPrev() {
    if (!input.isEnabled() || !input.isEditable()) return;
    if (historyStore == null) return;

    List<String> hist = historyStore.snapshot();
    if (hist.isEmpty()) {
      Toolkit.getDefaultToolkit().beep();
      return;
    }

    if (historyOffset < 0) {
      historyScratch = input.getText();
      historySearchPrefix = normalizeHistoryPrefix(historyScratch);
      int next = findPrevHistoryOffset(hist, -1, historySearchPrefix);
      if (next < 0) {
        Toolkit.getDefaultToolkit().beep();
        historyScratch = null;
        historySearchPrefix = null;
        return;
      }
      historyOffset = next;
    } else {
      int next = findPrevHistoryOffset(hist, historyOffset, historySearchPrefix);
      if (next < 0) {
        Toolkit.getDefaultToolkit().beep();
        return;
      }
      historyOffset = next;
    }

    String line = hist.get(hist.size() - 1 - historyOffset);
    setTextFromHistory(line);
  }

  private void browseHistoryNext() {
    if (!input.isEnabled() || !input.isEditable()) return;
    if (historyOffset < 0) return;

    List<String> hist = historyStore == null ? List.of() : historyStore.snapshot();
    if (hist.isEmpty()) {
      exitHistoryBrowse(true);
      return;
    }

    if (historySearchPrefix != null) {
      int next = findNextHistoryOffset(hist, historyOffset, historySearchPrefix);
      if (next < 0) {
        exitHistoryBrowse(true);
        return;
      }
      historyOffset = next;
      String line = hist.get(hist.size() - 1 - historyOffset);
      setTextFromHistory(line);
      return;
    }

    if (historyOffset == 0) {
      exitHistoryBrowse(true);
      return;
    }

    historyOffset--;
    String line = hist.get(hist.size() - 1 - historyOffset);
    setTextFromHistory(line);
  }

  private void exitHistoryBrowse(boolean restoreScratch) {
    String restore = restoreScratch && historyScratch != null ? historyScratch : "";
    setTextFromHistory(restore);
    historyOffset = -1;
    historyScratch = null;
    historySearchPrefix = null;
  }

  private static String normalizeHistoryPrefix(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private static int findPrevHistoryOffset(List<String> hist, int currentOffset, String prefix) {
    int start = currentOffset + 1;
    for (int off = start; off < hist.size(); off++) {
      String line = hist.get(hist.size() - 1 - off);
      if (matchesHistoryPrefix(line, prefix)) return off;
    }
    return -1;
  }

  private static int findNextHistoryOffset(List<String> hist, int currentOffset, String prefix) {
    for (int off = currentOffset - 1; off >= 0; off--) {
      String line = hist.get(hist.size() - 1 - off);
      if (matchesHistoryPrefix(line, prefix)) return off;
    }
    return -1;
  }

  private static boolean matchesHistoryPrefix(String line, String prefix) {
    if (prefix == null) return true;
    if (line == null) return false;
    return line.regionMatches(true, 0, prefix, 0, prefix.length());
  }

  private void setTextFromHistory(String text) {
    programmaticEdit = true;
    try {
      input.setText(text == null ? "" : text);
    } finally {
      programmaticEdit = false;
    }
    discardAllUndoEdits();
    try {
      input.setCaretPosition(input.getDocument().getLength());
    } catch (Exception ignored) {
    }
    updateHint();
  }

  private void maybeExitHistoryBrowseOnUserEdit() {
    if (programmaticEdit) return;
    if (historyOffset < 0) return;
    historyOffset = -1;
    historyScratch = null;
    historySearchPrefix = null;
  }

  private void updateUndoRedoActions() {
    try {
      // If we're mid-group, UndoManager can't undo yet because CompoundEdit is "inProgress".
      // But users still expect Ctrl+Z to work immediately; the action will finalize the group first.
      undoAction.setEnabled(undo.canUndo() || activeCompoundEdit != null);
      redoAction.setEnabled(undo.canRedo());
    } catch (Exception ignored) {
    }
  }
  private void installInputContextMenu() {
    final JPopupMenu menu = new JPopupMenu();
    final JMenuItem undoItem = new JMenuItem(undoAction);
    final JMenuItem redoItem = new JMenuItem(redoAction);
    // Best-effort: show shortcut hints (some LAFs render these in popups, others don't).
    try {
      int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
      undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask));
      redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | KeyEvent.SHIFT_DOWN_MASK));
    } catch (Exception ignored) {
    }
    final JMenuItem cutItem = new JMenuItem("Cut");
    cutItem.addActionListener(e -> input.cut());
    final JMenuItem copyItem = new JMenuItem("Copy");
    copyItem.addActionListener(e -> input.copy());
    final JMenuItem pasteItem = new JMenuItem("Paste");
    pasteItem.addActionListener(e -> input.paste());
    final JMenuItem deleteItem = new JMenuItem("Delete");
    deleteItem.addActionListener(e -> {
      if (!input.isEditable() || !input.isEnabled()) return;
      int start = Math.min(input.getSelectionStart(), input.getSelectionEnd());
      int end = Math.max(input.getSelectionStart(), input.getSelectionEnd());
      try {
        if (start != end) {
          input.getDocument().remove(start, end - start);
          input.setCaretPosition(start);
        } else {
          int caret = input.getCaretPosition();
          int len = input.getDocument().getLength();
          if (caret < len) {
            input.getDocument().remove(caret, 1);
          } else {
            Toolkit.getDefaultToolkit().beep();
          }
        }
      } catch (BadLocationException ex) {
        Toolkit.getDefaultToolkit().beep();
      }
    });
    final JMenuItem clearItem = new JMenuItem("Clear");
    clearItem.addActionListener(e -> {
      if (!input.isEditable() || !input.isEnabled()) return;
      if (input.getDocument().getLength() == 0) return;
      input.setText("");
    });
    clearItem.setToolTipText("Ctrl+L");
    try {
      clearItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK));
    } catch (Exception ignored) {
    }
    final JMenuItem selectAllItem = new JMenuItem("Select All");
    selectAllItem.addActionListener(e -> {
      if (!input.isEnabled()) return;
      input.selectAll();
    });

    final Action historyPrevAction = new AbstractAction("Previous Command") {
      @Override public void actionPerformed(ActionEvent e) {
        browseHistoryPrev();
      }
    };
    final Action historyNextAction = new AbstractAction("Next Command") {
      @Override public void actionPerformed(ActionEvent e) {
        browseHistoryNext();
      }
    };
    final Action historyClearAction = new AbstractAction("Clear Command History") {
      @Override public void actionPerformed(ActionEvent e) {
        if (historyStore == null) return;
        historyStore.clear();
        historyOffset = -1;
        historyScratch = null;
        historySearchPrefix = null;
      }
    };

    final JMenuItem historyPrevItem = new JMenuItem(historyPrevAction);
    final JMenuItem historyNextItem = new JMenuItem(historyNextAction);
    final JMenuItem historyClearItem = new JMenuItem(historyClearAction);
    try {
      historyPrevItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
      historyNextItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
    } catch (Exception ignored) {
    }
    historyPrevItem.setToolTipText("Up / Ctrl+P / Alt+Up");
    historyNextItem.setToolTipText("Down / Ctrl+N / Alt+Down");
    historyClearItem.setToolTipText("Clear stored commands (memory only)");
    final JMenu historyMenu = new JMenu("History");
    historyMenu.add(historyPrevItem);
    historyMenu.add(historyNextItem);
    historyMenu.addSeparator();
    historyMenu.add(historyClearItem);
    menu.add(undoItem);
    menu.add(redoItem);
    menu.addSeparator();
    menu.add(cutItem);
    menu.add(copyItem);
    menu.add(pasteItem);
    menu.addSeparator();
    menu.add(deleteItem);
    menu.add(clearItem);
    menu.addSeparator();
    menu.add(selectAllItem);
    menu.addSeparator();
    menu.add(historyMenu);
    final Runnable refreshEnabledStates = () -> {
      updateUndoRedoActions();
      boolean enabled = input.isEnabled();
      boolean editable = enabled && input.isEditable();
      int start = Math.min(input.getSelectionStart(), input.getSelectionEnd());
      int end = Math.max(input.getSelectionStart(), input.getSelectionEnd());
      boolean hasSelection = start != end;
      int len = input.getDocument().getLength();
      int caret = input.getCaretPosition();
      cutItem.setEnabled(editable && hasSelection);
      copyItem.setEnabled(enabled && hasSelection);
      pasteItem.setEnabled(editable && clipboardHasText());
      deleteItem.setEnabled(editable && (hasSelection || caret < len));
      clearItem.setEnabled(editable && len > 0);
      selectAllItem.setEnabled(enabled && len > 0 && !(start == 0 && end == len));

      // History navigation
      boolean historyMenuEnabled = false;
      boolean canHistoryPrev = false;
      boolean canHistoryNext = false;
      boolean canHistoryClear = false;
      if (editable && historyStore != null) {
        List<String> hist = historyStore.snapshot();
        int size = hist.size();
        historyMenuEnabled = size > 0 || historyOffset >= 0;
        canHistoryPrev = size > 0 && (historyOffset < 0 || historyOffset < size - 1);
        canHistoryNext = historyOffset >= 0;
        canHistoryClear = size > 0;
      }
      historyMenu.setEnabled(historyMenuEnabled);
      historyPrevAction.setEnabled(canHistoryPrev);
      historyNextAction.setEnabled(canHistoryNext);
      historyClearAction.setEnabled(canHistoryClear);
    };
    menu.addPopupMenuListener(new PopupMenuListener() {
      @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) { refreshEnabledStates.run(); }
      @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { }
      @Override public void popupMenuCanceled(PopupMenuEvent e) { }
    });
    // Use an explicit listener so enablement refreshes before showing.
    input.addMouseListener(new MouseAdapter() {
      private void maybeShow(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        if (!input.isShowing()) return;
        if (!input.hasFocus()) input.requestFocusInWindow();
        try {
          int pos = input.viewToModel2D(e.getPoint());
          if (pos >= 0) input.setCaretPosition(pos);
        } catch (Exception ignored) {
        }
        refreshEnabledStates.run();
        SwingUtilities.updateComponentTreeUI(menu);
        menu.show(e.getComponent(), e.getX(), e.getY());
      }
      @Override public void mousePressed(MouseEvent e) { maybeShow(e); }
      @Override public void mouseReleased(MouseEvent e) { maybeShow(e); }
    });
  }
  private boolean clipboardHasText() {
    try {
      Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
      if (cb == null) return false;
      Transferable t = cb.getContents(null);
      return t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor);
    } catch (Exception ignored) {
      return false;
    }
  }
  @Override
  public void addNotify() {
    super.addNotify();
    if (settingsBus != null) settingsBus.addListener(settingsListener);
  }
  @Override
  public void removeNotify() {
    if (settingsBus != null) settingsBus.removeListener(settingsListener);
    super.removeNotify();
  }
  private void onSettingsChanged(PropertyChangeEvent evt) {
    if (!UiSettingsBus.PROP_UI_SETTINGS.equals(evt.getPropertyName())) return;
    if (evt.getNewValue() instanceof UiSettings s) {
      applySettings(s);
    }
  }
  private void applySettings(UiSettings s) {
    if (s == null) return;
    try {
      Font f = new Font(s.chatFontFamily(), Font.PLAIN, s.chatFontSize());
      input.setFont(f);
      send.setFont(f);
      completionHint.setFont(f.deriveFont(Math.max(10f, f.getSize2D() - 2f)));
    } catch (Exception ignored) {
    }
  }

  /**
   * Fast-ish completion provider that supports bulk replace.
   *
   * The upstream provider sorts on every insertion; we instead replace the internal
   * list in one shot and sort once.
   */
  private static final class FastCompletionProvider extends DefaultCompletionProvider {
    private static final Field COMPLETIONS_FIELD = findCompletionsField();

    private static Field findCompletionsField() {
      // Prefer the historical field name first.
      Class<?> c = DefaultCompletionProvider.class;
      while (c != null && c != Object.class) {
        try {
          Field f = c.getDeclaredField("completions");
          f.setAccessible(true);
          return f;
        } catch (NoSuchFieldException ignored) {
          c = c.getSuperclass();
        } catch (Throwable t) {
          // InaccessibleObjectException or similar.
          return null;
        }
      }

      // If the field name changes, fall back to a heuristic: first List field whose name
      // contains "completion" (case-insensitive).
      c = DefaultCompletionProvider.class;
      while (c != null && c != Object.class) {
        try {
          for (Field f : c.getDeclaredFields()) {
            if (!java.util.List.class.isAssignableFrom(f.getType())) continue;
            String n = f.getName();
            if (n == null) continue;
            String lower = n.toLowerCase(java.util.Locale.ROOT);
            if (!lower.contains("completion")) continue;
            f.setAccessible(true);
            return f;
          }
          c = c.getSuperclass();
        } catch (Throwable t) {
          return null;
        }
      }
      return null;
    }

    @SuppressWarnings("unchecked")
    void replaceCompletions(List<? extends Completion> replacements) {
      // Best-effort: if we cannot access the field, fall back to slow path.
      if (COMPLETIONS_FIELD == null) {
        clear();
        if (replacements != null) {
          for (Completion c : replacements) {
            if (c != null) addCompletion(c);
          }
        }
        return;
      }

      try {
        Object v = COMPLETIONS_FIELD.get(this);
        if (v instanceof List<?> raw) {
          List<Completion> list = (List<Completion>) raw;
          list.clear();
          if (replacements != null && !replacements.isEmpty()) {
            list.addAll((List<? extends Completion>) replacements);
            // Sort once (Completion is Comparable).
            Collections.sort(list);
          }
        } else {
          // Unexpected shape; fall back.
          clear();
          if (replacements != null) {
            for (Completion c : replacements) {
              if (c != null) addCompletion(c);
            }
          }
        }
      } catch (Throwable t) {
        // Reflection failed; fall back.
        clear();
        if (replacements != null) {
          for (Completion c : replacements) {
            if (c != null) addCompletion(c);
          }
        }
      }
    }
  }

  public void setNickCompletions(List<String> nicks) {
    List<String> cleaned = cleanNickList(nicks);
    nickSnapshot = cleaned;

    // Build completions once and install in one shot; avoids O(n^2) sort churn.
    if (cleaned.isEmpty()) {
      completionProvider.replaceCompletions(List.of());
      updateHint();
      return;
    }
    ArrayList<Completion> completions = new ArrayList<>(cleaned.size());
    for (String nick : cleaned) {
      completions.add(new BasicCompletion(completionProvider, nick, "IRC nick"));
    }
    completionProvider.replaceCompletions(completions);
    updateHint();
  }
  private static List<String> cleanNickList(List<String> nicks) {
    if (nicks == null || nicks.isEmpty()) return List.of();
    ArrayList<String> out = new ArrayList<>(nicks.size());
    for (String n : nicks) {
      if (n == null) continue;
      String s = n.trim();
      if (s.isEmpty()) continue;
      out.add(s);
    }
    out.sort(String.CASE_INSENSITIVE_ORDER);
    ArrayList<String> deduped = new ArrayList<>(out.size());
    String last = null;
    for (String s : out) {
      if (last == null || !s.equalsIgnoreCase(last)) {
        deduped.add(s);
        last = s;
      }
    }
    return Collections.unmodifiableList(deduped);
  }
  private void updateHint() {
    if (!input.isEnabled() || !input.isEditable()) {
      completionHint.setText(" ");
      return;
    }
    String text = input.getText();
    int caret = input.getCaretPosition();
    String token = currentToken(text, caret);
    if (token.isBlank()) {
      completionHint.setText(" ");
      return;
    }
    String match = firstNickStartingWith(token, nickSnapshot);
    if (match == null) {
      completionHint.setText(" ");
    } else {
      completionHint.setText("Tab → " + match);
    }
  }
  private static String currentToken(String text, int caretPos) {
    if (text == null || text.isEmpty()) return "";
    int caret = Math.max(0, Math.min(caretPos, text.length()));
    int start = caret;
    while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
      start--;
    }
    String token = text.substring(start, caret);
    token = stripLeadingPunct(token);
    token = stripTrailingPunct(token);
    return token;
  }
  private static String stripLeadingPunct(String s) {
    if (s == null || s.isEmpty()) return "";
    int i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '[' || c == ']' || c == '\\') {
        break;
      }
      i++;
    }
    return i == 0 ? s : s.substring(i);
  }
  private static String stripTrailingPunct(String s) {
    if (s == null || s.isEmpty()) return "";
    int end = s.length();
    while (end > 0) {
      char c = s.charAt(end - 1);
      if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '[' || c == ']' || c == '\\') {
        break;
      }
      end--;
    }
    return end == s.length() ? s : s.substring(0, end);
  }
  private static String firstNickStartingWith(String token, List<String> nicks) {
    if (token == null || token.isBlank()) return null;
    if (nicks == null || nicks.isEmpty()) return null;
    String t = token.toLowerCase(Locale.ROOT);
    for (String n : nicks) {
      if (n == null) continue;
      if (n.toLowerCase(Locale.ROOT).startsWith(t)) return n;
    }
    return null;
  }
  public Flowable<String> outboundMessages() {
    return outbound.onBackpressureBuffer();
  }
  private void emit() {
    String msg = input.getText().trim();
    if (msg.isEmpty()) return;
    if (historyStore != null) {
      historyStore.add(msg);
    }
    // Leaving history-browse mode before clearing ensures draft persistence isn't polluted.
    historyOffset = -1;
    historyScratch = null;
    historySearchPrefix = null;
    programmaticEdit = true;
    try {
      input.setText("");
    } finally {
      programmaticEdit = false;
    }
    discardAllUndoEdits();
    outbound.onNext(msg);
  }

  /**
   * Called when this input becomes the active typing surface (focus or click).
   */
  public void setOnActivated(Runnable onActivated) {
    this.onActivated = onActivated == null ? () -> {} : onActivated;
  }

  public void setOnDraftChanged(Consumer<String> onDraftChanged) {
    this.onDraftChanged = onDraftChanged == null ? t -> {} : onDraftChanged;
  }

  private void fireDraftChanged() {
    try {
      onDraftChanged.accept(getDraftText());
    } catch (Exception ignored) {
    }
  }

  private void fireActivated() {
    try {
      onActivated.run();
    } catch (Exception ignored) {
    }
  }

  public void setInputEnabled(boolean enabled) {
    input.setEditable(enabled);
    input.setEnabled(enabled);
    send.setEnabled(enabled);
    updateHint();
  }
  public String getDraftText() {
    if (historyOffset >= 0) {
      return historyScratch == null ? "" : historyScratch;
    }
    return input.getText();
  }

  public void setDraftText(String text) {
    historyOffset = -1;
    historyScratch = null;
    historySearchPrefix = null;
    programmaticEdit = true;
    try {
      input.setText(text == null ? "" : text);
    } finally {
      programmaticEdit = false;
    }
    discardAllUndoEdits();
    updateHint();
  }

  public void focusInput() {
    if (!input.isEnabled()) return;
    input.requestFocusInWindow();
    try {
      input.setCaretPosition(input.getDocument().getLength());
    } catch (Exception ignored) {
    }
  }
}
