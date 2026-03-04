package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.Objects;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/** Builds target-node context menus for server tree channels and built-ins. */
final class ServerTreeTargetNodeMenuBuilder {

  private final ServerTreeContextMenuBuilder.Context context;

  ServerTreeTargetNodeMenuBuilder(ServerTreeContextMenuBuilder.Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  JPopupMenu buildTargetNodeMenu(ServerTreeNodeData nodeData) {
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
}
