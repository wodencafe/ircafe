package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.icons.SvgIcons.Palette;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Handles server/channel action button hover/click/paint behavior for the tree. */
public final class ServerTreeServerActionOverlay {

  public interface Context {
    boolean isServerNode(DefaultMutableTreeNode node);

    boolean isChannelNode(DefaultMutableTreeNode node);

    TreePath serverPathForId(String serverId);

    TreePath channelPathForRef(TargetRef channelRef);

    ConnectionState connectionStateForServer(String serverId);

    void connectServer(String serverId);

    void disconnectServer(String serverId);

    boolean isChannelDisconnected(TargetRef channelRef);

    void joinChannel(TargetRef channelRef);

    void disconnectChannel(TargetRef channelRef);

    boolean confirmCloseChannel(TargetRef channelRef, String channelLabel);

    void closeChannel(TargetRef channelRef);
  }

  private enum RowTargetKind {
    NONE,
    SERVER,
    CHANNEL
  }

  private enum ActionKind {
    SERVER_TOGGLE,
    CHANNEL_TOGGLE,
    CHANNEL_CLOSE
  }

  private record RowTarget(RowTargetKind kind, String serverId, TargetRef channelRef) {
    static RowTarget none() {
      return new RowTarget(RowTargetKind.NONE, "", null);
    }

    static RowTarget server(String serverId) {
      String sid = Objects.toString(serverId, "").trim();
      if (sid.isEmpty()) return none();
      return new RowTarget(RowTargetKind.SERVER, sid, null);
    }

    static RowTarget channel(TargetRef channelRef) {
      return channelRef == null ? none() : new RowTarget(RowTargetKind.CHANNEL, "", channelRef);
    }

    boolean isNone() {
      return kind == RowTargetKind.NONE;
    }

    boolean isServer() {
      return kind == RowTargetKind.SERVER && !serverId.isBlank();
    }

    boolean isChannel() {
      return kind == RowTargetKind.CHANNEL && channelRef != null;
    }
  }

  private record ButtonHit(
      ActionKind kind,
      RowTarget target,
      Rectangle buttonBounds,
      Rectangle clusterBounds,
      String label) {}

  private static final int CHANNEL_ACTION_BUTTON_GAP = 4;

  private final JTree tree;
  private final int buttonSize;
  private final int iconSize;
  private final int buttonMargin;
  private final Context context;
  private RowTarget hoveredRowTarget = RowTarget.none();

  public ServerTreeServerActionOverlay(
      JTree tree, int buttonSize, int iconSize, int buttonMargin, Context context) {
    this.tree = Objects.requireNonNull(tree, "tree");
    this.buttonSize = Math.max(1, buttonSize);
    this.iconSize = Math.max(1, iconSize);
    this.buttonMargin = Math.max(0, buttonMargin);
    this.context = Objects.requireNonNull(context, "context");
  }

  public void paint(Graphics graphics) {
    if (graphics == null) return;

    RowTarget selected = selectedRowTarget();
    if (!selected.isNone()) {
      paintRowActions(graphics, selected);
    }

    RowTarget hovered = hoveredRowTarget;
    if (!hovered.isNone() && !Objects.equals(hovered, selected)) {
      paintRowActions(graphics, hovered);
    }
  }

  public void updateHovered(MouseEvent event) {
    RowTarget next = RowTarget.none();
    if (event != null) {
      next = rowTargetAt(event.getX(), event.getY());
      if (next.isNone()) {
        RowTarget current = hoveredRowTarget;
        if (!current.isNone()) {
          Rectangle clusterBounds = buttonClusterBounds(current);
          if (clusterBounds != null && clusterBounds.contains(event.getPoint())) {
            next = current;
          }
        }
      }
    }

    if (Objects.equals(next, hoveredRowTarget)) {
      repaintButtonsForTarget(next);
      return;
    }

    RowTarget previous = hoveredRowTarget;
    hoveredRowTarget = next;
    repaintButtonsForTarget(previous);
    repaintButtonsForTarget(next);
  }

  public boolean maybeHandleActionClick(MouseEvent event) {
    if (event == null || event.isConsumed()) return false;
    if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger()) return false;

    ButtonHit hit = actionHitAtPoint(event, true);
    if (hit == null) return false;

    if (hit.kind() == ActionKind.SERVER_TOGGLE && hit.target().isServer()) {
      ConnectionState state = context.connectionStateForServer(hit.target().serverId());
      if (ServerTreeConnectionStateViewModel.canConnect(state)) {
        context.connectServer(hit.target().serverId());
      } else if (ServerTreeConnectionStateViewModel.canDisconnect(state)) {
        context.disconnectServer(hit.target().serverId());
      }
    } else if (hit.kind() == ActionKind.CHANNEL_TOGGLE && hit.target().isChannel()) {
      TargetRef channelRef = hit.target().channelRef();
      if (context.isChannelDisconnected(channelRef)) {
        context.joinChannel(channelRef);
      } else {
        context.disconnectChannel(channelRef);
      }
    } else if (hit.kind() == ActionKind.CHANNEL_CLOSE && hit.target().isChannel()) {
      TargetRef channelRef = hit.target().channelRef();
      if (context.confirmCloseChannel(channelRef, hit.label())) {
        context.closeChannel(channelRef);
      }
    }

    event.consume();
    if (hit.clusterBounds() != null) {
      tree.repaint(hit.clusterBounds());
    } else {
      tree.repaint();
    }
    return true;
  }

  public String toolTipForEvent(MouseEvent event) {
    ButtonHit hit = actionHitAtPoint(event, true);
    if (hit == null) return null;

    if (hit.kind() == ActionKind.SERVER_TOGGLE && hit.target().isServer()) {
      ConnectionState state = context.connectionStateForServer(hit.target().serverId());
      if (ServerTreeConnectionStateViewModel.canConnect(state)) {
        return "Connect server";
      }
      if (ServerTreeConnectionStateViewModel.canDisconnect(state)) {
        return "Disconnect server";
      }
      return "Connection state is changing";
    }

    if (hit.kind() == ActionKind.CHANNEL_TOGGLE && hit.target().isChannel()) {
      String label = buttonLabel(hit.target().channelRef(), hit.label());
      if (context.isChannelDisconnected(hit.target().channelRef())) {
        return "Reconnect \"" + label + "\"";
      }
      return "Disconnect \"" + label + "\"";
    }

    if (hit.kind() == ActionKind.CHANNEL_CLOSE && hit.target().isChannel()) {
      String label = buttonLabel(hit.target().channelRef(), hit.label());
      return "Close and PART \"" + label + "\"";
    }
    return null;
  }

  public boolean isHoveredServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;
    return hoveredRowTarget.isServer() && Objects.equals(hoveredRowTarget.serverId(), sid);
  }

  public void clearHoveredServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (!sid.isEmpty()
        && hoveredRowTarget.isServer()
        && Objects.equals(hoveredRowTarget.serverId(), sid)) {
      hoveredRowTarget = RowTarget.none();
    }
  }

  private RowTarget selectedRowTarget() {
    TreePath selected = tree.getSelectionPath();
    return rowTargetForPath(selected);
  }

  private RowTarget rowTargetAt(int x, int y) {
    return rowTargetForPath(pathForLocationWithRowFallback(x, y));
  }

  private RowTarget rowTargetForPath(TreePath path) {
    if (path == null) return RowTarget.none();

    Object last = path.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node)) return RowTarget.none();

    if (context.isServerNode(node)) {
      return RowTarget.server(nodeServerId(node));
    }
    if (context.isChannelNode(node)) {
      return RowTarget.channel(channelRefForNode(node));
    }
    return RowTarget.none();
  }

  private TreePath pathForLocationWithRowFallback(int x, int y) {
    TreePath path = tree.getPathForLocation(x, y);
    if (path != null) return path;

    TreePath closest = tree.getClosestPathForLocation(x, y);
    if (closest == null) return null;
    Rectangle row = tree.getPathBounds(closest);
    if (row != null && y >= row.y && y < (row.y + row.height)) {
      return closest;
    }
    return null;
  }

  private Rectangle rowBoundsForPath(TreePath path) {
    if (path == null) return null;

    Rectangle row = tree.getPathBounds(path);
    if (row == null) return null;

    Rectangle visibleRect = tree.getVisibleRect();
    if (visibleRect == null || visibleRect.isEmpty()) return null;
    if (row.y + row.height < visibleRect.y || row.y > visibleRect.y + visibleRect.height) {
      return null;
    }
    return row;
  }

  private Rectangle serverActionButtonBoundsForPath(TreePath path) {
    Rectangle row = rowBoundsForPath(path);
    if (row == null) return null;

    Rectangle visibleRect = tree.getVisibleRect();
    if (visibleRect == null || visibleRect.isEmpty()) return null;

    int x = visibleRect.x + visibleRect.width - buttonMargin - buttonSize;
    int y = row.y + Math.max(0, (row.height - buttonSize) / 2);
    if (x < visibleRect.x + buttonMargin) {
      x = visibleRect.x + buttonMargin;
    }
    return new Rectangle(x, y, buttonSize, buttonSize);
  }

  private Rectangle channelToggleButtonBoundsForPath(TreePath path) {
    return channelButtonBoundsForPath(path, 1);
  }

  private Rectangle channelCloseButtonBoundsForPath(TreePath path) {
    return channelButtonBoundsForPath(path, 0);
  }

  private Rectangle channelButtonClusterBoundsForPath(TreePath path) {
    Rectangle toggle = channelToggleButtonBoundsForPath(path);
    Rectangle close = channelCloseButtonBoundsForPath(path);
    if (toggle == null || close == null) return null;
    return toggle.union(close);
  }

  private Rectangle channelButtonBoundsForPath(TreePath path, int indexFromRight) {
    Rectangle row = rowBoundsForPath(path);
    if (row == null) return null;

    Rectangle visibleRect = tree.getVisibleRect();
    if (visibleRect == null || visibleRect.isEmpty()) return null;

    int right = visibleRect.x + visibleRect.width - buttonMargin;
    int clusterWidth = (2 * buttonSize) + CHANNEL_ACTION_BUTTON_GAP;
    int clusterLeft = right - clusterWidth;
    int minLeft = visibleRect.x + buttonMargin;
    int shift = clusterLeft < minLeft ? (minLeft - clusterLeft) : 0;

    int x =
        right - buttonSize - (indexFromRight * (buttonSize + CHANNEL_ACTION_BUTTON_GAP)) + shift;
    int y = row.y + Math.max(0, (row.height - buttonSize) / 2);
    return new Rectangle(x, y, buttonSize, buttonSize);
  }

  private ButtonHit actionHitAtPoint(MouseEvent event, boolean includeSelectedFallback) {
    if (event == null) return null;

    TreePath hoveredPath = pathForLocationWithRowFallback(event.getX(), event.getY());
    ButtonHit hoveredHit = buttonHitForPath(hoveredPath, event.getPoint());
    if (hoveredHit != null) return hoveredHit;

    if (!includeSelectedFallback) return null;
    TreePath selectedPath = tree.getSelectionPath();
    if (selectedPath == null || Objects.equals(selectedPath, hoveredPath)) return null;
    return buttonHitForPath(selectedPath, event.getPoint());
  }

  private ButtonHit buttonHitForPath(TreePath path, Point point) {
    RowTarget rowTarget = rowTargetForPath(path);
    if (rowTarget.isNone() || point == null) return null;

    if (rowTarget.isServer()) {
      Rectangle button = serverActionButtonBoundsForPath(path);
      if (button != null && button.contains(point)) {
        return new ButtonHit(ActionKind.SERVER_TOGGLE, rowTarget, button, button, "");
      }
      return null;
    }

    if (rowTarget.isChannel()) {
      Rectangle toggle = channelToggleButtonBoundsForPath(path);
      Rectangle close = channelCloseButtonBoundsForPath(path);
      Rectangle cluster = channelButtonClusterBoundsForPath(path);
      String label = channelLabelForPath(path, rowTarget.channelRef());
      if (toggle != null && toggle.contains(point)) {
        return new ButtonHit(ActionKind.CHANNEL_TOGGLE, rowTarget, toggle, cluster, label);
      }
      if (close != null && close.contains(point)) {
        return new ButtonHit(ActionKind.CHANNEL_CLOSE, rowTarget, close, cluster, label);
      }
    }

    return null;
  }

  private Rectangle buttonClusterBounds(RowTarget target) {
    if (target == null || target.isNone()) return null;

    TreePath path = pathForTarget(target);
    if (path == null) return null;

    if (target.isServer()) return serverActionButtonBoundsForPath(path);
    if (target.isChannel()) return channelButtonClusterBoundsForPath(path);
    return null;
  }

  private TreePath pathForTarget(RowTarget target) {
    if (target == null || target.isNone()) return null;

    if (target.isServer()) return context.serverPathForId(target.serverId());
    if (target.isChannel()) return context.channelPathForRef(target.channelRef());
    return null;
  }

  private void repaintButtonsForTarget(RowTarget target) {
    Rectangle bounds = buttonClusterBounds(target);
    if (bounds != null) {
      tree.repaint(bounds);
    }
  }

  private void paintRowActions(Graphics graphics, RowTarget target) {
    if (graphics == null || target == null || target.isNone()) return;

    if (target.isServer()) {
      paintServerAction(graphics, target.serverId());
      return;
    }

    if (target.isChannel()) {
      paintChannelActions(graphics, target.channelRef());
    }
  }

  private void paintServerAction(Graphics graphics, String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    Rectangle button = serverActionButtonBoundsForPath(context.serverPathForId(sid));
    if (button == null) return;

    ConnectionState state = context.connectionStateForServer(sid);
    boolean enabled =
        ServerTreeConnectionStateViewModel.canConnect(state)
            || ServerTreeConnectionStateViewModel.canDisconnect(state);
    String iconName = ServerTreeConnectionStateViewModel.serverActionIconName(state);

    Graphics2D g2 = (Graphics2D) graphics.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      paintStandardActionButton(g2, button, iconName, enabled, isHot(button));
    } finally {
      g2.dispose();
    }
  }

  private void paintChannelActions(Graphics graphics, TargetRef channelRef) {
    if (graphics == null || channelRef == null) return;

    TreePath path = context.channelPathForRef(channelRef);
    if (path == null) return;

    Rectangle toggle = channelToggleButtonBoundsForPath(path);
    Rectangle close = channelCloseButtonBoundsForPath(path);
    if (toggle == null || close == null) return;

    boolean detached = context.isChannelDisconnected(channelRef);
    String iconName = detached ? "plus" : "exit";

    Graphics2D g2 = (Graphics2D) graphics.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      paintStandardActionButton(g2, toggle, iconName, true, isHot(toggle));
      paintDangerCloseButton(g2, close, isHot(close));
    } finally {
      g2.dispose();
    }
  }

  private void paintStandardActionButton(
      Graphics2D g2, Rectangle button, String iconName, boolean enabled, boolean hot) {
    if (g2 == null || button == null) return;

    Color base = UIManager.getColor("Button.background");
    if (base == null) base = UIManager.getColor("Panel.background");
    if (base == null) base = Color.LIGHT_GRAY;

    Color border = UIManager.getColor("Component.borderColor");
    if (border == null) border = UIManager.getColor("Separator.foreground");
    if (border == null) border = Color.GRAY;

    Color fill = withAlpha(base, enabled ? 220 : 170);
    if (hot && enabled) {
      Color accent = UIManager.getColor("@accentColor");
      if (accent == null) accent = UIManager.getColor("Component.focusColor");
      if (accent != null) {
        fill = withAlpha(accent, 64);
        border = withAlpha(accent, 185);
      } else {
        fill = withAlpha(base, 240);
      }
    }

    g2.setColor(fill);
    g2.fillRoundRect(button.x, button.y, button.width, button.height, 8, 8);
    g2.setColor(withAlpha(border, 200));
    g2.drawRoundRect(button.x, button.y, button.width - 1, button.height - 1, 8, 8);

    Icon actionIcon =
        SvgIcons.icon(iconName, iconSize, enabled ? Palette.ACTION : Palette.ACTION_DISABLED);
    if (actionIcon == null) return;

    int ix = button.x + (button.width - actionIcon.getIconWidth()) / 2;
    int iy = button.y + (button.height - actionIcon.getIconHeight()) / 2;
    actionIcon.paintIcon(tree, g2, ix, iy);
  }

  private void paintDangerCloseButton(Graphics2D g2, Rectangle button, boolean hot) {
    if (g2 == null || button == null) return;

    Color dangerBase = UIManager.getColor("Component.error.borderColor");
    if (dangerBase == null) dangerBase = UIManager.getColor("Actions.Red");
    if (dangerBase == null) dangerBase = new Color(196, 55, 55);

    Color fill = withAlpha(dangerBase, hot ? 236 : 216);
    Color border = withAlpha(dangerBase.darker(), hot ? 240 : 220);

    g2.setColor(fill);
    g2.fillRoundRect(button.x, button.y, button.width, button.height, 5, 5);
    g2.setColor(border);
    g2.drawRoundRect(button.x, button.y, button.width - 1, button.height - 1, 5, 5);

    int pad = Math.max(4, button.width / 4);
    int left = button.x + pad;
    int right = button.x + button.width - pad - 1;
    int top = button.y + pad;
    int bottom = button.y + button.height - pad - 1;

    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.setColor(new Color(255, 255, 255, 236));
    g2.drawLine(left, top, right, bottom);
    g2.drawLine(left, bottom, right, top);
  }

  private boolean isHot(Rectangle bounds) {
    if (bounds == null) return false;
    Point mouse = currentMousePosition();
    return mouse != null && bounds.contains(mouse);
  }

  private Point currentMousePosition() {
    try {
      return tree.getMousePosition();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String nodeServerId(DefaultMutableTreeNode node) {
    if (node == null) return "";
    return Objects.toString(node.getUserObject(), "").trim();
  }

  private static TargetRef channelRefForNode(DefaultMutableTreeNode node) {
    if (node == null) return null;

    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData nodeData)) return null;

    TargetRef ref = nodeData.ref;
    if (ref == null || !ref.isChannel()) return null;
    return ref;
  }

  private static String channelLabelForPath(TreePath path, TargetRef fallbackRef) {
    if (path == null) return buttonLabel(fallbackRef, "");

    Object last = path.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node)) return buttonLabel(fallbackRef, "");

    Object userObject = node.getUserObject();
    if (userObject instanceof ServerTreeNodeData nodeData) {
      return buttonLabel(fallbackRef, nodeData.label);
    }
    return buttonLabel(fallbackRef, "");
  }

  private static String buttonLabel(TargetRef ref, String labelHint) {
    String label = Objects.toString(labelHint, "").trim();
    if (!label.isEmpty()) return label;
    if (ref == null) return "";
    return Objects.toString(ref.target(), "").trim();
  }

  private static Color withAlpha(Color color, int alpha) {
    Color base = color == null ? Color.GRAY : color;
    int a = Math.max(0, Math.min(255, alpha));
    return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
  }
}
