package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeQuasselNetworkNodeData;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/** Builds context menus for Quassel network container and empty-state tree nodes. */
public final class ServerTreeQuasselNetworkNodeMenuBuilder {

  public interface Context {
    void openPinnedChat(TargetRef ref);

    void connectQuasselNetwork(String serverId, String networkToken);

    void disconnectQuasselNetwork(String serverId, String networkToken);

    void removeQuasselNetwork(String serverId, String networkToken);

    void addQuasselNetwork(String serverId);

    void openQuasselSetup(String serverId);

    void openQuasselNetworkManager(String serverId);
  }

  public static Context context(
      Consumer<TargetRef> openPinnedChat,
      java.util.function.BiConsumer<String, String> connectQuasselNetwork,
      java.util.function.BiConsumer<String, String> disconnectQuasselNetwork,
      java.util.function.BiConsumer<String, String> removeQuasselNetwork,
      Consumer<String> addQuasselNetwork,
      Consumer<String> openQuasselSetup,
      Consumer<String> openQuasselNetworkManager) {
    Objects.requireNonNull(openPinnedChat, "openPinnedChat");
    Objects.requireNonNull(connectQuasselNetwork, "connectQuasselNetwork");
    Objects.requireNonNull(disconnectQuasselNetwork, "disconnectQuasselNetwork");
    Objects.requireNonNull(removeQuasselNetwork, "removeQuasselNetwork");
    Objects.requireNonNull(addQuasselNetwork, "addQuasselNetwork");
    Objects.requireNonNull(openQuasselSetup, "openQuasselSetup");
    Objects.requireNonNull(openQuasselNetworkManager, "openQuasselNetworkManager");
    return new Context() {
      @Override
      public void openPinnedChat(TargetRef ref) {
        openPinnedChat.accept(ref);
      }

      @Override
      public void connectQuasselNetwork(String serverId, String networkToken) {
        connectQuasselNetwork.accept(serverId, networkToken);
      }

      @Override
      public void disconnectQuasselNetwork(String serverId, String networkToken) {
        disconnectQuasselNetwork.accept(serverId, networkToken);
      }

      @Override
      public void removeQuasselNetwork(String serverId, String networkToken) {
        removeQuasselNetwork.accept(serverId, networkToken);
      }

      @Override
      public void addQuasselNetwork(String serverId) {
        addQuasselNetwork.accept(serverId);
      }

      @Override
      public void openQuasselSetup(String serverId) {
        openQuasselSetup.accept(serverId);
      }

      @Override
      public void openQuasselNetworkManager(String serverId) {
        openQuasselNetworkManager.accept(serverId);
      }
    };
  }

  private final Context context;

  public ServerTreeQuasselNetworkNodeMenuBuilder(Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  JPopupMenu buildNetworkNodeMenu(ServerTreeQuasselNetworkNodeData nodeData) {
    if (nodeData == null) return null;
    String serverId = Objects.toString(nodeData.serverId(), "").trim();
    if (serverId.isEmpty()) return null;

    JPopupMenu menu = new JPopupMenu();
    if (!nodeData.emptyState()) {
      String label = Objects.toString(nodeData.label(), "Network").trim();
      String networkToken = Objects.toString(nodeData.networkToken(), "").trim();
      TargetRef channelListRef = TargetRef.channelList(serverId, nodeData.networkToken());
      JMenuItem open = new JMenuItem("Open \"" + label + "\" Channel List");
      open.setIcon(SvgIcons.action("add", 16));
      open.setDisabledIcon(SvgIcons.actionDisabled("add", 16));
      open.addActionListener(ev -> context.openPinnedChat(channelListRef));
      menu.add(open);

      JMenuItem connect = new JMenuItem("Connect \"" + label + "\"");
      connect.setIcon(SvgIcons.action("plus", 16));
      connect.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
      connect.addActionListener(ev -> context.connectQuasselNetwork(serverId, networkToken));
      menu.add(connect);

      JMenuItem disconnect = new JMenuItem("Disconnect \"" + label + "\"");
      disconnect.setIcon(SvgIcons.action("exit", 16));
      disconnect.setDisabledIcon(SvgIcons.actionDisabled("exit", 16));
      disconnect.addActionListener(ev -> context.disconnectQuasselNetwork(serverId, networkToken));
      menu.add(disconnect);

      JMenuItem remove = new JMenuItem("Remove \"" + label + "\"");
      remove.setIcon(SvgIcons.action("close", 16));
      remove.setDisabledIcon(SvgIcons.actionDisabled("close", 16));
      remove.addActionListener(ev -> context.removeQuasselNetwork(serverId, networkToken));
      menu.add(remove);
      menu.addSeparator();
    } else {
      JMenuItem addNetwork = new JMenuItem("Add Quassel Network...");
      addNetwork.setIcon(SvgIcons.action("plus", 16));
      addNetwork.setDisabledIcon(SvgIcons.actionDisabled("plus", 16));
      addNetwork.addActionListener(ev -> context.addQuasselNetwork(serverId));
      menu.add(addNetwork);
      menu.addSeparator();
    }

    JMenuItem manageNetworks = new JMenuItem("Manage Quassel Networks...");
    manageNetworks.setIcon(SvgIcons.action("edit", 16));
    manageNetworks.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
    manageNetworks.addActionListener(ev -> context.openQuasselNetworkManager(serverId));
    menu.add(manageNetworks);

    JMenuItem setup = new JMenuItem("Run Quassel Setup...");
    setup.setIcon(SvgIcons.action("edit", 16));
    setup.setDisabledIcon(SvgIcons.actionDisabled("edit", 16));
    setup.addActionListener(ev -> context.openQuasselSetup(serverId));
    menu.add(setup);

    return menu;
  }
}
