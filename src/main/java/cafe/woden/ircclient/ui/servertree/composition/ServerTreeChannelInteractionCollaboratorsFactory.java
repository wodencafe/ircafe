package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelDisconnectStateManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelInteractionApi;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelTargetOperations;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetSelectionCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTypingActivityManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUnreadStateCoordinator;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeTypingTargetPolicy;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeChannelQueryService;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.springframework.stereotype.Component;

/** Factory that assembles channel-interaction collaborators for server tree construction. */
@Component
public final class ServerTreeChannelInteractionCollaboratorsFactory {

  public ServerTreeChannelInteractionCollaborators create(Inputs inputs) {
    Inputs in = Objects.requireNonNull(inputs, "inputs");

    Timer typingActivityTimer =
        new Timer(
            in.typingActivityTickMs(),
            event -> Objects.requireNonNull(in.onTypingActivityAnimationTick()).run());
    typingActivityTimer.setRepeats(true);

    ServerTreeTypingActivityManager typingActivityManager =
        new ServerTreeTypingActivityManager(
            Objects.requireNonNull(in.leaves(), "leaves"),
            Objects.requireNonNull(in.typingActivityNodes(), "typingActivityNodes"),
            typingActivityTimer,
            in.typingActivityHoldMs(),
            in.typingActivityFadeMs(),
            ServerTreeTypingActivityManager.context(
                ServerTreeTypingTargetPolicy::supportsTypingActivity,
                Objects.requireNonNull(
                    in.typingIndicatorsTreeEnabled(), "typingIndicatorsTreeEnabled"),
                Objects.requireNonNull(in.isDockableAndTreeShowing(), "isDockableAndTreeShowing"),
                Objects.requireNonNull(in.repaintTreeNode(), "repaintTreeNode")));
    ServerTreeChannelDisconnectStateManager channelDisconnectStateManager =
        new ServerTreeChannelDisconnectStateManager(
            in.typingActivityNodes(),
            typingActivityTimer,
            ServerTreeChannelDisconnectStateManager.context(
                Objects.requireNonNull(in.ensureNode(), "ensureNode"),
                Objects.requireNonNull(in.leafForTarget(), "leafForTarget"),
                Objects.requireNonNull(in.model(), "model")::nodeChanged,
                Objects.requireNonNull(
                    in.emitManagedChannelsChanged(), "emitManagedChannelsChanged")));
    ServerTreeTargetSelectionCoordinator targetSelectionCoordinator =
        new ServerTreeTargetSelectionCoordinator(
            ServerTreeTargetSelectionCoordinator.context(
                Objects.requireNonNull(in.ensureNode(), "ensureNode"),
                Objects.requireNonNull(in.monitorNodeForServer(), "monitorNodeForServer"),
                Objects.requireNonNull(in.interceptorsNodeForServer(), "interceptorsNodeForServer"),
                (serverId, node) -> {
                  ServerNodes nodes =
                      Objects.requireNonNull(in.serverNodesForServer(), "serverNodesForServer")
                          .apply(serverId);
                  if (nodes == null || node == null) return false;
                  return node.getParent() == nodes.serverNode
                      || node.getParent() == nodes.otherNode;
                },
                Objects.requireNonNull(in.leafForTarget(), "leafForTarget"),
                node -> {
                  if (node == null) return;
                  TreePath path = new TreePath(node.getPath());
                  Objects.requireNonNull(in.tree(), "tree").setSelectionPath(path);
                  in.tree().scrollPathToVisible(path);
                }));
    ServerTreeUnreadStateCoordinator unreadStateCoordinator =
        new ServerTreeUnreadStateCoordinator(
            in.leaves(),
            in.model(),
            Objects.requireNonNull(in.isChannelMuted(), "isChannelMuted"),
            Objects.requireNonNull(in.noteChannelActivity(), "noteChannelActivity"),
            Objects.requireNonNull(
                in.onChannelUnreadCountsChanged(), "onChannelUnreadCountsChanged"),
            Objects.requireNonNull(in.emitManagedChannelsChanged(), "emitManagedChannelsChanged"));
    ServerTreeChannelInteractionApi channelInteractionApi =
        new ServerTreeChannelInteractionApi(
            Objects.requireNonNull(in.channelQueryService(), "channelQueryService"),
            Objects.requireNonNull(in.channelTargetOperations(), "channelTargetOperations"),
            channelDisconnectStateManager,
            unreadStateCoordinator,
            typingActivityManager);

    return new ServerTreeChannelInteractionCollaborators(
        typingActivityTimer,
        typingActivityManager,
        channelDisconnectStateManager,
        targetSelectionCoordinator,
        unreadStateCoordinator,
        channelInteractionApi);
  }

  public record Inputs(
      JTree tree,
      DefaultTreeModel model,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Set<DefaultMutableTreeNode> typingActivityNodes,
      int typingActivityTickMs,
      int typingActivityHoldMs,
      int typingActivityFadeMs,
      Runnable onTypingActivityAnimationTick,
      BooleanSupplier typingIndicatorsTreeEnabled,
      BooleanSupplier isDockableAndTreeShowing,
      Consumer<DefaultMutableTreeNode> repaintTreeNode,
      Consumer<TargetRef> ensureNode,
      Function<TargetRef, DefaultMutableTreeNode> leafForTarget,
      Consumer<String> emitManagedChannelsChanged,
      Function<String, DefaultMutableTreeNode> monitorNodeForServer,
      Function<String, DefaultMutableTreeNode> interceptorsNodeForServer,
      Function<String, ServerNodes> serverNodesForServer,
      Predicate<TargetRef> isChannelMuted,
      Consumer<TargetRef> noteChannelActivity,
      Consumer<TargetRef> onChannelUnreadCountsChanged,
      ServerTreeChannelQueryService channelQueryService,
      ServerTreeChannelTargetOperations channelTargetOperations) {}
}
