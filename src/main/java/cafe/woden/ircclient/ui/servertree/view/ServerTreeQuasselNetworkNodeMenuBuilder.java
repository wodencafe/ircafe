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

    void openQuasselSetup(String serverId);

    void openQuasselNetworkManager(String serverId);
  }

  public static Context context(
      Consumer<TargetRef> openPinnedChat,
      Consumer<String> openQuasselSetup,
      Consumer<String> openQuasselNetworkManager) {
    Objects.requireNonNull(openPinnedChat, "openPinnedChat");
    Objects.requireNonNull(openQuasselSetup, "openQuasselSetup");
    Objects.requireNonNull(openQuasselNetworkManager, "openQuasselNetworkManager");
    return new Context() {
      @Override
      public void openPinnedChat(TargetRef ref) {
        openPinnedChat.accept(ref);
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
      TargetRef channelListRef = TargetRef.channelList(serverId, nodeData.networkToken());
      JMenuItem open = new JMenuItem("Open \"" + label + "\" Channel List");
      open.setIcon(SvgIcons.action("add", 16));
      open.setDisabledIcon(SvgIcons.actionDisabled("add", 16));
      open.addActionListener(ev -> context.openPinnedChat(channelListRef));
      menu.add(open);
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
