package cafe.woden.ircclient.ui.servertree.coordinator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreePrivateMessageOnlineStateCoordinatorTest {

  @Test
  void setPrivateMessageOnlineStateStoresStateAndRefreshesNode() {
    ServerTreePrivateMessageOnlineStateStore store = new ServerTreePrivateMessageOnlineStateStore();
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    List<DefaultMutableTreeNode> changedNodes = new ArrayList<>();
    ServerTreePrivateMessageOnlineStateCoordinator coordinator =
        new ServerTreePrivateMessageOnlineStateCoordinator(
            store,
            leaves,
            changedNodes::add,
            ref -> ref != null && !ref.isStatus() && !ref.isChannel() && !ref.isUiOnly());

    TargetRef pm = new TargetRef("libera", "alice");
    DefaultMutableTreeNode pmNode = new DefaultMutableTreeNode("alice");
    leaves.put(pm, pmNode);

    coordinator.setPrivateMessageOnlineState(" libera ", "alice", true);

    assertTrue(store.isOnline(pm));
    assertTrue(changedNodes.contains(pmNode));
  }

  @Test
  void setPrivateMessageOnlineStateIgnoresNonPrivateMessageTargets() {
    ServerTreePrivateMessageOnlineStateStore store = new ServerTreePrivateMessageOnlineStateStore();
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    List<DefaultMutableTreeNode> changedNodes = new ArrayList<>();
    ServerTreePrivateMessageOnlineStateCoordinator coordinator =
        new ServerTreePrivateMessageOnlineStateCoordinator(
            store,
            leaves,
            changedNodes::add,
            ref -> ref != null && !ref.isStatus() && !ref.isChannel() && !ref.isUiOnly());

    TargetRef channel = new TargetRef("libera", "#ircafe");
    leaves.put(channel, new DefaultMutableTreeNode("#ircafe"));

    coordinator.setPrivateMessageOnlineState("libera", "#ircafe", true);
    coordinator.setPrivateMessageOnlineState(" ", "alice", true);
    coordinator.setPrivateMessageOnlineState("libera", " ", true);

    assertFalse(store.isOnline(channel));
    assertTrue(changedNodes.isEmpty());
  }

  @Test
  void clearPrivateMessageOnlineStatesClearsServerEntriesAndRefreshesNodes() {
    ServerTreePrivateMessageOnlineStateStore store = new ServerTreePrivateMessageOnlineStateStore();
    Map<TargetRef, DefaultMutableTreeNode> leaves = new HashMap<>();
    List<DefaultMutableTreeNode> changedNodes = new ArrayList<>();
    ServerTreePrivateMessageOnlineStateCoordinator coordinator =
        new ServerTreePrivateMessageOnlineStateCoordinator(
            store,
            leaves,
            changedNodes::add,
            ref -> ref != null && !ref.isStatus() && !ref.isChannel() && !ref.isUiOnly());

    TargetRef pm1 = new TargetRef("libera", "alice");
    TargetRef pm2 = new TargetRef("libera", "bob");
    TargetRef pmOtherServer = new TargetRef("oftc", "carol");
    DefaultMutableTreeNode pm1Node = new DefaultMutableTreeNode("alice");
    leaves.put(pm1, pm1Node);
    store.put(pm1, true);
    store.put(pm2, false);
    store.put(pmOtherServer, true);

    coordinator.clearPrivateMessageOnlineStates("libera");

    assertFalse(store.isOnline(pm1));
    assertFalse(store.isOnline(pm2));
    assertTrue(store.isOnline(pmOtherServer));
    assertTrue(changedNodes.contains(pm1Node));
  }
}
