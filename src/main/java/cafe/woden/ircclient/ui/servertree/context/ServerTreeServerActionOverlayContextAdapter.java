package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.ServerTreeUiHooks;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeServerActionOverlay;
import java.util.Objects;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Adapter for {@link ServerTreeServerActionOverlay.Context}. */
public final class ServerTreeServerActionOverlayContextAdapter
    implements ServerTreeServerActionOverlay.Context {

  private final ServerTreeUiHooks uiHooks;

  public ServerTreeServerActionOverlayContextAdapter(ServerTreeUiHooks uiHooks) {
    this.uiHooks = Objects.requireNonNull(uiHooks, "uiHooks");
  }

  @Override
  public boolean isServerNode(DefaultMutableTreeNode node) {
    return uiHooks.isServerNode(node);
  }

  @Override
  public boolean isChannelNode(DefaultMutableTreeNode node) {
    return uiHooks.isChannelNode(node);
  }

  @Override
  public TreePath serverPathForId(String serverId) {
    return uiHooks.serverPathForId(serverId);
  }

  @Override
  public TreePath channelPathForRef(TargetRef channelRef) {
    return uiHooks.channelPathForRef(channelRef);
  }

  @Override
  public ConnectionState connectionStateForServer(String serverId) {
    return uiHooks.connectionStateForServer(serverId);
  }

  @Override
  public void connectServer(String serverId) {
    uiHooks.connectServer(serverId);
  }

  @Override
  public void disconnectServer(String serverId) {
    uiHooks.disconnectServer(serverId);
  }

  @Override
  public boolean isChannelDisconnected(TargetRef channelRef) {
    return uiHooks.isChannelDisconnected(channelRef);
  }

  @Override
  public void joinChannel(TargetRef channelRef) {
    uiHooks.joinChannel(channelRef);
  }

  @Override
  public void disconnectChannel(TargetRef channelRef) {
    uiHooks.disconnectChannel(channelRef);
  }

  @Override
  public boolean confirmCloseChannel(TargetRef channelRef, String channelLabel) {
    return uiHooks.confirmCloseChannel(channelRef, channelLabel);
  }

  @Override
  public void closeChannel(TargetRef channelRef) {
    uiHooks.closeChannel(channelRef);
  }
}
