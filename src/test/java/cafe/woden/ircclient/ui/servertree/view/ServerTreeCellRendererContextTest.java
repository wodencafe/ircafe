package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.model.TargetRef;
import java.awt.Color;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ServerTreeCellRendererContextTest {

  @Test
  void contextDelegatesCellRendererLookups() {
    TargetRef ref = new TargetRef("libera", "#ircafe");
    DefaultMutableTreeNode node = new DefaultMutableTreeNode("node");
    Color unreadColor = new Color(1, 2, 3);
    Color highlightColor = new Color(4, 5, 6);

    ServerTreeCellRenderer.Context context =
        ServerTreeCellRenderer.context(
            () -> true,
            () -> 125,
            () -> ServerTreeTypingIndicatorStyle.GLOW_DOT,
            () -> false,
            value -> value == ref,
            value -> value == ref,
            value -> true,
            value -> false,
            () -> unreadColor,
            () -> highlightColor,
            () -> true,
            value -> true,
            value -> value == node,
            value -> false,
            value -> false,
            value -> true,
            serverId -> "Server " + serverId,
            serverId -> "temp".equals(serverId),
            serverId -> ConnectionState.CONNECTED,
            value -> value == node,
            value -> false,
            value -> true,
            value -> false,
            value -> true);

    assertTrue(context.serverTreeNotificationBadgesEnabled());
    assertEquals(125, context.unreadBadgeScalePercent());
    assertEquals(ServerTreeTypingIndicatorStyle.GLOW_DOT, context.typingIndicatorStyle());
    assertFalse(context.typingIndicatorsTreeEnabled());
    assertTrue(context.isPrivateMessageTarget(ref));
    assertTrue(context.isPrivateMessageOnline(ref));
    assertTrue(context.isChannelPinned(ref));
    assertFalse(context.isChannelMuted(ref));
    assertSame(unreadColor, context.unreadChannelTextColor());
    assertSame(highlightColor, context.highlightChannelTextColor());
    assertTrue(context.isApplicationJfrActive());
    assertTrue(context.isInterceptorEnabled(ref));
    assertTrue(context.isMonitorGroupNode(node));
    assertFalse(context.isInterceptorsGroupNode(node));
    assertFalse(context.isOtherGroupNode(node));
    assertTrue(context.isServerNode(node));
    assertEquals("Server libera", context.serverNodeDisplayLabel("libera"));
    assertTrue(context.isEphemeralServer("temp"));
    assertEquals(ConnectionState.CONNECTED, context.connectionStateForServer("libera"));
    assertTrue(context.isIrcRootNode(node));
    assertFalse(context.isApplicationRootNode(node));
    assertTrue(context.isPrivateMessagesGroupNode(node));
    assertFalse(context.isSojuNetworksGroupNode(node));
    assertTrue(context.isZncNetworksGroupNode(node));
  }
}
