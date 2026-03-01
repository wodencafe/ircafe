package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeCellRenderer;
import cafe.woden.ircclient.ui.servertree.view.ServerTreeTypingIndicatorStyle;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeCellRenderer.Context}. */
public final class ServerTreeCellRendererContextAdapter implements ServerTreeCellRenderer.Context {

  private final Supplier<Boolean> serverTreeNotificationBadgesEnabled;
  private final IntSupplier unreadBadgeScalePercent;
  private final Supplier<ServerTreeTypingIndicatorStyle> typingIndicatorStyle;
  private final Supplier<Boolean> typingIndicatorsTreeEnabled;
  private final Predicate<TargetRef> isPrivateMessageTarget;
  private final Predicate<TargetRef> isPrivateMessageOnline;
  private final Supplier<Boolean> isApplicationJfrActive;
  private final Predicate<TargetRef> isInterceptorEnabled;
  private final Predicate<DefaultMutableTreeNode> isMonitorGroupNode;
  private final Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode;
  private final Predicate<DefaultMutableTreeNode> isOtherGroupNode;
  private final Predicate<DefaultMutableTreeNode> isServerNode;
  private final Function<String, String> serverNodeDisplayLabel;
  private final Predicate<String> isEphemeralServer;
  private final Function<String, ConnectionState> connectionStateForServer;
  private final Predicate<DefaultMutableTreeNode> isIrcRootNode;
  private final Predicate<DefaultMutableTreeNode> isApplicationRootNode;
  private final Predicate<DefaultMutableTreeNode> isPrivateMessagesGroupNode;
  private final Predicate<DefaultMutableTreeNode> isSojuNetworksGroupNode;
  private final Predicate<DefaultMutableTreeNode> isZncNetworksGroupNode;

  public ServerTreeCellRendererContextAdapter(
      Supplier<Boolean> serverTreeNotificationBadgesEnabled,
      IntSupplier unreadBadgeScalePercent,
      Supplier<ServerTreeTypingIndicatorStyle> typingIndicatorStyle,
      Supplier<Boolean> typingIndicatorsTreeEnabled,
      Predicate<TargetRef> isPrivateMessageTarget,
      Predicate<TargetRef> isPrivateMessageOnline,
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
      Predicate<DefaultMutableTreeNode> isSojuNetworksGroupNode,
      Predicate<DefaultMutableTreeNode> isZncNetworksGroupNode) {
    this.serverTreeNotificationBadgesEnabled =
        Objects.requireNonNull(
            serverTreeNotificationBadgesEnabled, "serverTreeNotificationBadgesEnabled");
    this.unreadBadgeScalePercent =
        Objects.requireNonNull(unreadBadgeScalePercent, "unreadBadgeScalePercent");
    this.typingIndicatorStyle =
        Objects.requireNonNull(typingIndicatorStyle, "typingIndicatorStyle");
    this.typingIndicatorsTreeEnabled =
        Objects.requireNonNull(typingIndicatorsTreeEnabled, "typingIndicatorsTreeEnabled");
    this.isPrivateMessageTarget =
        Objects.requireNonNull(isPrivateMessageTarget, "isPrivateMessageTarget");
    this.isPrivateMessageOnline =
        Objects.requireNonNull(isPrivateMessageOnline, "isPrivateMessageOnline");
    this.isApplicationJfrActive =
        Objects.requireNonNull(isApplicationJfrActive, "isApplicationJfrActive");
    this.isInterceptorEnabled =
        Objects.requireNonNull(isInterceptorEnabled, "isInterceptorEnabled");
    this.isMonitorGroupNode = Objects.requireNonNull(isMonitorGroupNode, "isMonitorGroupNode");
    this.isInterceptorsGroupNode =
        Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    this.isOtherGroupNode = Objects.requireNonNull(isOtherGroupNode, "isOtherGroupNode");
    this.isServerNode = Objects.requireNonNull(isServerNode, "isServerNode");
    this.serverNodeDisplayLabel =
        Objects.requireNonNull(serverNodeDisplayLabel, "serverNodeDisplayLabel");
    this.isEphemeralServer = Objects.requireNonNull(isEphemeralServer, "isEphemeralServer");
    this.connectionStateForServer =
        Objects.requireNonNull(connectionStateForServer, "connectionStateForServer");
    this.isIrcRootNode = Objects.requireNonNull(isIrcRootNode, "isIrcRootNode");
    this.isApplicationRootNode =
        Objects.requireNonNull(isApplicationRootNode, "isApplicationRootNode");
    this.isPrivateMessagesGroupNode =
        Objects.requireNonNull(isPrivateMessagesGroupNode, "isPrivateMessagesGroupNode");
    this.isSojuNetworksGroupNode =
        Objects.requireNonNull(isSojuNetworksGroupNode, "isSojuNetworksGroupNode");
    this.isZncNetworksGroupNode =
        Objects.requireNonNull(isZncNetworksGroupNode, "isZncNetworksGroupNode");
  }

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
  public boolean isSojuNetworksGroupNode(DefaultMutableTreeNode node) {
    return isSojuNetworksGroupNode.test(node);
  }

  @Override
  public boolean isZncNetworksGroupNode(DefaultMutableTreeNode node) {
    return isZncNetworksGroupNode.test(node);
  }
}
