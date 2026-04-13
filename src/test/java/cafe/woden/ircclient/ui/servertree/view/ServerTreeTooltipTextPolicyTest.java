package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeQuasselNetworkNodeData;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeTooltipTextPolicyTest {

  private final ServerTreeTooltipTextPolicy policy = new ServerTreeTooltipTextPolicy();

  @Test
  void standardServerTooltipIncludesBackendDisplayName() {
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode("plugin");

    String tip =
        policy.toolTipForNode(
            ServerTreeTooltipProvider.context(
                (x, y) -> "plugin",
                serverId -> null,
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
            serverNode);

    assertEquals(
        "State: Connected. Intent: Online. Backend: Fancy Plugin. Click the row action to disconnect.",
        tip);
  }

  @Test
  void applicationJfrTooltipReflectsRuntimeActivationState() {
    DefaultMutableTreeNode jfrNode =
        new DefaultMutableTreeNode(new ServerTreeNodeData(TargetRef.applicationJfr(), "JFR"));

    String enabledTip = policy.toolTipForNode(applicationNodeContext(jfrNode, true), jfrNode);
    String disabledTip = policy.toolTipForNode(applicationNodeContext(jfrNode, false), jfrNode);

    assertEquals(
        "Runtime JFR diagnostics are active (status gauges + JFR event stream).", enabledTip);
    assertEquals("Runtime JFR diagnostics are disabled. Open the JFR view to enable.", disabledTip);
  }

  @Test
  void quasselNetworkTooltipFallsBackToConnectionStateSummary() {
    ServerTreeQuasselNetworkNodeData nodeData =
        ServerTreeQuasselNetworkNodeData.network("quassel", "libera", "Libera", false, true);
    DefaultMutableTreeNode networkNode = new DefaultMutableTreeNode(nodeData);

    String tip =
        policy.toolTipForNode(
            ServerTreeTooltipProvider.context(
                (x, y) -> "",
                serverId -> null,
                value -> false,
                value -> false,
                value -> null,
                value -> false,
                value -> false,
                value -> false,
                value -> false,
                value -> value == networkNode,
                value -> false,
                serverId -> ConnectionState.DISCONNECTED,
                serverId -> false,
                serverId -> "",
                serverId -> "",
                serverId -> null,
                (backendId, serverId) -> "",
                serverId -> serverId,
                (backendId, originId, networkKey) -> false,
                () -> false,
                node -> false,
                (serverId, networkToken) -> ""),
            networkNode);

    assertEquals("Quassel network \"Libera\" (disconnected, token: libera).", tip);
  }

  private static ServerTreeTooltipProvider.Context applicationNodeContext(
      DefaultMutableTreeNode node, boolean applicationJfrActive) {
    return ServerTreeTooltipProvider.context(
        (x, y) -> "",
        serverId -> null,
        value -> false,
        value -> false,
        value -> null,
        value -> false,
        value -> false,
        value -> false,
        value -> false,
        value -> false,
        value -> false,
        serverId -> ConnectionState.DISCONNECTED,
        serverId -> false,
        serverId -> "",
        serverId -> "",
        serverId -> null,
        (backendId, serverId) -> "",
        serverId -> serverId,
        (backendId, originId, networkKey) -> false,
        () -> applicationJfrActive,
        nodeData -> false,
        (serverId, networkToken) -> "");
  }
}
