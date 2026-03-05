package cafe.woden.ircclient.ui.servertree.policy;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import cafe.woden.ircclient.ui.servertree.model.ServerNodes;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/** Encapsulates fallback and startup target-selection policy for a server tree. */
public final class ServerTreeSelectionFallbackPolicy {

  public interface Context {
    boolean serverExists(String serverId);

    boolean statusVisible(String serverId);

    boolean notificationsVisible(String serverId);

    boolean logViewerVisible(String serverId);

    TargetRef statusRef(String serverId);

    TargetRef notificationsRef(String serverId);

    TargetRef logViewerRef(String serverId);

    TargetRef channelListRef(String serverId);

    TargetRef weechatFiltersRef(String serverId);

    TargetRef ignoresRef(String serverId);

    boolean hasLeaf(TargetRef ref);

    boolean isMonitorGroupAttached(String serverId);

    boolean isInterceptorsGroupAttached(String serverId);

    void selectTarget(TargetRef ref);

    boolean isDirectChildOfServerNode(TargetRef ref, String serverId);

    void selectServerNodePath(String serverId);
  }

  public static Context context(
      Function<String, String> normalizeServerId,
      Map<String, ServerNodes> servers,
      Function<String, ServerBuiltInNodesVisibility> builtInNodesVisibility,
      Map<TargetRef, DefaultMutableTreeNode> leaves,
      Consumer<TargetRef> selectTarget,
      JTree tree) {
    Objects.requireNonNull(normalizeServerId, "normalizeServerId");
    Objects.requireNonNull(servers, "servers");
    Objects.requireNonNull(builtInNodesVisibility, "builtInNodesVisibility");
    Objects.requireNonNull(leaves, "leaves");
    Objects.requireNonNull(selectTarget, "selectTarget");
    Objects.requireNonNull(tree, "tree");
    return new Context() {
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
    };
  }

  private final Context context;

  public ServerTreeSelectionFallbackPolicy(Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  public void selectBestFallbackForServer(String serverId) {
    String sid = normalize(serverId);
    if (sid.isEmpty() || !context.serverExists(sid)) return;

    TargetRef statusRef = context.statusRef(sid);
    if (context.statusVisible(sid) && context.hasLeaf(statusRef)) {
      context.selectTarget(statusRef);
      return;
    }

    TargetRef notificationsRef = context.notificationsRef(sid);
    if (context.notificationsVisible(sid) && context.hasLeaf(notificationsRef)) {
      context.selectTarget(notificationsRef);
      return;
    }

    TargetRef logViewerRef = context.logViewerRef(sid);
    if (context.logViewerVisible(sid) && context.hasLeaf(logViewerRef)) {
      context.selectTarget(logViewerRef);
      return;
    }

    TargetRef channelListRef = context.channelListRef(sid);
    if (context.hasLeaf(channelListRef)) {
      context.selectTarget(channelListRef);
      return;
    }

    TargetRef filtersRef = context.weechatFiltersRef(sid);
    if (context.hasLeaf(filtersRef)) {
      context.selectTarget(filtersRef);
      return;
    }

    TargetRef ignoresRef = context.ignoresRef(sid);
    if (context.hasLeaf(ignoresRef)) {
      context.selectTarget(ignoresRef);
      return;
    }

    if (context.isMonitorGroupAttached(sid)) {
      context.selectTarget(TargetRef.monitorGroup(sid));
      return;
    }

    if (context.isInterceptorsGroupAttached(sid)) {
      context.selectTarget(TargetRef.interceptorsGroup(sid));
    }
  }

  public void selectStartupDefaultForServer(String serverId) {
    String sid = normalize(serverId);
    if (sid.isEmpty() || !context.serverExists(sid)) return;

    TargetRef channelListRef = context.channelListRef(sid);
    if (context.isDirectChildOfServerNode(channelListRef, sid)) {
      context.selectTarget(channelListRef);
      return;
    }

    TargetRef notificationsRef = context.notificationsRef(sid);
    if (context.isDirectChildOfServerNode(notificationsRef, sid)) {
      context.selectTarget(notificationsRef);
      return;
    }

    context.selectServerNodePath(sid);
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
