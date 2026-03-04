package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.tree.DefaultMutableTreeNode;

/** Builds server-node and interceptor-group context menus for server tree. */
final class ServerTreeServerNodeMenuBuilder {

  private final ServerTreeContextMenuBuilder.Context context;

  ServerTreeServerNodeMenuBuilder(ServerTreeContextMenuBuilder.Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  JPopupMenu buildServerNodeMenu(DefaultMutableTreeNode node) {
    String serverId = Objects.toString(node.getUserObject(), "").trim();
    if (serverId.isEmpty()) return null;

    String pretty = context.prettyServerLabel(serverId);
    cafe.woden.ircclient.app.api.ConnectionState state = context.connectionStateForServer(serverId);
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
