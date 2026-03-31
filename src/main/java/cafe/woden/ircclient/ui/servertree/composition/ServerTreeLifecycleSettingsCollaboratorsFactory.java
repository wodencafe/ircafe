package cafe.woden.ircclient.ui.servertree.composition;

import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeBuiltInLayout;
import cafe.woden.ircclient.config.api.ServerTreeLayoutConfigPort.ServerTreeRootSiblingOrder;
import cafe.woden.ircclient.config.api.UiSettingsRuntimeConfigPort;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.servertree.actions.ServerTreeInterceptorActions;
import cafe.woden.ircclient.ui.servertree.builder.ServerTreeServerNodeBuilder;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerLifecycleFacade;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerRootLifecycleManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeStatusLabelManager;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeServerParentResolver;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeNodeBadgeUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeRuntimeState;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerStateCleaner;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeSettingsSynchronizer;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTypingIndicatorStyle;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/** Factory that assembles lifecycle and settings collaborators for server tree construction. */
public final class ServerTreeLifecycleSettingsCollaboratorsFactory {

  private ServerTreeLifecycleSettingsCollaboratorsFactory() {}

  public static ServerTreeLifecycleSettingsCollaborators create(Inputs inputs) {
    Inputs in = Objects.requireNonNull(inputs, "inputs");

    ServerTreeServerRootLifecycleManager serverRootLifecycleManager =
        new ServerTreeServerRootLifecycleManager(
            Objects.requireNonNull(in.serverNodeBuilder(), "serverNodeBuilder"),
            Objects.requireNonNull(in.channelListLabel(), "channelListLabel"),
            Objects.requireNonNull(in.weechatFiltersLabel(), "weechatFiltersLabel"),
            Objects.requireNonNull(in.ignoresLabel(), "ignoresLabel"),
            Objects.requireNonNull(in.dccTransfersLabel(), "dccTransfersLabel"),
            Objects.requireNonNull(in.logViewerLabel(), "logViewerLabel"),
            Objects.requireNonNull(in.monitorGroupLabel(), "monitorGroupLabel"),
            Objects.requireNonNull(in.interceptorsGroupLabel(), "interceptorsGroupLabel"),
            ServerTreeServerRootLifecycleManager.context(
                Objects.requireNonNull(in.normalizeServerId(), "normalizeServerId"),
                Objects.requireNonNull(in.servers(), "servers"),
                Objects.requireNonNull(in.runtimeState(), "runtimeState")::markServerKnown,
                Objects.requireNonNull(in.channelStateCoordinator(), "channelStateCoordinator")
                    ::loadChannelStateForServer,
                Objects.requireNonNull(in.serverParentResolver(), "serverParentResolver")
                    ::resolveParentForServer,
                Objects.requireNonNull(in.builtInNodesVisibility(), "builtInNodesVisibility"),
                () ->
                    Objects.requireNonNull(
                            in.isDccTransfersNodesVisible(), "isDccTransfersNodesVisible")
                        .getAsBoolean(),
                Objects.requireNonNull(in.statusLabelManager(), "statusLabelManager")
                    ::statusLeafLabelForServer,
                serverId -> {
                  NotificationStore store = in.notificationStore();
                  return store == null ? 0 : store.count(serverId);
                },
                serverId -> {
                  InterceptorStore store = in.interceptorStore();
                  return store == null ? 0 : store.totalHitCount(serverId);
                },
                serverId -> {
                  InterceptorStore store = in.interceptorStore();
                  List<InterceptorDefinition> definitions =
                      store == null ? List.of() : store.listInterceptors(serverId);
                  return definitions == null ? List.of() : definitions;
                },
                Objects.requireNonNull(in.leaves(), "leaves"),
                Objects.requireNonNull(in.builtInLayout(), "builtInLayout"),
                Objects.requireNonNull(in.rootSiblingOrder(), "rootSiblingOrder"),
                Objects.requireNonNull(in.applyBuiltInLayoutToTree(), "applyBuiltInLayoutToTree"),
                Objects.requireNonNull(
                    in.applyRootSiblingOrderToTree(), "applyRootSiblingOrderToTree"),
                Objects.requireNonNull(in.model(), "model"),
                Objects.requireNonNull(in.root(), "root"),
                Objects.requireNonNull(in.tree(), "tree"),
                Objects.requireNonNull(in.nodeBadgeUpdater(), "nodeBadgeUpdater")
                    ::refreshNotificationsCount,
                Objects.requireNonNull(in.interceptorActions(), "interceptorActions")
                    ::refreshInterceptorGroupCount,
                Objects.requireNonNull(in.serverStateCleaner(), "serverStateCleaner")
                    ::cleanupServerState,
                Objects.requireNonNull(in.networkGroupManager(), "networkGroupManager")
                    ::removeEmptyGroupIfNeeded));
    ServerTreeServerLifecycleFacade serverLifecycleFacade =
        new ServerTreeServerLifecycleFacade(
            serverRootLifecycleManager,
            Objects.requireNonNull(in.statusLabelManager(), "statusLabelManager"));
    ServerTreeSettingsSynchronizer settingsSynchronizer =
        new ServerTreeSettingsSynchronizer(
            ServerTreeSettingsSynchronizer.context(
                in.settingsBus(),
                in.jfrRuntimeEventsService(),
                in.runtimeConfig(),
                () ->
                    Objects.requireNonNull(
                            in.typingIndicatorsTreeEnabled(), "typingIndicatorsTreeEnabled")
                        .getAsBoolean(),
                Objects.requireNonNull(
                    in.setTypingIndicatorsTreeEnabled(), "setTypingIndicatorsTreeEnabled"),
                Objects.requireNonNull(
                    in.clearTypingIndicatorsIfReady(), "clearTypingIndicatorsIfReady"),
                style -> {
                  ServerTreeTypingIndicatorStyle next =
                      style == null ? ServerTreeTypingIndicatorStyle.DOTS : style;
                  Objects.requireNonNull(in.setTypingIndicatorStyle(), "setTypingIndicatorStyle")
                      .accept(next);
                },
                Objects.requireNonNull(
                    in.setServerTreeNotificationBadgesEnabled(),
                    "setServerTreeNotificationBadgesEnabled"),
                Objects.requireNonNull(
                    in.setUnreadBadgeScalePercent(), "setUnreadBadgeScalePercent"),
                Objects.requireNonNull(in.setUnreadChannelTextColor(), "setUnreadChannelTextColor"),
                Objects.requireNonNull(
                    in.setHighlightChannelTextColor(), "setHighlightChannelTextColor"),
                Objects.requireNonNull(
                    in.refreshTreeLayoutAfterUiChange(), "refreshTreeLayoutAfterUiChange"),
                Objects.requireNonNull(
                    in.refreshApplicationJfrNode(), "refreshApplicationJfrNode")),
            in.treeBadgeScalePercentDefault());

    return new ServerTreeLifecycleSettingsCollaborators(
        serverRootLifecycleManager, serverLifecycleFacade, settingsSynchronizer);
  }

  public record Inputs(
      ServerTreeServerNodeBuilder serverNodeBuilder,
      String channelListLabel,
      String weechatFiltersLabel,
      String ignoresLabel,
      String dccTransfersLabel,
      String logViewerLabel,
      String monitorGroupLabel,
      String interceptorsGroupLabel,
      Function<String, String> normalizeServerId,
      Map<String, ServerNodes> servers,
      ServerTreeRuntimeState runtimeState,
      ServerTreeChannelStateCoordinator channelStateCoordinator,
      ServerTreeServerParentResolver serverParentResolver,
      Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility,
      BooleanSupplier isDccTransfersNodesVisible,
      ServerTreeStatusLabelManager statusLabelManager,
      NotificationStore notificationStore,
      InterceptorStore interceptorStore,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Function<String, ServerTreeBuiltInLayout> builtInLayout,
      Function<String, ServerTreeRootSiblingOrder> rootSiblingOrder,
      ServerTreeServerRootLifecycleManager.BiLayoutConsumer applyBuiltInLayoutToTree,
      ServerTreeServerRootLifecycleManager.BiRootOrderConsumer applyRootSiblingOrderToTree,
      DefaultTreeModel model,
      DefaultMutableTreeNode root,
      JTree tree,
      ServerTreeNodeBadgeUpdater nodeBadgeUpdater,
      ServerTreeInterceptorActions interceptorActions,
      ServerTreeServerStateCleaner serverStateCleaner,
      ServerTreeNetworkGroupManager networkGroupManager,
      UiSettingsBus settingsBus,
      JfrRuntimeEventsService jfrRuntimeEventsService,
      UiSettingsRuntimeConfigPort runtimeConfig,
      BooleanSupplier typingIndicatorsTreeEnabled,
      Consumer<Boolean> setTypingIndicatorsTreeEnabled,
      Runnable clearTypingIndicatorsIfReady,
      Consumer<ServerTreeTypingIndicatorStyle> setTypingIndicatorStyle,
      Consumer<Boolean> setServerTreeNotificationBadgesEnabled,
      IntConsumer setUnreadBadgeScalePercent,
      Consumer<Color> setUnreadChannelTextColor,
      Consumer<Color> setHighlightChannelTextColor,
      Runnable refreshTreeLayoutAfterUiChange,
      Runnable refreshApplicationJfrNode,
      int treeBadgeScalePercentDefault) {}
}
