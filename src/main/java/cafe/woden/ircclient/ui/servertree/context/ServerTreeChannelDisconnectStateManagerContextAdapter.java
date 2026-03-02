package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelDisconnectStateManager;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeChannelDisconnectStateManager.Context}. */
public final class ServerTreeChannelDisconnectStateManagerContextAdapter
    implements ServerTreeChannelDisconnectStateManager.Context {

  private final Consumer<TargetRef> ensureNode;
  private final Function<TargetRef, DefaultMutableTreeNode> leafNode;
  private final Consumer<DefaultMutableTreeNode> nodeChanged;
  private final Consumer<String> emitManagedChannelsChanged;

  public ServerTreeChannelDisconnectStateManagerContextAdapter(
      Consumer<TargetRef> ensureNode,
      Function<TargetRef, DefaultMutableTreeNode> leafNode,
      Consumer<DefaultMutableTreeNode> nodeChanged,
      Consumer<String> emitManagedChannelsChanged) {
    this.ensureNode = Objects.requireNonNull(ensureNode, "ensureNode");
    this.leafNode = Objects.requireNonNull(leafNode, "leafNode");
    this.nodeChanged = Objects.requireNonNull(nodeChanged, "nodeChanged");
    this.emitManagedChannelsChanged =
        Objects.requireNonNull(emitManagedChannelsChanged, "emitManagedChannelsChanged");
  }

  @Override
  public void ensureNode(TargetRef ref) {
    ensureNode.accept(ref);
  }

  @Override
  public DefaultMutableTreeNode leafNode(TargetRef ref) {
    return leafNode.apply(ref);
  }

  @Override
  public void nodeChanged(DefaultMutableTreeNode node) {
    nodeChanged.accept(node);
  }

  @Override
  public void emitManagedChannelsChanged(String serverId) {
    emitManagedChannelsChanged.accept(serverId);
  }
}
