package cafe.woden.ircclient.ui.servertree.composition;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeNodeActionsFactory;
import java.beans.PropertyChangeEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import org.junit.jupiter.api.Test;

class ServerTreeTreeInteractionBindingsFactoryTest {

  @Test
  void createBuildsNodeActionsAndInstallsTreeBindings() throws Exception {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultTreeModel model = new DefaultTreeModel(root);
    JTree tree = new JTree(model);
    AtomicInteger refreshCalls = new AtomicInteger();

    ServerTreeTreeInteractionBindings bindings =
        ServerTreeTreeInteractionBindingsFactory.create(
            new ServerTreeTreeInteractionBindingsFactory.Inputs(
                new ServerTreeNodeActionsFactory(),
                tree,
                model,
                node -> false,
                node -> false,
                ref -> false,
                node -> null,
                node -> "",
                ref -> false,
                ref -> {},
                ref -> {},
                node -> false,
                node -> false,
                node -> "",
                serverId -> {},
                serverId -> {},
                serverId -> {},
                refreshCalls::incrementAndGet,
                () -> {}));

    assertNotNull(bindings.nodeActions());
    assertNotNull(
        tree.getInputMap(JComponent.WHEN_FOCUSED)
            .get(javax.swing.KeyStroke.getKeyStroke("ctrl shift UP")));
    assertNotNull(
        tree.getInputMap(JComponent.WHEN_FOCUSED)
            .get(javax.swing.KeyStroke.getKeyStroke("ctrl shift DOWN")));
    assertNotNull(
        tree.getInputMap(JComponent.WHEN_FOCUSED)
            .get(javax.swing.KeyStroke.getKeyStroke("ctrl W")));
    assertNotNull(
        tree.getInputMap(JComponent.WHEN_FOCUSED)
            .get(javax.swing.KeyStroke.getKeyStroke("DELETE")));
    assertNotNull(
        tree.getInputMap(JComponent.WHEN_FOCUSED)
            .get(javax.swing.KeyStroke.getKeyStroke("ctrl shift O")));
    assertTrue(tree.getPropertyChangeListeners("UI").length > 0);

    for (var listener : tree.getPropertyChangeListeners("UI")) {
      listener.propertyChange(new PropertyChangeEvent(tree, "UI", null, null));
    }
    SwingUtilities.invokeAndWait(() -> {});
    assertTrue(refreshCalls.get() > 0);
  }
}
