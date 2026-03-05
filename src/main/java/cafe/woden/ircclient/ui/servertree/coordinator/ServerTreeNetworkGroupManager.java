package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.ui.servertree.ServerTreeBouncerBackends;
import cafe.woden.ircclient.ui.servertree.ServerTreeConventions;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

  private final Map<String, String> networksGroupLabelByBackendId;
  private final Map<String, Map<String, DefaultMutableTreeNode>> networksGroupByOriginByBackendId;
  private final Context context;

  public ServerTreeNetworkGroupManager(
      String sojuNetworksGroupLabel,
      String zncNetworksGroupLabel,
      String genericNetworksGroupLabel,
      Map<String, DefaultMutableTreeNode> sojuNetworksGroupByOrigin,
      Map<String, DefaultMutableTreeNode> zncNetworksGroupByOrigin,
      Map<String, DefaultMutableTreeNode> genericNetworksGroupByOrigin,
      Context context) {
    this(
        labelMap(sojuNetworksGroupLabel, zncNetworksGroupLabel, genericNetworksGroupLabel),
        groupsByBackendMap(
            sojuNetworksGroupByOrigin, zncNetworksGroupByOrigin, genericNetworksGroupByOrigin),
        context);
  }

  public ServerTreeNetworkGroupManager(
      Map<String, String> networksGroupLabelByBackendId,
      Map<String, Map<String, DefaultMutableTreeNode>> networksGroupByOriginByBackendId,
      Context context) {
    this.networksGroupLabelByBackendId =
        Objects.requireNonNull(networksGroupLabelByBackendId, "networksGroupLabelByBackendId");
    this.networksGroupByOriginByBackendId =
        Objects.requireNonNull(
            networksGroupByOriginByBackendId, "networksGroupByOriginByBackendId");
    this.context = Objects.requireNonNull(context, "context");
  }

  public DefaultMutableTreeNode getOrCreateSojuNetworksGroupNode(String originServerId) {
    return getOrCreateNetworksGroupNode(ServerTreeBouncerBackends.SOJU, originServerId);
  }

  public DefaultMutableTreeNode getOrCreateZncNetworksGroupNode(String originServerId) {
    return getOrCreateNetworksGroupNode(ServerTreeBouncerBackends.ZNC, originServerId);
  }

  public DefaultMutableTreeNode getOrCreateGenericNetworksGroupNode(String originServerId) {
    return getOrCreateNetworksGroupNode(ServerTreeBouncerBackends.GENERIC, originServerId);
  }

  public DefaultMutableTreeNode getOrCreateNetworksGroupNode(
      String backendId, String originServerId) {
    return getOrCreateNetworksGroupNode(
        originServerId, groupLabelForBackend(backendId), groupsByOriginForBackend(backendId));
  }

  public boolean isSojuNetworksGroupNode(DefaultMutableTreeNode node) {
    return isNetworksGroupNode(ServerTreeBouncerBackends.SOJU, node);
  }

  public boolean isZncNetworksGroupNode(DefaultMutableTreeNode node) {
    return isNetworksGroupNode(ServerTreeBouncerBackends.ZNC, node);
  }

  public boolean isGenericNetworksGroupNode(DefaultMutableTreeNode node) {
    return isNetworksGroupNode(ServerTreeBouncerBackends.GENERIC, node);
  }

  public boolean isNetworksGroupNode(String backendId, DefaultMutableTreeNode node) {
    return isNetworksGroupNode(
        node, groupLabelForBackend(backendId), groupsByOriginForBackend(backendId));
  }

  public String backendIdForNetworksGroupNode(DefaultMutableTreeNode node) {
    for (String backendId : backendIds()) {
      if (isNetworksGroupNode(backendId, node)) {
        return backendId;
      }
    }
    return null;
  }

  public void removeEmptyGroupIfNeeded(DefaultMutableTreeNode node) {
    if (node == null || node.getChildCount() > 0) return;

    String backendId = backendIdForNetworksGroupNode(node);
    if (backendId == null || backendId.isBlank()) return;
    removeGroupNode(node, groupsByOriginForBackend(backendId));
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

  private Set<String> backendIds() {
    LinkedHashSet<String> backendIds = new LinkedHashSet<>(ServerTreeBouncerBackends.orderedIds());
    backendIds.addAll(networksGroupLabelByBackendId.keySet());
    backendIds.addAll(networksGroupByOriginByBackendId.keySet());
    return backendIds;
  }

  private String groupLabelForBackend(String backendId) {
    String backend = normalize(backendId);
    if (backend.isEmpty()) {
      return ServerTreeBouncerBackends.defaultNetworksGroupLabel(backendId);
    }
    String label = networksGroupLabelByBackendId.get(backend);
    if (label == null || label.isBlank()) {
      return ServerTreeBouncerBackends.defaultNetworksGroupLabel(backend);
    }
    return label;
  }

  private Map<String, DefaultMutableTreeNode> groupsByOriginForBackend(String backendId) {
    String backend = normalize(backendId);
    if (backend.isEmpty()) {
      return new HashMap<>();
    }
    return networksGroupByOriginByBackendId.computeIfAbsent(backend, ignored -> new HashMap<>());
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

  private static Map<String, String> labelMap(
      String sojuNetworksGroupLabel,
      String zncNetworksGroupLabel,
      String genericNetworksGroupLabel) {
    Map<String, String> labels = new HashMap<>();
    labels.put(
        ServerTreeBouncerBackends.SOJU,
        Objects.toString(
            sojuNetworksGroupLabel,
            ServerTreeBouncerBackends.defaultNetworksGroupLabel(ServerTreeBouncerBackends.SOJU)));
    labels.put(
        ServerTreeBouncerBackends.ZNC,
        Objects.toString(
            zncNetworksGroupLabel,
            ServerTreeBouncerBackends.defaultNetworksGroupLabel(ServerTreeBouncerBackends.ZNC)));
    labels.put(
        ServerTreeBouncerBackends.GENERIC,
        Objects.toString(
            genericNetworksGroupLabel,
            ServerTreeBouncerBackends.defaultNetworksGroupLabel(
                ServerTreeBouncerBackends.GENERIC)));
    return labels;
  }

  private static Map<String, Map<String, DefaultMutableTreeNode>> groupsByBackendMap(
      Map<String, DefaultMutableTreeNode> sojuNetworksGroupByOrigin,
      Map<String, DefaultMutableTreeNode> zncNetworksGroupByOrigin,
      Map<String, DefaultMutableTreeNode> genericNetworksGroupByOrigin) {
    Map<String, Map<String, DefaultMutableTreeNode>> groupsByBackend = new HashMap<>();
    groupsByBackend.put(
        ServerTreeBouncerBackends.SOJU,
        Objects.requireNonNull(sojuNetworksGroupByOrigin, "sojuNetworksGroupByOrigin"));
    groupsByBackend.put(
        ServerTreeBouncerBackends.ZNC,
        Objects.requireNonNull(zncNetworksGroupByOrigin, "zncNetworksGroupByOrigin"));
    groupsByBackend.put(
        ServerTreeBouncerBackends.GENERIC,
        Objects.requireNonNull(genericNetworksGroupByOrigin, "genericNetworksGroupByOrigin"));
    return groupsByBackend;
  }
}
