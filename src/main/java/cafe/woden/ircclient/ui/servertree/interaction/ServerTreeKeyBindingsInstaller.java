package cafe.woden.ircclient.ui.servertree.interaction;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.KeyStroke;

/** Installs key bindings and actions used by the server tree. */
public final class ServerTreeKeyBindingsInstaller {

  private ServerTreeKeyBindingsInstaller() {}

  public static void install(
      JTree tree,
      Supplier<Action> moveNodeUpAction,
      Supplier<Action> moveNodeDownAction,
      Supplier<Action> closeNodeAction,
      Runnable openSelectedNodeInChatDock) {
    Objects.requireNonNull(tree, "tree");
    Objects.requireNonNull(moveNodeUpAction, "moveNodeUpAction");
    Objects.requireNonNull(moveNodeDownAction, "moveNodeDownAction");
    Objects.requireNonNull(closeNodeAction, "closeNodeAction");
    Objects.requireNonNull(openSelectedNodeInChatDock, "openSelectedNodeInChatDock");

    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(
            KeyStroke.getKeyStroke(
                KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            "ircafe.tree.nodeMoveUp");
    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(
            KeyStroke.getKeyStroke(
                KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            "ircafe.tree.nodeMoveDown");
    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(
            KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK),
            "ircafe.tree.nodeMoveUp");
    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(
            KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK),
            "ircafe.tree.nodeMoveDown");
    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(
            KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK),
            "ircafe.tree.closeNode");
    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "ircafe.tree.closeNode");
    tree.getInputMap(JComponent.WHEN_FOCUSED)
        .put(
            KeyStroke.getKeyStroke(
                KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            "ircafe.tree.openPinnedDock");

    tree.getActionMap()
        .put(
            "ircafe.tree.nodeMoveUp",
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent event) {
                moveNodeUpAction.get().actionPerformed(event);
              }
            });
    tree.getActionMap()
        .put(
            "ircafe.tree.nodeMoveDown",
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent event) {
                moveNodeDownAction.get().actionPerformed(event);
              }
            });
    tree.getActionMap()
        .put(
            "ircafe.tree.closeNode",
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent event) {
                closeNodeAction.get().actionPerformed(event);
              }
            });
    tree.getActionMap()
        .put(
            "ircafe.tree.openPinnedDock",
            new AbstractAction() {
              @Override
              public void actionPerformed(java.awt.event.ActionEvent event) {
                openSelectedNodeInChatDock.run();
              }
            });
  }
}
