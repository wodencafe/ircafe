package cafe.woden.ircclient.ui.servertree;

import cafe.woden.ircclient.bouncer.GenericBouncerAutoConnectStore;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.api.ServerTreeRuntimeConfigPort;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionCatalog;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.ui.controls.ConnectButton;
import cafe.woden.ircclient.ui.controls.DisconnectButton;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.servertree.builder.ServerTreeServerNodeBuilder;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeChannelInteractionCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeCompositionAssembler;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeLayoutCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeLifecycleSettingsCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeStateInteractionCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeTargetLifecycleCoordinatorFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeTreeInteractionBindingsFactory;
import cafe.woden.ircclient.ui.servertree.composition.ServerTreeViewInteractionCollaboratorsFactory;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeApplicationRootVisibilityCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeChannelTargetOperations;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreePrivateMessageOnlineStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeRequestApi;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeRuntimeHeaderApi;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeServerCatalogSynchronizer;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeTargetRemovalStateCoordinator;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUiLeafVisibilitySynchronizer;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUiRefreshCoordinator;
import cafe.woden.ircclient.ui.servertree.interaction.ServerTreeNodeActionsFactory;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeBuiltInLayoutCoordinator;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeLayoutApplier;
import cafe.woden.ircclient.ui.servertree.layout.ServerTreeRootSiblingOrderCoordinator;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeChannelListNodeEnsurer;
import cafe.woden.ircclient.ui.servertree.mutation.ServerTreeEnsureNodeLeafInserter;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeBouncerDetachPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeSelectionFallbackPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeSelectionPersistencePolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeServerLabelPolicy;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeStartupSelectionRestorer;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeTargetNodePolicy;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeChannelQueryService;
import cafe.woden.ircclient.ui.servertree.query.ServerTreeTargetSnapshotProvider;
import cafe.woden.ircclient.ui.servertree.resolver.ServerTreeEnsureNodeParentResolver;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeBuiltInVisibilitySettings;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerRuntimeUiUpdater;
import cafe.woden.ircclient.ui.servertree.state.ServerTreeServerStateCleaner;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeCellPresentationPolicy;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeContextMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeNetworkInfoDialogBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeQuasselNetworkNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTargetNodeMenuBuilder;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipResolver;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTooltipTextPolicy;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;

final class ServerTreeDockableTestSupport {

  private ServerTreeDockableTestSupport() {}

  static ServerTreeDockable newDockable(
      ServerCatalog serverCatalog,
      ServerTreeRuntimeConfigPort runtimeConfig,
      LogProperties logProps,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn,
      NotificationStore notificationStore,
      InterceptorStore interceptorStore,
      UiSettingsBus settingsBus,
      ServerDialogs serverDialogs) {
    return newDockable(
        serverCatalog,
        runtimeConfig,
        logProps,
        null,
        sojuAutoConnect,
        zncAutoConnect,
        connectBtn,
        disconnectBtn,
        notificationStore,
        interceptorStore,
        settingsBus,
        serverDialogs);
  }

  static ServerTreeDockable newDockable(
      ServerCatalog serverCatalog,
      ServerTreeRuntimeConfigPort runtimeConfig,
      LogProperties logProps,
      GenericBouncerAutoConnectStore genericAutoConnect,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn,
      NotificationStore notificationStore,
      InterceptorStore interceptorStore,
      UiSettingsBus settingsBus,
      ServerDialogs serverDialogs) {
    ServerTreeEdtExecutor edtExecutor = new ServerTreeEdtExecutor();
    return new ServerTreeDockable(
        serverCatalog,
        runtimeConfig,
        logProps,
        genericAutoConnect,
        sojuAutoConnect,
        zncAutoConnect,
        connectBtn,
        disconnectBtn,
        notificationStore,
        interceptorStore,
        settingsBus,
        serverDialogs,
        null,
        null,
        new ServerTreeNetworkInfoDialogBuilder(
            runtimeConfig, Ircv3ExtensionCatalog.builtInCatalog()),
        new ServerTreeBuiltInVisibilitySettings(),
        new ServerTreeCellPresentationPolicy(),
        new ServerTreeServerLabelPolicy(),
        new ServerTreeBouncerDetachPolicy(),
        new ServerTreeSelectionFallbackPolicy(),
        new ServerTreeSelectionPersistencePolicy(),
        new ServerTreeStartupSelectionRestorer(),
        new ServerTreeTargetNodePolicy(interceptorStore),
        new cafe.woden.ircclient.ui.servertree.query.ServerTreeServerNodeResolver(),
        new cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeClassifier(),
        new cafe.woden.ircclient.ui.servertree.resolver.ServerTreeServerParentResolver(),
        new cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeStatusLabelManager(),
        new ServerTreeNetworkGroupManager(),
        new cafe.woden.ircclient.ui.servertree.state.ServerTreeNodeBadgeUpdater(),
        new cafe.woden.ircclient.ui.servertree.actions.ServerTreeInterceptorActions(),
        new ServerTreeServerCatalogSynchronizer(),
        new ServerTreePrivateMessageOnlineStateCoordinator(),
        new ServerTreeUiLeafVisibilitySynchronizer(),
        new ServerTreeApplicationRootVisibilityCoordinator(),
        new ServerTreeChannelListNodeEnsurer(),
        new ServerTreeTargetSnapshotProvider(),
        new ServerTreeChannelQueryService(edtExecutor),
        new ServerTreeChannelTargetOperations(edtExecutor),
        new ServerTreeRequestApi(),
        new ServerTreeRuntimeHeaderApi(new ServerTreeServerRuntimeUiUpdater()),
        new ServerTreeUiRefreshCoordinator(),
        edtExecutor,
        newCompositionAssembler(runtimeConfig));
  }

  static ServerTreeCompositionAssembler newCompositionAssembler(
      ServerTreeRuntimeConfigPort runtimeConfig) {
    ServerTreeLayoutApplier layoutApplier = new ServerTreeLayoutApplier();
    ServerTreeBuiltInLayoutCoordinator builtInLayoutCoordinator =
        new ServerTreeBuiltInLayoutCoordinator(runtimeConfig);
    ServerTreeRootSiblingOrderCoordinator rootSiblingOrderCoordinator =
        new ServerTreeRootSiblingOrderCoordinator(runtimeConfig);
    ServerTreeServerNodeBuilder serverNodeBuilder = new ServerTreeServerNodeBuilder();
    ServerTreeNodeActionsFactory nodeActionsFactory = new ServerTreeNodeActionsFactory();
    return new ServerTreeCompositionAssembler(
        new ServerTreeLayoutCollaboratorsFactory(
            layoutApplier, builtInLayoutCoordinator, rootSiblingOrderCoordinator),
        new ServerTreeStateInteractionCollaboratorsFactory(
            new ServerTreeEnsureNodeParentResolver(),
            new ServerTreeEnsureNodeLeafInserter(),
            new ServerTreeServerRuntimeUiUpdater(),
            new ServerTreeServerStateCleaner(),
            new cafe.woden.ircclient.ui.servertree.mutation.ServerTreeTargetNodeRemovalMutator(),
            new ServerTreeTargetRemovalStateCoordinator()),
        new ServerTreeViewInteractionCollaboratorsFactory(
            new ServerTreeContextMenuBuilder(
                new ServerTreeServerNodeMenuBuilder(),
                new ServerTreeTargetNodeMenuBuilder(),
                new ServerTreeQuasselNetworkNodeMenuBuilder()),
            new ServerTreeTooltipResolver(),
            new ServerTreeTooltipTextPolicy()),
        new ServerTreeLifecycleSettingsCollaboratorsFactory(serverNodeBuilder),
        new ServerTreeTargetLifecycleCoordinatorFactory(),
        new ServerTreeChannelInteractionCollaboratorsFactory(),
        new ServerTreeTreeInteractionBindingsFactory(nodeActionsFactory));
  }
}
