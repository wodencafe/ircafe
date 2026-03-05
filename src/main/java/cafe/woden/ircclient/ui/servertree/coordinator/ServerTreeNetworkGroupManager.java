package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.tree.DefaultMutableTreeNode;

/** Manages lifecycle and lookup of bouncer-network grouping nodes beneath origin servers. */
public final class ServerTreeNetworkGroupManager {

  public interface Context {
    DefaultMutableTreeNode serverNode(String serverId);

    DefaultMutableTreeNode privateMessagesNode(String serverId);
  }

  public static Context context(
      Function<String, DefaultMutableTreeNode> serverNode,
      Function<String, DefaultMutableTreeNode> privateMessagesNode) {
    Objects.requireNonNull(serverNode, "serverNode");
    Objects.requireNonNull(privateMessagesNode, "privateMessagesNode");
    return new Context() {
      @Override
      public DefaultMutableTreeNode serverNode(String serverId) {
        return serverNode.apply(serverId);
      }

      @Override
      public DefaultMutableTreeNode privateMessagesNode(String serverId) {
        return privateMessagesNode.apply(serverId);
      }
    };
  }

  private final String sojuNetworksGroupLabel;
  private final String zncNetworksGroupLabel;
  private final String genericNetworksGroupLabel;
  private final Map<String, DefaultMutableTreeNode> sojuNetworksGroupByOrigin;
  private final Map<String, DefaultMutableTreeNode> zncNetworksGroupByOrigin;
  private final Map<String, DefaultMutableTreeNode> genericNetworksGroupByOrigin;
  private final Context context;

  public ServerTreeNetworkGroupManager(
      String sojuNetworksGroupLabel,
      String zncNetworksGroupLabel,
      String genericNetworksGroupLabel,
      Map<String, DefaultMutableTreeNode> sojuNetworksGroupByOrigin,
      Map<String, DefaultMutableTreeNode> zncNetworksGroupByOrigin,
      Map<String, DefaultMutableTreeNode> genericNetworksGroupByOrigin,
      Context context) {
    this.sojuNetworksGroupLabel = Objects.toString(sojuNetworksGroupLabel, "Soju Networks");
    this.zncNetworksGroupLabel = Objects.toString(zncNetworksGroupLabel, "ZNC Networks");
    this.genericNetworksGroupLabel =
        Objects.toString(genericNetworksGroupLabel, "Bouncer Networks");
    this.sojuNetworksGroupByOrigin =
        Objects.requireNonNull(sojuNetworksGroupByOrigin, "sojuNetworksGroupByOrigin");
    this.zncNetworksGroupByOrigin =
        Objects.requireNonNull(zncNetworksGroupByOrigin, "zncNetworksGroupByOrigin");
    this.genericNetworksGroupByOrigin =
        Objects.requireNonNull(genericNetworksGroupByOrigin, "genericNetworksGroupByOrigin");
    this.context = Objects.requireNonNull(context, "context");
  }

  public DefaultMutableTreeNode getOrCreateSojuNetworksGroupNode(String originServerId) {
    return getOrCreateNetworksGroupNode(
        originServerId, sojuNetworksGroupLabel, sojuNetworksGroupByOrigin);
  }

  public DefaultMutableTreeNode getOrCreateZncNetworksGroupNode(String originServerId) {
    return getOrCreateNetworksGroupNode(
        originServerId, zncNetworksGroupLabel, zncNetworksGroupByOrigin);
  }

  public DefaultMutableTreeNode getOrCreateGenericNetworksGroupNode(String originServerId) {
    return getOrCreateNetworksGroupNode(
        originServerId, genericNetworksGroupLabel, genericNetworksGroupByOrigin);
  }

  public boolean isSojuNetworksGroupNode(DefaultMutableTreeNode node) {
    return isNetworksGroupNode(node, sojuNetworksGroupLabel, sojuNetworksGroupByOrigin);
  }

  public boolean isZncNetworksGroupNode(DefaultMutableTreeNode node) {
    return isNetworksGroupNode(node, zncNetworksGroupLabel, zncNetworksGroupByOrigin);
  }

  public boolean isGenericNetworksGroupNode(DefaultMutableTreeNode node) {
    return isNetworksGroupNode(node, genericNetworksGroupLabel, genericNetworksGroupByOrigin);
  }

  public void removeEmptyGroupIfNeeded(DefaultMutableTreeNode node) {
    if (node == null || node.getChildCount() > 0) return;

    if (isSojuNetworksGroupNode(node)) {
      removeGroupNode(node, sojuNetworksGroupByOrigin);
      return;
    }

    if (isZncNetworksGroupNode(node)) {
      removeGroupNode(node, zncNetworksGroupByOrigin);
      return;
    }

    if (isGenericNetworksGroupNode(node)) {
      removeGroupNode(node, genericNetworksGroupByOrigin);
    }
  }

  public static String parseOriginFromCompoundServerId(String serverId, String prefix) {
    String id = ServerTreeConventions.normalize(serverId);
    String p = ServerTreeConventions.normalize(prefix);
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
    return ServerTreeConventions.normalize(value);
  }

  private DefaultMutableTreeNode getOrCreateNetworksGroupNode(
      String originServerId,
      String groupLabel,
      Map<String, DefaultMutableTreeNode> groupsByOrigin) {
    String origin = normalize(originServerId);
    if (origin.isEmpty()) return null;

    DefaultMutableTreeNode existing = groupsByOrigin.get(origin);
    if (existing != null) return existing;

    DefaultMutableTreeNode originNode = context.serverNode(origin);
    DefaultMutableTreeNode pmNode = context.privateMessagesNode(origin);
    if (originNode == null || pmNode == null) return null;

    DefaultMutableTreeNode group = new DefaultMutableTreeNode(groupLabel);
    originNode.insert(group, insertIndexBeforePrivateMessages(originNode, pmNode));
    groupsByOrigin.put(origin, group);
    return group;
  }

  private static boolean isNetworksGroupNode(
      DefaultMutableTreeNode node,
      String groupLabel,
      Map<String, DefaultMutableTreeNode> groupsByOrigin) {
    if (node == null) return false;
    Object userObject = node.getUserObject();
    if (userObject instanceof String s && groupLabel.equals(s)) return true;
    return groupsByOrigin.containsValue(node);
  }

  private static void removeGroupNode(
      DefaultMutableTreeNode node, Map<String, DefaultMutableTreeNode> groupsByOrigin) {
    DefaultMutableTreeNode originNode = (DefaultMutableTreeNode) node.getParent();
    if (originNode != null) {
      originNode.remove(node);
    }
    groupsByOrigin.entrySet().removeIf(entry -> entry.getValue() == node);
  }
}
