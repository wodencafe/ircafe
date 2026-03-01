package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import java.util.Objects;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Builds context menus for server tree nodes. */
public final class ServerTreeContextMenuBuilder {

  public interface Context {
    boolean isServerNode(DefaultMutableTreeNode node);

    boolean isRootServerNode(DefaultMutableTreeNode node);

    String prettyServerLabel(String serverId);

    ConnectionState connectionStateForServer(String serverId);

    Optional<ServerEntry> serverEntry(String serverId);

    Action moveNodeUpAction();

    Action moveNodeDownAction();

    void requestConnectServer(String serverId);

    void requestDisconnectServer(String serverId);

    void openServerInfoDialog(String serverId);

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

    String sojuOriginForServer(String serverId);

    String zncOriginForServer(String serverId);

    String serverDisplayNameOrDefault(String serverId);

    boolean isSojuAutoConnectEnabled(String originId, String networkKey);

    boolean isZncAutoConnectEnabled(String originId, String networkKey);

    void setSojuAutoConnectEnabled(String originId, String networkKey, boolean enabled);

    void setZncAutoConnectEnabled(String originId, String networkKey, boolean enabled);

    void refreshSojuAutoConnectBadges();

    void refreshZncAutoConnectBadges();

    boolean isInterceptorsGroupNode(DefaultMutableTreeNode node);

    String owningServerIdForNode(DefaultMutableTreeNode node);

    void openPinnedChat(TargetRef ref);

    void confirmAndRequestClearLog(TargetRef target, String label);

    boolean isChannelDisconnected(TargetRef target);

    void requestJoinChannel(TargetRef target);

    void requestDisconnectChannel(TargetRef target);

    void requestCloseChannel(TargetRef target);

    boolean supportsBouncerDetach(String serverId);

    void requestBouncerDetachChannel(TargetRef target);

    boolean isChannelAutoReattach(TargetRef target);

    void setChannelAutoReattach(TargetRef target, boolean autoReattach);

    boolean isChannelPinned(TargetRef target);

    void setChannelPinned(TargetRef target, boolean pinned);

    void requestCloseTarget(TargetRef target);

    InterceptorDefinition interceptorDefinition(TargetRef target);

    void setInterceptorEnabled(TargetRef target, boolean enabled);

    void promptRenameInterceptor(TargetRef target, String currentLabel);

    void confirmDeleteInterceptor(TargetRef target, String label);
  }

  private final Context context;

  public ServerTreeContextMenuBuilder(Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  public JPopupMenu build(TreePath path) {
    if (path == null) return null;

    Object last = path.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node)) return null;

    if (context.isServerNode(node)) {
      return buildServerNodeMenu(node);
    }

    if (context.isInterceptorsGroupNode(node)) {
      return buildInterceptorsGroupMenu(node);
    }

    Object userObject = node.getUserObject();
    if (userObject instanceof ServerTreeNodeData nodeData && nodeData.ref != null) {
      return buildTargetNodeMenu(nodeData);
    }

    return null;
  }

  private JPopupMenu buildServerNodeMenu(DefaultMutableTreeNode node) {
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

    menu.addSeparator();
    JMenuItem addInterceptor = new JMenuItem("Add Interceptor...");
    addInterceptor.setIcon(SvgIcons.action("plus", 16));
    addInterceptor.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
    addInterceptor.setEnabled(context.interceptorStoreAvailable());
    addInterceptor.addActionListener(ev -> context.promptAndAddInterceptor(serverId));
    menu.add(addInterceptor);

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

  private JPopupMenu buildInterceptorsGroupMenu(DefaultMutableTreeNode node) {
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

  private JPopupMenu buildTargetNodeMenu(ServerTreeNodeData nodeData) {
    TargetRef ref = nodeData.ref;
    if (ref == null) return null;

    JPopupMenu menu = new JPopupMenu();

    JMenuItem openDock = new JMenuItem("Open chat dock");
    openDock.addActionListener(ev -> context.openPinnedChat(ref));
    menu.add(openDock);

    menu.addSeparator();
    menu.add(new JMenuItem(context.moveNodeUpAction()));
    menu.add(new JMenuItem(context.moveNodeDownAction()));

    if (ref.isChannel() || ref.isStatus()) {
      menu.addSeparator();
      JMenuItem clearLog = new JMenuItem("Clear Log…");
      clearLog.addActionListener(ev -> context.confirmAndRequestClearLog(ref, nodeData.label));
      menu.add(clearLog);
    }

    if (!ref.isStatus() && !ref.isUiOnly()) {
      menu.addSeparator();
      if (ref.isChannel()) {
        addChannelNodeActions(menu, nodeData);
      } else {
        JMenuItem close = new JMenuItem("Close \"" + nodeData.label + "\"");
        close.addActionListener(ev -> context.requestCloseTarget(ref));
        menu.add(close);
      }
    }

    if (ref.isInterceptor()) {
      addInterceptorActions(menu, nodeData);
    }

    return menu;
  }

  private void addChannelNodeActions(JPopupMenu menu, ServerTreeNodeData nodeData) {
    TargetRef ref = nodeData.ref;
    if (ref == null) return;

    boolean detached = context.isChannelDisconnected(ref);
    if (detached) {
      JMenuItem join = new JMenuItem("Reconnect \"" + nodeData.label + "\"");
      join.addActionListener(ev -> context.requestJoinChannel(ref));
      menu.add(join);
    } else {
      JMenuItem disconnect = new JMenuItem("Disconnect \"" + nodeData.label + "\"");
      disconnect.addActionListener(ev -> context.requestDisconnectChannel(ref));
      menu.add(disconnect);

      JMenuItem closeAndPart = new JMenuItem("Close and PART \"" + nodeData.label + "\"");
      closeAndPart.addActionListener(ev -> context.requestCloseChannel(ref));
      menu.add(closeAndPart);

      if (context.supportsBouncerDetach(ref.serverId())) {
        JMenuItem bouncerDetach = new JMenuItem("Detach (Bouncer) \"" + nodeData.label + "\"");
        bouncerDetach.addActionListener(ev -> context.requestBouncerDetachChannel(ref));
        menu.add(bouncerDetach);
      }
    }

    JCheckBoxMenuItem autoReattach = new JCheckBoxMenuItem("Auto-reconnect on startup");
    autoReattach.setSelected(context.isChannelAutoReattach(ref));
    autoReattach.addActionListener(
        ev -> context.setChannelAutoReattach(ref, autoReattach.isSelected()));
    menu.add(autoReattach);

    boolean pinned = context.isChannelPinned(ref);
    JMenuItem pinToggle = new JMenuItem(pinned ? "Unpin Channel" : "Pin Channel");
    pinToggle.addActionListener(ev -> context.setChannelPinned(ref, !pinned));
    menu.add(pinToggle);

    if (detached) {
      JMenuItem closeChannel = new JMenuItem("Close Channel \"" + nodeData.label + "\"");
      closeChannel.addActionListener(ev -> context.requestCloseChannel(ref));
      menu.add(closeChannel);
    }
  }

  private void addInterceptorActions(JPopupMenu menu, ServerTreeNodeData nodeData) {
    TargetRef ref = nodeData.ref;
    if (ref == null) return;

    menu.addSeparator();

    InterceptorDefinition definition = context.interceptorDefinition(ref);
    boolean currentlyEnabled = definition == null || definition.enabled();

    JMenuItem toggleEnabled =
        new JMenuItem(currentlyEnabled ? "Disable Interceptor" : "Enable Interceptor");
    toggleEnabled.setIcon(SvgIcons.action(currentlyEnabled ? "pause" : "check", 16));
    toggleEnabled.setDisabledIcon(
        SvgIcons.actionDisabled(currentlyEnabled ? "pause" : "check", 16));
    toggleEnabled.setEnabled(context.interceptorStoreAvailable() && definition != null);
    toggleEnabled.addActionListener(ev -> context.setInterceptorEnabled(ref, !currentlyEnabled));
    menu.add(toggleEnabled);

    JMenuItem rename = new JMenuItem("Rename Interceptor...");
    rename.setIcon(SvgIcons.action("edit", 16));
    rename.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
    rename.setEnabled(context.interceptorStoreAvailable());
    rename.addActionListener(ev -> context.promptRenameInterceptor(ref, nodeData.label));
    menu.add(rename);

    JMenuItem delete = new JMenuItem("Delete Interceptor...");
    delete.setIcon(SvgIcons.action("exit", 16));
    delete.setDisabledIcon(SvgIcons.actionDisabled("exit", 16));
    delete.setEnabled(context.interceptorStoreAvailable());
    delete.addActionListener(ev -> context.confirmDeleteInterceptor(ref, nodeData.label));
    menu.add(delete);
  }
}
