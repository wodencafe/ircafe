package cafe.woden.ircclient.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles message-input auto-completion (nicks + slash commands + word suggestions).
 *
 * <p>Responsibilities: - Own AutoCompletion + its CompletionProvider - Efficient bulk rebuild of
 * completion items (avoid O(n^2) sorting) - Merge dynamic word suggestions for non-command text -
 * IRC-style addressing suffix behavior for first-word completion ("nick: ") - Best-effort refresh
 * of AutoCompletion popup UI on theme/LAF changes
 */
final class MessageInputNickCompletionSupport {

  private static final Logger log =
      LoggerFactory.getLogger(MessageInputNickCompletionSupport.class);

  private static final int PENDING_NICK_SUFFIX_TIMEOUT_MS = 5000;
  private static final int RELEVANCE_NICK = 300;
  private static final int RELEVANCE_SLASH = 280;
  private static final int RELEVANCE_WORD_PREFIX = 220;
  private static final int MAX_WORD_SUGGESTIONS = 8;
  private static final List<SlashCommand> SLASH_COMMANDS =
      List.of(
          new SlashCommand("/join", "Join channel"),
          new SlashCommand("/j", "Alias: /join"),
          new SlashCommand("/part", "Leave channel"),
          new SlashCommand("/leave", "Alias: /part"),
          new SlashCommand("/connect", "Connect server/all"),
          new SlashCommand("/disconnect", "Disconnect server/all"),
          new SlashCommand("/reconnect", "Reconnect server/all"),
          new SlashCommand("/quit", "Disconnect all and quit"),
          new SlashCommand("/nick", "Change nickname"),
          new SlashCommand("/away", "Set/remove away status"),
          new SlashCommand("/query", "Open private message"),
          new SlashCommand("/whois", "WHOIS lookup"),
          new SlashCommand("/wi", "Alias: /whois"),
          new SlashCommand("/whowas", "WHOWAS lookup"),
          new SlashCommand("/msg", "Send private message"),
          new SlashCommand("/notice", "Send notice"),
          new SlashCommand("/me", "Send action"),
          new SlashCommand("/topic", "View/change topic"),
          new SlashCommand("/kick", "Kick user"),
          new SlashCommand("/invite", "Invite user"),
          new SlashCommand("/invites", "List pending invites"),
          new SlashCommand("/invjoin", "Join pending invite"),
          new SlashCommand("/invitejoin", "Alias: /invjoin"),
          new SlashCommand("/invignore", "Ignore pending invite"),
          new SlashCommand("/inviteignore", "Alias: /invignore"),
          new SlashCommand("/invwhois", "WHOIS inviter from invite"),
          new SlashCommand("/invitewhois", "Alias: /invwhois"),
          new SlashCommand("/invblock", "Block inviter nick"),
          new SlashCommand("/inviteblock", "Alias: /invblock"),
          new SlashCommand("/inviteautojoin", "Toggle invite auto-join"),
          new SlashCommand("/invautojoin", "Alias: /inviteautojoin"),
          new SlashCommand("/ajinvite", "Alias: /inviteautojoin (toggle)"),
          new SlashCommand("/names", "Request NAMES"),
          new SlashCommand("/who", "Request WHO"),
          new SlashCommand("/list", "Request LIST"),
          new SlashCommand("/mode", "Set/query mode"),
          new SlashCommand("/op", "Grant op"),
          new SlashCommand("/deop", "Remove op"),
          new SlashCommand("/voice", "Grant voice"),
          new SlashCommand("/devoice", "Remove voice"),
          new SlashCommand("/ban", "Set ban"),
          new SlashCommand("/unban", "Remove ban"),
          new SlashCommand("/ignore", "Add hard ignore"),
          new SlashCommand("/unignore", "Remove hard ignore"),
          new SlashCommand("/ignorelist", "Show hard ignores"),
          new SlashCommand("/ignores", "Alias: /ignorelist"),
          new SlashCommand("/softignore", "Add soft ignore"),
          new SlashCommand("/unsoftignore", "Remove soft ignore"),
          new SlashCommand("/softignorelist", "Show soft ignores"),
          new SlashCommand("/softignores", "Alias: /softignorelist"),
          new SlashCommand("/version", "CTCP VERSION"),
          new SlashCommand("/ping", "CTCP PING"),
          new SlashCommand("/time", "CTCP TIME"),
          new SlashCommand("/ctcp", "Send CTCP"),
          new SlashCommand("/dcc", "DCC command"),
          new SlashCommand("/dccmsg", "DCC message"),
          new SlashCommand("/chathistory", "IRCv3 CHATHISTORY"),
          new SlashCommand("/history", "Alias: /chathistory"),
          new SlashCommand("/help", "Show command help"),
          new SlashCommand("/commands", "Alias: /help"),
          new SlashCommand("/reply", "Reply to message-id"),
          new SlashCommand("/react", "React to message-id"),
          new SlashCommand("/edit", "Edit message-id"),
          new SlashCommand("/redact", "Redact message-id"),
          new SlashCommand("/delete", "Alias: /redact"),
          new SlashCommand("/filter", "Local filtering controls"),
          new SlashCommand("/quote", "Send raw IRC line"),
          new SlashCommand("/raw", "Alias: /quote"));

  private final JComponent owner;
  private final JTextField input;
  private final MessageInputUndoSupport undoSupport;
  private final MessageInputWordSuggestionProvider wordSuggestionProvider;

  /**
   * NOTE: DefaultCompletionProvider sorts its list on every addCompletion(), which becomes
   * catastrophically slow when we rebuild completions for large channels.
   */
  private final FastCompletionProvider completionProvider;

  private final AutoCompletion autoCompletion;
  private final List<Completion> slashCommandCompletions;

  // AutoCompletion popups are lazily created and may cache UI defaults; mark dirty on theme
  // changes.
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

  private final PropertyChangeListener lafListener =
      evt -> {
        if (!"lookAndFeel".equals(evt.getPropertyName())) return;
        markUiDirty();
        SwingUtilities.invokeLater(this::refreshAutoCompletionUi);
      };

  MessageInputNickCompletionSupport(
      JComponent owner, JTextField input, MessageInputUndoSupport undoSupport) {
    this(owner, input, undoSupport, null);
  }

  MessageInputNickCompletionSupport(
      JComponent owner,
      JTextField input,
      MessageInputUndoSupport undoSupport,
      MessageInputWordSuggestionProvider wordSuggestionProvider) {
    this.owner = owner;
    this.input = input;
    this.undoSupport = undoSupport;
    this.wordSuggestionProvider = wordSuggestionProvider;
    this.completionProvider = new FastCompletionProvider(this::dynamicWordCompletions);
    this.autoCompletion = new AutoCompletion(completionProvider);
    this.slashCommandCompletions = buildSlashCommandCompletions();
    rebuildCompletionModel(List.of());
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
    installSlashCommandAutoPopup();
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

  String firstCompletionHint(String token) {
    return firstNickStartingWith(token);
  }

  void setNickCompletions(List<String> nicks) {
    List<String> cleaned = cleanNickList(nicks);
    nickSnapshot = cleaned;
    rebuildCompletionModel(cleaned);
  }

  private void rebuildCompletionModel(List<String> nicks) {
    List<String> cleaned = (nicks == null) ? List.of() : nicks;
    ArrayList<Completion> completions =
        new ArrayList<>(slashCommandCompletions.size() + cleaned.size());
    completions.addAll(slashCommandCompletions);
    for (String nick : cleaned) {
      BasicCompletion completion = new BasicCompletion(completionProvider, nick, "IRC nick");
      completion.setRelevance(RELEVANCE_NICK);
      completions.add(completion);
    }
    completionProvider.replaceCompletions(completions);
    markUiDirty();
  }

  private List<Completion> buildSlashCommandCompletions() {
    ArrayList<Completion> completions = new ArrayList<>(SLASH_COMMANDS.size());
    for (SlashCommand cmd : SLASH_COMMANDS) {
      BasicCompletion completion =
          new BasicCompletion(completionProvider, cmd.command(), cmd.summary());
      completion.setRelevance(RELEVANCE_SLASH);
      completions.add(completion);
    }
    return List.copyOf(completions);
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

  private List<Completion> dynamicWordCompletions(JTextComponent component, String token) {
    if (wordSuggestionProvider == null) return List.of();
    if (component == null) return List.of();
    String t = token == null ? "" : token.trim();
    if (t.isEmpty()) return List.of();

    String text = component.getText();
    if (startsWithSlashCommand(text)) return List.of();
    if (isKnownNick(t)) return List.of();

    List<String> suggestions = wordSuggestionProvider.suggestWords(t, MAX_WORD_SUGGESTIONS);
    if (suggestions == null || suggestions.isEmpty()) return List.of();

    String tokenLower = t.toLowerCase(Locale.ROOT);
    ArrayList<Completion> out = new ArrayList<>(suggestions.size());
    for (int i = 0; i < suggestions.size(); i++) {
      String suggestion = suggestions.get(i);
      if (suggestion == null) continue;
      String word = suggestion.trim();
      if (word.isEmpty()) continue;
      if (isKnownNick(word)) continue;

      boolean prefix = word.toLowerCase(Locale.ROOT).startsWith(tokenLower);
      BasicCompletion completion =
          new BasicCompletion(
              completionProvider, word, prefix ? "Word completion" : "Spelling correction");
      // Keep all words below nick relevance while preserving provider likelihood order.
      completion.setRelevance(Math.max(1, RELEVANCE_WORD_PREFIX - i));
      out.add(completion);
    }
    return List.copyOf(out);
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
      am.put(
          key,
          new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
              String beforeText = input.getText();
              int beforeCaret = input.getCaretPosition();
              boolean forcePopupForNickHint =
                  shouldForcePopupInsteadOfImmediateCompletion(beforeText, beforeCaret);
              boolean prevSingleChoice = autoCompletion.getAutoCompleteSingleChoices();
              if (forcePopupForNickHint) {
                autoCompletion.setAutoCompleteSingleChoices(false);
              }
              try {
                delegate.actionPerformed(e);
              } finally {
                if (forcePopupForNickHint) {
                  autoCompletion.setAutoCompleteSingleChoices(prevSingleChoice);
                }
              }

              SwingUtilities.invokeLater(
                  () -> {
                    // Refresh the completion popup UI only when needed (first creation or after a
                    // theme change).
                    // Doing this on every TAB causes visible flicker because it can touch the owner
                    // window.
                    if (autoCompletionUiDirty) {
                      if (refreshAutoCompletionUiIfPresent(false)) {
                        autoCompletionUiDirty = false;
                      }
                    }

                    boolean appended = maybeAppendNickAddressSuffix(beforeText, beforeCaret);
                    if (!appended) {
                      String afterText = input.getText();
                      int afterCaret = input.getCaretPosition();
                      pendingNickAddressSuffix = false;
                      if (shouldArmPendingNickAddressSuffix(
                          beforeText, beforeCaret, afterText, afterCaret)) {
                        pendingNickAddressSuffix = true;
                        pendingNickAddressBeforeText = beforeText;
                        pendingNickAddressBeforeCaret = beforeCaret;
                        pendingNickAddressSetAtMs = System.currentTimeMillis();
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

  private boolean shouldForcePopupInsteadOfImmediateCompletion(String beforeText, int beforeCaret) {
    if (autoCompletion.isPopupVisible()) return false;
    if (beforeText == null) beforeText = "";
    if (startsWithSlashCommand(beforeText)) return false;
    if (beforeCaret < 0 || beforeCaret > beforeText.length()) return false;

    String token = completionProvider.getAlreadyEnteredText(input);
    if (token == null || token.isBlank()) return false;
    return firstCompletionHint(token) != null;
  }

  private void installPendingNickAddressSuffixListener() {
    if (pendingSuffixListenerInstalled) return;
    pendingSuffixListenerInstalled = true;
    try {
      input
          .getDocument()
          .addDocumentListener(
              new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                  maybeAppendPendingNickSuffixAsync();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                  maybeAppendPendingNickSuffixAsync();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                  maybeAppendPendingNickSuffixAsync();
                }
              });
    } catch (Exception ignored) {
    }
  }

  private void installSlashCommandAutoPopup() {
    try {
      input
          .getDocument()
          .addDocumentListener(
              new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                  maybeShowSlashCommandPopup(e);
                }

                @Override
                public void removeUpdate(DocumentEvent e) {}

                @Override
                public void changedUpdate(DocumentEvent e) {}
              });
    } catch (Exception ignored) {
    }
  }

  private void maybeShowSlashCommandPopup(DocumentEvent e) {
    if (e == null || e.getLength() != 1) return;
    if (!input.isEditable() || !input.isEnabled() || !input.hasFocus()) return;

    try {
      Document doc = e.getDocument();
      if (doc == null) return;
      String inserted = doc.getText(e.getOffset(), 1);
      if (!"/".equals(inserted)) return;
    } catch (Exception ignored) {
      return;
    }

    SwingUtilities.invokeLater(
        () -> {
          if (!shouldShowSlashCommandCompletion()) return;
          autoCompletion.doCompletion();
        });
  }

  private boolean shouldShowSlashCommandCompletion() {
    if (!input.isEditable() || !input.isEnabled() || !input.hasFocus()) return false;
    String text = input.getText();
    if (text == null || text.isEmpty()) return false;
    int caret = input.getCaretPosition();
    if (caret <= 0 || caret > text.length()) return false;
    if (text.charAt(caret - 1) != '/') return false;
    int first = firstNonWhitespace(text);
    return first >= 0 && first == caret - 1;
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

    SwingUtilities.invokeLater(
        () -> {
          if (!pendingNickAddressSuffix) return;
          boolean appended =
              maybeAppendNickAddressSuffix(
                  pendingNickAddressBeforeText, pendingNickAddressBeforeCaret);
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

      if (afterText.equals(beforeText) && afterCaret == beforeCaret)
        return false; // no change -> no completion

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
        if (!Character.isWhitespace(ch))
          return false; // only add when nick is followed by whitespace
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

  private boolean shouldArmPendingNickAddressSuffix(
      String beforeText, int beforeCaret, String afterText, int afterCaret) {
    // If TAB only opened/updated a multi-choice completion popup, the completion text may be
    // inserted later (e.g., after selecting an item). Arm a one-shot suffix append so the chosen
    // nick becomes "nick: " when it's the first word.
    if (!isEligibleForNickAddressSuffix(beforeText, beforeCaret)) return false;

    String beforePrefix = firstWordPrefix(beforeText);
    if (!hasNickPrefixMatch(beforePrefix)) return false;

    if (Objects.equals(afterText, beforeText) && afterCaret == beforeCaret) {
      return true;
    }

    String afterPrefix = firstWordPrefix(afterText);
    if (afterPrefix.isEmpty()) return false;
    if (isKnownNick(afterPrefix)) return false;
    return hasNickPrefixMatch(afterPrefix);
  }

  private static int firstNonWhitespace(String s) {
    if (s == null) return -1;
    for (int i = 0; i < s.length(); i++) {
      if (!Character.isWhitespace(s.charAt(i))) return i;
    }
    return -1;
  }

  private static boolean startsWithSlashCommand(String text) {
    if (text == null || text.isEmpty()) return false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c)) continue;
      return c == '/';
    }
    return false;
  }

  private static int wordEnd(String s, int start) {
    if (s == null || start < 0) return 0;
    int i = start;
    while (i < s.length() && !Character.isWhitespace(s.charAt(i))) i++;
    return i;
  }

  private record SlashCommand(String command, String summary) {}

  private void installAutoCompletionUiRefreshOnLafChange() {
    try {
      UIManager.addPropertyChangeListener(lafListener);
    } catch (Exception ignored) {
    }

    // Remove listener when the owner component is disposed.
    owner.addHierarchyListener(
        evt -> {
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
   * <p>The upstream provider sorts on every insertion; we instead replace the internal list in one
   * shot and sort once.
   */
  private static final class FastCompletionProvider extends DefaultCompletionProvider {
    private static final Comparator<Completion> RELEVANCE_SORT =
        (a, b) -> {
          int r = Integer.compare(b.getRelevance(), a.getRelevance());
          return (r != 0) ? r : a.compareTo(b);
        };

    private final DynamicCompletionSource dynamicCompletionSource;
    private static final Field COMPLETIONS_FIELD = findCompletionsField();

    FastCompletionProvider(DynamicCompletionSource dynamicCompletionSource) {
      this.dynamicCompletionSource = dynamicCompletionSource;
    }

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

    @Override
    public List<Completion> getCompletions(JTextComponent comp) {
      List<Completion> base = super.getCompletions(comp);
      if (dynamicCompletionSource == null) return base;

      String token = getAlreadyEnteredText(comp);
      List<Completion> dynamic = dynamicCompletionSource.lookup(comp, token);
      if (dynamic == null || dynamic.isEmpty()) return base;

      ArrayList<Completion> merged = new ArrayList<>(base.size() + dynamic.size());
      merged.addAll(base);

      Set<String> seen = new HashSet<>();
      for (Completion c : base) {
        if (c == null) continue;
        String key = c.getReplacementText();
        if (key == null) continue;
        seen.add(key.toLowerCase(Locale.ROOT));
      }

      for (Completion c : dynamic) {
        if (c == null) continue;
        String key = c.getReplacementText();
        if (key == null || key.isBlank()) continue;
        String lower = key.toLowerCase(Locale.ROOT);
        if (!seen.add(lower)) continue;
        merged.add(c);
      }

      merged.sort(RELEVANCE_SORT);
      return merged;
    }

    @Override
    protected boolean isValidChar(char ch) {
      return super.isValidChar(ch) || ch == '/';
    }
  }

  @FunctionalInterface
  private interface DynamicCompletionSource {
    List<Completion> lookup(JTextComponent component, String token);
  }
}
