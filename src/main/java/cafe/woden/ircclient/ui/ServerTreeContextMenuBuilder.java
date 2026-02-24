package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.TargetRef;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * Builds the context menu for entries in {@link ServerTreeDockable}.
 *
 * <p>This is intentionally decoupled from the Dockable so the Dockable can focus on tree/model
 * wiring, while this class focuses on menu construction/policy.
 */
final class ServerTreeContextMenuBuilder {

  private final Predicate<DefaultMutableTreeNode> isServerNode;
  private final Function<String, Boolean> isServerConnected;

  private final Consumer<String> connectServer;
  private final Consumer<String> disconnectServer;
  private final Consumer<TargetRef> closeTarget;
  private final Consumer<TargetRef> openPinnedChat;

  ServerTreeContextMenuBuilder(
      Predicate<DefaultMutableTreeNode> isServerNode,
      Function<String, Boolean> isServerConnected,
      Consumer<String> connectServer,
      Consumer<String> disconnectServer,
      Consumer<TargetRef> closeTarget,
      Consumer<TargetRef> openPinnedChat) {
    this.isServerNode = Objects.requireNonNull(isServerNode, "isServerNode");
    this.isServerConnected = Objects.requireNonNull(isServerConnected, "isServerConnected");
    this.connectServer = Objects.requireNonNull(connectServer, "connectServer");
    this.disconnectServer = Objects.requireNonNull(disconnectServer, "disconnectServer");
    this.closeTarget = Objects.requireNonNull(closeTarget, "closeTarget");
    this.openPinnedChat = Objects.requireNonNull(openPinnedChat, "openPinnedChat");
  }

  JPopupMenu build(TreePath path) {
    if (path == null) return null;

    Object last = path.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node)) return null;

    // Server node menu: connect/disconnect.
    if (isServerNode.test(node)) {
      String serverId = Objects.toString(node.getUserObject(), "").trim();
      if (serverId.isEmpty()) return null;

      boolean connected = Boolean.TRUE.equals(isServerConnected.apply(serverId));
      JPopupMenu menu = new JPopupMenu();

      JMenuItem connectOne = new JMenuItem("Connect \"" + serverId + "\"");
      connectOne.setEnabled(!connected);
      connectOne.addActionListener(ev -> connectServer.accept(serverId));
      menu.add(connectOne);

      JMenuItem disconnectOne = new JMenuItem("Disconnect \"" + serverId + "\"");
      disconnectOne.setEnabled(connected);
      disconnectOne.addActionListener(ev -> disconnectServer.accept(serverId));
      menu.add(disconnectOne);

      return menu;
    }

    Object uo = node.getUserObject();
    if (uo instanceof ServerTreeDockable.NodeData nd) {
      if (nd.ref != null) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem openDock = new JMenuItem("Open chat dock");
        openDock.addActionListener(ev -> openPinnedChat.accept(nd.ref));
        menu.add(openDock);

        if (!nd.ref.isStatus()) {
          menu.addSeparator();
          if (nd.ref.isChannel()) {
            JMenuItem leave = new JMenuItem("Leave \"" + nd.label + "\"");
            leave.addActionListener(ev -> closeTarget.accept(nd.ref));
            menu.add(leave);
          } else {
            JMenuItem close = new JMenuItem("Close \"" + nd.label + "\"");
            close.addActionListener(ev -> closeTarget.accept(nd.ref));
            menu.add(close);
          }
        }

        return menu;
      }
    }

    return null;
  }
}
