package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelStateCoordinator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Adapter for {@link ServerTreeChannelStateCoordinator.Context}. */
public final class ServerTreeChannelStateCoordinatorContextAdapter
    implements ServerTreeChannelStateCoordinator.Context {

  private final Function<String, String> normalizeServerId;
  private final Function<String, DefaultMutableTreeNode> channelListNode;
  private final Supplier<Set<TreePath>> snapshotExpandedTreePaths;
  private final Consumer<Set<TreePath>> restoreExpandedTreePaths;
  private final Consumer<String> emitManagedChannelsChanged;

  public ServerTreeChannelStateCoordinatorContextAdapter(
      Function<String, String> normalizeServerId,
      Function<String, DefaultMutableTreeNode> channelListNode,
      Supplier<Set<TreePath>> snapshotExpandedTreePaths,
      Consumer<Set<TreePath>> restoreExpandedTreePaths,
      Consumer<String> emitManagedChannelsChanged) {
    this.normalizeServerId = Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    this.channelListNode = Objects.requireNonNull(channelListNode, "channelListNode");
    this.snapshotExpandedTreePaths =
        Objects.requireNonNull(snapshotExpandedTreePaths, "snapshotExpandedTreePaths");
    this.restoreExpandedTreePaths =
        Objects.requireNonNull(restoreExpandedTreePaths, "restoreExpandedTreePaths");
    this.emitManagedChannelsChanged =
        Objects.requireNonNull(emitManagedChannelsChanged, "emitManagedChannelsChanged");
  }

  @Override
  public String normalizeServerId(String serverId) {
    return normalizeServerId.apply(serverId);
  }

  @Override
  public DefaultMutableTreeNode channelListNode(String serverId) {
    return channelListNode.apply(serverId);
  }

  @Override
  public Set<TreePath> snapshotExpandedTreePaths() {
    return snapshotExpandedTreePaths.get();
  }

  @Override
  public void restoreExpandedTreePaths(Set<TreePath> expanded) {
    restoreExpandedTreePaths.accept(expanded);
  }

  @Override
  public void emitManagedChannelsChanged(String serverId) {
    emitManagedChannelsChanged.accept(serverId);
  }
}
