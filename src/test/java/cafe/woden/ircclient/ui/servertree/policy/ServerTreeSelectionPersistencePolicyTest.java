package cafe.woden.ircclient.ui.servertree.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import cafe.woden.ircclient.model.TargetRef;
import java.util.HashSet;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeSelectionPersistencePolicyTest {

  @Test
  void returnsLastBroadcastSelectionWhenPresent() {
    StubContext context = new StubContext();
    context.lastBroadcast = new TargetRef("libera", "#last");
    context.selectedTarget = new TargetRef("libera", "#selected");

    ServerTreeSelectionPersistencePolicy policy = new ServerTreeSelectionPersistencePolicy(context);

    assertEquals(context.lastBroadcast, policy.selectedTargetForPersistence());
  }

  @Test
  void returnsSelectedTargetWhenNoLastBroadcast() {
    StubContext context = new StubContext();
    context.selectedTarget = new TargetRef("libera", "#selected");

    ServerTreeSelectionPersistencePolicy policy = new ServerTreeSelectionPersistencePolicy(context);

    assertEquals(context.selectedTarget, policy.selectedTargetForPersistence());
  }

  @Test
  void returnsMonitorGroupWhenMonitorNodeSelected() {
    StubContext context = new StubContext();
    DefaultMutableTreeNode monitorNode = new DefaultMutableTreeNode("monitor");
    context.selectedNode = monitorNode;
    context.monitorNodes.add(monitorNode);
    context.serverIdByNode.put(monitorNode, "libera");

    ServerTreeSelectionPersistencePolicy policy = new ServerTreeSelectionPersistencePolicy(context);

    assertEquals(TargetRef.monitorGroup("libera"), policy.selectedTargetForPersistence());
  }

  @Test
  void returnsInterceptorsGroupWhenInterceptorsNodeSelected() {
    StubContext context = new StubContext();
    DefaultMutableTreeNode interceptorsNode = new DefaultMutableTreeNode("interceptors");
    context.selectedNode = interceptorsNode;
    context.interceptorsNodes.add(interceptorsNode);
    context.serverIdByNode.put(interceptorsNode, "libera");

    ServerTreeSelectionPersistencePolicy policy = new ServerTreeSelectionPersistencePolicy(context);

    assertEquals(TargetRef.interceptorsGroup("libera"), policy.selectedTargetForPersistence());
  }

  @Test
  void returnsNullWhenNoSelectableState() {
    StubContext context = new StubContext();

    ServerTreeSelectionPersistencePolicy policy = new ServerTreeSelectionPersistencePolicy(context);
    assertNull(policy.selectedTargetForPersistence());

    DefaultMutableTreeNode unknownNode = new DefaultMutableTreeNode("unknown");
    context.selectedNode = unknownNode;
    context.serverIdByNode.put(unknownNode, " ");
    assertNull(policy.selectedTargetForPersistence());
  }

  @Test
  void returnsSyntheticTargetWhenSelectedNodeMapsToOne() {
    StubContext context = new StubContext();
    DefaultMutableTreeNode networkNode = new DefaultMutableTreeNode("network");
    context.selectedNode = networkNode;
    context.serverIdByNode.put(networkNode, "quassel");
    context.syntheticByNode.put(networkNode, TargetRef.channelList("quassel", "libera"));

    ServerTreeSelectionPersistencePolicy policy = new ServerTreeSelectionPersistencePolicy(context);

    assertEquals(TargetRef.channelList("quassel", "libera"), policy.selectedTargetForPersistence());
  }

  private static final class StubContext implements ServerTreeSelectionPersistencePolicy.Context {
    private TargetRef lastBroadcast = null;
    private TargetRef selectedTarget = null;
    private DefaultMutableTreeNode selectedNode = null;
    private final java.util.Map<DefaultMutableTreeNode, String> serverIdByNode =
        new java.util.HashMap<>();
    private final java.util.Map<DefaultMutableTreeNode, TargetRef> syntheticByNode =
        new java.util.HashMap<>();
    private final Set<DefaultMutableTreeNode> monitorNodes = new HashSet<>();
    private final Set<DefaultMutableTreeNode> interceptorsNodes = new HashSet<>();

    @Override
    public TargetRef lastBroadcastSelection() {
      return lastBroadcast;
    }

    @Override
    public TargetRef selectedTargetRef() {
      return selectedTarget;
    }

    @Override
    public DefaultMutableTreeNode selectedTreeNode() {
      return selectedNode;
    }

    @Override
    public String owningServerIdForNode(DefaultMutableTreeNode node) {
      return serverIdByNode.get(node);
    }

    @Override
    public boolean isMonitorGroupNode(DefaultMutableTreeNode node) {
      return monitorNodes.contains(node);
    }

    @Override
    public boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
      return interceptorsNodes.contains(node);
    }

    @Override
    public TargetRef syntheticTargetForNode(DefaultMutableTreeNode node) {
      return syntheticByNode.get(node);
    }
  }
}
