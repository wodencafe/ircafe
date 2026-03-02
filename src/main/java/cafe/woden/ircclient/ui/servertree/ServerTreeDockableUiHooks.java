package cafe.woden.ircclient.ui.servertree;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.request.ServerTreeRequestEmitter;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Default {@link ServerTreeUiHooks} implementation used by {@link ServerTreeDockable}. */
public final class ServerTreeDockableUiHooks implements ServerTreeUiHooks {

  private final Component dialogOwner;
  private final Map<String, ServerNodes> servers;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final Function<String, ConnectionState> connectionStateForServer;
  private final Predicate<TargetRef> isChannelDisconnected;
  private final ServerTreeRequestEmitter requestEmitter;

  public ServerTreeDockableUiHooks(
      Component dialogOwner,
      Map<String, ServerNodes> servers,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Function<String, ConnectionState> connectionStateForServer,
      Predicate<TargetRef> isChannelDisconnected,
      ServerTreeRequestEmitter requestEmitter) {
    this.dialogOwner = Objects.requireNonNull(dialogOwner, "dialogOwner");
    this.servers = Objects.requireNonNull(servers, "servers");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.connectionStateForServer =
        Objects.requireNonNull(connectionStateForServer, "connectionStateForServer");
    this.isChannelDisconnected =
        Objects.requireNonNull(isChannelDisconnected, "isChannelDisconnected");
    this.requestEmitter = Objects.requireNonNull(requestEmitter, "requestEmitter");
  }

  @Override
  public boolean isServerNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object userObject = node.getUserObject();
    if (!(userObject instanceof String serverId)) return false;
    ServerNodes serverNodes = servers.get(serverId);
    return serverNodes != null && serverNodes.serverNode == node;
  }

  @Override
  public boolean isChannelNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData nodeData)) return false;
    return nodeData.ref != null && nodeData.ref.isChannel();
  }

  @Override
  public TreePath serverPathForId(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return null;

    ServerNodes serverNodes = servers.get(sid);
    if (serverNodes == null
        || serverNodes.serverNode == null
        || serverNodes.serverNode.getPath() == null) {
      return null;
    }
    return new TreePath(serverNodes.serverNode.getPath());
  }

  @Override
  public TreePath channelPathForRef(TargetRef channelRef) {
    if (channelRef == null || !channelRef.isChannel()) return null;
    DefaultMutableTreeNode node = leaves.get(channelRef);
    if (node == null || node.getPath() == null) return null;
    return new TreePath(node.getPath());
  }

  @Override
  public ConnectionState connectionStateForServer(String serverId) {
    return connectionStateForServer.apply(serverId);
  }

  @Override
  public boolean isChannelDisconnected(TargetRef channelRef) {
    return isChannelDisconnected.test(channelRef);
  }

  @Override
  public void connectServer(String serverId) {
    requestEmitter.emitConnectServer(serverId);
  }

  @Override
  public void disconnectServer(String serverId) {
    requestEmitter.emitDisconnectServer(serverId);
  }

  @Override
  public void joinChannel(TargetRef channelRef) {
    requestEmitter.emitJoinChannel(channelRef);
  }

  @Override
  public void disconnectChannel(TargetRef channelRef) {
    requestEmitter.emitDisconnectChannel(channelRef);
  }

  @Override
  public void bouncerDetachChannel(TargetRef channelRef) {
    requestEmitter.emitBouncerDetachChannel(channelRef);
  }

  @Override
  public void closeChannel(TargetRef channelRef) {
    requestEmitter.emitCloseChannel(channelRef);
  }

  @Override
  public void confirmAndClearLog(TargetRef targetRef, String label) {
    if (targetRef == null) return;
    if (!(targetRef.isChannel() || targetRef.isStatus())) return;
    if (GraphicsEnvironment.isHeadless()) {
      requestEmitter.emitClearLog(targetRef);
      return;
    }

    Window owner = SwingUtilities.getWindowAncestor(dialogOwner);
    String pretty = (label == null || label.isBlank()) ? targetRef.target() : label;
    String scope = targetRef.isStatus() ? "status" : "channel";

    String message =
        "Clear log for "
            + scope
            + " \""
            + pretty
            + "\"?\n\n"
            + "This will permanently delete the persisted chat history for this target.";

    int choice =
        JOptionPane.showConfirmDialog(
            owner,
            message,
            "Clear Log",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

    if (choice == JOptionPane.YES_OPTION) {
      requestEmitter.emitClearLog(targetRef);
    }
  }

  @Override
  public void closeTarget(TargetRef targetRef) {
    requestEmitter.emitCloseTarget(targetRef);
  }

  @Override
  public void openPinnedChat(TargetRef targetRef) {
    requestEmitter.emitOpenPinnedChat(targetRef);
  }

  @Override
  public boolean confirmCloseChannel(TargetRef channelRef, String channelLabel) {
    if (channelRef == null || !channelRef.isChannel()) return false;
    if (GraphicsEnvironment.isHeadless()) return true;

    Window owner = SwingUtilities.getWindowAncestor(dialogOwner);
    String pretty =
        (channelLabel == null || channelLabel.isBlank()) ? channelRef.target() : channelLabel;
    String message =
        "Close and PART channel \""
            + pretty
            + "\"?\n\n"
            + "This will send PART if connected, then remove the channel from the server tree.";

    int choice =
        JOptionPane.showConfirmDialog(
            owner,
            message,
            "Close Channel",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    return choice == JOptionPane.YES_OPTION;
  }
}
