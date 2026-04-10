package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.icons.SvgIcons.Palette;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeQuasselNetworkNodeData;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeTypingTargetPolicy;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import java.awt.Color;
import java.awt.Font;
import java.util.Enumeration;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import org.springframework.stereotype.Component;

/** Stateless presentation policy for server-tree cell labels, icons, badges, and typing state. */
@Component
public final class ServerTreeCellPresentationPolicy {

  private static final int TYPING_ACTIVITY_FADE_MS = 900;
  private static final int TYPING_ACTIVITY_PULSE_MS = 1200;

  public interface Context {
    boolean serverTreeNotificationBadgesEnabled();

    int unreadBadgeScalePercent();

    ServerTreeTypingIndicatorStyle typingIndicatorStyle();

    boolean typingIndicatorsTreeEnabled();

    boolean isPrivateMessageTarget(TargetRef ref);

    boolean isPrivateMessageOnline(TargetRef ref);

    boolean isChannelPinned(TargetRef ref);

    boolean isChannelMuted(TargetRef ref);

    Color unreadChannelTextColor();

    Color highlightChannelTextColor();

    boolean isApplicationJfrActive();

    boolean isInterceptorEnabled(TargetRef ref);

    boolean isMonitorGroupNode(DefaultMutableTreeNode node);

    boolean isInterceptorsGroupNode(DefaultMutableTreeNode node);

    boolean isOtherGroupNode(DefaultMutableTreeNode node);

    boolean isServerNode(DefaultMutableTreeNode node);

    String serverNodeDisplayLabel(String serverId);

    boolean isEphemeralServer(String serverId);

    ConnectionState connectionStateForServer(String serverId);

    boolean isIrcRootNode(DefaultMutableTreeNode node);

    boolean isApplicationRootNode(DefaultMutableTreeNode node);

    boolean isPrivateMessagesGroupNode(DefaultMutableTreeNode node);

    String backendIdForNetworksGroupNode(DefaultMutableTreeNode node);

    boolean isQuasselNetworkNode(DefaultMutableTreeNode node);

    boolean isQuasselEmptyStateNode(DefaultMutableTreeNode node);
  }

  public record IconSpec(String name, Palette palette, Palette disabledPalette) {
    public IconSpec {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(palette, "palette");
      Objects.requireNonNull(disabledPalette, "disabledPalette");
    }

    public static IconSpec tree(String name) {
      return new IconSpec(name, Palette.TREE, Palette.TREE_DISABLED);
    }
  }

  public record Presentation(
      String text,
      int fontStyle,
      IconSpec iconSpec,
      Color foreground,
      int unreadBadgeCount,
      int highlightBadgeCount,
      boolean typingIndicatorSlotVisible,
      boolean detachedWarningIndicatorVisible,
      float typingIndicatorAlpha) {

    public static Presentation plain() {
      return new Presentation(null, Font.PLAIN, null, null, 0, 0, false, false, 0f);
    }
  }

  public static Context context(
      Supplier<Boolean> serverTreeNotificationBadgesEnabled,
      IntSupplier unreadBadgeScalePercent,
      Supplier<ServerTreeTypingIndicatorStyle> typingIndicatorStyle,
      Supplier<Boolean> typingIndicatorsTreeEnabled,
      Predicate<TargetRef> isPrivateMessageTarget,
      Predicate<TargetRef> isPrivateMessageOnline,
      Predicate<TargetRef> isChannelPinned,
      Predicate<TargetRef> isChannelMuted,
      Supplier<Color> unreadChannelTextColor,
      Supplier<Color> highlightChannelTextColor,
      Supplier<Boolean> isApplicationJfrActive,
      Predicate<TargetRef> isInterceptorEnabled,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Predicate<DefaultMutableTreeNode> isOtherGroupNode,
      Predicate<DefaultMutableTreeNode> isServerNode,
      Function<String, String> serverNodeDisplayLabel,
      Predicate<String> isEphemeralServer,
      Function<String, ConnectionState> connectionStateForServer,
      Predicate<DefaultMutableTreeNode> isIrcRootNode,
      Predicate<DefaultMutableTreeNode> isApplicationRootNode,
      Predicate<DefaultMutableTreeNode> isPrivateMessagesGroupNode,
      Function<DefaultMutableTreeNode, String> backendIdForNetworksGroupNode,
      Predicate<DefaultMutableTreeNode> isQuasselNetworkNode,
      Predicate<DefaultMutableTreeNode> isQuasselEmptyStateNode) {
    Objects.requireNonNull(
        serverTreeNotificationBadgesEnabled, "serverTreeNotificationBadgesEnabled");
    Objects.requireNonNull(unreadBadgeScalePercent, "unreadBadgeScalePercent");
    Objects.requireNonNull(typingIndicatorStyle, "typingIndicatorStyle");
    Objects.requireNonNull(typingIndicatorsTreeEnabled, "typingIndicatorsTreeEnabled");
    Objects.requireNonNull(isPrivateMessageTarget, "isPrivateMessageTarget");
    Objects.requireNonNull(isPrivateMessageOnline, "isPrivateMessageOnline");
    Objects.requireNonNull(isChannelPinned, "isChannelPinned");
    Objects.requireNonNull(isChannelMuted, "isChannelMuted");
    Objects.requireNonNull(unreadChannelTextColor, "unreadChannelTextColor");
    Objects.requireNonNull(highlightChannelTextColor, "highlightChannelTextColor");
    Objects.requireNonNull(isApplicationJfrActive, "isApplicationJfrActive");
    Objects.requireNonNull(isInterceptorEnabled, "isInterceptorEnabled");
    Objects.requireNonNull(isMonitorGroupNode, "isMonitorGroupNode");
    Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    Objects.requireNonNull(isOtherGroupNode, "isOtherGroupNode");
    Objects.requireNonNull(isServerNode, "isServerNode");
    Objects.requireNonNull(serverNodeDisplayLabel, "serverNodeDisplayLabel");
    Objects.requireNonNull(isEphemeralServer, "isEphemeralServer");
    Objects.requireNonNull(connectionStateForServer, "connectionStateForServer");
    Objects.requireNonNull(isIrcRootNode, "isIrcRootNode");
    Objects.requireNonNull(isApplicationRootNode, "isApplicationRootNode");
    Objects.requireNonNull(isPrivateMessagesGroupNode, "isPrivateMessagesGroupNode");
    Objects.requireNonNull(backendIdForNetworksGroupNode, "backendIdForNetworksGroupNode");
    Objects.requireNonNull(isQuasselNetworkNode, "isQuasselNetworkNode");
    Objects.requireNonNull(isQuasselEmptyStateNode, "isQuasselEmptyStateNode");
    return new Context() {
      @Override
      public boolean serverTreeNotificationBadgesEnabled() {
        return serverTreeNotificationBadgesEnabled.get();
      }

      @Override
      public int unreadBadgeScalePercent() {
        return unreadBadgeScalePercent.getAsInt();
      }

      @Override
      public ServerTreeTypingIndicatorStyle typingIndicatorStyle() {
        return typingIndicatorStyle.get();
      }

      @Override
      public boolean typingIndicatorsTreeEnabled() {
        return typingIndicatorsTreeEnabled.get();
      }

      @Override
      public boolean isPrivateMessageTarget(TargetRef ref) {
        return isPrivateMessageTarget.test(ref);
      }

      @Override
      public boolean isPrivateMessageOnline(TargetRef ref) {
        return isPrivateMessageOnline.test(ref);
      }

      @Override
      public boolean isChannelPinned(TargetRef ref) {
        return isChannelPinned.test(ref);
      }

      @Override
      public boolean isChannelMuted(TargetRef ref) {
        return isChannelMuted.test(ref);
      }

      @Override
      public Color unreadChannelTextColor() {
        return unreadChannelTextColor.get();
      }

      @Override
      public Color highlightChannelTextColor() {
        return highlightChannelTextColor.get();
      }

      @Override
      public boolean isApplicationJfrActive() {
        return isApplicationJfrActive.get();
      }

      @Override
      public boolean isInterceptorEnabled(TargetRef ref) {
        return isInterceptorEnabled.test(ref);
      }

      @Override
      public boolean isMonitorGroupNode(DefaultMutableTreeNode node) {
        return isMonitorGroupNode.test(node);
      }

      @Override
      public boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
        return isInterceptorsGroupNode.test(node);
      }

      @Override
      public boolean isOtherGroupNode(DefaultMutableTreeNode node) {
        return isOtherGroupNode.test(node);
      }

      @Override
      public boolean isServerNode(DefaultMutableTreeNode node) {
        return isServerNode.test(node);
      }

      @Override
      public String serverNodeDisplayLabel(String serverId) {
        return serverNodeDisplayLabel.apply(serverId);
      }

      @Override
      public boolean isEphemeralServer(String serverId) {
        return isEphemeralServer.test(serverId);
      }

      @Override
      public ConnectionState connectionStateForServer(String serverId) {
        return connectionStateForServer.apply(serverId);
      }

      @Override
      public boolean isIrcRootNode(DefaultMutableTreeNode node) {
        return isIrcRootNode.test(node);
      }

      @Override
      public boolean isApplicationRootNode(DefaultMutableTreeNode node) {
        return isApplicationRootNode.test(node);
      }

      @Override
      public boolean isPrivateMessagesGroupNode(DefaultMutableTreeNode node) {
        return isPrivateMessagesGroupNode.test(node);
      }

      @Override
      public String backendIdForNetworksGroupNode(DefaultMutableTreeNode node) {
        return backendIdForNetworksGroupNode.apply(node);
      }

      @Override
      public boolean isQuasselNetworkNode(DefaultMutableTreeNode node) {
        return isQuasselNetworkNode.test(node);
      }

      @Override
      public boolean isQuasselEmptyStateNode(DefaultMutableTreeNode node) {
        return isQuasselEmptyStateNode.test(node);
      }
    };
  }

  public Presentation presentationForNode(
      Context context,
      DefaultMutableTreeNode node,
      String ircRootLabel,
      String applicationRootLabel,
      boolean selected) {
    Objects.requireNonNull(context, "context");
    if (node == null) {
      return Presentation.plain();
    }

    Object userObject = node.getUserObject();
    if (userObject instanceof ServerTreeNodeData nodeData) {
      return presentationForNodeData(context, node, nodeData, selected);
    }
    if (userObject instanceof ServerTreeQuasselNetworkNodeData networkNodeData
        && (context.isQuasselNetworkNode(node) || context.isQuasselEmptyStateNode(node))) {
      return presentationForQuasselNetwork(context, node, networkNodeData);
    }
    if (userObject instanceof String serverId && context.isServerNode(node)) {
      return presentationForServerNode(context, serverId);
    }
    if (context.isIrcRootNode(node)) {
      return new Presentation(
          ircRootLabel, Font.PLAIN, IconSpec.tree("chat"), null, 0, 0, false, false, 0f);
    }
    if (context.isApplicationRootNode(node)) {
      return new Presentation(
          applicationRootLabel,
          Font.PLAIN,
          IconSpec.tree("settings"),
          null,
          0,
          0,
          false,
          false,
          0f);
    }
    if (context.isPrivateMessagesGroupNode(node)) {
      return new Presentation(
          null, Font.PLAIN, IconSpec.tree("account-unknown"), null, 0, 0, false, false, 0f);
    }
    if (context.isMonitorGroupNode(node)) {
      return new Presentation(null, Font.PLAIN, IconSpec.tree("eye"), null, 0, 0, false, false, 0f);
    }
    if (context.isInterceptorsGroupNode(node)) {
      return new Presentation(
          null, Font.PLAIN, IconSpec.tree("yin-yang"), null, 0, 0, false, false, 0f);
    }
    if (context.isOtherGroupNode(node)) {
      return new Presentation(
          null, Font.PLAIN, IconSpec.tree("settings"), null, 0, 0, false, false, 0f);
    }
    if (isNetworksGroupNode(context, node)) {
      return new Presentation(
          null, Font.PLAIN, IconSpec.tree("dock-left"), null, 0, 0, false, false, 0f);
    }
    return Presentation.plain();
  }

  private Presentation presentationForNodeData(
      Context context, DefaultMutableTreeNode node, ServerTreeNodeData nodeData, boolean selected) {
    boolean detachedChannel = nodeData.ref != null && nodeData.ref.isChannel() && nodeData.detached;
    int fontStyle = nodeData.highlightUnread > 0 ? Font.BOLD : Font.PLAIN;
    int unreadBadgeCount = 0;
    int highlightBadgeCount = 0;
    if (context.serverTreeNotificationBadgesEnabled()) {
      unreadBadgeCount = Math.max(0, nodeData.unread);
      highlightBadgeCount = Math.max(0, nodeData.highlightUnread);
    }
    if (detachedChannel) {
      fontStyle |= Font.ITALIC;
    }

    IconSpec iconSpec = null;
    Color foreground = null;
    if (nodeData.ref != null && nodeData.ref.isChannel()) {
      boolean mutedChannel = context.isChannelMuted(nodeData.ref);
      iconSpec = mutedChannel ? IconSpec.tree("pause") : channelIcon(context, nodeData.ref);
      if (!selected) {
        foreground =
            nodeData.highlightUnread > 0
                ? context.highlightChannelTextColor()
                : (nodeData.unread > 0 ? context.unreadChannelTextColor() : null);
      }
    } else if (context.isPrivateMessageTarget(nodeData.ref)) {
      boolean online = context.isPrivateMessageOnline(nodeData.ref);
      iconSpec =
          new IconSpec(
              online ? "pm-online" : "pm-offline",
              online ? Palette.TREE_PM_ONLINE : Palette.TREE_PM_OFFLINE,
              online ? Palette.TREE_PM_ONLINE : Palette.TREE_PM_OFFLINE);
    } else if (nodeData.ref != null && nodeData.ref.isApplicationUnhandledErrors()) {
      iconSpec = IconSpec.tree("info");
    } else if (nodeData.ref != null && nodeData.ref.isApplicationAssertjSwing()) {
      iconSpec = IconSpec.tree("settings");
    } else if (nodeData.ref != null && nodeData.ref.isApplicationJhiccup()) {
      iconSpec = IconSpec.tree("refresh");
    } else if (nodeData.ref != null && nodeData.ref.isApplicationInboundDedup()) {
      iconSpec = IconSpec.tree("copy");
    } else if (nodeData.ref != null && nodeData.ref.isApplicationPlugins()) {
      iconSpec = IconSpec.tree("info");
    } else if (nodeData.ref != null && nodeData.ref.isApplicationJfr()) {
      boolean active = context.isApplicationJfrActive();
      Palette palette = active ? Palette.TREE_PM_ONLINE : Palette.TREE_DISABLED;
      iconSpec = new IconSpec(active ? "play" : "pause", palette, palette);
    } else if (nodeData.ref != null && nodeData.ref.isApplicationSpring()) {
      iconSpec = IconSpec.tree("theme");
    } else if (nodeData.ref != null && nodeData.ref.isApplicationTerminal()) {
      iconSpec = IconSpec.tree("terminal");
    } else if (nodeData.ref != null && nodeData.ref.isStatus()) {
      iconSpec = IconSpec.tree("dock-left");
    } else if (nodeData.ref != null && nodeData.ref.isNotifications()) {
      iconSpec = IconSpec.tree("info");
    } else if (nodeData.ref != null && nodeData.ref.isLogViewer()) {
      iconSpec = IconSpec.tree("copy");
    } else if (nodeData.ref != null && nodeData.ref.isInterceptor()) {
      iconSpec =
          IconSpec.tree(context.isInterceptorEnabled(nodeData.ref) ? "interceptor" : "pause");
    } else if (nodeData.ref != null && nodeData.ref.isChannelList()) {
      iconSpec = IconSpec.tree("add");
    } else if (nodeData.ref != null && nodeData.ref.isWeechatFilters()) {
      iconSpec = IconSpec.tree("settings");
    } else if (nodeData.ref != null && nodeData.ref.isIgnores()) {
      iconSpec = IconSpec.tree("ban");
    } else if (nodeData.ref != null && nodeData.ref.isDccTransfers()) {
      iconSpec = IconSpec.tree("dock-right");
    } else if (nodeData.ref == null && context.isMonitorGroupNode(node)) {
      iconSpec = IconSpec.tree("eye");
    } else if (nodeData.ref == null && context.isInterceptorsGroupNode(node)) {
      iconSpec = IconSpec.tree("yin-yang");
    } else if (nodeData.ref == null && context.isOtherGroupNode(node)) {
      iconSpec = IconSpec.tree("settings");
    }

    if (!selected && detachedChannel) {
      foreground = disabledForegroundColor();
    }

    boolean detachedWarningIndicatorVisible = false;
    boolean typingIndicatorSlotVisible = false;
    float typingIndicatorAlpha = 0f;
    if (ServerTreeTypingTargetPolicy.supportsTypingActivity(nodeData.ref)) {
      detachedWarningIndicatorVisible = nodeData.hasDetachedWarning();
      typingIndicatorSlotVisible =
          detachedWarningIndicatorVisible || context.typingIndicatorsTreeEnabled();
      if (context.typingIndicatorsTreeEnabled() && !detachedWarningIndicatorVisible) {
        typingIndicatorAlpha =
            nodeData.typingDotAlpha(
                System.currentTimeMillis(), TYPING_ACTIVITY_PULSE_MS, TYPING_ACTIVITY_FADE_MS);
      }
    }

    return new Presentation(
        nodeData.label,
        fontStyle,
        iconSpec,
        foreground,
        unreadBadgeCount,
        highlightBadgeCount,
        typingIndicatorSlotVisible,
        detachedWarningIndicatorVisible,
        typingIndicatorAlpha);
  }

  private Presentation presentationForQuasselNetwork(
      Context context,
      DefaultMutableTreeNode node,
      ServerTreeQuasselNetworkNodeData networkNodeData) {
    int unreadBadgeCount = 0;
    int highlightBadgeCount = 0;
    if (context.serverTreeNotificationBadgesEnabled() && !networkNodeData.emptyState()) {
      int[] rollup = rollupUnreadCounts(node);
      unreadBadgeCount = rollup[0];
      highlightBadgeCount = rollup[1];
    }

    int fontStyle = (highlightBadgeCount > 0 || unreadBadgeCount > 0) ? Font.BOLD : Font.PLAIN;
    if (networkNodeData.emptyState() || Boolean.FALSE.equals(networkNodeData.enabled())) {
      fontStyle |= Font.ITALIC;
    }

    String iconName;
    Palette palette;
    if (networkNodeData.emptyState()) {
      iconName = "dock-left";
      palette = Palette.TREE_DISABLED;
    } else if (Boolean.FALSE.equals(networkNodeData.enabled())) {
      iconName = "pause";
      palette = Palette.TREE_DISABLED;
    } else {
      ConnectionState state =
          networkNodeData.connected() == null
              ? context.connectionStateForServer(networkNodeData.serverId())
              : (Boolean.TRUE.equals(networkNodeData.connected())
                  ? ConnectionState.CONNECTED
                  : ConnectionState.DISCONNECTED);
      iconName = ServerTreeConnectionStateViewModel.serverNodeIconName(state);
      palette = ServerTreeConnectionStateViewModel.serverNodeIconPalette(state);
    }

    return new Presentation(
        networkNodeData.label(),
        fontStyle,
        new IconSpec(iconName, palette, Palette.TREE_DISABLED),
        null,
        unreadBadgeCount,
        highlightBadgeCount,
        false,
        false,
        0f);
  }

  private Presentation presentationForServerNode(Context context, String serverId) {
    ConnectionState state = context.connectionStateForServer(serverId);
    String iconName = ServerTreeConnectionStateViewModel.serverNodeIconName(state);
    Palette palette = ServerTreeConnectionStateViewModel.serverNodeIconPalette(state);
    return new Presentation(
        context.serverNodeDisplayLabel(serverId),
        context.isEphemeralServer(serverId) ? Font.ITALIC : Font.PLAIN,
        new IconSpec(iconName, palette, Palette.TREE_DISABLED),
        null,
        0,
        0,
        false,
        false,
        0f);
  }

  private static IconSpec channelIcon(Context context, TargetRef ref) {
    return IconSpec.tree(context.isChannelPinned(ref) ? "star" : "channel");
  }

  private static boolean isNetworksGroupNode(Context context, DefaultMutableTreeNode node) {
    String backendId = context.backendIdForNetworksGroupNode(node);
    return backendId != null && !backendId.isBlank();
  }

  private static int[] rollupUnreadCounts(DefaultMutableTreeNode rootNode) {
    if (rootNode == null) return new int[] {0, 0};
    int unread = 0;
    int highlight = 0;
    Enumeration<?> walk = rootNode.depthFirstEnumeration();
    while (walk.hasMoreElements()) {
      Object next = walk.nextElement();
      if (!(next instanceof DefaultMutableTreeNode node) || node == rootNode) continue;
      Object userObject = node.getUserObject();
      if (!(userObject instanceof ServerTreeNodeData data)) continue;
      unread += Math.max(0, data.unread);
      highlight += Math.max(0, data.highlightUnread);
    }
    return new int[] {unread, highlight};
  }

  private static Color disabledForegroundColor() {
    Color muted = UIManager.getColor("Label.disabledForeground");
    return muted != null ? muted : UIManager.getColor("Component.disabledForeground");
  }
}
