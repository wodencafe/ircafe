package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeQuasselNetworkNodeData;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.Test;

class ServerTreeQuasselNetworkNodeMenuBuilderTest {

  @Test
  void buildsNetworkMenuAndRoutesActions() {
    List<TargetRef> openedRefs = new ArrayList<>();
    List<String> setupRequests = new ArrayList<>();
    List<String> managerRequests = new ArrayList<>();
    ServerTreeQuasselNetworkNodeMenuBuilder builder =
        new ServerTreeQuasselNetworkNodeMenuBuilder(
            ServerTreeQuasselNetworkNodeMenuBuilder.context(
                openedRefs::add, setupRequests::add, managerRequests::add));
    ServerTreeQuasselNetworkNodeData nodeData =
        ServerTreeQuasselNetworkNodeData.network("quassel", "libera", "Libera");

    JPopupMenu menu = builder.buildNetworkNodeMenu(nodeData);

    assertNotNull(menu);
    assertEquals(4, menu.getComponentCount());

    ((JMenuItem) menu.getComponent(0)).doClick();
    ((JMenuItem) menu.getComponent(2)).doClick();
    ((JMenuItem) menu.getComponent(3)).doClick();

    assertEquals(List.of(TargetRef.channelList("quassel", "libera")), openedRefs);
    assertEquals(List.of("quassel"), managerRequests);
    assertEquals(List.of("quassel"), setupRequests);
  }

  @Test
  void buildsEmptyStateMenuWithoutOpenChannelListAction() {
    ServerTreeQuasselNetworkNodeMenuBuilder builder =
        new ServerTreeQuasselNetworkNodeMenuBuilder(
            ServerTreeQuasselNetworkNodeMenuBuilder.context(ref -> {}, sid -> {}, sid -> {}));
    ServerTreeQuasselNetworkNodeData nodeData =
        ServerTreeQuasselNetworkNodeData.emptyState("quassel", "No Quassel networks configured");

    JPopupMenu menu = builder.buildNetworkNodeMenu(nodeData);

    assertNotNull(menu);
    assertEquals(2, menu.getComponentCount());
  }

  @Test
  void returnsNullForInvalidServer() {
    ServerTreeQuasselNetworkNodeMenuBuilder builder =
        new ServerTreeQuasselNetworkNodeMenuBuilder(
            ServerTreeQuasselNetworkNodeMenuBuilder.context(ref -> {}, sid -> {}, sid -> {}));
    ServerTreeQuasselNetworkNodeData nodeData =
        ServerTreeQuasselNetworkNodeData.network(" ", "libera", "Libera");

    assertNull(builder.buildNetworkNodeMenu(nodeData));
  }
}
