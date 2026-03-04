package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.soju.SojuAutoConnectStore;
import cafe.woden.ircclient.irc.znc.ZncAutoConnectStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.ui.servers.ServerDialogs;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeContextMenuBuilder;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Factory for composing {@link ServerTreeContextMenuBuilder.Context} from dockable collaborators.
 */
public final class ServerTreeContextMenuContextFactory {

  private ServerTreeContextMenuContextFactory() {}

  public record Inputs(
      Predicate<DefaultMutableTreeNode> isServerNode,
      Predicate<DefaultMutableTreeNode> isRootServerNode,
      Function<String, String> prettyServerLabel,
      Function<String, ConnectionState> connectionStateForServer,
      ServerCatalog serverCatalog,
      Supplier<Action> moveNodeUpAction,
      Supplier<Action> moveNodeDownAction,
      Consumer<String> requestConnectServer,
      Consumer<String> requestDisconnectServer,
      Consumer<String> openServerInfoDialog,
      Consumer<String> openQuasselNetworkManager,
      InterceptorStore interceptorStore,
      Consumer<String> promptAndAddInterceptor,
      ServerDialogs serverDialogs,
      Component ownerComponent,
      RuntimeConfigStore runtimeConfig,
      Predicate<String> isSojuEphemeralServer,
      Predicate<String> isZncEphemeralServer,
      Function<String, String> sojuOriginByServerId,
      Function<String, String> zncOriginByServerId,
      Function<String, String> serverDisplayName,
      SojuAutoConnectStore sojuAutoConnect,
      ZncAutoConnectStore zncAutoConnect,
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
      BiConsumer<TargetRef, String> requestChannelModeSet,
      Consumer<TargetRef> requestCloseTarget,
      BiConsumer<TargetRef, Boolean> setInterceptorEnabled,
      BiConsumer<TargetRef, String> promptRenameInterceptor,
      BiConsumer<TargetRef, String> confirmDeleteInterceptor) {}

  public static ServerTreeContextMenuBuilder.Context create(Inputs input) {
    Inputs in = Objects.requireNonNull(input, "input");
    Objects.requireNonNull(in.ownerComponent(), "ownerComponent");
    return new ServerTreeContextMenuBuilderContextAdapter(
        Objects.requireNonNull(in.isServerNode(), "isServerNode"),
        Objects.requireNonNull(in.isRootServerNode(), "isRootServerNode"),
        Objects.requireNonNull(in.prettyServerLabel(), "prettyServerLabel"),
        Objects.requireNonNull(in.connectionStateForServer(), "connectionStateForServer"),
        serverId -> {
          ServerCatalog catalog = in.serverCatalog();
          return catalog != null ? catalog.findEntry(serverId) : Optional.empty();
        },
        Objects.requireNonNull(in.moveNodeUpAction(), "moveNodeUpAction"),
        Objects.requireNonNull(in.moveNodeDownAction(), "moveNodeDownAction"),
        Objects.requireNonNull(in.requestConnectServer(), "requestConnectServer"),
        Objects.requireNonNull(in.requestDisconnectServer(), "requestDisconnectServer"),
        Objects.requireNonNull(in.openServerInfoDialog(), "openServerInfoDialog"),
        Objects.requireNonNull(in.openQuasselNetworkManager(), "openQuasselNetworkManager"),
        () -> in.interceptorStore() != null,
        Objects.requireNonNull(in.promptAndAddInterceptor(), "promptAndAddInterceptor"),
        () -> in.serverDialogs() != null,
        serverId -> {
          ServerDialogs dialogs = in.serverDialogs();
          if (dialogs == null) return;
          Window window = SwingUtilities.getWindowAncestor(in.ownerComponent());
          dialogs.openSaveEphemeralServer(window, serverId);
        },
        serverId -> {
          ServerDialogs dialogs = in.serverDialogs();
          if (dialogs == null) return;
          Window window = SwingUtilities.getWindowAncestor(in.ownerComponent());
          dialogs.openEditServer(window, serverId);
        },
        () -> in.runtimeConfig() != null,
        (serverId, defaultValue) -> {
          RuntimeConfigStore config = in.runtimeConfig();
          return config == null
              ? defaultValue
              : config.readServerAutoConnectOnStart(serverId, defaultValue);
        },
        (serverId, enabled) -> {
          RuntimeConfigStore config = in.runtimeConfig();
          if (config == null) return;
          config.rememberServerAutoConnectOnStart(serverId, enabled);
        },
        Objects.requireNonNull(in.isSojuEphemeralServer(), "isSojuEphemeralServer"),
        Objects.requireNonNull(in.isZncEphemeralServer(), "isZncEphemeralServer"),
        Objects.requireNonNull(in.sojuOriginByServerId(), "sojuOriginByServerId"),
        Objects.requireNonNull(in.zncOriginByServerId(), "zncOriginByServerId"),
        Objects.requireNonNull(in.serverDisplayName(), "serverDisplayName"),
        (originId, networkKey) -> {
          SojuAutoConnectStore store = in.sojuAutoConnect();
          return store != null && store.isEnabled(originId, networkKey);
        },
        (originId, networkKey) -> {
          ZncAutoConnectStore store = in.zncAutoConnect();
          return store != null && store.isEnabled(originId, networkKey);
        },
        (originId, networkKey, enabled) -> {
          SojuAutoConnectStore store = in.sojuAutoConnect();
          if (store == null) return;
          store.setEnabled(originId, networkKey, enabled);
        },
        (originId, networkKey, enabled) -> {
          ZncAutoConnectStore store = in.zncAutoConnect();
          if (store == null) return;
          store.setEnabled(originId, networkKey, enabled);
        },
        Objects.requireNonNull(in.refreshSojuAutoConnectBadges(), "refreshSojuAutoConnectBadges"),
        Objects.requireNonNull(in.refreshZncAutoConnectBadges(), "refreshZncAutoConnectBadges"),
        Objects.requireNonNull(in.isInterceptorsGroupNode(), "isInterceptorsGroupNode"),
        Objects.requireNonNull(in.owningServerIdForNode(), "owningServerIdForNode"),
        Objects.requireNonNull(in.openPinnedChat(), "openPinnedChat"),
        Objects.requireNonNull(in.confirmAndRequestClearLog(), "confirmAndRequestClearLog"),
        Objects.requireNonNull(in.isChannelDisconnected(), "isChannelDisconnected"),
        Objects.requireNonNull(in.requestJoinChannel(), "requestJoinChannel"),
        Objects.requireNonNull(in.requestDisconnectChannel(), "requestDisconnectChannel"),
        Objects.requireNonNull(in.requestCloseChannel(), "requestCloseChannel"),
        Objects.requireNonNull(in.supportsBouncerDetach(), "supportsBouncerDetach"),
        Objects.requireNonNull(in.requestBouncerDetachChannel(), "requestBouncerDetachChannel"),
        Objects.requireNonNull(in.isChannelAutoReattach(), "isChannelAutoReattach"),
        Objects.requireNonNull(in.setChannelAutoReattach(), "setChannelAutoReattach"),
        Objects.requireNonNull(in.isChannelPinned(), "isChannelPinned"),
        Objects.requireNonNull(in.setChannelPinned(), "setChannelPinned"),
        Objects.requireNonNull(in.isChannelMuted(), "isChannelMuted"),
        Objects.requireNonNull(in.setChannelMuted(), "setChannelMuted"),
        Objects.requireNonNull(in.openChannelModeDetails(), "openChannelModeDetails"),
        Objects.requireNonNull(in.requestChannelModeRefresh(), "requestChannelModeRefresh"),
        Objects.requireNonNull(in.canEditChannelModes(), "canEditChannelModes"),
        (target, channelLabel) -> {
          if (target == null || !target.isChannel()) return;
          if (!in.canEditChannelModes().test(target)) return;
          if (GraphicsEnvironment.isHeadless()) return;

          Window owner = SwingUtilities.getWindowAncestor(in.ownerComponent());
          String pretty =
              Objects.toString(channelLabel, "").isBlank()
                  ? target.target()
                  : Objects.toString(channelLabel, "");
          String modeSpec =
              Objects.toString(
                      JOptionPane.showInputDialog(
                          owner,
                          "Enter channel mode changes for "
                              + pretty
                              + " (examples: +m, -m, +o nick):",
                          ""),
                      "")
                  .trim();
          if (modeSpec.isEmpty()) return;
          in.requestChannelModeSet().accept(target, modeSpec);
        },
        Objects.requireNonNull(in.requestCloseTarget(), "requestCloseTarget"),
        target -> interceptorDefinition(in.interceptorStore(), target),
        Objects.requireNonNull(in.setInterceptorEnabled(), "setInterceptorEnabled"),
        Objects.requireNonNull(in.promptRenameInterceptor(), "promptRenameInterceptor"),
        Objects.requireNonNull(in.confirmDeleteInterceptor(), "confirmDeleteInterceptor"));
  }

  private static InterceptorDefinition interceptorDefinition(
      InterceptorStore interceptorStore, TargetRef target) {
    if (interceptorStore == null || target == null || !target.isInterceptor()) return null;
    String serverId = Objects.toString(target.serverId(), "").trim();
    String interceptorId = Objects.toString(target.interceptorId(), "").trim();
    if (serverId.isEmpty() || interceptorId.isEmpty()) return null;
    return interceptorStore.interceptor(serverId, interceptorId);
  }
}
