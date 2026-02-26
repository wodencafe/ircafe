package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.util.PopupMenuThemeSupport;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.BadLocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a right-click context menu for the message input field.
 *
 * <p>This is intentionally UI-local and not Spring-managed; it is a small, Swing-only helper.
 */
public final class MessageInputContextMenuSupport {
  private static final Logger log = LoggerFactory.getLogger(MessageInputContextMenuSupport.class);

  private final JTextField input;
  private final MessageInputUndoSupport undoSupport;
  private final MessageInputHistorySupport historySupport;
  private final MessageInputSpellcheckSupport spellcheckSupport;

  private boolean installed;

  public MessageInputContextMenuSupport(
      JTextField input,
      MessageInputUndoSupport undoSupport,
      MessageInputHistorySupport historySupport,
      MessageInputSpellcheckSupport spellcheckSupport) {
    this.input = Objects.requireNonNull(input, "input");
    this.undoSupport = Objects.requireNonNull(undoSupport, "undoSupport");
    this.historySupport = Objects.requireNonNull(historySupport, "historySupport");
    this.spellcheckSupport = Objects.requireNonNull(spellcheckSupport, "spellcheckSupport");
  }

  public void install() {
    if (installed) return;
    installed = true;

    final JPopupMenu menu = new JPopupMenu();

    final JMenuItem undoItem = new JMenuItem(undoSupport.getUndoAction());
    final JMenuItem redoItem = new JMenuItem(undoSupport.getRedoAction());

    // Best-effort: show shortcut hints (some LAFs render these in popups, others don't).
    try {
      int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
      undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask));
      redoItem.setAccelerator(
          KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | KeyEvent.SHIFT_DOWN_MASK));
    } catch (Exception ex) {
      log.debug("[MessageInputContextMenuSupport] set undo/redo accelerators failed", ex);
    }

    final JMenuItem cutItem = new JMenuItem("Cut");
    cutItem.addActionListener(e -> input.cut());

    final JMenuItem copyItem = new JMenuItem("Copy");
    copyItem.addActionListener(e -> input.copy());

    final JMenuItem pasteItem = new JMenuItem("Paste");
    pasteItem.addActionListener(e -> input.paste());

    final JMenuItem deleteItem = new JMenuItem("Delete");
    deleteItem.addActionListener(
        e -> {
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
    clearItem.addActionListener(
        e -> {
          if (!input.isEditable() || !input.isEnabled()) return;
          if (input.getDocument().getLength() == 0) return;
          input.setText("");
        });
    clearItem.setToolTipText("Ctrl+L");
    try {
      clearItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK));
    } catch (Exception ex) {
      log.debug("[MessageInputContextMenuSupport] set clear accelerator failed", ex);
    }

    final JMenuItem selectAllItem = new JMenuItem("Select All");
    selectAllItem.addActionListener(
        e -> {
          if (!input.isEnabled()) return;
          input.selectAll();
        });

    final JMenuItem checkSpellingItem = new JMenuItem("Check spelling");
    final JSeparator checkSpellingBeforeSeparator = new JPopupMenu.Separator();
    final JSeparator checkSpellingAfterSeparator = new JPopupMenu.Separator();
    checkSpellingItem.addActionListener(e -> showCheckSpellingDialogAtCaret());
    checkSpellingItem.setVisible(false);
    checkSpellingBeforeSeparator.setVisible(false);
    checkSpellingAfterSeparator.setVisible(false);

    final Action historyPrevAction =
        new AbstractAction("Previous Command") {
          @Override
          public void actionPerformed(ActionEvent e) {
            historySupport.browsePrev();
          }
        };
    final Action historyNextAction =
        new AbstractAction("Next Command") {
          @Override
          public void actionPerformed(ActionEvent e) {
            historySupport.browseNext();
          }
        };
    final Action historyClearAction =
        new AbstractAction("Clear Command History") {
          @Override
          public void actionPerformed(ActionEvent e) {
            historySupport.clearHistory();
          }
        };

    final JMenuItem historyPrevItem = new JMenuItem(historyPrevAction);
    final JMenuItem historyNextItem = new JMenuItem(historyNextAction);
    final JMenuItem historyClearItem = new JMenuItem(historyClearAction);

    try {
      // These are mainly visual hints inside the popup.
      historyPrevItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
      historyNextItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
    } catch (Exception ex) {
      log.debug("[MessageInputContextMenuSupport] set history accelerators failed", ex);
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
    menu.add(checkSpellingBeforeSeparator);
    menu.add(checkSpellingItem);
    menu.add(checkSpellingAfterSeparator);
    menu.addSeparator();
    menu.add(historyMenu);

    final Runnable refreshEnabledStates =
        () -> {
          undoSupport.refreshActions();

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

          Optional<MessageInputSpellcheckSupport.MisspelledWord> misspelledWordAtCaret =
              editable ? spellcheckSupport.misspelledWordAtCaret() : Optional.empty();
          boolean showCheckSpelling = misspelledWordAtCaret.isPresent();
          checkSpellingBeforeSeparator.setVisible(showCheckSpelling);
          checkSpellingItem.setVisible(showCheckSpelling);
          checkSpellingItem.setEnabled(showCheckSpelling);
          checkSpellingAfterSeparator.setVisible(showCheckSpelling);

          // History navigation
          MessageInputHistorySupport.MenuState hs = historySupport.menuState(editable);
          historyMenu.setEnabled(hs.menuEnabled);
          historyPrevAction.setEnabled(hs.canPrev);
          historyNextAction.setEnabled(hs.canNext);
          historyClearAction.setEnabled(hs.canClear);
        };

    menu.addPopupMenuListener(
        new PopupMenuListener() {
          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            refreshEnabledStates.run();
          }

          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

          @Override
          public void popupMenuCanceled(PopupMenuEvent e) {}
        });

    // Use an explicit listener so enablement refreshes before showing.
    input.addMouseListener(
        new MouseAdapter() {
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
            PopupMenuThemeSupport.prepareForDisplay(menu);
            menu.show(e.getComponent(), e.getX(), e.getY());
          }

          @Override
          public void mousePressed(MouseEvent e) {
            maybeShow(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            maybeShow(e);
          }
        });
  }

  private static boolean clipboardHasText() {
    try {
      Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
      if (cb == null) return false;
      Transferable t = cb.getContents(null);
      return t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor);
    } catch (Exception ignored) {
      return false;
    }
  }

  private void showCheckSpellingDialogAtCaret() {
    if (!input.isEditable() || !input.isEnabled()) return;

    Optional<MessageInputSpellcheckSupport.MisspelledWord> maybeMisspelled =
        spellcheckSupport.misspelledWordAtCaret();
    if (maybeMisspelled.isEmpty()) return;
    MessageInputSpellcheckSupport.MisspelledWord misspelled = maybeMisspelled.get();

    List<String> suggestions = spellcheckSupport.suggestionsForMisspelledWord(misspelled, 12);
    Optional<String> selectedSuggestion =
        showSuggestionSelectionDialog(misspelled.token(), suggestions);
    if (selectedSuggestion.isEmpty()) return;

    spellcheckSupport.replaceMisspelledWord(misspelled, selectedSuggestion.get());
  }

  private Optional<String> showSuggestionSelectionDialog(String token, List<String> suggestions) {
    DefaultListModel<String> model = new DefaultListModel<>();
    if (suggestions != null) {
      for (String suggestion : suggestions) {
        if (suggestion == null || suggestion.isBlank()) continue;
        model.addElement(suggestion);
      }
    }

    JList<String> suggestionList = new JList<>(model);
    suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    suggestionList.setVisibleRowCount(Math.min(8, Math.max(4, model.getSize())));
    if (!model.isEmpty()) {
      suggestionList.setSelectedIndex(0);
    }

    JScrollPane scrollPane = new JScrollPane(suggestionList);
    int rowCount = suggestionList.getVisibleRowCount();
    scrollPane.setPreferredSize(new Dimension(280, Math.max(96, rowCount * 22)));

    JPanel content = new JPanel(new BorderLayout(0, 8));
    content.add(new JLabel("Suggestions for \"" + token + "\""), BorderLayout.NORTH);
    content.add(scrollPane, BorderLayout.CENTER);
    if (model.isEmpty()) {
      content.add(new JLabel("No suggestions available."), BorderLayout.SOUTH);
    }

    Object[] options = {"Select", "Cancel"};
    int option =
        JOptionPane.showOptionDialog(
            SwingUtilities.getWindowAncestor(input),
            content,
            "Check spelling",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            options,
            options[0]);
    if (option != JOptionPane.OK_OPTION) return Optional.empty();

    String selectedValue = suggestionList.getSelectedValue();
    if (selectedValue == null || selectedValue.isBlank()) return Optional.empty();
    return Optional.of(selectedValue);
  }
}
