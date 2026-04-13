package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.state.ServerTreePrivateMessageOnlineStateStore;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;
import org.springframework.stereotype.Component;

/** Coordinates private-message online-state updates and affected tree node refreshes. */
@Component
public final class ServerTreePrivateMessageOnlineStateCoordinator {

  public interface Context {
    boolean isPrivateMessageTarget(TargetRef ref);

    void storeOnlineState(TargetRef ref, boolean online);

    List<TargetRef> clearServer(String serverId);

    DefaultMutableTreeNode nodeFor(TargetRef ref);

    void nodeChanged(DefaultMutableTreeNode node);
  }

  public static Context context(
      ServerTreePrivateMessageOnlineStateStore privateMessageOnlineStateStore,
      java.util.Map<TargetRef, DefaultMutableTreeNode> leaves,
      Consumer<DefaultMutableTreeNode> nodeChanged,
      Predicate<TargetRef> isPrivateMessageTarget) {
    Objects.requireNonNull(privateMessageOnlineStateStore, "privateMessageOnlineStateStore");
    Objects.requireNonNull(leaves, "leaves");
    Objects.requireNonNull(nodeChanged, "nodeChanged");
    Objects.requireNonNull(isPrivateMessageTarget, "isPrivateMessageTarget");
    return new Context() {
      @Override
      public boolean isPrivateMessageTarget(TargetRef ref) {
        return isPrivateMessageTarget.test(ref);
      }

      @Override
      public void storeOnlineState(TargetRef ref, boolean online) {
        privateMessageOnlineStateStore.put(ref, online);
      }

      @Override
      public List<TargetRef> clearServer(String serverId) {
        return privateMessageOnlineStateStore.clearServer(serverId);
      }

      @Override
      public DefaultMutableTreeNode nodeFor(TargetRef ref) {
        return leaves.get(ref);
      }

      @Override
      public void nodeChanged(DefaultMutableTreeNode node) {
        nodeChanged.accept(node);
      }
    };
  }

  public void setPrivateMessageOnlineState(
      Context context, String serverId, String nick, boolean online) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = Objects.toString(serverId, "").trim();
    String n = Objects.toString(nick, "").trim();
    if (sid.isEmpty() || n.isEmpty()) return;

    TargetRef pm;
    try {
      pm = new TargetRef(sid, n);
    } catch (Exception ignored) {
      return;
    }
    if (!in.isPrivateMessageTarget(pm)) return;

    in.storeOnlineState(pm, online);
    refreshNode(in, pm);
  }

  public void clearPrivateMessageOnlineStates(Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    for (TargetRef ref : in.clearServer(sid)) {
      refreshNode(in, ref);
    }
  }

  private void refreshNode(Context context, TargetRef ref) {
    DefaultMutableTreeNode node = context.nodeFor(ref);
    if (node != null) {
      context.nodeChanged(node);
    }
  }
}
