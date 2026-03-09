package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    List<String> connectRequests = new ArrayList<>();
    List<String> disconnectRequests = new ArrayList<>();
    List<String> removeRequests = new ArrayList<>();
    List<String> removeConfirmRequests = new ArrayList<>();
    List<String> addRequests = new ArrayList<>();
    List<String> setupRequests = new ArrayList<>();
    List<String> managerRequests = new ArrayList<>();
    ServerTreeQuasselNetworkNodeMenuBuilder builder =
        new ServerTreeQuasselNetworkNodeMenuBuilder(
            ServerTreeQuasselNetworkNodeMenuBuilder.context(
                openedRefs::add,
                (sid, token) -> connectRequests.add(sid + ":" + token),
                (sid, token) -> disconnectRequests.add(sid + ":" + token),
                (sid, token) -> removeRequests.add(sid + ":" + token),
                addRequests::add,
                (sid, token, label) -> {
                  removeConfirmRequests.add(sid + ":" + token + ":" + label);
                  return true;
                },
                setupRequests::add,
                managerRequests::add,
                sid -> true));
    ServerTreeQuasselNetworkNodeData nodeData =
        ServerTreeQuasselNetworkNodeData.network("quassel", "libera", "Libera");

    JPopupMenu menu = builder.buildNetworkNodeMenu(nodeData);

    assertNotNull(menu);
    assertEquals(7, menu.getComponentCount());

    ((JMenuItem) menu.getComponent(0)).doClick();
    ((JMenuItem) menu.getComponent(1)).doClick();
    ((JMenuItem) menu.getComponent(2)).doClick();
    ((JMenuItem) menu.getComponent(3)).doClick();
    ((JMenuItem) menu.getComponent(5)).doClick();
    ((JMenuItem) menu.getComponent(6)).doClick();

    assertEquals(List.of(TargetRef.channelList("quassel", "libera")), openedRefs);
    assertEquals(List.of("quassel:libera"), connectRequests);
    assertEquals(List.of("quassel:libera"), disconnectRequests);
    assertEquals(List.of("quassel:libera"), removeRequests);
    assertEquals(List.of("quassel:libera:Libera"), removeConfirmRequests);
    assertEquals(List.of(), addRequests);
    assertEquals(List.of("quassel"), managerRequests);
    assertEquals(List.of("quassel"), setupRequests);
  }

  @Test
  void networkMenuDisablesConnectWhenAlreadyConnectedAndSupportsRemoveConfirmation() {
    List<String> removeRequests = new ArrayList<>();
    ServerTreeQuasselNetworkNodeMenuBuilder builder =
        new ServerTreeQuasselNetworkNodeMenuBuilder(
            ServerTreeQuasselNetworkNodeMenuBuilder.context(
                ref -> {},
                (sid, token) -> {},
                (sid, token) -> {},
                (sid, token) -> removeRequests.add(sid + ":" + token),
                sid -> {},
                (sid, token, label) -> false,
                sid -> {},
                sid -> {},
                sid -> false));
    ServerTreeQuasselNetworkNodeData nodeData =
        ServerTreeQuasselNetworkNodeData.network("quassel", "libera", "Libera", true, true);

    JPopupMenu menu = builder.buildNetworkNodeMenu(nodeData);

    assertNotNull(menu);
    JMenuItem connect = (JMenuItem) menu.getComponent(1);
    JMenuItem disconnect = (JMenuItem) menu.getComponent(2);
    JMenuItem remove = (JMenuItem) menu.getComponent(3);
    assertFalse(connect.isEnabled());
    assertTrue(disconnect.isEnabled());
    assertTrue(remove.isEnabled());

    remove.doClick();
    assertTrue(removeRequests.isEmpty());
  }

  @Test
  void networkMenuDisablesConnectAndDisconnectWhenNetworkDisabled() {
    ServerTreeQuasselNetworkNodeMenuBuilder builder =
        new ServerTreeQuasselNetworkNodeMenuBuilder(
            ServerTreeQuasselNetworkNodeMenuBuilder.context(
                ref -> {},
                (sid, token) -> {},
                (sid, token) -> {},
                (sid, token) -> {},
                sid -> {},
                (sid, token, label) -> true,
                sid -> {},
                sid -> {},
                sid -> false));
    ServerTreeQuasselNetworkNodeData nodeData =
        ServerTreeQuasselNetworkNodeData.network("quassel", "libera", "Libera", false, false);

    JPopupMenu menu = builder.buildNetworkNodeMenu(nodeData);

    assertNotNull(menu);
    assertFalse(((JMenuItem) menu.getComponent(1)).isEnabled());
    assertFalse(((JMenuItem) menu.getComponent(2)).isEnabled());
  }

  @Test
  void buildsEmptyStateMenuWithoutOpenChannelListAction() {
    List<String> addRequests = new ArrayList<>();
    List<String> setupRequests = new ArrayList<>();
    List<String> managerRequests = new ArrayList<>();
    ServerTreeQuasselNetworkNodeMenuBuilder builder =
        new ServerTreeQuasselNetworkNodeMenuBuilder(
            ServerTreeQuasselNetworkNodeMenuBuilder.context(
                ref -> {},
                (sid, token) -> {},
                (sid, token) -> {},
                (sid, token) -> {},
                addRequests::add,
                (sid, token, label) -> true,
                setupRequests::add,
                managerRequests::add,
                sid -> true));
    ServerTreeQuasselNetworkNodeData nodeData =
        ServerTreeQuasselNetworkNodeData.emptyState("quassel", "No Quassel networks configured");

    JPopupMenu menu = builder.buildNetworkNodeMenu(nodeData);

    assertNotNull(menu);
    assertEquals(4, menu.getComponentCount());

    ((JMenuItem) menu.getComponent(0)).doClick();
    ((JMenuItem) menu.getComponent(2)).doClick();
    ((JMenuItem) menu.getComponent(3)).doClick();

    assertEquals(List.of("quassel"), addRequests);
    assertEquals(List.of("quassel"), managerRequests);
    assertEquals(List.of("quassel"), setupRequests);
  }

  @Test
  void returnsNullForInvalidServer() {
    ServerTreeQuasselNetworkNodeMenuBuilder builder =
        new ServerTreeQuasselNetworkNodeMenuBuilder(
            ServerTreeQuasselNetworkNodeMenuBuilder.context(
                ref -> {},
                (sid, token) -> {},
                (sid, token) -> {},
                (sid, token) -> {},
                sid -> {},
                (sid, token, label) -> true,
                sid -> {},
                sid -> {},
                sid -> false));
    ServerTreeQuasselNetworkNodeData nodeData =
        ServerTreeQuasselNetworkNodeData.network(" ", "libera", "Libera");

    assertNull(builder.buildNetworkNodeMenu(nodeData));
  }

  @Test
  void omitsSetupActionWhenSetupNotPending() {
    ServerTreeQuasselNetworkNodeMenuBuilder builder =
        new ServerTreeQuasselNetworkNodeMenuBuilder(
            ServerTreeQuasselNetworkNodeMenuBuilder.context(
                ref -> {},
                (sid, token) -> {},
                (sid, token) -> {},
                (sid, token) -> {},
                sid -> {},
                (sid, token, label) -> true,
                sid -> {},
                sid -> {},
                sid -> false));
    ServerTreeQuasselNetworkNodeMenuBuilder pendingBuilder =
        new ServerTreeQuasselNetworkNodeMenuBuilder(
            ServerTreeQuasselNetworkNodeMenuBuilder.context(
                ref -> {},
                (sid, token) -> {},
                (sid, token) -> {},
                (sid, token) -> {},
                sid -> {},
                (sid, token, label) -> true,
                sid -> {},
                sid -> {},
                sid -> true));
    ServerTreeQuasselNetworkNodeData nodeData =
        ServerTreeQuasselNetworkNodeData.network("quassel", "libera", "Libera");

    JPopupMenu menu = builder.buildNetworkNodeMenu(nodeData);
    JPopupMenu pendingMenu = pendingBuilder.buildNetworkNodeMenu(nodeData);

    assertNotNull(menu);
    assertNotNull(pendingMenu);
    assertEquals(6, menu.getComponentCount());
    assertEquals(7, pendingMenu.getComponentCount());
  }
}
