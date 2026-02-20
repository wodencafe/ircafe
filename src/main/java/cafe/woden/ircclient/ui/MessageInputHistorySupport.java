package cafe.woden.ircclient.ui;

import org.fife.ui.autocomplete.AutoCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Handles command-history browsing in the message input.
 *
 * Responsibilities:
 *  - Maintain browsing state (offset, scratch draft, search prefix)
 *  - Install keybindings for history navigation (Up/Down + alternatives)
 *  - Route Up/Down to the completion popup if it is visible
 *
 * This class intentionally does not own non-history shortcuts (e.g. global filter toggles).
 */
final class MessageInputHistorySupport {

  private static final Logger log = LoggerFactory.getLogger(MessageInputHistorySupport.class);

  static final class MenuState {
    final boolean menuEnabled;
    final boolean canPrev;
    final boolean canNext;
    final boolean canClear;

    MenuState(boolean menuEnabled, boolean canPrev, boolean canNext, boolean canClear) {
      this.menuEnabled = menuEnabled;
      this.canPrev = canPrev;
      this.canNext = canNext;
      this.canClear = canClear;
    }
  }

  private final JTextField input;
  private final CommandHistoryStore historyStore;
  private final AutoCompletion autoCompletion;
  private final MessageInputUndoSupport undoSupport;
  private final MessageInputUiHooks hooks;

  private InputMap historyInputMap;

  private int historyOffset = -1;
  private String historyScratch = null;
  private String historySearchPrefix = null;

  MessageInputHistorySupport(
      JTextField input,
      CommandHistoryStore historyStore,
      AutoCompletion autoCompletion,
      MessageInputUndoSupport undoSupport,
      MessageInputUiHooks hooks
  ) {
    this.input = input;
    this.historyStore = historyStore;
    this.autoCompletion = autoCompletion;
    this.undoSupport = undoSupport;
    this.hooks = hooks;
  }

  void installKeybindings() {
    // Use an overlay InputMap so Up/Down can always flow through our history handlers.
    // When the completion popup is open, those handlers route the keys into the popup.
    InputMap base = input.getInputMap(JComponent.WHEN_FOCUSED);
    historyInputMap = new InputMap();
    historyInputMap.setParent(base);
    input.setInputMap(JComponent.WHEN_FOCUSED, historyInputMap);

    InputMap im = historyInputMap;
    ActionMap am = input.getActionMap();

    am.put("ircafe.historyPrev", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) {
        browsePrev();
      }
    });
    am.put("ircafe.historyNext", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) {
        browseNext();
      }
    });
    am.put("ircafe.historyCancel", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) {
        if (historyOffset < 0) {
          Toolkit.getDefaultToolkit().beep();
          return;
        }
        exitBrowse(true);
      }
    });
    am.put("ircafe.clearInput", new AbstractAction() {
      @Override public void actionPerformed(ActionEvent e) {
        if (!input.isEnabled() || !input.isEditable()) return;
        // Exit history-browse mode first so draft tracking stays sane.
        clearBrowseState();
        input.setText("");
      }
    });

    // Primary: Up/Down (classic IRC client behavior).
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "ircafe.historyPrev");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "ircafe.historyNext");

    // Shell-style alternatives that don't collide with common app shortcuts (Cmd+P is Print on macOS).
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK), "ircafe.historyPrev");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK), "ircafe.historyNext");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.ALT_DOWN_MASK), "ircafe.historyPrev");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.ALT_DOWN_MASK), "ircafe.historyNext");

    // Cancel history browse / restore draft. (ESC is handled conditionally by MessageInputPanel.)
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_G, KeyEvent.CTRL_DOWN_MASK), "ircafe.historyCancel");

    // Clear the current input line (undoable).
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), "ircafe.clearInput");
  }

  boolean isBrowsing() {
    return historyOffset >= 0;
  }

  String getDraftText() {
    if (historyOffset >= 0) {
      return historyScratch == null ? "" : historyScratch;
    }
    return input.getText();
  }

  void setDraftText(String text) {
    clearBrowseState();
    applyTextProgrammatically(text);
  }

  void addToHistory(String msg) {
    if (historyStore == null) return;
    historyStore.add(msg);
  }

  void clearHistory() {
    if (historyStore == null) return;
    historyStore.clear();
    clearBrowseState();
  }

  MenuState menuState(boolean editable) {
    if (!editable || historyStore == null) {
      return new MenuState(false, false, false, false);
    }
    int size = historyStore.size();
    boolean menuEnabled = size > 0 || historyOffset >= 0;
    boolean canPrev = size > 0 && (historyOffset < 0 || historyOffset < size - 1);
    boolean canNext = historyOffset >= 0;
    boolean canClear = size > 0;
    return new MenuState(menuEnabled, canPrev, canNext, canClear);
  }

  void onUserEdit(boolean programmaticEdit) {
    if (programmaticEdit) return;
    if (historyOffset < 0) return;
    clearBrowseState();
  }

  void browsePrev() {
    if (maybeRouteHistoryKeyToAutocomplete(true)) return;
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
    applyTextProgrammatically(line);
  }

  void browseNext() {
    if (maybeRouteHistoryKeyToAutocomplete(false)) return;
    if (!input.isEnabled() || !input.isEditable()) return;
    if (historyOffset < 0) return;

    List<String> hist = historyStore == null ? List.of() : historyStore.snapshot();
    if (hist.isEmpty()) {
      exitBrowse(true);
      return;
    }

    if (historySearchPrefix != null) {
      int next = findNextHistoryOffset(hist, historyOffset, historySearchPrefix);
      if (next < 0) {
        exitBrowse(true);
        return;
      }
      historyOffset = next;
      String line = hist.get(hist.size() - 1 - historyOffset);
      applyTextProgrammatically(line);
      return;
    }

    if (historyOffset == 0) {
      exitBrowse(true);
      return;
    }

    historyOffset--;
    String line = hist.get(hist.size() - 1 - historyOffset);
    applyTextProgrammatically(line);
  }

  void exitBrowse(boolean restoreScratch) {
    String restore = restoreScratch && historyScratch != null ? historyScratch : "";
    applyTextProgrammatically(restore);
    clearBrowseState();
  }

  void clearBrowseState() {
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

  private void applyTextProgrammatically(String text) {
    if (hooks != null) {
      hooks.runProgrammaticEdit(() -> input.setText(text == null ? "" : text));
    } else {
      input.setText(text == null ? "" : text);
    }

    if (undoSupport != null) {
      undoSupport.discardAllEdits();
    }
    try {
      input.setCaretPosition(input.getDocument().getLength());
    } catch (Exception ex) {
      log.warn("[MessageInputHistorySupport] caret move failed", ex);
    }
    if (hooks != null) {
      try {
        hooks.updateHint();
      } catch (Exception ex) {
        log.warn("[MessageInputHistorySupport] hooks.updateHint failed", ex);
      }
      try {
        hooks.markCompletionUiDirty();
      } catch (Exception ex) {
        log.warn("[MessageInputHistorySupport] hooks.markCompletionUiDirty failed", ex);
      }
    }
  }

  private boolean maybeRouteHistoryKeyToAutocomplete(boolean up) {
    if (autoCompletion == null || !autoCompletion.isPopupVisible()) return false;
    String popupActionKey = up ? "Up" : "Down";
    KeyStroke key = KeyStroke.getKeyStroke(up ? KeyEvent.VK_UP : KeyEvent.VK_DOWN, 0);
    boolean routed = invokeActionByKey(popupActionKey) || invokeParentInputBinding(key);
    if (!routed) {
      Toolkit.getDefaultToolkit().beep();
    }
    return true;
  }

  private boolean invokeActionByKey(String actionKey) {
    if (actionKey == null || actionKey.isBlank()) return false;
    Action a = input.getActionMap().get(actionKey);
    if (a == null || !a.isEnabled()) return false;
    a.actionPerformed(new ActionEvent(
        input,
        ActionEvent.ACTION_PERFORMED,
        actionKey,
        EventQueue.getMostRecentEventTime(),
        0
    ));
    return true;
  }

  private boolean invokeParentInputBinding(KeyStroke keyStroke) {
    if (keyStroke == null || historyInputMap == null) return false;
    InputMap m = historyInputMap.getParent();
    Object binding = null;
    while (m != null) {
      binding = m.get(keyStroke);
      if (binding != null) break;
      m = m.getParent();
    }
    if (binding == null) return false;
    Action a = input.getActionMap().get(binding);
    if (a == null || !a.isEnabled()) return false;
    a.actionPerformed(new ActionEvent(
        input,
        ActionEvent.ACTION_PERFORMED,
        String.valueOf(binding),
        EventQueue.getMostRecentEventTime(),
        0
    ));
    return true;
  }
}
