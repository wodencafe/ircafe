package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Coordinates unread/highlight counters and notifications for server tree channel nodes. */
public final class ServerTreeUnreadStateCoordinator {

  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final DefaultTreeModel model;
  private final Predicate<TargetRef> isChannelMuted;
  private final Consumer<TargetRef> noteChannelActivity;
  private final Consumer<TargetRef> onChannelUnreadCountsChanged;
  private final Consumer<String> emitManagedChannelsChanged;

  public ServerTreeUnreadStateCoordinator(
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      DefaultTreeModel model,
      Predicate<TargetRef> isChannelMuted,
      Consumer<TargetRef> noteChannelActivity,
      Consumer<TargetRef> onChannelUnreadCountsChanged,
      Consumer<String> emitManagedChannelsChanged) {
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.model = Objects.requireNonNull(model, "model");
    this.isChannelMuted = Objects.requireNonNull(isChannelMuted, "isChannelMuted");
    this.noteChannelActivity = Objects.requireNonNull(noteChannelActivity, "noteChannelActivity");
    this.onChannelUnreadCountsChanged =
        Objects.requireNonNull(onChannelUnreadCountsChanged, "onChannelUnreadCountsChanged");
    this.emitManagedChannelsChanged =
        Objects.requireNonNull(emitManagedChannelsChanged, "emitManagedChannelsChanged");
  }

  public void markUnread(TargetRef ref) {
    bumpUnreadCounter(ref, false);
  }

  public void markHighlight(TargetRef ref) {
    bumpUnreadCounter(ref, true);
  }

  public void clearUnread(TargetRef ref) {
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof ServerTreeNodeData nodeData)) return;
    if (nodeData.unread == 0 && nodeData.highlightUnread == 0) return;
    nodeData.unread = 0;
    nodeData.highlightUnread = 0;
    model.nodeChanged(node);
    onChannelUnreadCountsChanged.accept(ref);
    emitManagedChannelsChangedIfChannel(ref);
  }

  public void onChannelMutedStateChanged(TargetRef ref, boolean muted) {
    if (!ServerTreeConventions.isChannelTarget(ref)) return;
    if (muted) {
      clearUnreadForMutedChannel(ref);
      return;
    }
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node != null) {
      model.nodeChanged(node);
    }
  }

  private void bumpUnreadCounter(TargetRef ref, boolean highlight) {
    if (ServerTreeConventions.isChannelTarget(ref) && isChannelMuted.test(ref)) return;
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof ServerTreeNodeData nodeData)) return;
    if (highlight) {
      nodeData.highlightUnread++;
    } else {
      nodeData.unread++;
    }
    noteChannelActivity.accept(ref);
    model.nodeChanged(node);
    onChannelUnreadCountsChanged.accept(ref);
    emitManagedChannelsChangedIfChannel(ref);
  }

  private void clearUnreadForMutedChannel(TargetRef ref) {
    if (!ServerTreeConventions.isChannelTarget(ref)) return;
    DefaultMutableTreeNode node = leaves.get(ref);
    if (node == null) return;
    if (!(node.getUserObject() instanceof ServerTreeNodeData nodeData)) return;
    if (nodeData.unread == 0 && nodeData.highlightUnread == 0) {
      model.nodeChanged(node);
      return;
    }
    nodeData.unread = 0;
    nodeData.highlightUnread = 0;
    model.nodeChanged(node);
    onChannelUnreadCountsChanged.accept(ref);
    emitManagedChannelsChangedIfChannel(ref);
  }

  private void emitManagedChannelsChangedIfChannel(TargetRef ref) {
    if (!ServerTreeConventions.isChannelTarget(ref)) return;
    String serverId = Objects.toString(ref.serverId(), "").trim();
    if (serverId.isEmpty()) return;
    emitManagedChannelsChanged.accept(serverId);
  }
}
