package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.ServerEntry;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeContextMenuBuilder;
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
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeContextMenuBuilder.Context}. */
public final class ServerTreeContextMenuBuilderContextAdapter
    implements ServerTreeContextMenuBuilder.Context {

  private final Predicate<DefaultMutableTreeNode> isServerNode;
  private final Predicate<DefaultMutableTreeNode> isRootServerNode;
  private final Function<String, String> prettyServerLabel;
  private final Function<String, ConnectionState> connectionStateForServer;
  private final Function<String, Optional<ServerEntry>> serverEntry;
  private final Supplier<Action> moveNodeUpAction;
  private final Supplier<Action> moveNodeDownAction;
  private final Consumer<String> requestConnectServer;
  private final Consumer<String> requestDisconnectServer;
  private final Consumer<String> openServerInfoDialog;
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
  private final Consumer<TargetRef> requestCloseTarget;
  private final Function<TargetRef, InterceptorDefinition> interceptorDefinition;
  private final BiConsumer<TargetRef, Boolean> setInterceptorEnabled;
  private final BiConsumer<TargetRef, String> promptRenameInterceptor;
  private final BiConsumer<TargetRef, String> confirmDeleteInterceptor;

  @FunctionalInterface
  public interface TriConsumer<A, B, C> {
    void accept(A first, B second, C third);
  }

  public ServerTreeContextMenuBuilderContextAdapter(
      Predicate<DefaultMutableTreeNode> isServerNode,
      Predicate<DefaultMutableTreeNode> isRootServerNode,
      Function<String, String> prettyServerLabel,
      Function<String, ConnectionState> connectionStateForServer,
      Function<String, Optional<ServerEntry>> serverEntry,
      Supplier<Action> moveNodeUpAction,
      Supplier<Action> moveNodeDownAction,
      Consumer<String> requestConnectServer,
      Consumer<String> requestDisconnectServer,
      Consumer<String> openServerInfoDialog,
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
    this.serverEntry = Objects.requireNonNull(serverEntry, "serverEntry");
    this.moveNodeUpAction = Objects.requireNonNull(moveNodeUpAction, "moveNodeUpAction");
    this.moveNodeDownAction = Objects.requireNonNull(moveNodeDownAction, "moveNodeDownAction");
    this.requestConnectServer =
        Objects.requireNonNull(requestConnectServer, "requestConnectServer");
    this.requestDisconnectServer =
        Objects.requireNonNull(requestDisconnectServer, "requestDisconnectServer");
    this.openServerInfoDialog =
        Objects.requireNonNull(openServerInfoDialog, "openServerInfoDialog");
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
        Objects.requireNonNull(
            rememberServerAutoConnectOnStart, "rememberServerAutoConnectOnStart");
    this.isSojuEphemeralServer =
        Objects.requireNonNull(isSojuEphemeralServer, "isSojuEphemeralServer");
    this.isZncEphemeralServer =
        Objects.requireNonNull(isZncEphemeralServer, "isZncEphemeralServer");
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
