package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeStatusLabelManagerTest {

  private final ServerTreeStatusLabelManager manager = new ServerTreeStatusLabelManager();

  @Test
  void statusLeafLabelReflectsBouncerControlMembership() {
    Map<String, Set<String>> bouncerControlByBackend = new HashMap<>();
    bouncerControlByBackend.put(ServerTreeBouncerBackends.SOJU, new HashSet<>(Set.of("soju:work")));

    ServerTreeStatusLabelManager.Context context =
        ServerTreeStatusLabelManager.context(
            "Server", "Bouncer Control", bouncerControlByBackend, new HashMap<>(), __ -> {});

    assertEquals("Server", manager.statusLeafLabelForServer(context, "libera"));
    assertEquals("Bouncer Control", manager.statusLeafLabelForServer(context, "soju:work"));
  }

  @Test
  void updateBouncerControlLabelsRewritesAffectedStatusNodes() {
    TargetRef statusRef = new TargetRef("libera", "status");
    DefaultMutableTreeNode statusNode =
        new DefaultMutableTreeNode(new ServerTreeNodeData(statusRef, "Server"));
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    leaves.put(statusRef, statusNode);

    DefaultMutableTreeNode[] changed = new DefaultMutableTreeNode[1];
    ServerTreeStatusLabelManager.Context context =
        ServerTreeStatusLabelManager.context(
            "Server", "Bouncer Control", new HashMap<>(), leaves, node -> changed[0] = node);

    manager.updateBouncerControlLabels(
        context, Map.of(ServerTreeBouncerBackends.GENERIC, Set.of("libera")));

    assertSame(statusNode, changed[0]);
    Object userObject = statusNode.getUserObject();
    assertEquals("Bouncer Control", ((ServerTreeNodeData) userObject).label);
  }
}
