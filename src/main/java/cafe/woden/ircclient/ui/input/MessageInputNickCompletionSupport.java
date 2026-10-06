package cafe.woden.ircclient.ui.input;

import cafe.woden.ircclient.app.commands.spi.SlashCommandDescriptor;
import cafe.woden.ircclient.ui.input.spi.MessageInputWordSuggestionProvider;
import cafe.woden.ircclient.ui.localization.UiMessages;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionProvider;
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
  private static final UiMessages MESSAGES = UiMessages.bundledDefaults();

  private static final int PENDING_NICK_SUFFIX_TIMEOUT_MS = 5000;
  private static final int RELEVANCE_NICK = 300;
  private static final int RELEVANCE_SLASH = 280;
  private static final int RELEVANCE_WORD_PREFIX = 220;
  private static final int MAX_WORD_SUGGESTIONS = 8;

  private final JComponent owner;
  private final JTextComponent input;
  private final MessageInputUndoSupport undoSupport;
  private final MessageInputWordSuggestionProvider wordSuggestionProvider;
  private final List<SlashCommand> slashCommands;

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
  private volatile boolean cycleNickCompletionsWithTab = false;
  private volatile boolean appendNickAddressSuffix = true;

  private boolean installed;
  private boolean pendingSuffixListenerInstalled;
  private boolean lafListenerInstalled;
  private NickCycleState nickCycleState;

  private final PropertyChangeListener lafListener =
      evt -> {
        if (!"lookAndFeel".equals(evt.getPropertyName())) return;
        markUiDirty();
        SwingUtilities.invokeLater(this::refreshAutoCompletionUi);
      };

  private final HierarchyListener lafCleanupHierarchyListener =
      this::onOwnerHierarchyChangedForLafCleanup;

  MessageInputNickCompletionSupport(
      JComponent owner, JTextComponent input, MessageInputUndoSupport undoSupport) {
    this(owner, input, undoSupport, null);
  }

  MessageInputNickCompletionSupport(
      JComponent owner,
      JTextComponent input,
      MessageInputUndoSupport undoSupport,
      MessageInputWordSuggestionProvider wordSuggestionProvider) {
    this(owner, input, undoSupport, wordSuggestionProvider, List.of());
  }

  MessageInputNickCompletionSupport(
      JComponent owner,
      JTextComponent input,
      MessageInputUndoSupport undoSupport,
      MessageInputWordSuggestionProvider wordSuggestionProvider,
      List<SlashCommandDescriptor> slashCommandDescriptors) {
    this.owner = owner;
    this.input = input;
    this.undoSupport = undoSupport;
    this.wordSuggestionProvider = wordSuggestionProvider;
    this.slashCommands = mergeSlashCommands(slashCommandDescriptors);
    this.completionProvider =
        new FastCompletionProvider(this::dynamicWordCompletions, this::contextualCompletions);
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
    String t = token.trim();
    if (t.isEmpty()) return null;
    String tLower = t.toLowerCase(Locale.ROOT);
    String best = null;
    NickMatchRank bestRank = null;
    for (String n : nicks) {
      if (n == null) continue;
      NickMatchRank rank = NickMatchRank.forCandidate(n, t, tLower);
      if (!rank.isMatch()) continue;
      if (bestRank == null || rank.compareTo(bestRank) < 0) {
        bestRank = rank;
        best = n;
      }
    }
    return best;
  }

  String firstCompletionHint(String token) {
    return firstNickStartingWith(token);
  }

  void setCompletionPreferences(
      boolean cycleNickCompletionsWithTab, boolean appendNickAddressSuffix) {
    this.cycleNickCompletionsWithTab = cycleNickCompletionsWithTab;
    this.appendNickAddressSuffix = appendNickAddressSuffix;
    if (!cycleNickCompletionsWithTab) {
      nickCycleState = null;
    }
    if (!appendNickAddressSuffix) {
      pendingNickAddressSuffix = false;
    }
  }

  void setNickCompletions(List<String> nicks) {
    List<String> cleaned = cleanNickList(nicks);
    nickSnapshot = cleaned;
    nickCycleState = null;
    rebuildCompletionModel(cleaned);
  }

  void shutdown() {
    removeLafRefreshListeners();
  }

  private void rebuildCompletionModel(List<String> nicks) {
    List<String> cleaned = (nicks == null) ? List.of() : nicks;
    ArrayList<Completion> completions =
        new ArrayList<>(slashCommandCompletions.size() + cleaned.size());
    completions.addAll(slashCommandCompletions);
    for (String nick : cleaned) {
      BasicCompletion completion =
          new BasicCompletion(
              completionProvider, nick, MESSAGES.text("messageInput.completion.description.nick"));
      completion.setRelevance(RELEVANCE_NICK);
      completions.add(completion);
    }
    completionProvider.replaceCompletions(completions);
    markUiDirty();
  }

  private List<Completion> buildSlashCommandCompletions() {
    ArrayList<Completion> completions = new ArrayList<>(slashCommands.size());
    for (SlashCommand cmd : slashCommands) {
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

  private static List<SlashCommand> mergeSlashCommands(
      List<SlashCommandDescriptor> slashCommandDescriptors) {
    LinkedHashMap<String, SlashCommand> merged = new LinkedHashMap<>();
    if (slashCommandDescriptors != null) {
      for (SlashCommandDescriptor command : slashCommandDescriptors) {
        if (command == null) continue;
        SlashCommand slashCommand = new SlashCommand(command.command(), command.summary());
        merged.putIfAbsent(slashCommand.command().toLowerCase(Locale.ROOT), slashCommand);
      }
    }
    return List.copyOf(merged.values());
  }

  private List<Completion> dynamicWordCompletions(JTextComponent component, String token) {
    if (wordSuggestionProvider == null) return List.of();
    if (component == null) return List.of();
    String t = token == null ? "" : token.trim();
    if (t.isEmpty()) return List.of();

    String text = component.getText();
    if (!isWordSuggestionContext(text, component.getCaretPosition())) return List.of();
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
              completionProvider,
              word,
              prefix
                  ? MESSAGES.text("messageInput.completion.description.word")
                  : MESSAGES.text("messageInput.completion.description.spellingCorrection"));
      // Keep all words below nick relevance while preserving provider likelihood order.
      completion.setRelevance(Math.max(1, RELEVANCE_WORD_PREFIX - i));
      out.add(completion);
    }
    return List.copyOf(out);
  }

  private List<Completion> contextualCompletions(
      JTextComponent component, String token, List<Completion> completions) {
    if (!appendNickAddressSuffix) return completions;
    if (component == null || completions == null || completions.isEmpty()) return completions;
    if (!isFirstWordNickAddressContext(component)) return completions;

    ArrayList<Completion> out = new ArrayList<>(completions.size());
    boolean changed = false;
    for (Completion completion : completions) {
      Completion next = maybeWithAddressingReplacement(completion);
      if (next != completion) changed = true;
      out.add(next);
    }
    return changed ? out : completions;
  }

  private Completion maybeWithAddressingReplacement(Completion completion) {
    if (completion == null || completion.getRelevance() != RELEVANCE_NICK) return completion;
    String replacement = completion.getReplacementText();
    if (replacement == null || replacement.isBlank()) return completion;
    String nick = replacement.trim();
    if (!isKnownNick(nick)) return completion;
    BasicCompletion addressed = new AddressedNickCompletion(completionProvider, nick, nick + ": ");
    addressed.setRelevance(completion.getRelevance());
    return addressed;
  }

  private static boolean isFirstWordNickAddressContext(JTextComponent component) {
    String text = component.getText();
    if (text == null) text = "";
    if (text.stripLeading().startsWith("/")) return false;
    int caret = component.getCaretPosition();
    if (caret < 0 || caret > text.length()) return false;
    int start = firstNonWhitespace(text);
    if (start < 0) return false;
    int end = wordEnd(text, start);
    return caret >= start && caret <= end;
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
              if (tryCycleNickCompletion(beforeText, beforeCaret)) {
                return;
              }
              boolean forcePopupInsteadOfImmediateCompletion =
                  shouldForcePopupInsteadOfImmediateCompletion(beforeText, beforeCaret);
              boolean prevSingleChoice = autoCompletion.getAutoCompleteSingleChoices();
              if (forcePopupInsteadOfImmediateCompletion) {
                autoCompletion.setAutoCompleteSingleChoices(false);
              }
              try {
                delegate.actionPerformed(e);
              } finally {
                if (forcePopupInsteadOfImmediateCompletion) {
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
                    String afterText = input.getText();
                    int afterCaret = input.getCaretPosition();
                    if (!appended) {
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
                    maybeScheduleAsyncWordCompletionPopup(
                        beforeText, beforeCaret, afterText, afterCaret);
                  });
            }
          });

      installPendingNickAddressSuffixListener();

    } catch (Exception ignored) {
    }
  }

  private boolean tryCycleNickCompletion(String beforeText, int beforeCaret) {
    if (!cycleNickCompletionsWithTab) return false;
    NickCycleRequest request = nickCycleRequest(beforeText, beforeCaret);
    if (request == null || request.matches().isEmpty()) {
      nickCycleState = null;
      return false;
    }

    int nextIndex =
        request.continuing()
            ? (request.currentIndex() + 1) % request.matches().size()
            : request.currentIndex();
    String nick = request.matches().get(nextIndex);
    if (nick == null || nick.isBlank()) return false;

    String suffix = request.appendAddressSuffix() ? ": " : "";
    try {
      if (undoSupport != null) {
        undoSupport.endCompoundEdit();
      }
      Document doc = input.getDocument();
      doc.remove(request.replaceStart(), request.replaceEnd() - request.replaceStart());
      String replacement = nick + suffix;
      doc.insertString(request.replaceStart(), replacement, null);
      int caret = request.replaceStart() + replacement.length();
      input.setCaretPosition(caret);
      pendingNickAddressSuffix = false;
      nickCycleState =
          new NickCycleState(
              request.sourcePrefix(),
              request.replaceStart(),
              caret,
              request.matches(),
              nextIndex,
              request.appendAddressSuffix());
      return true;
    } catch (Exception ignored) {
      nickCycleState = null;
      return false;
    }
  }

  private NickCycleRequest nickCycleRequest(String beforeText, int beforeCaret) {
    String text = beforeText == null ? "" : beforeText;
    if (beforeCaret < 0 || beforeCaret > text.length()) return null;

    NickCycleState state = nickCycleState;
    if (state != null && state.matchesCurrent(text, beforeCaret)) {
      return new NickCycleRequest(
          state.sourcePrefix(),
          state.replaceStart(),
          state.replaceEnd(),
          state.matches(),
          state.index(),
          state.appendAddressSuffix(),
          true);
    }

    int start = wordStart(text, beforeCaret);
    int end = wordEnd(text, start);
    if (start < 0 || end < start || beforeCaret < start || beforeCaret > end) return null;

    String prefix = text.substring(start, beforeCaret).trim();
    if (prefix.isEmpty()) return null;
    List<String> matches = nickPrefixMatches(prefix);
    if (matches.isEmpty()) return null;

    boolean appendSuffix =
        appendNickAddressSuffix && isEligibleForNickAddressSuffix(text, beforeCaret);
    int replaceEnd = appendSuffix ? skipWhitespaceAfter(text, end) : end;
    int initialIndex = 0;
    for (int i = 0; i < matches.size(); i++) {
      String match = matches.get(i);
      if (match != null && match.equalsIgnoreCase(prefix) && matches.size() > 1) {
        initialIndex = (i + 1) % matches.size();
        break;
      }
    }
    return new NickCycleRequest(
        prefix, start, replaceEnd, matches, initialIndex, appendSuffix, false);
  }

  private List<String> nickPrefixMatches(String prefix) {
    if (prefix == null || prefix.isBlank()) return List.of();
    String p = prefix.trim();
    String pLower = p.toLowerCase(Locale.ROOT);
    ArrayList<String> matches = new ArrayList<>();
    for (String nick : nickSnapshot) {
      if (nick == null || nick.isBlank()) continue;
      if (nick.toLowerCase(Locale.ROOT).startsWith(pLower)) {
        matches.add(nick);
      }
    }
    if (matches.size() < 2) return List.copyOf(matches);
    matches.sort(
        (left, right) ->
            NickMatchRank.forCandidate(left, p, pLower)
                .compareTo(NickMatchRank.forCandidate(right, p, pLower)));
    return List.copyOf(matches);
  }

  private boolean shouldForcePopupInsteadOfImmediateCompletion(String beforeText, int beforeCaret) {
    if (autoCompletion.isPopupVisible()) return false;
    if (beforeText == null) beforeText = "";
    if (beforeCaret < 0 || beforeCaret > beforeText.length()) return false;

    String token = completionProvider.getAlreadyEnteredText(input);
    if (token == null || token.isBlank()) return false;
    if (firstCompletionHint(token) != null) return !cycleNickCompletionsWithTab;
    return shouldForcePopupForWordSuggestion(beforeText, beforeCaret, token);
  }

  private boolean shouldForcePopupForWordSuggestion(String text, int caret, String token) {
    if (wordSuggestionProvider == null) return false;
    if (caret < 0 || text == null || caret > text.length()) return false;
    if (!isWordSuggestionContext(text, caret)) return false;
    if (isKnownNick(token)) return false;
    return isPotentialWordSuggestionToken(token);
  }

  private static boolean isWordSuggestionContext(String text, int caret) {
    if (!startsWithSlashCommand(text)) return true;
    // /me carries ordinary message text; other command arguments may be identifiers or options.
    int start = firstNonWhitespace(text);
    int end = wordEnd(text, start);
    return text.substring(start, end).equalsIgnoreCase("/me") && caret > end;
  }

  private void maybeScheduleAsyncWordCompletionPopup(
      String beforeText, int beforeCaret, String afterText, int afterCaret) {
    if (wordSuggestionProvider == null) return;
    if (!Objects.equals(beforeText, afterText) || beforeCaret != afterCaret) return;
    if (autoCompletion.isPopupVisible()) return;

    String token = tokenAt(beforeText, beforeCaret);
    if (!shouldForcePopupForWordSuggestion(beforeText, beforeCaret, token)) return;

    CompletableFuture<List<String>> future;
    try {
      future = wordSuggestionProvider.suggestWordsAsync(token, MAX_WORD_SUGGESTIONS);
    } catch (RuntimeException ex) {
      return;
    }
    if (future == null) return;
    future.thenAccept(
        suggestions -> {
          if (suggestions == null || suggestions.isEmpty()) return;
          SwingUtilities.invokeLater(
              () -> {
                if (!input.isEditable() || !input.isEnabled()) return;
                if (!Objects.equals(input.getText(), beforeText)) return;
                if (input.getCaretPosition() != beforeCaret) return;
                if (autoCompletion.isPopupVisible()) return;
                showCompletionPopupWithoutSingleChoiceInsertion();
              });
        });
  }

  private void showCompletionPopupWithoutSingleChoiceInsertion() {
    boolean prevSingleChoice = autoCompletion.getAutoCompleteSingleChoices();
    autoCompletion.setAutoCompleteSingleChoices(false);
    try {
      autoCompletion.doCompletion();
    } finally {
      autoCompletion.setAutoCompleteSingleChoices(prevSingleChoice);
    }
  }

  private static String tokenAt(String text, int caret) {
    if (text == null || caret < 0 || caret > text.length()) return "";
    int start = wordStart(text, caret);
    int end = wordEnd(text, start);
    if (start < 0 || end < start || caret < start || caret > end) return "";
    return text.substring(start, caret).trim();
  }

  private static boolean isPotentialWordSuggestionToken(String token) {
    String t = token == null ? "" : token.trim();
    if (t.length() < 2) return false;
    if (t.startsWith("#") || t.startsWith("&") || t.startsWith("@")) return false;
    if (t.startsWith("/") || t.contains("://")) return false;
    boolean hasLetter = false;
    for (int i = 0; i < t.length(); i++) {
      char c = t.charAt(i);
      if (Character.isLetter(c)) {
        hasLetter = true;
        continue;
      }
      if (c == '\'' || c == '-') continue;
      return false;
    }
    return hasLetter;
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
      if (!appendNickAddressSuffix) return false;
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
    if (!appendNickAddressSuffix) return false;
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

  private static int wordStart(String s, int caret) {
    if (s == null || caret < 0 || caret > s.length()) return -1;
    int i = caret;
    while (i > 0 && !Character.isWhitespace(s.charAt(i - 1))) {
      i--;
    }
    return i;
  }

  private static int skipWhitespaceAfter(String s, int offset) {
    if (s == null) return offset;
    int i = Math.max(0, Math.min(offset, s.length()));
    while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
      i++;
    }
    return i;
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

  /**
   * Lightweight nick-likelihood rank used to prioritize the most probable nick when the completion
   * popup opens.
   */
  private static final class NickMatchRank implements Comparable<NickMatchRank> {
    private static final int BUCKET_NON_MATCH = 4;

    private final int bucket;
    private final int sharedPrefixLength;
    private final int lengthDelta;
    private final String lower;
    private final String original;

    private NickMatchRank(
        int bucket, int sharedPrefixLength, int lengthDelta, String lower, String original) {
      this.bucket = bucket;
      this.sharedPrefixLength = sharedPrefixLength;
      this.lengthDelta = lengthDelta;
      this.lower = lower;
      this.original = original;
    }

    static NickMatchRank forCandidate(String candidate, String token, String tokenLower) {
      String text = Objects.toString(candidate, "").trim();
      String lower = text.toLowerCase(Locale.ROOT);
      int bucket = bucketFor(text, lower, token, tokenLower);
      int sharedPrefixLength = sharedPrefixLength(lower, tokenLower);
      int lengthDelta = Math.abs(text.length() - token.length());
      return new NickMatchRank(bucket, sharedPrefixLength, lengthDelta, lower, text);
    }

    boolean isMatch() {
      return bucket < BUCKET_NON_MATCH;
    }

    @Override
    public int compareTo(NickMatchRank other) {
      int byBucket = Integer.compare(this.bucket, other.bucket);
      if (byBucket != 0) return byBucket;

      int bySharedPrefix = Integer.compare(other.sharedPrefixLength, this.sharedPrefixLength);
      if (bySharedPrefix != 0) return bySharedPrefix;

      int byLengthDelta = Integer.compare(this.lengthDelta, other.lengthDelta);
      if (byLengthDelta != 0) return byLengthDelta;

      int byLower = this.lower.compareTo(other.lower);
      if (byLower != 0) return byLower;
      return this.original.compareTo(other.original);
    }

    private static int bucketFor(String text, String lower, String token, String tokenLower) {
      if (text.equals(token)) return 0;
      if (text.equalsIgnoreCase(token)) return 1;
      if (text.startsWith(token)) return 2;
      if (lower.startsWith(tokenLower)) return 3;
      return BUCKET_NON_MATCH;
    }

    private static int sharedPrefixLength(String leftLower, String rightLower) {
      int limit = Math.min(leftLower.length(), rightLower.length());
      int i = 0;
      while (i < limit && leftLower.charAt(i) == rightLower.charAt(i)) {
        i++;
      }
      return i;
    }
  }

  private record SlashCommand(String command, String summary) {}

  private record NickCycleRequest(
      String sourcePrefix,
      int replaceStart,
      int replaceEnd,
      List<String> matches,
      int currentIndex,
      boolean appendAddressSuffix,
      boolean continuing) {}

  private record NickCycleState(
      String sourcePrefix,
      int replaceStart,
      int replaceEnd,
      List<String> matches,
      int index,
      boolean appendAddressSuffix) {
    boolean matchesCurrent(String text, int caret) {
      if (text == null || caret != replaceEnd) return false;
      if (replaceStart < 0 || replaceEnd < replaceStart || replaceEnd > text.length()) return false;
      if (matches == null || matches.isEmpty() || index < 0 || index >= matches.size())
        return false;
      String expected = matches.get(index) + (appendAddressSuffix ? ": " : "");
      return text.regionMatches(replaceStart, expected, 0, expected.length());
    }
  }

  private static final class AddressedNickCompletion extends BasicCompletion {
    private final String displayText;

    AddressedNickCompletion(
        CompletionProvider provider, String displayText, String replacementText) {
      super(provider, replacementText, MESSAGES.text("messageInput.completion.description.nick"));
      this.displayText = displayText;
    }

    @Override
    public String getInputText() {
      return displayText;
    }
  }

  private void installAutoCompletionUiRefreshOnLafChange() {
    if (lafListenerInstalled) return;
    lafListenerInstalled = true;
    try {
      UIManager.addPropertyChangeListener(lafListener);
    } catch (Exception ignored) {
    }

    // Remove listener when the owner component is disposed.
    try {
      owner.addHierarchyListener(lafCleanupHierarchyListener);
    } catch (Exception ignored) {
    }
  }

  private void onOwnerHierarchyChangedForLafCleanup(HierarchyEvent evt) {
    long flags = evt.getChangeFlags();
    if ((flags & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
    if (owner.isDisplayable()) return;
    removeLafRefreshListeners();
  }

  private void removeLafRefreshListeners() {
    if (!lafListenerInstalled) return;
    lafListenerInstalled = false;
    try {
      UIManager.removePropertyChangeListener(lafListener);
    } catch (Exception ex) {
      log.warn("[MessageInputNickCompletionSupport] remove LAF listener failed", ex);
    }
    try {
      owner.removeHierarchyListener(lafCleanupHierarchyListener);
    } catch (Exception ignored) {
    }
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
    private final ContextualCompletionSource contextualCompletionSource;
    private static final Field COMPLETIONS_FIELD = findCompletionsField();

    FastCompletionProvider(
        DynamicCompletionSource dynamicCompletionSource,
        ContextualCompletionSource contextualCompletionSource) {
      this.dynamicCompletionSource = dynamicCompletionSource;
      this.contextualCompletionSource = contextualCompletionSource;
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
      String token = getAlreadyEnteredText(comp);
      base = contextualize(comp, token, base);
      if (dynamicCompletionSource == null) {
        if (base.size() < 2) return base;
        ArrayList<Completion> ranked = new ArrayList<>(base);
        sortByRelevanceAndNickLikelihood(ranked, token);
        return ranked;
      }

      List<Completion> dynamic = dynamicCompletionSource.lookup(comp, token);
      if (dynamic == null || dynamic.isEmpty()) {
        if (base.size() < 2) return base;
        ArrayList<Completion> ranked = new ArrayList<>(base);
        sortByRelevanceAndNickLikelihood(ranked, token);
        return ranked;
      }

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

      sortByRelevanceAndNickLikelihood(merged, token);
      return merged;
    }

    private static void sortByRelevanceAndNickLikelihood(
        List<Completion> completions, String token) {
      if (completions == null || completions.size() < 2) return;

      String normalizedToken = token == null ? "" : token.trim();
      if (!normalizedToken.isEmpty()) {
        filterNickCompletionsToPrefix(completions, normalizedToken);
        if (completions.size() < 2) return;
      }
      String tokenLower = normalizedToken.toLowerCase(Locale.ROOT);
      IdentityHashMap<Completion, NickMatchRank> nickRanks = new IdentityHashMap<>();
      if (!normalizedToken.isEmpty()) {
        for (Completion completion : completions) {
          if (completion == null || completion.getRelevance() != RELEVANCE_NICK) continue;
          String text = completion.getReplacementText();
          if (text == null || text.isBlank()) continue;
          nickRanks.put(completion, NickMatchRank.forCandidate(text, normalizedToken, tokenLower));
        }
      }

      completions.sort(
          (left, right) -> {
            int relevance = Integer.compare(right.getRelevance(), left.getRelevance());
            if (relevance != 0) return relevance;

            NickMatchRank leftRank = nickRanks.get(left);
            NickMatchRank rightRank = nickRanks.get(right);
            if (leftRank != null && rightRank != null) {
              int rank = leftRank.compareTo(rightRank);
              if (rank != 0) return rank;
            }

            return RELEVANCE_SORT.compare(left, right);
          });
    }

    private static void filterNickCompletionsToPrefix(List<Completion> completions, String token) {
      String tokenLower = token.toLowerCase(Locale.ROOT);
      completions.removeIf(
          completion -> {
            if (completion == null || completion.getRelevance() != RELEVANCE_NICK) return false;
            String text = completion.getReplacementText();
            if (text == null || text.isBlank()) return true;
            return !text.toLowerCase(Locale.ROOT).startsWith(tokenLower);
          });
    }

    @Override
    protected boolean isValidChar(char ch) {
      return super.isValidChar(ch) || ch == '/';
    }

    private List<Completion> contextualize(
        JTextComponent component, String token, List<Completion> completions) {
      if (contextualCompletionSource == null || completions == null || completions.isEmpty()) {
        return completions;
      }
      List<Completion> contextual = contextualCompletionSource.apply(component, token, completions);
      return contextual == null ? completions : contextual;
    }
  }

  @FunctionalInterface
  private interface DynamicCompletionSource {
    List<Completion> lookup(JTextComponent component, String token);
  }

  @FunctionalInterface
  private interface ContextualCompletionSource {
    List<Completion> apply(JTextComponent component, String token, List<Completion> completions);
  }
}
