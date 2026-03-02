package cafe.woden.ircclient.ui.servertree.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;

class ServerTreeServerActionOverlayTest {

  private static final int BUTTON_SIZE = 16;
  private static final int BUTTON_ICON_SIZE = 12;
  private static final int BUTTON_MARGIN = 6;
  private static final int CHANNEL_BUTTON_GAP = 4;

  @Test
  void channelToggleClickDisconnectsWhenAttachedAndReconnectsWhenDetached() throws Exception {
    onEdt(
        () -> {
          Fixture fixture = new Fixture();
          ServerTreeServerActionOverlay overlay = fixture.overlay();

          Point toggleCenter = fixture.channelToggleCenterPoint();
          MouseEvent firstClick = mousePress(fixture.tree, toggleCenter);
          boolean firstHandled = overlay.maybeHandleActionClick(firstClick);

          assertTrue(firstHandled);
          assertEquals(1, fixture.context.disconnectChannelCalls);
          assertEquals(0, fixture.context.joinChannelCalls);

          fixture.context.channelDisconnected = true;
          MouseEvent secondClick = mousePress(fixture.tree, toggleCenter);
          boolean secondHandled = overlay.maybeHandleActionClick(secondClick);

          assertTrue(secondHandled);
          assertEquals(1, fixture.context.disconnectChannelCalls);
          assertEquals(1, fixture.context.joinChannelCalls);
        });
  }

  @Test
  void channelCloseClickHonorsConfirmationResult() throws Exception {
    onEdt(
        () -> {
          Fixture fixture = new Fixture();
          ServerTreeServerActionOverlay overlay = fixture.overlay();
          Point closeCenter = fixture.channelCloseCenterPoint();

          fixture.context.confirmCloseResult = false;
          boolean firstHandled =
              overlay.maybeHandleActionClick(mousePress(fixture.tree, closeCenter));
          assertTrue(firstHandled);
          assertEquals(1, fixture.context.confirmCloseCalls);
          assertEquals(0, fixture.context.closeChannelCalls);

          fixture.context.confirmCloseResult = true;
          boolean secondHandled =
              overlay.maybeHandleActionClick(mousePress(fixture.tree, closeCenter));
          assertTrue(secondHandled);
          assertEquals(2, fixture.context.confirmCloseCalls);
          assertEquals(1, fixture.context.closeChannelCalls);
        });
  }

  @Test
  void channelButtonsExposeTooltips() throws Exception {
    onEdt(
        () -> {
          Fixture fixture = new Fixture();
          ServerTreeServerActionOverlay overlay = fixture.overlay();

          String disconnectTip =
              overlay.toolTipForEvent(mouseMove(fixture.tree, fixture.channelToggleCenterPoint()));
          String closeTip =
              overlay.toolTipForEvent(mouseMove(fixture.tree, fixture.channelCloseCenterPoint()));

          assertEquals("Disconnect \"#ircafe\"", disconnectTip);
          assertEquals("Close and PART \"#ircafe\"", closeTip);

          fixture.context.channelDisconnected = true;
          String reconnectTip =
              overlay.toolTipForEvent(mouseMove(fixture.tree, fixture.channelToggleCenterPoint()));
          assertEquals("Reconnect \"#ircafe\"", reconnectTip);
        });
  }

  private static MouseEvent mousePress(JTree tree, Point point) {
    return new MouseEvent(
        tree,
        MouseEvent.MOUSE_PRESSED,
        System.currentTimeMillis(),
        0,
        point.x,
        point.y,
        1,
        false,
        MouseEvent.BUTTON1);
  }

  private static MouseEvent mouseMove(JTree tree, Point point) {
    return new MouseEvent(
        tree,
        MouseEvent.MOUSE_MOVED,
        System.currentTimeMillis(),
        0,
        point.x,
        point.y,
        0,
        false,
        MouseEvent.NOBUTTON);
  }

  private static void onEdt(Runnable task) throws InvocationTargetException, InterruptedException {
    if (SwingUtilities.isEventDispatchThread()) {
      task.run();
      return;
    }
    SwingUtilities.invokeAndWait(task);
  }

  private static final class Fixture {
    private final JTree tree;
    private final DefaultMutableTreeNode serverNode;
    private final DefaultMutableTreeNode channelNode;
    private final TestContext context;

    private Fixture() {
      DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
      this.serverNode = new DefaultMutableTreeNode("libera");
      this.channelNode =
          new DefaultMutableTreeNode(
              new ServerTreeNodeData(new TargetRef("libera", "#ircafe"), "#ircafe"));
      serverNode.add(channelNode);
      root.add(serverNode);

      DefaultTreeModel model = new DefaultTreeModel(root);
      this.tree = new JTree(model);
      this.tree.setRootVisible(false);
      this.tree.setShowsRootHandles(true);
      this.tree.expandPath(new TreePath(serverNode.getPath()));
      this.tree.setBounds(0, 0, 360, 240);
      this.tree.setSize(360, 240);
      this.tree.doLayout();
      this.tree.setSelectionPath(new TreePath(channelNode.getPath()));

      this.context = new TestContext(serverNode, channelNode);
    }

    private ServerTreeServerActionOverlay overlay() {
      return new ServerTreeServerActionOverlay(
          tree, BUTTON_SIZE, BUTTON_ICON_SIZE, BUTTON_MARGIN, context);
    }

    private Point channelToggleCenterPoint() {
      Rectangle row = tree.getPathBounds(new TreePath(channelNode.getPath()));
      assertNotNull(row);
      Rectangle visible = tree.getVisibleRect();
      int right = visible.x + visible.width - BUTTON_MARGIN;
      int x =
          right - BUTTON_SIZE - (BUTTON_SIZE + CHANNEL_BUTTON_GAP) + Math.max(1, BUTTON_SIZE / 2);
      int y = row.y + Math.max(1, row.height / 2);
      return new Point(x, y);
    }

    private Point channelCloseCenterPoint() {
      Rectangle row = tree.getPathBounds(new TreePath(channelNode.getPath()));
      assertNotNull(row);
      Rectangle visible = tree.getVisibleRect();
      int right = visible.x + visible.width - BUTTON_MARGIN;
      int x = right - BUTTON_SIZE + Math.max(1, BUTTON_SIZE / 2);
      int y = row.y + Math.max(1, row.height / 2);
      return new Point(x, y);
    }
  }

  private static final class TestContext implements ServerTreeServerActionOverlay.Context {
    private final DefaultMutableTreeNode serverNode;
    private final DefaultMutableTreeNode channelNode;
    private final TargetRef channelRef;

    private ConnectionState connectionState = ConnectionState.CONNECTED;
    private boolean channelDisconnected = false;
    private boolean confirmCloseResult = true;

    private int joinChannelCalls = 0;
    private int disconnectChannelCalls = 0;
    private int confirmCloseCalls = 0;
    private int closeChannelCalls = 0;

    private TestContext(DefaultMutableTreeNode serverNode, DefaultMutableTreeNode channelNode) {
      this.serverNode = serverNode;
      this.channelNode = channelNode;
      this.channelRef = ((ServerTreeNodeData) channelNode.getUserObject()).ref;
    }

    @Override
    public boolean isServerNode(DefaultMutableTreeNode node) {
      return node == serverNode;
    }

    @Override
    public boolean isChannelNode(DefaultMutableTreeNode node) {
      return node == channelNode;
    }

    @Override
    public TreePath serverPathForId(String serverId) {
      return "libera".equals(serverId) ? new TreePath(serverNode.getPath()) : null;
    }

    @Override
    public TreePath channelPathForRef(TargetRef channelRef) {
      return this.channelRef.equals(channelRef) ? new TreePath(channelNode.getPath()) : null;
    }

    @Override
    public ConnectionState connectionStateForServer(String serverId) {
      return connectionState;
    }

    @Override
    public void connectServer(String serverId) {
      connectionState = ConnectionState.CONNECTED;
    }

    @Override
    public void disconnectServer(String serverId) {
      connectionState = ConnectionState.DISCONNECTED;
    }

    @Override
    public boolean isChannelDisconnected(TargetRef channelRef) {
      return channelDisconnected;
    }

    @Override
    public void joinChannel(TargetRef channelRef) {
      joinChannelCalls++;
      channelDisconnected = false;
    }

    @Override
    public void disconnectChannel(TargetRef channelRef) {
      disconnectChannelCalls++;
      channelDisconnected = true;
    }

    @Override
    public boolean confirmCloseChannel(TargetRef channelRef, String channelLabel) {
      confirmCloseCalls++;
      return confirmCloseResult;
    }

    @Override
    public void closeChannel(TargetRef channelRef) {
      closeChannelCalls++;
    }
  }
}
