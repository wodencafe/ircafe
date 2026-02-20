package cafe.woden.ircclient.ui;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Handles nick auto-completion in the message input.
 *
 * Responsibilities:
 *  - Own AutoCompletion + its CompletionProvider
 *  - Efficient bulk rebuild of nick completions (avoid O(n^2) sorting)
 *  - IRC-style addressing suffix behavior for first-word completion ("nick: ")
 *  - Best-effort refresh of AutoCompletion popup UI on theme/LAF changes
 */
final class MessageInputNickCompletionSupport {

  private static final Logger log = LoggerFactory.getLogger(MessageInputNickCompletionSupport.class);

  private static final int PENDING_NICK_SUFFIX_TIMEOUT_MS = 5000;

  private final JComponent owner;
  private final JTextField input;
  private final MessageInputUndoSupport undoSupport;

  /**
   * NOTE: DefaultCompletionProvider sorts its list on every addCompletion(), which becomes
   * catastrophically slow when we rebuild completions for large channels.
   */
  private final FastCompletionProvider completionProvider = new FastCompletionProvider();
  private final AutoCompletion autoCompletion = new AutoCompletion(completionProvider);

  // AutoCompletion popups are lazily created and may cache UI defaults; mark dirty on theme changes.
  private volatile boolean autoCompletionUiDirty = true;

  // When TAB shows a multi-choice completion popup, the actual completion text is inserted later
  // (after the user picks an item). We "arm" a one-shot suffix append so the chosen nick becomes
  // "nick: " when it is the first word in the message.
  private volatile boolean pendingNickAddressSuffix = false;
  private volatile String pendingNickAddressBeforeText = "";
  private volatile int pendingNickAddressBeforeCaret = 0;
  private volatile long pendingNickAddressSetAtMs = 0L;

  private volatile List<String> nickSnapshot = List.of();

  private boolean installed;
  private boolean pendingSuffixListenerInstalled;

  private final PropertyChangeListener lafListener = evt -> {
    if (!"lookAndFeel".equals(evt.getPropertyName())) return;
    markUiDirty();
    SwingUtilities.invokeLater(this::refreshAutoCompletionUi);
  };

  MessageInputNickCompletionSupport(JComponent owner, JTextField input, MessageInputUndoSupport undoSupport) {
    this.owner = owner;
    this.input = input;
    this.undoSupport = undoSupport;
  }

  AutoCompletion getAutoCompletion() {
    return autoCompletion;
  }

  void install() {
    if (installed) return;
    installed = true;

    autoCompletion.setAutoActivationEnabled(false);
    autoCompletion.setParameterAssistanceEnabled(false);
    autoCompletion.setShowDescWindow(false);
    autoCompletion.setAutoCompleteSingleChoices(true);
    autoCompletion.setTriggerKey(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
    autoCompletion.install(input);

    installNickCompletionAddressingSuffix();
    installAutoCompletionUiRefreshOnLafChange();
  }

  void onAddNotify() {
    // AutoCompletion popups are created lazily; try to refresh if present, otherwise keep dirty
    // so the first TAB-triggered popup can be refreshed once without flicker.
    markUiDirty();
    SwingUtilities.invokeLater(this::refreshAutoCompletionUi);
  }

  void markUiDirty() {
    autoCompletionUiDirty = true;
  }

  void markUiDirtyAndRefreshAsync() {
    autoCompletionUiDirty = true;
    SwingUtilities.invokeLater(this::refreshAutoCompletionUi);
  }

  String firstNickStartingWith(String token) {
    if (token == null || token.isBlank()) return null;
    List<String> nicks = nickSnapshot;
    if (nicks == null || nicks.isEmpty()) return null;
    String t = token.toLowerCase(Locale.ROOT);
    for (String n : nicks) {
      if (n == null) continue;
      if (n.toLowerCase(Locale.ROOT).startsWith(t)) return n;
    }
    return null;
  }

  void setNickCompletions(List<String> nicks) {
    List<String> cleaned = cleanNickList(nicks);
    nickSnapshot = cleaned;

    // Build completions once and install in one shot; avoids O(n^2) sort churn.
    if (cleaned.isEmpty()) {
      completionProvider.replaceCompletions(List.of());
      markUiDirty();
      return;
    }
    ArrayList<Completion> completions = new ArrayList<>(cleaned.size());
    for (String nick : cleaned) {
      completions.add(new BasicCompletion(completionProvider, nick, "IRC nick"));
    }
    completionProvider.replaceCompletions(completions);
    markUiDirty();
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

  private void installNickCompletionAddressingSuffix() {
    try {
      KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);
      InputMap im = input.getInputMap(JComponent.WHEN_FOCUSED);
      Object key = (im == null) ? null : im.get(ks);
      if (key == null) return;
      ActionMap am = input.getActionMap();
      Action delegate = (am == null) ? null : am.get(key);
      if (delegate == null) return;

      // Wrap the AutoCompletion trigger action so we can apply IRC-style "nick: " addressing
      // when the completion occurs as the first word in the line.
      am.put(key, new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) {
          String beforeText = input.getText();
          int beforeCaret = input.getCaretPosition();
          delegate.actionPerformed(e);

          SwingUtilities.invokeLater(() -> {
            // Refresh the completion popup UI only when needed (first creation or after a theme change).
            // Doing this on every TAB causes visible flicker because it can touch the owner window.
            if (autoCompletionUiDirty) {
              if (refreshAutoCompletionUiIfPresent(false)) {
                autoCompletionUiDirty = false;
              }
            }

            boolean appended = maybeAppendNickAddressSuffix(beforeText, beforeCaret);
            if (!appended) {
              String afterText = input.getText();
              int afterCaret = input.getCaretPosition();
              boolean completionOccurred = !(Objects.equals(afterText, beforeText) && afterCaret == beforeCaret);

              // If TAB only opened a multi-choice completion popup, the completion text is inserted later.
              // Arm a one-shot suffix append so the chosen nick becomes "nick: " when it's the first word.
              pendingNickAddressSuffix = false;
              if (!completionOccurred && isEligibleForNickAddressSuffix(beforeText, beforeCaret)) {
                String prefix = firstWordPrefix(beforeText);
                if (hasNickPrefixMatch(prefix)) {
                  pendingNickAddressSuffix = true;
                  pendingNickAddressBeforeText = beforeText;
                  pendingNickAddressBeforeCaret = beforeCaret;
                  pendingNickAddressSetAtMs = System.currentTimeMillis();
                }
              }
            } else {
              pendingNickAddressSuffix = false;
            }
          });
        }
      });

      installPendingNickAddressSuffixListener();

    } catch (Exception ignored) {
    }
  }

  private void installPendingNickAddressSuffixListener() {
    if (pendingSuffixListenerInstalled) return;
    pendingSuffixListenerInstalled = true;
    try {
      input.getDocument().addDocumentListener(new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent e) { maybeAppendPendingNickSuffixAsync(); }
        @Override public void removeUpdate(DocumentEvent e) { maybeAppendPendingNickSuffixAsync(); }
        @Override public void changedUpdate(DocumentEvent e) { maybeAppendPendingNickSuffixAsync(); }
      });
    } catch (Exception ignored) {
    }
  }

  private void maybeAppendPendingNickSuffixAsync() {
    if (!pendingNickAddressSuffix) return;

    long now = System.currentTimeMillis();
    if (now - pendingNickAddressSetAtMs > PENDING_NICK_SUFFIX_TIMEOUT_MS) {
      pendingNickAddressSuffix = false;
      return;
    }

    String t = input.getText();
    if (t == null) {
      pendingNickAddressSuffix = false;
      return;
    }
    if (t.stripLeading().startsWith("/")) {
      pendingNickAddressSuffix = false;
      return;
    }

    int start = firstNonWhitespace(t);
    if (start < 0) {
      pendingNickAddressSuffix = false;
      return;
    }
    int end = wordEnd(t, start);
    int caret = input.getCaretPosition();

    // If the user has already moved past the first word, stop waiting.
    if (caret > end + 1) {
      pendingNickAddressSuffix = false;
      return;
    }

    SwingUtilities.invokeLater(() -> {
      if (!pendingNickAddressSuffix) return;
      boolean appended = maybeAppendNickAddressSuffix(pendingNickAddressBeforeText, pendingNickAddressBeforeCaret);
      if (appended) {
        pendingNickAddressSuffix = false;
      }
    });
  }

  private boolean maybeAppendNickAddressSuffix(String beforeText, int beforeCaret) {
    try {
      if (beforeText == null) beforeText = "";

      // Only apply when the user was tab-completing inside the first word.
      int startBefore = firstNonWhitespace(beforeText);
      if (startBefore < 0) return false;
      int endBefore = wordEnd(beforeText, startBefore);
      if (beforeCaret > endBefore) return false;

      String afterText = input.getText();
      if (afterText == null) afterText = "";
      int afterCaret = input.getCaretPosition();

      if (afterText.equals(beforeText) && afterCaret == beforeCaret) return false; // no change -> no completion

      // Don't do this for slash-commands.
      String trimmed = afterText.stripLeading();
      if (trimmed.startsWith("/")) return false;

      int start = firstNonWhitespace(afterText);
      if (start < 0) return false;
      int end = wordEnd(afterText, start);
      if (end <= start) return false;

      // Only if caret is at/after the nick we just completed.
      if (afterCaret < end) return false;

      String nick = afterText.substring(start, end);
      if (nick.isBlank()) return false;
      if (!isKnownNick(nick)) return false;

      // If already addressed like "nick:" or "nick,", do nothing.
      if (end < afterText.length()) {
        char ch = afterText.charAt(end);
        if (ch == ':' || ch == ',') return false;
        if (!Character.isWhitespace(ch)) return false; // only add when nick is followed by whitespace
      }

      // Normalize whitespace after the nick into exactly ": ".
      int wsEnd = end;
      while (wsEnd < afterText.length() && Character.isWhitespace(afterText.charAt(wsEnd))) wsEnd++;

      if (undoSupport != null) {
        undoSupport.endCompoundEdit();
      }
      Document doc = input.getDocument();
      if (wsEnd > end) {
        doc.remove(end, wsEnd - end);
      }
      doc.insertString(end, ": ", null);
      input.setCaretPosition(end + 2);
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isKnownNick(String candidate) {
    if (candidate == null) return false;
    for (String n : nickSnapshot) {
      if (n != null && n.equalsIgnoreCase(candidate)) return true;
    }
    return false;
  }

  private static boolean isEligibleForNickAddressSuffix(String beforeText, int beforeCaret) {
    if (beforeText == null) beforeText = "";
    // Don't do this for slash-commands.
    String trimmed = beforeText.stripLeading();
    if (trimmed.startsWith("/")) return false;

    int startBefore = firstNonWhitespace(beforeText);
    if (startBefore < 0) return false;
    int endBefore = wordEnd(beforeText, startBefore);
    return beforeCaret <= endBefore;
  }

  private static String firstWordPrefix(String text) {
    if (text == null) return "";
    int start = firstNonWhitespace(text);
    if (start < 0) return "";
    int end = wordEnd(text, start);
    if (end <= start) return "";
    return text.substring(start, end);
  }

  private boolean hasNickPrefixMatch(String prefix) {
    if (prefix == null) return false;
    String p = prefix.strip();
    if (p.isEmpty()) return false;
    String pLower = p.toLowerCase(Locale.ROOT);
    for (String n : nickSnapshot) {
      if (n == null) continue;
      if (n.toLowerCase(Locale.ROOT).startsWith(pLower)) return true;
    }
    return false;
  }

  private static int firstNonWhitespace(String s) {
    if (s == null) return -1;
    for (int i = 0; i < s.length(); i++) {
      if (!Character.isWhitespace(s.charAt(i))) return i;
    }
    return -1;
  }

  private static int wordEnd(String s, int start) {
    if (s == null || start < 0) return 0;
    int i = start;
    while (i < s.length() && !Character.isWhitespace(s.charAt(i))) i++;
    return i;
  }

  private void installAutoCompletionUiRefreshOnLafChange() {
    try {
      UIManager.addPropertyChangeListener(lafListener);
    } catch (Exception ignored) {
    }

    // Remove listener when the owner component is disposed.
    owner.addHierarchyListener(evt -> {
      long flags = evt.getChangeFlags();
      if ((flags & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
      if (owner.isDisplayable()) return;
      try {
        UIManager.removePropertyChangeListener(lafListener);
      } catch (Exception ex) {
        log.warn("[MessageInputNickCompletionSupport] remove LAF listener failed", ex);
      }
    });
  }

  private void refreshAutoCompletionUi() {
    // Theme/LAF changes: refresh any existing completion popups. If none exist yet,
    // keep the dirty flag so the first TAB-created popup can be refreshed once.
    if (refreshAutoCompletionUiIfPresent(true)) {
      autoCompletionUiDirty = false;
    }
  }

  private boolean refreshAutoCompletionUiIfPresent(boolean hideFirst) {
    try {
      Window ownerWindow = SwingUtilities.getWindowAncestor(owner);

      if (hideFirst) {
        // Hide any active popups to prevent UI delegates from being mid-event.
        try {
          java.lang.reflect.Method m = autoCompletion.getClass().getMethod("hideChildWindows");
          m.setAccessible(true);
          m.invoke(autoCompletion);
        } catch (Throwable ignored) {
        }
      }

      ArrayList<Window> windows = new ArrayList<>();
      try {
        Class<?> c = autoCompletion.getClass();
        while (c != null && c != Object.class) {
          for (Field f : c.getDeclaredFields()) {
            if (!Window.class.isAssignableFrom(f.getType())) continue;
            f.setAccessible(true);
            Object v = f.get(autoCompletion);
            if (v instanceof Window w) windows.add(w);
          }
          c = c.getSuperclass();
        }
      } catch (Throwable ignored) {
      }

      int updated = 0;
      for (Window w : windows) {
        if (w == null) continue;
        // Avoid reapplying UI to the main application frame (causes visible flicker).
        if (ownerWindow != null && w == ownerWindow) continue;
        if (w instanceof Frame) continue;

        try {
          SwingUtilities.updateComponentTreeUI(w);
          w.invalidate();
          w.validate();
          w.repaint();
          updated++;
        } catch (Throwable ignored) {
        }
      }

      return updated > 0;
    } catch (Exception ignored) {
      return false;
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
}
