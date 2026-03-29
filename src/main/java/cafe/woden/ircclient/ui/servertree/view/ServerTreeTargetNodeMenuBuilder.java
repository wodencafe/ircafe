package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/** Builds target-node context menus for server tree channels and built-ins. */
public final class ServerTreeTargetNodeMenuBuilder {

  public interface Context {
    void openPinnedChat(TargetRef ref);

    Action moveNodeUpAction();

    Action moveNodeDownAction();

    void confirmAndRequestClearLog(TargetRef target, String label);

    boolean isChannelDisconnected(TargetRef target);

    void requestJoinChannel(TargetRef target);

    void requestDisconnectChannel(TargetRef target);

    void requestCloseChannel(TargetRef target);

    boolean supportsBouncerDetach(String serverId);

    void requestBouncerDetachChannel(TargetRef target);

    boolean isChannelAutoReattach(TargetRef target);

    void setChannelAutoReattach(TargetRef target, boolean autoReattach);

    boolean isChannelPinned(TargetRef target);

    void setChannelPinned(TargetRef target, boolean pinned);

    boolean isChannelMuted(TargetRef target);

    void setChannelMuted(TargetRef target, boolean muted);

    void openChannelModeDetails(TargetRef target);

    void requestChannelModeRefresh(TargetRef target);

    boolean canEditChannelModes(TargetRef target);

    void promptAndRequestChannelModeSet(TargetRef target, String channelLabel);

    void requestCloseTarget(TargetRef target);

    InterceptorDefinition interceptorDefinition(TargetRef target);

    boolean interceptorStoreAvailable();

    void setInterceptorEnabled(TargetRef target, boolean enabled);

    void promptRenameInterceptor(TargetRef target, String currentLabel);

    void confirmDeleteInterceptor(TargetRef target, String label);
  }

  public static Context context(
      Consumer<TargetRef> openPinnedChat,
      Supplier<Action> moveNodeUpAction,
      Supplier<Action> moveNodeDownAction,
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
      BiConsumer<TargetRef, String> promptAndRequestChannelModeSet,
      Consumer<TargetRef> requestCloseTarget,
      Function<TargetRef, InterceptorDefinition> interceptorDefinition,
      Supplier<Boolean> interceptorStoreAvailable,
      BiConsumer<TargetRef, Boolean> setInterceptorEnabled,
      BiConsumer<TargetRef, String> promptRenameInterceptor,
      BiConsumer<TargetRef, String> confirmDeleteInterceptor) {
    Objects.requireNonNull(openPinnedChat, "openPinnedChat");
    Objects.requireNonNull(moveNodeUpAction, "moveNodeUpAction");
    Objects.requireNonNull(moveNodeDownAction, "moveNodeDownAction");
    Objects.requireNonNull(confirmAndRequestClearLog, "confirmAndRequestClearLog");
    Objects.requireNonNull(isChannelDisconnected, "isChannelDisconnected");
    Objects.requireNonNull(requestJoinChannel, "requestJoinChannel");
    Objects.requireNonNull(requestDisconnectChannel, "requestDisconnectChannel");
    Objects.requireNonNull(requestCloseChannel, "requestCloseChannel");
    Objects.requireNonNull(supportsBouncerDetach, "supportsBouncerDetach");
    Objects.requireNonNull(requestBouncerDetachChannel, "requestBouncerDetachChannel");
    Objects.requireNonNull(isChannelAutoReattach, "isChannelAutoReattach");
    Objects.requireNonNull(setChannelAutoReattach, "setChannelAutoReattach");
    Objects.requireNonNull(isChannelPinned, "isChannelPinned");
    Objects.requireNonNull(setChannelPinned, "setChannelPinned");
    Objects.requireNonNull(isChannelMuted, "isChannelMuted");
    Objects.requireNonNull(setChannelMuted, "setChannelMuted");
    Objects.requireNonNull(openChannelModeDetails, "openChannelModeDetails");
    Objects.requireNonNull(requestChannelModeRefresh, "requestChannelModeRefresh");
    Objects.requireNonNull(canEditChannelModes, "canEditChannelModes");
    Objects.requireNonNull(promptAndRequestChannelModeSet, "promptAndRequestChannelModeSet");
    Objects.requireNonNull(requestCloseTarget, "requestCloseTarget");
    Objects.requireNonNull(interceptorDefinition, "interceptorDefinition");
    Objects.requireNonNull(interceptorStoreAvailable, "interceptorStoreAvailable");
    Objects.requireNonNull(setInterceptorEnabled, "setInterceptorEnabled");
    Objects.requireNonNull(promptRenameInterceptor, "promptRenameInterceptor");
    Objects.requireNonNull(confirmDeleteInterceptor, "confirmDeleteInterceptor");
    return new Context() {
      @Override
      public void openPinnedChat(TargetRef ref) {
        openPinnedChat.accept(ref);
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
      public boolean isChannelPinned(TargetRef target) {
        return isChannelPinned.test(target);
      }

      @Override
      public void setChannelPinned(TargetRef target, boolean pinned) {
        setChannelPinned.accept(target, pinned);
      }

      @Override
      public boolean isChannelMuted(TargetRef target) {
        return isChannelMuted.test(target);
      }

      @Override
      public void setChannelMuted(TargetRef target, boolean muted) {
        setChannelMuted.accept(target, muted);
      }

      @Override
      public void openChannelModeDetails(TargetRef target) {
        openChannelModeDetails.accept(target);
      }

      @Override
      public void requestChannelModeRefresh(TargetRef target) {
        requestChannelModeRefresh.accept(target);
      }

      @Override
      public boolean canEditChannelModes(TargetRef target) {
        return canEditChannelModes.test(target);
      }

      @Override
      public void promptAndRequestChannelModeSet(TargetRef target, String channelLabel) {
        promptAndRequestChannelModeSet.accept(target, channelLabel);
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
      public boolean interceptorStoreAvailable() {
        return interceptorStoreAvailable.get();
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
    };
  }

  private final Context context;

  public ServerTreeTargetNodeMenuBuilder(Context context) {
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

    JMenuItem channelDetails = new JMenuItem("Channel Details...");
    channelDetails.addActionListener(ev -> context.openChannelModeDetails(ref));
    menu.add(channelDetails);

    JMenu channelModes = new JMenu("Channel Modes");

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
