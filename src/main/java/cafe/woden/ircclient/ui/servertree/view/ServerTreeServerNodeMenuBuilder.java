package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
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

    boolean isSojuEphemeralServer(String serverId);

    boolean isZncEphemeralServer(String serverId);

    boolean isGenericEphemeralServer(String serverId);

    String sojuOriginForServer(String serverId);

    String zncOriginForServer(String serverId);

    String genericOriginForServer(String serverId);

    String serverDisplayNameOrDefault(String serverId);

    boolean isSojuAutoConnectEnabled(String originId, String networkKey);

    boolean isZncAutoConnectEnabled(String originId, String networkKey);

    boolean isGenericAutoConnectEnabled(String originId, String networkKey);

    void setSojuAutoConnectEnabled(String originId, String networkKey, boolean enabled);

    void setZncAutoConnectEnabled(String originId, String networkKey, boolean enabled);

    void setGenericAutoConnectEnabled(String originId, String networkKey, boolean enabled);

    void refreshSojuAutoConnectBadges();

    void refreshZncAutoConnectBadges();

    void refreshGenericAutoConnectBadges();

    String owningServerIdForNode(DefaultMutableTreeNode node);
  }

  @FunctionalInterface
  public interface TriConsumer<A, B, C> {
    void accept(A first, B second, C third);
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
      Predicate<String> isSojuEphemeralServer,
      Predicate<String> isZncEphemeralServer,
      Predicate<String> isGenericEphemeralServer,
      Function<String, String> sojuOriginForServer,
      Function<String, String> zncOriginForServer,
      Function<String, String> genericOriginForServer,
      Function<String, String> serverDisplayNameOrDefault,
      BiPredicate<String, String> isSojuAutoConnectEnabled,
      BiPredicate<String, String> isZncAutoConnectEnabled,
      BiPredicate<String, String> isGenericAutoConnectEnabled,
      TriConsumer<String, String, Boolean> setSojuAutoConnectEnabled,
      TriConsumer<String, String, Boolean> setZncAutoConnectEnabled,
      TriConsumer<String, String, Boolean> setGenericAutoConnectEnabled,
      Runnable refreshSojuAutoConnectBadges,
      Runnable refreshZncAutoConnectBadges,
      Runnable refreshGenericAutoConnectBadges,
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
    Objects.requireNonNull(isSojuEphemeralServer, "isSojuEphemeralServer");
    Objects.requireNonNull(isZncEphemeralServer, "isZncEphemeralServer");
    Objects.requireNonNull(isGenericEphemeralServer, "isGenericEphemeralServer");
    Objects.requireNonNull(sojuOriginForServer, "sojuOriginForServer");
    Objects.requireNonNull(zncOriginForServer, "zncOriginForServer");
    Objects.requireNonNull(genericOriginForServer, "genericOriginForServer");
    Objects.requireNonNull(serverDisplayNameOrDefault, "serverDisplayNameOrDefault");
    Objects.requireNonNull(isSojuAutoConnectEnabled, "isSojuAutoConnectEnabled");
    Objects.requireNonNull(isZncAutoConnectEnabled, "isZncAutoConnectEnabled");
    Objects.requireNonNull(isGenericAutoConnectEnabled, "isGenericAutoConnectEnabled");
    Objects.requireNonNull(setSojuAutoConnectEnabled, "setSojuAutoConnectEnabled");
    Objects.requireNonNull(setZncAutoConnectEnabled, "setZncAutoConnectEnabled");
    Objects.requireNonNull(setGenericAutoConnectEnabled, "setGenericAutoConnectEnabled");
    Objects.requireNonNull(refreshSojuAutoConnectBadges, "refreshSojuAutoConnectBadges");
    Objects.requireNonNull(refreshZncAutoConnectBadges, "refreshZncAutoConnectBadges");
    Objects.requireNonNull(refreshGenericAutoConnectBadges, "refreshGenericAutoConnectBadges");
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
      public boolean isSojuEphemeralServer(String serverId) {
        return isSojuEphemeralServer.test(serverId);
      }

      @Override
      public boolean isZncEphemeralServer(String serverId) {
        return isZncEphemeralServer.test(serverId);
      }

      @Override
      public boolean isGenericEphemeralServer(String serverId) {
        return isGenericEphemeralServer.test(serverId);
      }

      @Override
      public String sojuOriginForServer(String serverId) {
        return sojuOriginForServer.apply(serverId);
      }

      @Override
      public String zncOriginForServer(String serverId) {
        return zncOriginForServer.apply(serverId);
      }

      @Override
      public String genericOriginForServer(String serverId) {
        return genericOriginForServer.apply(serverId);
      }

      @Override
      public String serverDisplayNameOrDefault(String serverId) {
        return serverDisplayNameOrDefault.apply(serverId);
      }

      @Override
      public boolean isSojuAutoConnectEnabled(String originId, String networkKey) {
        return isSojuAutoConnectEnabled.test(originId, networkKey);
      }

      @Override
      public boolean isZncAutoConnectEnabled(String originId, String networkKey) {
        return isZncAutoConnectEnabled.test(originId, networkKey);
      }

      @Override
      public boolean isGenericAutoConnectEnabled(String originId, String networkKey) {
        return isGenericAutoConnectEnabled.test(originId, networkKey);
      }

      @Override
      public void setSojuAutoConnectEnabled(String originId, String networkKey, boolean enabled) {
        setSojuAutoConnectEnabled.accept(originId, networkKey, enabled);
      }

      @Override
      public void setZncAutoConnectEnabled(String originId, String networkKey, boolean enabled) {
        setZncAutoConnectEnabled.accept(originId, networkKey, enabled);
      }

      @Override
      public void setGenericAutoConnectEnabled(
          String originId, String networkKey, boolean enabled) {
        setGenericAutoConnectEnabled.accept(originId, networkKey, enabled);
      }

      @Override
      public void refreshSojuAutoConnectBadges() {
        refreshSojuAutoConnectBadges.run();
      }

      @Override
      public void refreshZncAutoConnectBadges() {
        refreshZncAutoConnectBadges.run();
      }

      @Override
      public void refreshGenericAutoConnectBadges() {
        refreshGenericAutoConnectBadges.run();
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

    if (context.isSojuEphemeralServer(serverId)) {
      String originId = context.sojuOriginForServer(serverId);
      String networkKey = context.serverDisplayNameOrDefault(serverId);
      boolean enabled =
          originId != null
              && !originId.isBlank()
              && context.isSojuAutoConnectEnabled(originId, networkKey);

      menu.addSeparator();
      JCheckBoxMenuItem auto =
          new JCheckBoxMenuItem("Auto-connect \"" + networkKey + "\" next time");
      auto.setSelected(enabled);
      auto.setEnabled(originId != null && !originId.isBlank());
      auto.addActionListener(
          ev -> {
            if (originId == null || originId.isBlank()) return;
            context.setSojuAutoConnectEnabled(originId, networkKey, auto.isSelected());
            context.refreshSojuAutoConnectBadges();
          });
      menu.add(auto);
    }

    if (context.isZncEphemeralServer(serverId)) {
      String originId = context.zncOriginForServer(serverId);
      String networkKey = context.serverDisplayNameOrDefault(serverId);
      boolean enabled =
          originId != null
              && !originId.isBlank()
              && context.isZncAutoConnectEnabled(originId, networkKey);

      menu.addSeparator();
      JCheckBoxMenuItem auto =
          new JCheckBoxMenuItem("Auto-connect \"" + networkKey + "\" next time");
      auto.setSelected(enabled);
      auto.setEnabled(originId != null && !originId.isBlank());
      auto.addActionListener(
          ev -> {
            if (originId == null || originId.isBlank()) return;
            context.setZncAutoConnectEnabled(originId, networkKey, auto.isSelected());
            context.refreshZncAutoConnectBadges();
          });
      menu.add(auto);
    }

    if (context.isGenericEphemeralServer(serverId)) {
      String originId = context.genericOriginForServer(serverId);
      String networkKey = context.serverDisplayNameOrDefault(serverId);
      boolean enabled =
          originId != null
              && !originId.isBlank()
              && context.isGenericAutoConnectEnabled(originId, networkKey);

      menu.addSeparator();
      JCheckBoxMenuItem auto =
          new JCheckBoxMenuItem("Auto-connect \"" + networkKey + "\" next time");
      auto.setSelected(enabled);
      auto.setEnabled(originId != null && !originId.isBlank());
      auto.addActionListener(
          ev -> {
            if (originId == null || originId.isBlank()) return;
            context.setGenericAutoConnectEnabled(originId, networkKey, auto.isSelected());
            context.refreshGenericAutoConnectBadges();
          });
      menu.add(auto);
    }

    return menu;
  }

  JPopupMenu buildInterceptorsGroupMenu(DefaultMutableTreeNode node) {
    String serverId = context.owningServerIdForNode(node);
    if (serverId.isEmpty()) return null;

    JPopupMenu menu = new JPopupMenu();
    JMenuItem addInterceptor = new JMenuItem("Add Interceptor...");
    addInterceptor.setIcon(SvgIcons.action("plus", 16));
    addInterceptor.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
    addInterceptor.setEnabled(context.interceptorStoreAvailable());
    addInterceptor.addActionListener(ev -> context.promptAndAddInterceptor(serverId));
    menu.add(addInterceptor);
    return menu;
  }
}
