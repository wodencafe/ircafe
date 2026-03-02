package cafe.woden.ircclient.ui.servertree;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * Small mediator-style hook surface for server-tree node/action behavior.
 *
 * <p>This keeps consumers (overlay/tooltip/context builders) from depending on many direct {@link
 * ServerTreeDockable} methods.
 */
public interface ServerTreeUiHooks {

  boolean isServerNode(DefaultMutableTreeNode node);

  boolean isChannelNode(DefaultMutableTreeNode node);

  TreePath serverPathForId(String serverId);

  TreePath channelPathForRef(TargetRef channelRef);

  ConnectionState connectionStateForServer(String serverId);

  boolean isChannelDisconnected(TargetRef channelRef);

  void connectServer(String serverId);

  void disconnectServer(String serverId);

  void joinChannel(TargetRef channelRef);

  void disconnectChannel(TargetRef channelRef);

  void closeChannel(TargetRef channelRef);

  boolean confirmCloseChannel(TargetRef channelRef, String channelLabel);
}
