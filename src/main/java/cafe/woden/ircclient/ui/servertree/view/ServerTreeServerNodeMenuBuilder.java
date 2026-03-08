package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.interceptors.InterceptorScope;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.tree.DefaultMutableTreeNode;

/** Builds server-node and interceptor-group context menus for server tree. */
public final class ServerTreeServerNodeMenuBuilder {

  public interface Context {
    boolean isRootServerNode(DefaultMutableTreeNode node);

    String prettyServerLabel(String serverId);

    ConnectionState connectionStateForServer(String serverId);

    String connectionDiagnosticsTipForServer(String serverId);

    Optional<ServerEntry> serverEntry(String serverId);

    Action moveNodeUpAction();

    Action moveNodeDownAction();

    void requestConnectServer(String serverId);

    void requestDisconnectServer(String serverId);

    void openServerInfoDialog(String serverId);

    void openQuasselSetup(String serverId);

    void openQuasselNetworkManager(String serverId);

    boolean interceptorStoreAvailable();

    void promptAndAddInterceptor(String serverId);

    boolean serverDialogsAvailable();

    void openSaveEphemeralServer(String serverId);

    void openEditServer(String serverId);

    boolean runtimeConfigAvailable();

    boolean readServerAutoConnectOnStart(String serverId, boolean defaultValue);

    void rememberServerAutoConnectOnStart(String serverId, boolean enabled);

    String backendIdForEphemeralServer(String serverId);

    String originForServer(String backendId, String serverId);

    boolean isAutoConnectEnabled(String backendId, String originId, String networkKey);

    String serverDisplayNameOrDefault(String serverId);

    void setAutoConnectEnabled(
        String backendId, String originId, String networkKey, boolean enabled);

    void refreshAutoConnectBadges(String backendId);

    String owningServerIdForNode(DefaultMutableTreeNode node);
  }

  @FunctionalInterface
  public interface TriPredicate<A, B, C> {
    boolean test(A first, B second, C third);
  }

  @FunctionalInterface
  public interface QuadConsumer<A, B, C, D> {
    void accept(A first, B second, C third, D fourth);
  }

  public static Context context(
      Predicate<DefaultMutableTreeNode> isRootServerNode,
      Function<String, String> prettyServerLabel,
      Function<String, ConnectionState> connectionStateForServer,
      Function<String, String> connectionDiagnosticsTipForServer,
      Function<String, Optional<ServerEntry>> serverEntry,
      Supplier<Action> moveNodeUpAction,
      Supplier<Action> moveNodeDownAction,
      Consumer<String> requestConnectServer,
      Consumer<String> requestDisconnectServer,
      Consumer<String> openServerInfoDialog,
      Consumer<String> openQuasselSetup,
      Consumer<String> openQuasselNetworkManager,
      Supplier<Boolean> interceptorStoreAvailable,
      Consumer<String> promptAndAddInterceptor,
      Supplier<Boolean> serverDialogsAvailable,
      Consumer<String> openSaveEphemeralServer,
      Consumer<String> openEditServer,
      Supplier<Boolean> runtimeConfigAvailable,
      BiFunction<String, Boolean, Boolean> readServerAutoConnectOnStart,
      BiConsumer<String, Boolean> rememberServerAutoConnectOnStart,
      Function<String, String> backendIdForEphemeralServer,
      BiFunction<String, String, String> originForServer,
      TriPredicate<String, String, String> isAutoConnectEnabled,
      Function<String, String> serverDisplayNameOrDefault,
      QuadConsumer<String, String, String, Boolean> setAutoConnectEnabled,
      Consumer<String> refreshAutoConnectBadges,
      Function<DefaultMutableTreeNode, String> owningServerIdForNode) {
    Objects.requireNonNull(isRootServerNode, "isRootServerNode");
    Objects.requireNonNull(prettyServerLabel, "prettyServerLabel");
    Objects.requireNonNull(connectionStateForServer, "connectionStateForServer");
    Objects.requireNonNull(connectionDiagnosticsTipForServer, "connectionDiagnosticsTipForServer");
    Objects.requireNonNull(serverEntry, "serverEntry");
    Objects.requireNonNull(moveNodeUpAction, "moveNodeUpAction");
    Objects.requireNonNull(moveNodeDownAction, "moveNodeDownAction");
    Objects.requireNonNull(requestConnectServer, "requestConnectServer");
    Objects.requireNonNull(requestDisconnectServer, "requestDisconnectServer");
    Objects.requireNonNull(openServerInfoDialog, "openServerInfoDialog");
    Objects.requireNonNull(openQuasselSetup, "openQuasselSetup");
    Objects.requireNonNull(openQuasselNetworkManager, "openQuasselNetworkManager");
    Objects.requireNonNull(interceptorStoreAvailable, "interceptorStoreAvailable");
    Objects.requireNonNull(promptAndAddInterceptor, "promptAndAddInterceptor");
    Objects.requireNonNull(serverDialogsAvailable, "serverDialogsAvailable");
    Objects.requireNonNull(openSaveEphemeralServer, "openSaveEphemeralServer");
    Objects.requireNonNull(openEditServer, "openEditServer");
    Objects.requireNonNull(runtimeConfigAvailable, "runtimeConfigAvailable");
    Objects.requireNonNull(readServerAutoConnectOnStart, "readServerAutoConnectOnStart");
    Objects.requireNonNull(rememberServerAutoConnectOnStart, "rememberServerAutoConnectOnStart");
    Objects.requireNonNull(backendIdForEphemeralServer, "backendIdForEphemeralServer");
    Objects.requireNonNull(originForServer, "originForServer");
    Objects.requireNonNull(isAutoConnectEnabled, "isAutoConnectEnabled");
    Objects.requireNonNull(serverDisplayNameOrDefault, "serverDisplayNameOrDefault");
    Objects.requireNonNull(setAutoConnectEnabled, "setAutoConnectEnabled");
    Objects.requireNonNull(refreshAutoConnectBadges, "refreshAutoConnectBadges");
    Objects.requireNonNull(owningServerIdForNode, "owningServerIdForNode");
    return new Context() {
      @Override
      public boolean isRootServerNode(DefaultMutableTreeNode node) {
        return isRootServerNode.test(node);
      }

      @Override
      public String prettyServerLabel(String serverId) {
        return prettyServerLabel.apply(serverId);
      }

      @Override
      public ConnectionState connectionStateForServer(String serverId) {
        return connectionStateForServer.apply(serverId);
      }

      @Override
      public String connectionDiagnosticsTipForServer(String serverId) {
        return connectionDiagnosticsTipForServer.apply(serverId);
      }

      @Override
      public Optional<ServerEntry> serverEntry(String serverId) {
        return serverEntry.apply(serverId);
      }

      @Override
      public Action moveNodeUpAction() {
        return moveNodeUpAction.get();
      }

      @Override
      public Action moveNodeDownAction() {
        return moveNodeDownAction.get();
      }

      @Override
      public void requestConnectServer(String serverId) {
        requestConnectServer.accept(serverId);
      }

      @Override
      public void requestDisconnectServer(String serverId) {
        requestDisconnectServer.accept(serverId);
      }

      @Override
      public void openServerInfoDialog(String serverId) {
        openServerInfoDialog.accept(serverId);
      }

      @Override
      public void openQuasselSetup(String serverId) {
        openQuasselSetup.accept(serverId);
      }

      @Override
      public void openQuasselNetworkManager(String serverId) {
        openQuasselNetworkManager.accept(serverId);
      }

      @Override
      public boolean interceptorStoreAvailable() {
        return interceptorStoreAvailable.get();
      }

      @Override
      public void promptAndAddInterceptor(String serverId) {
        promptAndAddInterceptor.accept(serverId);
      }

      @Override
      public boolean serverDialogsAvailable() {
        return serverDialogsAvailable.get();
      }

      @Override
      public void openSaveEphemeralServer(String serverId) {
        openSaveEphemeralServer.accept(serverId);
      }

      @Override
      public void openEditServer(String serverId) {
        openEditServer.accept(serverId);
      }

      @Override
      public boolean runtimeConfigAvailable() {
        return runtimeConfigAvailable.get();
      }

      @Override
      public boolean readServerAutoConnectOnStart(String serverId, boolean defaultValue) {
        return readServerAutoConnectOnStart.apply(serverId, defaultValue);
      }

      @Override
      public void rememberServerAutoConnectOnStart(String serverId, boolean enabled) {
        rememberServerAutoConnectOnStart.accept(serverId, enabled);
      }

      @Override
      public String backendIdForEphemeralServer(String serverId) {
        return backendIdForEphemeralServer.apply(serverId);
      }

      @Override
      public String originForServer(String backendId, String serverId) {
        return originForServer.apply(backendId, serverId);
      }

      @Override
      public boolean isAutoConnectEnabled(String backendId, String originId, String networkKey) {
        return isAutoConnectEnabled.test(backendId, originId, networkKey);
      }

      @Override
      public String serverDisplayNameOrDefault(String serverId) {
        return serverDisplayNameOrDefault.apply(serverId);
      }

      @Override
      public void setAutoConnectEnabled(
          String backendId, String originId, String networkKey, boolean enabled) {
        setAutoConnectEnabled.accept(backendId, originId, networkKey, enabled);
      }

      @Override
      public void refreshAutoConnectBadges(String backendId) {
        refreshAutoConnectBadges.accept(backendId);
      }

      @Override
      public String owningServerIdForNode(DefaultMutableTreeNode node) {
        return owningServerIdForNode.apply(node);
      }
    };
  }

  private final Context context;

  public ServerTreeServerNodeMenuBuilder(Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  JPopupMenu buildServerNodeMenu(DefaultMutableTreeNode node) {
    String serverId = Objects.toString(node.getUserObject(), "").trim();
    if (serverId.isEmpty()) return null;

    String pretty = context.prettyServerLabel(serverId);
    ConnectionState state = context.connectionStateForServer(serverId);
    boolean canReorder = context.isRootServerNode(node);

    Optional<ServerEntry> serverEntry = context.serverEntry(serverId);
    boolean persistedServerEntry = serverEntry.map(se -> !se.ephemeral()).orElse(false);

    JPopupMenu menu = new JPopupMenu();

    if (canReorder) {
      menu.add(new JMenuItem(context.moveNodeUpAction()));
      menu.add(new JMenuItem(context.moveNodeDownAction()));
      menu.addSeparator();
    }

    JMenuItem connectOne = new JMenuItem("Connect \"" + pretty + "\"");
    connectOne.setIcon(SvgIcons.action("plus", 16));
    connectOne.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
    connectOne.setEnabled(ServerTreeConnectionStateViewModel.canConnect(state));
    connectOne.addActionListener(ev -> context.requestConnectServer(serverId));
    menu.add(connectOne);

    JMenuItem disconnectOne = new JMenuItem("Disconnect \"" + pretty + "\"");
    disconnectOne.setIcon(SvgIcons.action("exit", 16));
    disconnectOne.setDisabledIcon(SvgIcons.actionDisabled("exit", 16));
    disconnectOne.setEnabled(ServerTreeConnectionStateViewModel.canDisconnect(state));
    disconnectOne.addActionListener(ev -> context.requestDisconnectServer(serverId));
    menu.add(disconnectOne);

    JMenuItem networkInfo = new JMenuItem("View Network Info...");
    networkInfo.setIcon(SvgIcons.action("info", 16));
    networkInfo.setDisabledIcon(SvgIcons.actionDisabled("info", 16));
    networkInfo.addActionListener(ev -> context.openServerInfoDialog(serverId));
    menu.add(networkInfo);

    boolean quasselCoreServer =
        serverEntry
            .map(ServerEntry::server)
            .map(IrcProperties.Server::backend)
            .map(backend -> backend == IrcProperties.Server.Backend.QUASSEL_CORE)
            .orElse(false);
    if (quasselCoreServer) {
      String diagnostics =
          Objects.toString(context.connectionDiagnosticsTipForServer(serverId), "")
              .toLowerCase(Locale.ROOT);
      boolean setupPending =
          diagnostics.contains("setup required") || diagnostics.contains("setup is required");
      JMenuItem runQuasselSetup =
          new JMenuItem(setupPending ? "Complete Quassel Setup..." : "Run Quassel Setup...");
      runQuasselSetup.setIcon(SvgIcons.action("edit", 16));
      runQuasselSetup.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
      runQuasselSetup.addActionListener(ev -> context.openQuasselSetup(serverId));
      menu.add(runQuasselSetup);

      JMenuItem manageQuasselNetworks = new JMenuItem("Manage Quassel Networks...");
      manageQuasselNetworks.setIcon(SvgIcons.action("edit", 16));
      manageQuasselNetworks.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
      manageQuasselNetworks.addActionListener(ev -> context.openQuasselNetworkManager(serverId));
      menu.add(manageQuasselNetworks);
    }

    boolean ephemeral = serverEntry.map(ServerEntry::ephemeral).orElse(false);
    if (ephemeral) {
      menu.addSeparator();
      JMenuItem save = new JMenuItem("Save \"" + pretty + "\"…");
      save.setIcon(SvgIcons.action("plus", 16));
      save.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
      save.setEnabled(context.serverDialogsAvailable());
      save.addActionListener(ev -> context.openSaveEphemeralServer(serverId));
      menu.add(save);
    }

    if (canReorder) {
      menu.addSeparator();
      boolean editable = context.serverDialogsAvailable() && persistedServerEntry;
      JMenuItem edit = new JMenuItem("Edit \"" + pretty + "\"…");
      edit.setIcon(SvgIcons.action("edit", 16));
      edit.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
      edit.setEnabled(editable);
      edit.addActionListener(ev -> context.openEditServer(serverId));
      menu.add(edit);
    }

    if (canReorder && persistedServerEntry && context.runtimeConfigAvailable()) {
      menu.addSeparator();
      JCheckBoxMenuItem startupAutoConnect =
          new JCheckBoxMenuItem("Auto-connect \"" + pretty + "\" on startup");
      startupAutoConnect.setSelected(context.readServerAutoConnectOnStart(serverId, true));
      startupAutoConnect.addActionListener(
          ev ->
              context.rememberServerAutoConnectOnStart(serverId, startupAutoConnect.isSelected()));
      menu.add(startupAutoConnect);
    }

    addEphemeralAutoConnectToggleIfNeeded(menu, serverId);

    return menu;
  }

  JPopupMenu buildInterceptorsGroupMenu(DefaultMutableTreeNode node) {
    String scopeServerId = interceptorScopeServerIdForNode(node);
    if (scopeServerId.isEmpty()) return null;

    JPopupMenu menu = new JPopupMenu();
    JMenuItem addInterceptor = new JMenuItem("Add Interceptor...");
    addInterceptor.setIcon(SvgIcons.action("plus", 16));
    addInterceptor.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
    addInterceptor.setEnabled(context.interceptorStoreAvailable());
    addInterceptor.addActionListener(ev -> context.promptAndAddInterceptor(scopeServerId));
    menu.add(addInterceptor);
    return menu;
  }

  private String interceptorScopeServerIdForNode(DefaultMutableTreeNode node) {
    if (node == null) return "";
    Object userObject = node.getUserObject();
    if (userObject instanceof ServerTreeNodeData nodeData) {
      TargetRef ref = nodeData.ref;
      if (ref != null && ref.isInterceptorsGroup()) {
        return InterceptorScope.scopedServerIdForTarget(ref);
      }
    }
    String serverId = context.owningServerIdForNode(node);
    return InterceptorScope.normalizeScopeServerId(serverId);
  }

  private void addEphemeralAutoConnectToggleIfNeeded(JPopupMenu menu, String serverId) {
    String backendId = normalizeBackendId(context.backendIdForEphemeralServer(serverId));
    if (backendId.isEmpty()) return;

    String originId = context.originForServer(backendId, serverId);
    String networkKey = context.serverDisplayNameOrDefault(serverId);
    boolean enabled =
        originId != null
            && !originId.isBlank()
            && context.isAutoConnectEnabled(backendId, originId, networkKey);

    menu.addSeparator();
    JCheckBoxMenuItem auto = new JCheckBoxMenuItem("Auto-connect \"" + networkKey + "\" next time");
    auto.setSelected(enabled);
    auto.setEnabled(originId != null && !originId.isBlank());
    auto.addActionListener(
        ev -> {
          if (originId == null || originId.isBlank()) return;
          context.setAutoConnectEnabled(backendId, originId, networkKey, auto.isSelected());
          context.refreshAutoConnectBadges(backendId);
        });
    menu.add(auto);
  }

  private static String normalizeBackendId(String backendId) {
    return Objects.toString(backendId, "").trim().toLowerCase(java.util.Locale.ROOT);
  }
}
