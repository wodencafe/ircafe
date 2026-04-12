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
import org.springframework.stereotype.Component;

/** Manages lifecycle and lookup of bouncer-network grouping nodes beneath origin servers. */
@Component
public final class ServerTreeNetworkGroupManager {

  public interface Context {
    Map<String, String> networksGroupLabelByBackendId();

    Map<String, Map<String, DefaultMutableTreeNode>> networksGroupByOriginByBackendId();

    DefaultMutableTreeNode serverNode(String serverId);

    DefaultMutableTreeNode privateMessagesNode(String serverId);
  }

  public static Context context(
      Map<String, String> networksGroupLabelByBackendId,
      Map<String, Map<String, DefaultMutableTreeNode>> networksGroupByOriginByBackendId,
      Function<String, DefaultMutableTreeNode> serverNode,
      Function<String, DefaultMutableTreeNode> privateMessagesNode) {
    Objects.requireNonNull(networksGroupLabelByBackendId, "networksGroupLabelByBackendId");
    Objects.requireNonNull(networksGroupByOriginByBackendId, "networksGroupByOriginByBackendId");
    Objects.requireNonNull(serverNode, "serverNode");
    Objects.requireNonNull(privateMessagesNode, "privateMessagesNode");
    return new Context() {
      @Override
      public Map<String, String> networksGroupLabelByBackendId() {
        return networksGroupLabelByBackendId;
      }

      @Override
      public Map<String, Map<String, DefaultMutableTreeNode>> networksGroupByOriginByBackendId() {
        return networksGroupByOriginByBackendId;
      }

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

  public DefaultMutableTreeNode getOrCreateSojuNetworksGroupNode(
      Context context, String originServerId) {
    return getOrCreateNetworksGroupNode(context, ServerTreeBouncerBackends.SOJU, originServerId);
  }

  public DefaultMutableTreeNode getOrCreateZncNetworksGroupNode(
      Context context, String originServerId) {
    return getOrCreateNetworksGroupNode(context, ServerTreeBouncerBackends.ZNC, originServerId);
  }

  public DefaultMutableTreeNode getOrCreateGenericNetworksGroupNode(
      Context context, String originServerId) {
    return getOrCreateNetworksGroupNode(context, ServerTreeBouncerBackends.GENERIC, originServerId);
  }

  public DefaultMutableTreeNode getOrCreateNetworksGroupNode(
      Context context, String backendId, String originServerId) {
    Context in = Objects.requireNonNull(context, "context");
    return getOrCreateNetworksGroupNode(
        in,
        originServerId,
        groupLabelForBackend(in, backendId),
        groupsByOriginForBackend(in, backendId));
  }

  public boolean isSojuNetworksGroupNode(Context context, DefaultMutableTreeNode node) {
    return isNetworksGroupNode(context, ServerTreeBouncerBackends.SOJU, node);
  }

  public boolean isZncNetworksGroupNode(Context context, DefaultMutableTreeNode node) {
    return isNetworksGroupNode(context, ServerTreeBouncerBackends.ZNC, node);
  }

  public boolean isGenericNetworksGroupNode(Context context, DefaultMutableTreeNode node) {
    return isNetworksGroupNode(context, ServerTreeBouncerBackends.GENERIC, node);
  }

  public boolean isNetworksGroupNode(
      Context context, String backendId, DefaultMutableTreeNode node) {
    Context in = Objects.requireNonNull(context, "context");
    return isNetworksGroupNode(
        node, groupLabelForBackend(in, backendId), groupsByOriginForBackend(in, backendId));
  }

  public String backendIdForNetworksGroupNode(Context context, DefaultMutableTreeNode node) {
    Context in = Objects.requireNonNull(context, "context");
    for (String backendId : backendIds(in)) {
      if (isNetworksGroupNode(in, backendId, node)) {
        return backendId;
      }
    }
    return null;
  }

  public void removeEmptyGroupIfNeeded(Context context, DefaultMutableTreeNode node) {
    Context in = Objects.requireNonNull(context, "context");
    if (node == null || node.getChildCount() > 0) return;

    String backendId = backendIdForNetworksGroupNode(in, node);
    if (backendId == null || backendId.isBlank()) return;
    removeGroupNode(node, groupsByOriginForBackend(in, backendId));
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

  private Set<String> backendIds(Context context) {
    LinkedHashSet<String> backendIds = new LinkedHashSet<>(ServerTreeBouncerBackends.orderedIds());
    backendIds.addAll(context.networksGroupLabelByBackendId().keySet());
    backendIds.addAll(context.networksGroupByOriginByBackendId().keySet());
    return backendIds;
  }

  private String groupLabelForBackend(Context context, String backendId) {
    String backend = normalize(backendId);
    if (backend.isEmpty()) {
      return ServerTreeBouncerBackends.defaultNetworksGroupLabel(backendId);
    }
    String label = context.networksGroupLabelByBackendId().get(backend);
    if (label == null || label.isBlank()) {
      return ServerTreeBouncerBackends.defaultNetworksGroupLabel(backend);
    }
    return label;
  }

  private Map<String, DefaultMutableTreeNode> groupsByOriginForBackend(
      Context context, String backendId) {
    String backend = normalize(backendId);
    if (backend.isEmpty()) {
      return new HashMap<>();
    }
    return context
        .networksGroupByOriginByBackendId()
        .computeIfAbsent(backend, ignored -> new HashMap<>());
  }

  private DefaultMutableTreeNode getOrCreateNetworksGroupNode(
      Context context,
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
