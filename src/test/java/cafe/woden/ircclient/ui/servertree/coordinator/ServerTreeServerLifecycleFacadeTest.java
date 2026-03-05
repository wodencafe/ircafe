package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeServerLifecycleFacadeTest {

  @Test
  void delegatesLifecycleAndStatusLabelUpdates() {
    ServerTreeServerRootLifecycleManager rootLifecycleManager =
        mock(ServerTreeServerRootLifecycleManager.class);
    ServerTreeStatusLabelManager statusLabelManager = mock(ServerTreeStatusLabelManager.class);
    ServerTreeServerLifecycleFacade facade =
        new ServerTreeServerLifecycleFacade(rootLifecycleManager, statusLabelManager);

    ServerNodes nodes = serverNodes("libera");
    when(rootLifecycleManager.addServerRoot("libera")).thenReturn(nodes);

    assertSame(nodes, facade.addServerRoot("libera"));
    facade.removeServerRoot("libera");

    Set<String> soju = Set.of("soju-origin");
    Set<String> znc = Set.of("znc-origin");
    Set<String> generic = Set.of("bouncer-origin");
    facade.updateBouncerControlLabels(soju, znc, generic);

    verify(rootLifecycleManager).addServerRoot("libera");
    verify(rootLifecycleManager).removeServerRoot("libera");
    verify(statusLabelManager).updateBouncerControlLabels(soju, znc, generic);
  }

  private static ServerNodes serverNodes(String serverId) {
    DefaultMutableTreeNode serverNode = new DefaultMutableTreeNode(serverId);
    DefaultMutableTreeNode pmNode = new DefaultMutableTreeNode("Private Messages");
    DefaultMutableTreeNode otherNode = new DefaultMutableTreeNode("Other");
    DefaultMutableTreeNode monitorNode = new DefaultMutableTreeNode("Monitor");
    DefaultMutableTreeNode interceptorsNode = new DefaultMutableTreeNode("Interceptors");
    return new ServerNodes(
        serverNode,
        pmNode,
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
  }
}
