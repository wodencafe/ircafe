package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.app.api.ConnectionState;
import java.awt.event.MouseEvent;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeTooltipProviderTest {

  @Test
  void standardServerTooltipIncludesBackendDisplayName() {
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("plugin");
    root.add(serverNode);
    JTree tree = new JTree(root);
    TreePath serverPath = new TreePath(serverNode.getPath());

    ServerTreeTooltipProvider provider =
        new ServerTreeTooltipProvider(
            tree,
            ServerTreeTooltipProvider.context(
                (x, y) -> "plugin",
                serverId -> serverPath,
                value -> false,
                value -> false,
                value -> null,
                value -> false,
                value -> false,
                value -> false,
                value -> value == serverNode,
                value -> false,
                value -> false,
                serverId -> ConnectionState.CONNECTED,
                serverId -> true,
                serverId -> "",
                serverId -> "Fancy Plugin",
                serverId -> null,
                (backendId, serverId) -> "",
                serverId -> "Plugin Server",
                (backendId, originId, networkKey) -> false,
                () -> false,
                nodeData -> false,
                (serverId, networkToken) -> ""),
            new ServerTreeTooltipTextPolicy());

    MouseEvent event =
        new MouseEvent(
            tree, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 999, 999, 0, false);

    assertEquals(
        "State: Connected. Intent: Online. Backend: Fancy Plugin. Click the row action to disconnect.",
        provider.toolTipForEvent(event));
  }
}
