package cafe.woden.ircclient.ui.servertree.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeStartupSelectionRestorerContextAdapterTest {

  @Test
  void monitorAndInterceptorsGroupSelectableRequireAttachmentUnderServerOrOther() {
    String serverId = "libera";
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode(serverId);
    DefaultMutableTreeNode otherNode = new DefaultMutableTreeNode("other");
    DefaultMutableTreeNode monitorNode = new DefaultMutableTreeNode("monitor");
    DefaultMutableTreeNode interceptorsNode = new DefaultMutableTreeNode("interceptors");

    serverNode.add(otherNode);
    serverNode.add(monitorNode);
    otherNode.add(interceptorsNode);

    ServerNodes nodes =
        new ServerNodes(
            serverNode,
            new DefaultMutableTreeNode("pm"),
            otherNode,
            monitorNode,
            interceptorsNode,
            new TargetRef(serverId, "status"),
            TargetRef.notifications(serverId),
            TargetRef.logViewer(serverId),
            TargetRef.channelList(serverId),
            TargetRef.weechatFilters(serverId),
            TargetRef.ignores(serverId),
            TargetRef.dccTransfers(serverId));
    Map<String, ServerNodes> servers = Map.of(serverId, nodes);

    TargetRef leaf = new TargetRef(serverId, "#ircafe");
    Set<TargetRef> leaves = Set.of(leaf);
    List<TargetRef> selections = new ArrayList<>();

    ServerTreeStartupSelectionRestorerContextAdapter adapter =
        new ServerTreeStartupSelectionRestorerContextAdapter(
            id -> Objects.toString(id, "").trim(),
            leaves::contains,
            sid -> {
              ServerNodes sn = servers.get(sid);
              return sn == null ? null : sn.monitorNode;
            },
            sid -> {
              ServerNodes sn = servers.get(sid);
              return sn == null ? null : sn.interceptorsNode;
            },
            servers::get,
            selections::add);

    assertEquals(serverId, adapter.normalizeServerId(" libera "));
    assertTrue(adapter.hasLeaf(leaf));
    assertTrue(adapter.isMonitorGroupSelectable(serverId));
    assertTrue(adapter.isInterceptorsGroupSelectable(serverId));

    interceptorsNode.removeFromParent();
    assertFalse(adapter.isInterceptorsGroupSelectable(serverId));
  }

  @Test
  void selectTargetDelegates() {
    List<TargetRef> selections = new ArrayList<>();
    ServerTreeStartupSelectionRestorerContextAdapter adapter =
        new ServerTreeStartupSelectionRestorerContextAdapter(
            id -> Objects.toString(id, ""),
            ref -> false,
            sid -> null,
            sid -> null,
            sid -> null,
            selections::add);

    TargetRef ref = new TargetRef("libera", "#target");
    adapter.selectTarget(ref);

    assertEquals(List.of(ref), selections);
  }
}
