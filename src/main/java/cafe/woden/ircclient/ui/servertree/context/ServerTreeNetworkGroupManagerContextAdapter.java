package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeNetworkGroupManager;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeNetworkGroupManager.Context}. */
public final class ServerTreeNetworkGroupManagerContextAdapter
    implements ServerTreeNetworkGroupManager.Context {

  private final Function<String, DefaultMutableTreeNode> serverNode;
  private final Function<String, DefaultMutableTreeNode> privateMessagesNode;

  public ServerTreeNetworkGroupManagerContextAdapter(
      Function<String, DefaultMutableTreeNode> serverNode,
      Function<String, DefaultMutableTreeNode> privateMessagesNode) {
    this.serverNode = Objects.requireNonNull(serverNode, "serverNode");
    this.privateMessagesNode = Objects.requireNonNull(privateMessagesNode, "privateMessagesNode");
  }

  @Override
  public DefaultMutableTreeNode serverNode(String serverId) {
    return serverNode.apply(serverId);
  }

  @Override
  public DefaultMutableTreeNode privateMessagesNode(String serverId) {
    return privateMessagesNode.apply(serverId);
  }
}
