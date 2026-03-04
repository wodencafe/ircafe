package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
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
import javax.swing.JMenu;
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

    boolean isChannelMuted(TargetRef target);

    void setChannelMuted(TargetRef target, boolean muted);

    void openChannelModeDetails(TargetRef target);

    void requestChannelModeRefresh(TargetRef target);

    boolean canEditChannelModes(TargetRef target);

    void promptAndRequestChannelModeSet(TargetRef target, String channelLabel);

    void requestCloseTarget(TargetRef target);

    InterceptorDefinition interceptorDefinition(TargetRef target);

    void setInterceptorEnabled(TargetRef target, boolean enabled);

    void promptRenameInterceptor(TargetRef target, String currentLabel);

    void confirmDeleteInterceptor(TargetRef target, String label);
  }

  @FunctionalInterface
  public interface TriConsumer<A, B, C> {
    void accept(A first, B second, C third);
  }

  public static Context context(
      Predicate<DefaultMutableTreeNode> isServerNode,
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
      Function<String, String> sojuOriginForServer,
      Function<String, String> zncOriginForServer,
      Function<String, String> serverDisplayNameOrDefault,
      BiPredicate<String, String> isSojuAutoConnectEnabled,
      BiPredicate<String, String> isZncAutoConnectEnabled,
      TriConsumer<String, String, Boolean> setSojuAutoConnectEnabled,
      TriConsumer<String, String, Boolean> setZncAutoConnectEnabled,
      Runnable refreshSojuAutoConnectBadges,
      Runnable refreshZncAutoConnectBadges,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Function<DefaultMutableTreeNode, String> owningServerIdForNode,
      Consumer<TargetRef> openPinnedChat,
      BiConsumer<TargetRef, String> confirmAndRequestClearLog,
      Predicate<TargetRef> isChannelDisconnected,
      Consumer<TargetRef> requestJoinChannel,
      Consumer<TargetRef> requestDisconnectChannel,
      Consumer<TargetRef> requestCloseChannel,
      Predicate<String> supportsBouncerDetach,
      Consumer<TargetRef> requestBouncerDetachChannel,
      Predicate<TargetRef> isChannelAutoReattach,
      BiConsumer<TargetRef, Boolean> setChannelAutoReattach,
      Predicate<TargetRef> isChannelPinned,
      BiConsumer<TargetRef, Boolean> setChannelPinned,
      Predicate<TargetRef> isChannelMuted,
      BiConsumer<TargetRef, Boolean> setChannelMuted,
      Consumer<TargetRef> openChannelModeDetails,
      Consumer<TargetRef> requestChannelModeRefresh,
      Predicate<TargetRef> canEditChannelModes,
      BiConsumer<TargetRef, String> promptAndRequestChannelModeSet,
      Consumer<TargetRef> requestCloseTarget,
      Function<TargetRef, InterceptorDefinition> interceptorDefinition,
      BiConsumer<TargetRef, Boolean> setInterceptorEnabled,
      BiConsumer<TargetRef, String> promptRenameInterceptor,
      BiConsumer<TargetRef, String> confirmDeleteInterceptor) {
    return new DelegatingContext(
        isServerNode,
        isRootServerNode,
        prettyServerLabel,
        connectionStateForServer,
        connectionDiagnosticsTipForServer,
        serverEntry,
        moveNodeUpAction,
        moveNodeDownAction,
        requestConnectServer,
        requestDisconnectServer,
        openServerInfoDialog,
        openQuasselSetup,
        openQuasselNetworkManager,
        interceptorStoreAvailable,
        promptAndAddInterceptor,
        serverDialogsAvailable,
        openSaveEphemeralServer,
        openEditServer,
        runtimeConfigAvailable,
        readServerAutoConnectOnStart,
        rememberServerAutoConnectOnStart,
        isSojuEphemeralServer,
        isZncEphemeralServer,
        sojuOriginForServer,
        zncOriginForServer,
        serverDisplayNameOrDefault,
        isSojuAutoConnectEnabled,
        isZncAutoConnectEnabled,
        setSojuAutoConnectEnabled,
        setZncAutoConnectEnabled,
        refreshSojuAutoConnectBadges,
        refreshZncAutoConnectBadges,
        isInterceptorsGroupNode,
        owningServerIdForNode,
        openPinnedChat,
        confirmAndRequestClearLog,
        isChannelDisconnected,
        requestJoinChannel,
        requestDisconnectChannel,
        requestCloseChannel,
        supportsBouncerDetach,
        requestBouncerDetachChannel,
        isChannelAutoReattach,
        setChannelAutoReattach,
        isChannelPinned,
        setChannelPinned,
        isChannelMuted,
        setChannelMuted,
        openChannelModeDetails,
        requestChannelModeRefresh,
        canEditChannelModes,
        promptAndRequestChannelModeSet,
        requestCloseTarget,
        interceptorDefinition,
        setInterceptorEnabled,
        promptRenameInterceptor,
        confirmDeleteInterceptor);
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

    JMenu channelModes = new JMenu("Channel Modes");

    JMenuItem modeDetails = new JMenuItem("View Details...");
    modeDetails.addActionListener(ev -> context.openChannelModeDetails(ref));
    channelModes.add(modeDetails);

    JMenuItem refreshModes = new JMenuItem("Refresh Modes");
    refreshModes.addActionListener(ev -> context.requestChannelModeRefresh(ref));
    channelModes.add(refreshModes);

    JMenuItem setModes = new JMenuItem("Set Modes...");
    boolean canEditModes = context.canEditChannelModes(ref);
    setModes.setEnabled(canEditModes);
    setModes.setToolTipText(
        canEditModes
            ? "Set channel modes (sends /mode command)"
            : "Requires owner/admin/op privileges for this channel");
    setModes.addActionListener(ev -> context.promptAndRequestChannelModeSet(ref, nodeData.label));
    channelModes.add(setModes);
    menu.add(channelModes);

    JCheckBoxMenuItem muted = new JCheckBoxMenuItem("Mute notifications in this channel");
    muted.setSelected(context.isChannelMuted(ref));
    muted.addActionListener(ev -> context.setChannelMuted(ref, muted.isSelected()));
    menu.add(muted);

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

  private static final class DelegatingContext implements Context {
    private final Predicate<DefaultMutableTreeNode> isServerNode;
    private final Predicate<DefaultMutableTreeNode> isRootServerNode;
    private final Function<String, String> prettyServerLabel;
    private final Function<String, ConnectionState> connectionStateForServer;
    private final Function<String, String> connectionDiagnosticsTipForServer;
    private final Function<String, Optional<ServerEntry>> serverEntry;
    private final Supplier<Action> moveNodeUpAction;
    private final Supplier<Action> moveNodeDownAction;
    private final Consumer<String> requestConnectServer;
    private final Consumer<String> requestDisconnectServer;
    private final Consumer<String> openServerInfoDialog;
    private final Consumer<String> openQuasselSetup;
    private final Consumer<String> openQuasselNetworkManager;
    private final Supplier<Boolean> interceptorStoreAvailable;
    private final Consumer<String> promptAndAddInterceptor;
    private final Supplier<Boolean> serverDialogsAvailable;
    private final Consumer<String> openSaveEphemeralServer;
    private final Consumer<String> openEditServer;
    private final Supplier<Boolean> runtimeConfigAvailable;
    private final BiFunction<String, Boolean, Boolean> readServerAutoConnectOnStart;
    private final BiConsumer<String, Boolean> rememberServerAutoConnectOnStart;
    private final Predicate<String> isSojuEphemeralServer;
    private final Predicate<String> isZncEphemeralServer;
    private final Function<String, String> sojuOriginForServer;
    private final Function<String, String> zncOriginForServer;
    private final Function<String, String> serverDisplayNameOrDefault;
    private final BiPredicate<String, String> isSojuAutoConnectEnabled;
    private final BiPredicate<String, String> isZncAutoConnectEnabled;
    private final TriConsumer<String, String, Boolean> setSojuAutoConnectEnabled;
    private final TriConsumer<String, String, Boolean> setZncAutoConnectEnabled;
    private final Runnable refreshSojuAutoConnectBadges;
    private final Runnable refreshZncAutoConnectBadges;
    private final Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode;
    private final Function<DefaultMutableTreeNode, String> owningServerIdForNode;
    private final Consumer<TargetRef> openPinnedChat;
    private final BiConsumer<TargetRef, String> confirmAndRequestClearLog;
    private final Predicate<TargetRef> isChannelDisconnected;
    private final Consumer<TargetRef> requestJoinChannel;
    private final Consumer<TargetRef> requestDisconnectChannel;
    private final Consumer<TargetRef> requestCloseChannel;
    private final Predicate<String> supportsBouncerDetach;
    private final Consumer<TargetRef> requestBouncerDetachChannel;
    private final Predicate<TargetRef> isChannelAutoReattach;
    private final BiConsumer<TargetRef, Boolean> setChannelAutoReattach;
    private final Predicate<TargetRef> isChannelPinned;
    private final BiConsumer<TargetRef, Boolean> setChannelPinned;
    private final Predicate<TargetRef> isChannelMuted;
    private final BiConsumer<TargetRef, Boolean> setChannelMuted;
    private final Consumer<TargetRef> openChannelModeDetails;
    private final Consumer<TargetRef> requestChannelModeRefresh;
    private final Predicate<TargetRef> canEditChannelModes;
    private final BiConsumer<TargetRef, String> promptAndRequestChannelModeSet;
    private final Consumer<TargetRef> requestCloseTarget;
    private final Function<TargetRef, InterceptorDefinition> interceptorDefinition;
    private final BiConsumer<TargetRef, Boolean> setInterceptorEnabled;
    private final BiConsumer<TargetRef, String> promptRenameInterceptor;
    private final BiConsumer<TargetRef, String> confirmDeleteInterceptor;

    private DelegatingContext(
        Predicate<DefaultMutableTreeNode> isServerNode,
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
        Function<String, String> sojuOriginForServer,
        Function<String, String> zncOriginForServer,
        Function<String, String> serverDisplayNameOrDefault,
        BiPredicate<String, String> isSojuAutoConnectEnabled,
        BiPredicate<String, String> isZncAutoConnectEnabled,
        TriConsumer<String, String, Boolean> setSojuAutoConnectEnabled,
        TriConsumer<String, String, Boolean> setZncAutoConnectEnabled,
        Runnable refreshSojuAutoConnectBadges,
        Runnable refreshZncAutoConnectBadges,
        Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
        Function<DefaultMutableTreeNode, String> owningServerIdForNode,
        Consumer<TargetRef> openPinnedChat,
        BiConsumer<TargetRef, String> confirmAndRequestClearLog,
        Predicate<TargetRef> isChannelDisconnected,
        Consumer<TargetRef> requestJoinChannel,
        Consumer<TargetRef> requestDisconnectChannel,
        Consumer<TargetRef> requestCloseChannel,
        Predicate<String> supportsBouncerDetach,
        Consumer<TargetRef> requestBouncerDetachChannel,
        Predicate<TargetRef> isChannelAutoReattach,
        BiConsumer<TargetRef, Boolean> setChannelAutoReattach,
        Predicate<TargetRef> isChannelPinned,
        BiConsumer<TargetRef, Boolean> setChannelPinned,
        Predicate<TargetRef> isChannelMuted,
        BiConsumer<TargetRef, Boolean> setChannelMuted,
        Consumer<TargetRef> openChannelModeDetails,
        Consumer<TargetRef> requestChannelModeRefresh,
        Predicate<TargetRef> canEditChannelModes,
        BiConsumer<TargetRef, String> promptAndRequestChannelModeSet,
        Consumer<TargetRef> requestCloseTarget,
        Function<TargetRef, InterceptorDefinition> interceptorDefinition,
        BiConsumer<TargetRef, Boolean> setInterceptorEnabled,
        BiConsumer<TargetRef, String> promptRenameInterceptor,
        BiConsumer<TargetRef, String> confirmDeleteInterceptor) {
      this.isServerNode = Objects.requireNonNull(isServerNode, "isServerNode");
      this.isRootServerNode = Objects.requireNonNull(isRootServerNode, "isRootServerNode");
      this.prettyServerLabel = Objects.requireNonNull(prettyServerLabel, "prettyServerLabel");
      this.connectionStateForServer =
          Objects.requireNonNull(connectionStateForServer, "connectionStateForServer");
      this.connectionDiagnosticsTipForServer =
          Objects.requireNonNull(
              connectionDiagnosticsTipForServer, "connectionDiagnosticsTipForServer");
      this.serverEntry = Objects.requireNonNull(serverEntry, "serverEntry");
      this.moveNodeUpAction = Objects.requireNonNull(moveNodeUpAction, "moveNodeUpAction");
      this.moveNodeDownAction = Objects.requireNonNull(moveNodeDownAction, "moveNodeDownAction");
      this.requestConnectServer = Objects.requireNonNull(requestConnectServer, "requestConnectServer");
      this.requestDisconnectServer =
          Objects.requireNonNull(requestDisconnectServer, "requestDisconnectServer");
      this.openServerInfoDialog = Objects.requireNonNull(openServerInfoDialog, "openServerInfoDialog");
      this.openQuasselSetup = Objects.requireNonNull(openQuasselSetup, "openQuasselSetup");
      this.openQuasselNetworkManager =
          Objects.requireNonNull(openQuasselNetworkManager, "openQuasselNetworkManager");
      this.interceptorStoreAvailable =
          Objects.requireNonNull(interceptorStoreAvailable, "interceptorStoreAvailable");
      this.promptAndAddInterceptor =
          Objects.requireNonNull(promptAndAddInterceptor, "promptAndAddInterceptor");
      this.serverDialogsAvailable =
          Objects.requireNonNull(serverDialogsAvailable, "serverDialogsAvailable");
      this.openSaveEphemeralServer =
          Objects.requireNonNull(openSaveEphemeralServer, "openSaveEphemeralServer");
      this.openEditServer = Objects.requireNonNull(openEditServer, "openEditServer");
      this.runtimeConfigAvailable =
          Objects.requireNonNull(runtimeConfigAvailable, "runtimeConfigAvailable");
      this.readServerAutoConnectOnStart =
          Objects.requireNonNull(readServerAutoConnectOnStart, "readServerAutoConnectOnStart");
      this.rememberServerAutoConnectOnStart =
          Objects.requireNonNull(rememberServerAutoConnectOnStart, "rememberServerAutoConnectOnStart");
      this.isSojuEphemeralServer =
          Objects.requireNonNull(isSojuEphemeralServer, "isSojuEphemeralServer");
      this.isZncEphemeralServer = Objects.requireNonNull(isZncEphemeralServer, "isZncEphemeralServer");
      this.sojuOriginForServer = Objects.requireNonNull(sojuOriginForServer, "sojuOriginForServer");
      this.zncOriginForServer = Objects.requireNonNull(zncOriginForServer, "zncOriginForServer");
      this.serverDisplayNameOrDefault =
          Objects.requireNonNull(serverDisplayNameOrDefault, "serverDisplayNameOrDefault");
      this.isSojuAutoConnectEnabled =
          Objects.requireNonNull(isSojuAutoConnectEnabled, "isSojuAutoConnectEnabled");
      this.isZncAutoConnectEnabled =
          Objects.requireNonNull(isZncAutoConnectEnabled, "isZncAutoConnectEnabled");
      this.setSojuAutoConnectEnabled =
          Objects.requireNonNull(setSojuAutoConnectEnabled, "setSojuAutoConnectEnabled");
      this.setZncAutoConnectEnabled =
          Objects.requireNonNull(setZncAutoConnectEnabled, "setZncAutoConnectEnabled");
      this.refreshSojuAutoConnectBadges =
          Objects.requireNonNull(refreshSojuAutoConnectBadges, "refreshSojuAutoConnectBadges");
      this.refreshZncAutoConnectBadges =
          Objects.requireNonNull(refreshZncAutoConnectBadges, "refreshZncAutoConnectBadges");
      this.isInterceptorsGroupNode =
          Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
      this.owningServerIdForNode =
          Objects.requireNonNull(owningServerIdForNode, "owningServerIdForNode");
      this.openPinnedChat = Objects.requireNonNull(openPinnedChat, "openPinnedChat");
      this.confirmAndRequestClearLog =
          Objects.requireNonNull(confirmAndRequestClearLog, "confirmAndRequestClearLog");
      this.isChannelDisconnected =
          Objects.requireNonNull(isChannelDisconnected, "isChannelDisconnected");
      this.requestJoinChannel = Objects.requireNonNull(requestJoinChannel, "requestJoinChannel");
      this.requestDisconnectChannel =
          Objects.requireNonNull(requestDisconnectChannel, "requestDisconnectChannel");
      this.requestCloseChannel = Objects.requireNonNull(requestCloseChannel, "requestCloseChannel");
      this.supportsBouncerDetach =
          Objects.requireNonNull(supportsBouncerDetach, "supportsBouncerDetach");
      this.requestBouncerDetachChannel =
          Objects.requireNonNull(requestBouncerDetachChannel, "requestBouncerDetachChannel");
      this.isChannelAutoReattach =
          Objects.requireNonNull(isChannelAutoReattach, "isChannelAutoReattach");
      this.setChannelAutoReattach =
          Objects.requireNonNull(setChannelAutoReattach, "setChannelAutoReattach");
      this.isChannelPinned = Objects.requireNonNull(isChannelPinned, "isChannelPinned");
      this.setChannelPinned = Objects.requireNonNull(setChannelPinned, "setChannelPinned");
      this.isChannelMuted = Objects.requireNonNull(isChannelMuted, "isChannelMuted");
      this.setChannelMuted = Objects.requireNonNull(setChannelMuted, "setChannelMuted");
      this.openChannelModeDetails =
          Objects.requireNonNull(openChannelModeDetails, "openChannelModeDetails");
      this.requestChannelModeRefresh =
          Objects.requireNonNull(requestChannelModeRefresh, "requestChannelModeRefresh");
      this.canEditChannelModes = Objects.requireNonNull(canEditChannelModes, "canEditChannelModes");
      this.promptAndRequestChannelModeSet =
          Objects.requireNonNull(promptAndRequestChannelModeSet, "promptAndRequestChannelModeSet");
      this.requestCloseTarget = Objects.requireNonNull(requestCloseTarget, "requestCloseTarget");
      this.interceptorDefinition =
          Objects.requireNonNull(interceptorDefinition, "interceptorDefinition");
      this.setInterceptorEnabled =
          Objects.requireNonNull(setInterceptorEnabled, "setInterceptorEnabled");
      this.promptRenameInterceptor =
          Objects.requireNonNull(promptRenameInterceptor, "promptRenameInterceptor");
      this.confirmDeleteInterceptor =
          Objects.requireNonNull(confirmDeleteInterceptor, "confirmDeleteInterceptor");
    }

    @Override
    public boolean isServerNode(DefaultMutableTreeNode node) {
      return isServerNode.test(node);
    }

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
    public String sojuOriginForServer(String serverId) {
      return sojuOriginForServer.apply(serverId);
    }

    @Override
    public String zncOriginForServer(String serverId) {
      return zncOriginForServer.apply(serverId);
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
    public void setSojuAutoConnectEnabled(String originId, String networkKey, boolean enabled) {
      setSojuAutoConnectEnabled.accept(originId, networkKey, enabled);
    }

    @Override
    public void setZncAutoConnectEnabled(String originId, String networkKey, boolean enabled) {
      setZncAutoConnectEnabled.accept(originId, networkKey, enabled);
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
    public boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
      return isInterceptorsGroupNode.test(node);
    }

    @Override
    public String owningServerIdForNode(DefaultMutableTreeNode node) {
      return owningServerIdForNode.apply(node);
    }

    @Override
    public void openPinnedChat(TargetRef ref) {
      openPinnedChat.accept(ref);
    }

    @Override
    public void confirmAndRequestClearLog(TargetRef target, String label) {
      confirmAndRequestClearLog.accept(target, label);
    }

    @Override
    public boolean isChannelDisconnected(TargetRef target) {
      return isChannelDisconnected.test(target);
    }

    @Override
    public void requestJoinChannel(TargetRef target) {
      requestJoinChannel.accept(target);
    }

    @Override
    public void requestDisconnectChannel(TargetRef target) {
      requestDisconnectChannel.accept(target);
    }

    @Override
    public void requestCloseChannel(TargetRef target) {
      requestCloseChannel.accept(target);
    }

    @Override
    public boolean supportsBouncerDetach(String serverId) {
      return supportsBouncerDetach.test(serverId);
    }

    @Override
    public void requestBouncerDetachChannel(TargetRef target) {
      requestBouncerDetachChannel.accept(target);
    }

    @Override
    public boolean isChannelAutoReattach(TargetRef target) {
      return isChannelAutoReattach.test(target);
    }

    @Override
    public void setChannelAutoReattach(TargetRef target, boolean autoReattach) {
      setChannelAutoReattach.accept(target, autoReattach);
    }

    @Override
    public boolean isChannelPinned(TargetRef target) {
      return isChannelPinned.test(target);
    }

    @Override
    public void setChannelPinned(TargetRef target, boolean pinned) {
      setChannelPinned.accept(target, pinned);
    }

    @Override
    public boolean isChannelMuted(TargetRef target) {
      return isChannelMuted.test(target);
    }

    @Override
    public void setChannelMuted(TargetRef target, boolean muted) {
      setChannelMuted.accept(target, muted);
    }

    @Override
    public void openChannelModeDetails(TargetRef target) {
      openChannelModeDetails.accept(target);
    }

    @Override
    public void requestChannelModeRefresh(TargetRef target) {
      requestChannelModeRefresh.accept(target);
    }

    @Override
    public boolean canEditChannelModes(TargetRef target) {
      return canEditChannelModes.test(target);
    }

    @Override
    public void promptAndRequestChannelModeSet(TargetRef target, String channelLabel) {
      promptAndRequestChannelModeSet.accept(target, channelLabel);
    }

    @Override
    public void requestCloseTarget(TargetRef target) {
      requestCloseTarget.accept(target);
    }

    @Override
    public InterceptorDefinition interceptorDefinition(TargetRef target) {
      return interceptorDefinition.apply(target);
    }

    @Override
    public void setInterceptorEnabled(TargetRef target, boolean enabled) {
      setInterceptorEnabled.accept(target, enabled);
    }

    @Override
    public void promptRenameInterceptor(TargetRef target, String currentLabel) {
      promptRenameInterceptor.accept(target, currentLabel);
    }

    @Override
    public void confirmDeleteInterceptor(TargetRef target, String label) {
      confirmDeleteInterceptor.accept(target, label);
    }
  }
}
