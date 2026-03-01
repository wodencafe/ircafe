package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.icons.SvgIcons.Palette;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
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

/** Handles server connect/disconnect action button hover/click/paint behavior for the tree. */
public final class ServerTreeServerActionOverlay {

  public interface Context {
    boolean isServerNode(DefaultMutableTreeNode node);

    TreePath serverPathForId(String serverId);

    ConnectionState connectionStateForServer(String serverId);

    void connectServer(String serverId);

    void disconnectServer(String serverId);
  }

  private final JTree tree;
  private final int buttonSize;
  private final int iconSize;
  private final int buttonMargin;
  private final Context context;
  private String hoveredServerActionServerId = "";

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

    String selected = selectedServerActionServerId();
    if (!selected.isEmpty()) {
      paintServerAction(graphics, selected);
    }

    String hovered = Objects.toString(hoveredServerActionServerId, "").trim();
    if (!hovered.isEmpty() && !Objects.equals(hovered, selected)) {
      paintServerAction(graphics, hovered);
    }
  }

  public void updateHovered(MouseEvent event) {
    String next = "";
    if (event != null) {
      next = serverIdAt(event.getX(), event.getY());
      if (next.isEmpty()) {
        String current = Objects.toString(hoveredServerActionServerId, "").trim();
        if (!current.isEmpty()) {
          Rectangle button = serverActionButtonBoundsForServer(current);
          if (button != null && button.contains(event.getPoint())) {
            next = current;
          }
        }
      }
    }

    if (Objects.equals(next, hoveredServerActionServerId)) {
      if (!next.isEmpty()) {
        Rectangle button = serverActionButtonBoundsForServer(next);
        if (button != null) {
          tree.repaint(button);
        }
      }
      return;
    }

    hoveredServerActionServerId = next;
    tree.repaint();
  }

  public boolean maybeHandleActionClick(MouseEvent event) {
    if (event == null) return false;
    if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger()) return false;

    String serverId = serverActionServerIdAtPoint(event);
    if (serverId.isEmpty()) return false;

    Rectangle button = serverActionButtonBoundsForServer(serverId);
    if (button == null || !button.contains(event.getPoint())) return false;

    ConnectionState state = context.connectionStateForServer(serverId);
    if (ServerTreeConnectionStateViewModel.canConnect(state)) {
      context.connectServer(serverId);
    } else if (ServerTreeConnectionStateViewModel.canDisconnect(state)) {
      context.disconnectServer(serverId);
    }

    event.consume();
    tree.repaint(button);
    return true;
  }

  public boolean isHoveredServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;
    return Objects.equals(hoveredServerActionServerId, sid);
  }

  public void clearHoveredServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (!sid.isEmpty() && Objects.equals(hoveredServerActionServerId, sid)) {
      hoveredServerActionServerId = "";
    }
  }

  private String selectedServerActionServerId() {
    TreePath selected = tree.getSelectionPath();
    if (selected == null) return "";
    Object last = selected.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node) || !context.isServerNode(node)) return "";
    return Objects.toString(node.getUserObject(), "").trim();
  }

  private String serverIdAt(int x, int y) {
    TreePath path = tree.getPathForLocation(x, y);
    if (path == null) {
      TreePath closest = tree.getClosestPathForLocation(x, y);
      if (closest != null) {
        Rectangle row = tree.getPathBounds(closest);
        if (row != null && y >= row.y && y < (row.y + row.height)) {
          path = closest;
        }
      }
    }
    if (path == null) return "";

    Object last = path.getLastPathComponent();
    if (!(last instanceof DefaultMutableTreeNode node) || !context.isServerNode(node)) {
      return "";
    }
    return Objects.toString(node.getUserObject(), "").trim();
  }

  private Rectangle serverActionButtonBoundsForPath(TreePath path) {
    if (path == null) return null;

    Rectangle row = tree.getPathBounds(path);
    if (row == null) return null;

    Rectangle visibleRect = tree.getVisibleRect();
    if (visibleRect == null || visibleRect.isEmpty()) return null;
    if (row.y + row.height < visibleRect.y || row.y > visibleRect.y + visibleRect.height) {
      return null;
    }

    int x = visibleRect.x + visibleRect.width - buttonMargin - buttonSize;
    int y = row.y + Math.max(0, (row.height - buttonSize) / 2);
    if (x < visibleRect.x + buttonMargin) {
      x = visibleRect.x + buttonMargin;
    }
    return new Rectangle(x, y, buttonSize, buttonSize);
  }

  private Rectangle serverActionButtonBoundsForServer(String serverId) {
    return serverActionButtonBoundsForPath(context.serverPathForId(serverId));
  }

  private String serverActionServerIdAtPoint(MouseEvent event) {
    if (event == null) return "";

    String sid = serverIdAt(event.getX(), event.getY());
    if (!sid.isEmpty()) {
      Rectangle button = serverActionButtonBoundsForServer(sid);
      if (button != null && button.contains(event.getPoint())) {
        return sid;
      }
    }

    String selected = selectedServerActionServerId();
    if (!selected.isEmpty()) {
      Rectangle button = serverActionButtonBoundsForServer(selected);
      if (button != null && button.contains(event.getPoint())) {
        return selected;
      }
    }

    return "";
  }

  private void paintServerAction(Graphics graphics, String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    Rectangle button = serverActionButtonBoundsForServer(sid);
    if (button == null) return;

    ConnectionState state = context.connectionStateForServer(sid);
    boolean enabled =
        ServerTreeConnectionStateViewModel.canConnect(state)
            || ServerTreeConnectionStateViewModel.canDisconnect(state);

    Graphics2D g2 = (Graphics2D) graphics.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      Color base = UIManager.getColor("Button.background");
      if (base == null) base = UIManager.getColor("Panel.background");
      if (base == null) base = Color.LIGHT_GRAY;

      Color border = UIManager.getColor("Component.borderColor");
      if (border == null) border = UIManager.getColor("Separator.foreground");
      if (border == null) border = Color.GRAY;

      java.awt.Point mousePosition = null;
      try {
        mousePosition = tree.getMousePosition();
      } catch (Exception ignored) {
      }
      boolean hot = mousePosition != null && button.contains(mousePosition);

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
          SvgIcons.icon(
              ServerTreeConnectionStateViewModel.serverActionIconName(state),
              iconSize,
              enabled ? Palette.ACTION : Palette.ACTION_DISABLED);
      if (actionIcon != null) {
        int ix = button.x + (button.width - actionIcon.getIconWidth()) / 2;
        int iy = button.y + (button.height - actionIcon.getIconHeight()) / 2;
        actionIcon.paintIcon(tree, g2, ix, iy);
      }
    } finally {
      g2.dispose();
    }
  }

  private static Color withAlpha(Color color, int alpha) {
    Color base = color == null ? Color.GRAY : color;
    int a = Math.max(0, Math.min(255, alpha));
    return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
  }
}
