package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;

/** Coordinates private-message online-state updates and affected tree node refreshes. */
public final class ServerTreePrivateMessageOnlineStateCoordinator {
  private final ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final Consumer<DefaultMutableTreeNode> nodeChanged;
  private final Predicate<TargetRef> isPrivateMessageTarget;

  public ServerTreePrivateMessageOnlineStateCoordinator(
      ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Consumer<DefaultMutableTreeNode> nodeChanged,
      Predicate<TargetRef> isPrivateMessageTarget) {
    this.privateMessageOnlineStateStore =
        Objects.requireNonNull(privateMessageOnlineStateStore, "privateMessageOnlineStateStore");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.nodeChanged = Objects.requireNonNull(nodeChanged, "nodeChanged");
    this.isPrivateMessageTarget =
        Objects.requireNonNull(isPrivateMessageTarget, "isPrivateMessageTarget");
  }

  public void setPrivateMessageOnlineState(String serverId, String nick, boolean online) {
    String sid = Objects.toString(serverId, "").trim();
    String n = Objects.toString(nick, "").trim();
    if (sid.isEmpty() || n.isEmpty()) return;

    TargetRef pm;
    try {
      pm = new TargetRef(sid, n);
    } catch (Exception ignored) {
      return;
    }
    if (!isPrivateMessageTarget.test(pm)) return;

    privateMessageOnlineStateStore.put(pm, online);
    refreshNode(pm);
  }

  public void clearPrivateMessageOnlineStates(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    for (TargetRef ref : privateMessageOnlineStateStore.clearServer(sid)) {
      refreshNode(ref);
    }
  }

  private void refreshNode(TargetRef ref) {
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node != null) {
      nodeChanged.accept(node);
    }
  }
}
