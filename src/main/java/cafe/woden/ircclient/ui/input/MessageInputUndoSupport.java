package cafe.woden.ircclient.ui.input;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Undo/Redo support for {@link MessageInputPanel}.
 *
 * <p>This groups single-character insert/remove edits into a short compound edit so that Ctrl+Z
 * behaves like users expect (undoing words rather than single characters), without trying to be too
 * clever.
 */
public final class MessageInputUndoSupport {

  private static final Logger log = LoggerFactory.getLogger(MessageInputUndoSupport.class);

  private static final int UNDO_GROUP_WINDOW_MS = 800;

  private final JTextComponent input;
  private final BooleanSupplier isProgrammaticEdit;

  private final UndoManager undo = new UndoManager();
  private final Timer undoGroupTimer;
  private CompoundEdit activeCompoundEdit;
  private DocumentEvent.EventType activeCompoundType;
  private long lastUndoEditAtMs;

  private final Action undoAction =
      new AbstractAction("Undo") {
        @Override
        public void actionPerformed(ActionEvent e) {
          try {
            endCompoundEdit();
            if (undo.canUndo()) undo.undo();
          } catch (CannotUndoException ignored) {
          }
          refreshActions();
        }
      };

  private final Action redoAction =
      new AbstractAction("Redo") {
        @Override
        public void actionPerformed(ActionEvent e) {
          try {
            endCompoundEdit();
            if (undo.canRedo()) undo.redo();
          } catch (CannotRedoException ignored) {
          }
          refreshActions();
        }
      };

  public MessageInputUndoSupport(JTextComponent input, BooleanSupplier isProgrammaticEdit) {
    this.input = Objects.requireNonNull(input, "input");
    this.isProgrammaticEdit = isProgrammaticEdit != null ? isProgrammaticEdit : () -> false;

    this.undoGroupTimer = new Timer(UNDO_GROUP_WINDOW_MS, e -> endCompoundEdit());
    this.undoGroupTimer.setRepeats(false);
    refreshActions();
  }

  public Action getUndoAction() {
    return undoAction;
  }

  public Action getRedoAction() {
    return redoAction;
  }

  public void installKeybindings() {
    int menuMask;
    try {
      menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    } catch (Exception ex) {
      menuMask = KeyEvent.CTRL_DOWN_MASK;
    }
    InputMap im = input.getInputMap(JComponent.WHEN_FOCUSED);
    ActionMap am = input.getActionMap();
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask), "ircafe.undo");
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menuMask), "ircafe.redo");
    im.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_Z, menuMask | KeyEvent.SHIFT_DOWN_MASK), "ircafe.redo");
    am.put("ircafe.undo", undoAction);
    am.put("ircafe.redo", redoAction);
    refreshActions();
  }

  public void handleUndoableEdit(UndoableEdit edit) {
    if (edit == null) return;
    if (isProgrammaticEdit.getAsBoolean()) return;

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
    boolean groupable =
        (type == DocumentEvent.EventType.INSERT || type == DocumentEvent.EventType.REMOVE)
            && len == 1;
    if (!groupable) {
      endCompoundEdit();
      undo.addEdit(edit);
      refreshActions();
      return;
    }

    boolean expired = activeCompoundEdit != null && (now - lastUndoEditAtMs) > UNDO_GROUP_WINDOW_MS;
    boolean typeChanged = activeCompoundEdit != null && !Objects.equals(activeCompoundType, type);
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
        refreshActions();
        return;
      }
      activeCompoundEdit = ce;
      activeCompoundType = type;
    } else {
      activeCompoundEdit.addEdit(edit);
    }

    lastUndoEditAtMs = now;
    undoGroupTimer.restart();
    refreshActions();

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

  public void endCompoundEdit() {
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
    refreshActions();
  }

  public void discardAllEdits() {
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
    refreshActions();
  }

  public void refreshActions() {
    try {
      // If we're mid-group, UndoManager can't undo yet because CompoundEdit is "inProgress".
      // But users still expect Ctrl+Z to work immediately; the action will finalize the group
      // first.
      undoAction.setEnabled(undo.canUndo() || activeCompoundEdit != null);
      redoAction.setEnabled(undo.canRedo());
    } catch (Exception ex) {
      log.warn("[MessageInputUndoSupport] refresh failed", ex);
    }
  }
}
