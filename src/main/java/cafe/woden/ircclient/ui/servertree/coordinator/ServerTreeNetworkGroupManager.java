package cafe.woden.ircclient.ui.servertree.coordinator;

import java.util.Map;
import java.util.Objects;
import javax.swing.tree.DefaultMutableTreeNode;

/** Manages lifecycle and lookup of bouncer-network grouping nodes beneath origin servers. */
public final class ServerTreeNetworkGroupManager {

  public interface Context {
    DefaultMutableTreeNode serverNode(String serverId);

    DefaultMutableTreeNode privateMessagesNode(String serverId);
  }

  private final String sojuNetworksGroupLabel;
  private final String zncNetworksGroupLabel;
  private final Map<String, DefaultMutableTreeNode> sojuNetworksGroupByOrigin;
  private final Map<String, DefaultMutableTreeNode> zncNetworksGroupByOrigin;
  private final Context context;

  public ServerTreeNetworkGroupManager(
      String sojuNetworksGroupLabel,
      String zncNetworksGroupLabel,
      Map<String, DefaultMutableTreeNode> sojuNetworksGroupByOrigin,
      Map<String, DefaultMutableTreeNode> zncNetworksGroupByOrigin,
      Context context) {
    this.sojuNetworksGroupLabel = Objects.toString(sojuNetworksGroupLabel, "Soju Networks");
    this.zncNetworksGroupLabel = Objects.toString(zncNetworksGroupLabel, "ZNC Networks");
    this.sojuNetworksGroupByOrigin =
        Objects.requireNonNull(sojuNetworksGroupByOrigin, "sojuNetworksGroupByOrigin");
    this.zncNetworksGroupByOrigin =
        Objects.requireNonNull(zncNetworksGroupByOrigin, "zncNetworksGroupByOrigin");
    this.context = Objects.requireNonNull(context, "context");
  }

  public DefaultMutableTreeNode getOrCreateSojuNetworksGroupNode(String originServerId) {
    String origin = normalize(originServerId);
    if (origin.isEmpty()) return null;

    DefaultMutableTreeNode existing = sojuNetworksGroupByOrigin.get(origin);
    if (existing != null) return existing;

    DefaultMutableTreeNode originNode = context.serverNode(origin);
    DefaultMutableTreeNode pmNode = context.privateMessagesNode(origin);
    if (originNode == null || pmNode == null) return null;

    DefaultMutableTreeNode group = new DefaultMutableTreeNode(sojuNetworksGroupLabel);
    originNode.insert(group, insertIndexBeforePrivateMessages(originNode, pmNode));
    sojuNetworksGroupByOrigin.put(origin, group);
    return group;
  }

  public DefaultMutableTreeNode getOrCreateZncNetworksGroupNode(String originServerId) {
    String origin = normalize(originServerId);
    if (origin.isEmpty()) return null;

    DefaultMutableTreeNode existing = zncNetworksGroupByOrigin.get(origin);
    if (existing != null) return existing;

    DefaultMutableTreeNode originNode = context.serverNode(origin);
    DefaultMutableTreeNode pmNode = context.privateMessagesNode(origin);
    if (originNode == null || pmNode == null) return null;

    DefaultMutableTreeNode group = new DefaultMutableTreeNode(zncNetworksGroupLabel);
    originNode.insert(group, insertIndexBeforePrivateMessages(originNode, pmNode));
    zncNetworksGroupByOrigin.put(origin, group);
    return group;
  }

  public boolean isSojuNetworksGroupNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object userObject = node.getUserObject();
    if (userObject instanceof String s && sojuNetworksGroupLabel.equals(s)) return true;
    return sojuNetworksGroupByOrigin.containsValue(node);
  }

  public boolean isZncNetworksGroupNode(DefaultMutableTreeNode node) {
    if (node == null) return false;
    Object userObject = node.getUserObject();
    if (userObject instanceof String s && zncNetworksGroupLabel.equals(s)) return true;
    return zncNetworksGroupByOrigin.containsValue(node);
  }

  public void removeEmptyGroupIfNeeded(DefaultMutableTreeNode node) {
    if (node == null || node.getChildCount() > 0) return;

    if (isSojuNetworksGroupNode(node)) {
      DefaultMutableTreeNode originNode = (DefaultMutableTreeNode) node.getParent();
      if (originNode != null) {
        originNode.remove(node);
      }
      sojuNetworksGroupByOrigin.entrySet().removeIf(entry -> entry.getValue() == node);
      return;
    }

    if (isZncNetworksGroupNode(node)) {
      DefaultMutableTreeNode originNode = (DefaultMutableTreeNode) node.getParent();
      if (originNode != null) {
        originNode.remove(node);
      }
      zncNetworksGroupByOrigin.entrySet().removeIf(entry -> entry.getValue() == node);
    }
  }

  public static String parseOriginFromCompoundServerId(String serverId, String prefix) {
    String id = normalize(serverId);
    String p = normalize(prefix);
    if (id.isEmpty() || p.isEmpty() || !id.startsWith(p)) return null;
    int start = p.length();
    int nextColon = id.indexOf(':', start);
    if (nextColon <= start) return null;
    String origin = id.substring(start, nextColon).trim();
    return origin.isEmpty() ? null : origin;
  }

  private static int insertIndexBeforePrivateMessages(
      DefaultMutableTreeNode serverNode, DefaultMutableTreeNode privateMessagesNode) {
    int insertIdx = serverNode.getChildCount();
    int pmIdx = serverNode.getIndex(privateMessagesNode);
    if (pmIdx >= 0) insertIdx = Math.min(insertIdx, pmIdx);
    return Math.min(insertIdx, serverNode.getChildCount());
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
