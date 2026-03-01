package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeSelectionFallbackPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Adapter for {@link ServerTreeSelectionFallbackPolicy.Context}. */
public final class ServerTreeSelectionFallbackContextAdapter
    implements ServerTreeSelectionFallbackPolicy.Context {

  private final Function<String, String> normalizeServerId;
  private final Map<String, ServerNodes> servers;
  private final Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility;
  private final Map<TargetRef, DefaultMutableTreeNode> leaves;
  private final Consumer<TargetRef> selectTarget;
  private final JTree tree;

  public ServerTreeSelectionFallbackContextAdapter(
      Function<String, String> normalizeServerId,
      Map<String, ServerNodes> servers,
      Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Consumer<TargetRef> selectTarget,
      JTree tree) {
    this.normalizeServerId = Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    this.servers = Objects.requireNonNull(servers, "servers");
    this.builtInNodesVisibility =
        Objects.requireNonNull(builtInNodesVisibility, "builtInNodesVisibility");
    this.leaves = Objects.requireNonNull(leaves, "leaves");
    this.selectTarget = Objects.requireNonNull(selectTarget, "selectTarget");
    this.tree = Objects.requireNonNull(tree, "tree");
  }

  @Override
  public boolean serverExists(String serverId) {
    return servers.containsKey(Objects.toString(serverId, "").trim());
  }

  @Override
  public boolean statusVisible(String serverId) {
    return builtInNodesVisibility.apply(serverId).server();
  }

  @Override
  public boolean notificationsVisible(String serverId) {
    return builtInNodesVisibility.apply(serverId).notifications();
  }

  @Override
  public boolean logViewerVisible(String serverId) {
    return builtInNodesVisibility.apply(serverId).logViewer();
  }

  @Override
  public TargetRef statusRef(String serverId) {
    String sid = normalizeServerId.apply(serverId);
    ServerNodes nodes = servers.get(sid);
    return nodes == null ? null : nodes.statusRef;
  }

  @Override
  public TargetRef notificationsRef(String serverId) {
    String sid = normalizeServerId.apply(serverId);
    ServerNodes nodes = servers.get(sid);
    return nodes == null ? null : nodes.notificationsRef;
  }

  @Override
  public TargetRef logViewerRef(String serverId) {
    String sid = normalizeServerId.apply(serverId);
    ServerNodes nodes = servers.get(sid);
    return nodes == null ? null : nodes.logViewerRef;
  }

  @Override
  public TargetRef channelListRef(String serverId) {
    String sid = normalizeServerId.apply(serverId);
    ServerNodes nodes = servers.get(sid);
    return nodes == null ? null : nodes.channelListRef;
  }

  @Override
  public TargetRef weechatFiltersRef(String serverId) {
    String sid = normalizeServerId.apply(serverId);
    ServerNodes nodes = servers.get(sid);
    return nodes == null ? null : nodes.weechatFiltersRef;
  }

  @Override
  public TargetRef ignoresRef(String serverId) {
    String sid = normalizeServerId.apply(serverId);
    ServerNodes nodes = servers.get(sid);
    return nodes == null ? null : nodes.ignoresRef;
  }

  @Override
  public boolean hasLeaf(TargetRef ref) {
    return ref != null && leaves.containsKey(ref);
  }

  @Override
  public boolean isMonitorGroupAttached(String serverId) {
    String sid = normalizeServerId.apply(serverId);
    ServerNodes nodes = servers.get(sid);
    if (nodes == null || nodes.monitorNode == null) return false;
    return builtInNodesVisibility.apply(sid).monitor()
        && (nodes.monitorNode.getParent() == nodes.serverNode
            || nodes.monitorNode.getParent() == nodes.otherNode);
  }

  @Override
  public boolean isInterceptorsGroupAttached(String serverId) {
    String sid = normalizeServerId.apply(serverId);
    ServerNodes nodes = servers.get(sid);
    if (nodes == null || nodes.interceptorsNode == null) return false;
    return builtInNodesVisibility.apply(sid).interceptors()
        && (nodes.interceptorsNode.getParent() == nodes.serverNode
            || nodes.interceptorsNode.getParent() == nodes.otherNode);
  }

  @Override
  public void selectTarget(TargetRef ref) {
    if (ref != null) {
      selectTarget.accept(ref);
    }
  }

  @Override
  public boolean isDirectChildOfServerNode(TargetRef ref, String serverId) {
    if (ref == null) return false;
    String sid = normalizeServerId.apply(serverId);
    ServerNodes nodes = servers.get(sid);
    if (nodes == null || nodes.serverNode == null) return false;
    DefaultMutableTreeNode leaf = leaves.get(ref);
    return leaf != null && leaf.getParent() == nodes.serverNode;
  }

  @Override
  public void selectServerNodePath(String serverId) {
    String sid = normalizeServerId.apply(serverId);
    ServerNodes nodes = servers.get(sid);
    if (nodes == null || nodes.serverNode == null) return;
    TreePath serverPath = new TreePath(nodes.serverNode.getPath());
    tree.setSelectionPath(serverPath);
    tree.scrollPathToVisible(serverPath);
  }
}
