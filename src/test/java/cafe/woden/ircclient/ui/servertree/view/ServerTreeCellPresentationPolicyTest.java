package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.icons.SvgIcons.Palette;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeQuasselNetworkNodeData;
import java.awt.Color;
import java.awt.Font;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeCellPresentationPolicyTest {

  private final ServerTreeCellPresentationPolicy policy = new ServerTreeCellPresentationPolicy();

  @Test
  void channelNodePresentationUsesPinnedHighlightBadgesAndTypingState() {
    TargetRef channel = new TargetRef("libera", "#ircafe");
    ServerTreeNodeData nodeData = new ServerTreeNodeData(channel, "#ircafe");
    nodeData.unread = 4;
    nodeData.highlightUnread = 2;
    nodeData.applyTypingState("active", System.currentTimeMillis(), 5_000);
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(nodeData);

    TestContext context = new TestContext();
    context.typingIndicatorsTreeEnabled = true;
    context.isChannelPinned = ref -> channel.equals(ref);

    ServerTreeCellPresentationPolicy.Presentation presentation =
        policy.presentationForNode(context, node, "IRC", "Application", false);

    assertEquals("#ircafe", presentation.text());
    assertEquals(Font.BOLD, presentation.fontStyle());
    assertNotNull(presentation.iconSpec());
    assertEquals("star", presentation.iconSpec().name());
    assertEquals(Palette.TREE, presentation.iconSpec().palette());
    assertEquals(Palette.TREE_DISABLED, presentation.iconSpec().disabledPalette());
    assertEquals(context.highlightChannelTextColor(), presentation.foreground());
    assertEquals(4, presentation.unreadBadgeCount());
    assertEquals(2, presentation.highlightBadgeCount());
    assertTrue(presentation.typingIndicatorSlotVisible());
    assertTrue(presentation.typingIndicatorAlpha() > 0f);
  }

  @Test
  void quasselNetworkPresentationRollsUpUnreadAndUsesDisabledIconState() {
    DefaultMutableTreeNode node =
        new DefaultMutableTreeNode(
            ServerTreeQuasselNetworkNodeData.network("core", "work", "Work", null, false));
    ServerTreeNodeData childOne = new ServerTreeNodeData(new TargetRef("core", "#one"), "#one");
    childOne.unread = 2;
    childOne.highlightUnread = 1;
    ServerTreeNodeData childTwo = new ServerTreeNodeData(new TargetRef("core", "#two"), "#two");
    childTwo.unread = 3;
    node.add(new DefaultMutableTreeNode(childOne));
    node.add(new DefaultMutableTreeNode(childTwo));

    TestContext context = new TestContext();
    context.isQuasselNetworkNode = treeNode -> treeNode == node;

    ServerTreeCellPresentationPolicy.Presentation presentation =
        policy.presentationForNode(context, node, "IRC", "Application", false);

    assertEquals("Work", presentation.text());
    assertEquals(Font.BOLD | Font.ITALIC, presentation.fontStyle());
    assertNotNull(presentation.iconSpec());
    assertEquals("pause", presentation.iconSpec().name());
    assertEquals(Palette.TREE_DISABLED, presentation.iconSpec().palette());
    assertEquals(5, presentation.unreadBadgeCount());
    assertEquals(1, presentation.highlightBadgeCount());
  }

  @Test
  void serverNodePresentationUsesDisplayLabelAndConnectionStateIcon() {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode("temp-server");

    TestContext context = new TestContext();
    context.isServerNode = treeNode -> treeNode == node;
    context.serverNodeDisplayLabel = serverId -> "Server " + serverId;
    context.isEphemeralServer = "temp-server"::equals;
    context.connectionStateForServer = serverId -> ConnectionState.CONNECTING;

    ServerTreeCellPresentationPolicy.Presentation presentation =
        policy.presentationForNode(context, node, "IRC", "Application", false);

    assertEquals("Server temp-server", presentation.text());
    assertEquals(Font.ITALIC, presentation.fontStyle());
    assertNotNull(presentation.iconSpec());
    assertEquals("refresh", presentation.iconSpec().name());
    assertEquals(Palette.TREE, presentation.iconSpec().palette());
    assertEquals(Palette.TREE_DISABLED, presentation.iconSpec().disabledPalette());
  }

  private static final class TestContext implements ServerTreeCellPresentationPolicy.Context {
    private boolean serverTreeNotificationBadgesEnabled = true;
    private int unreadBadgeScalePercent = 100;
    private ServerTreeTypingIndicatorStyle typingIndicatorStyle =
        ServerTreeTypingIndicatorStyle.DOTS;
    private boolean typingIndicatorsTreeEnabled = false;
    private Predicate<TargetRef> isPrivateMessageTarget = ref -> false;
    private Predicate<TargetRef> isPrivateMessageOnline = ref -> false;
    private Predicate<TargetRef> isChannelPinned = ref -> false;
    private Predicate<TargetRef> isChannelMuted = ref -> false;
    private Color unreadChannelTextColor = new Color(12, 34, 56);
    private Color highlightChannelTextColor = new Color(78, 90, 12);
    private boolean applicationJfrActive = false;
    private Predicate<TargetRef> isInterceptorEnabled = ref -> true;
    private Predicate<DefaultMutableTreeNode> isMonitorGroupNode = node -> false;
    private Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode = node -> false;
    private Predicate<DefaultMutableTreeNode> isOtherGroupNode = node -> false;
    private Predicate<DefaultMutableTreeNode> isServerNode = node -> false;
    private Function<String, String> serverNodeDisplayLabel = Function.identity();
    private Predicate<String> isEphemeralServer = serverId -> false;
    private Function<String, ConnectionState> connectionStateForServer =
        serverId -> ConnectionState.DISCONNECTED;
    private Predicate<DefaultMutableTreeNode> isIrcRootNode = node -> false;
    private Predicate<DefaultMutableTreeNode> isApplicationRootNode = node -> false;
    private Predicate<DefaultMutableTreeNode> isPrivateMessagesGroupNode = node -> false;
    private Function<DefaultMutableTreeNode, String> backendIdForNetworksGroupNode = node -> "";
    private Predicate<DefaultMutableTreeNode> isQuasselNetworkNode = node -> false;
    private Predicate<DefaultMutableTreeNode> isQuasselEmptyStateNode = node -> false;

    @Override
    public boolean serverTreeNotificationBadgesEnabled() {
      return serverTreeNotificationBadgesEnabled;
    }

    @Override
    public int unreadBadgeScalePercent() {
      return unreadBadgeScalePercent;
    }

    @Override
    public ServerTreeTypingIndicatorStyle typingIndicatorStyle() {
      return typingIndicatorStyle;
    }

    @Override
    public boolean typingIndicatorsTreeEnabled() {
      return typingIndicatorsTreeEnabled;
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
      return unreadChannelTextColor;
    }

    @Override
    public Color highlightChannelTextColor() {
      return highlightChannelTextColor;
    }

    @Override
    public boolean isApplicationJfrActive() {
      return applicationJfrActive;
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
  }
}
