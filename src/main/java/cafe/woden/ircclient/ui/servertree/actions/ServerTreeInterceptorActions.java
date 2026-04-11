package cafe.woden.ircclient.ui.servertree.actions;

import cafe.woden.ircclient.interceptors.InterceptorScope;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.awt.Component;
import java.awt.Window;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Encapsulates interceptor CRUD actions and tree-label refresh behavior. */
@org.springframework.stereotype.Component
public final class ServerTreeInterceptorActions {

  public interface Context {
    Component ownerComponent();

    InterceptorStore interceptorStore();

    String interceptorsGroupLabel();

    void ensureNode(TargetRef ref);

    void selectTarget(TargetRef ref);

    void removeTarget(TargetRef ref);

    DefaultMutableTreeNode leafNode(TargetRef ref);

    DefaultMutableTreeNode interceptorsGroupNode(String serverId);

    void nodeChanged(DefaultMutableTreeNode node);
  }

  public static Context context(
      Component ownerComponent,
      InterceptorStore interceptorStore,
      String interceptorsGroupLabel,
      Consumer<TargetRef> ensureNode,
      Consumer<TargetRef> selectTarget,
      Consumer<TargetRef> removeTarget,
      Function<TargetRef, DefaultMutableTreeNode> leafNode,
      Function<String, DefaultMutableTreeNode> interceptorsGroupNode,
      Consumer<DefaultMutableTreeNode> nodeChanged) {
    Objects.requireNonNull(ensureNode, "ensureNode");
    Objects.requireNonNull(selectTarget, "selectTarget");
    Objects.requireNonNull(removeTarget, "removeTarget");
    Objects.requireNonNull(leafNode, "leafNode");
    Objects.requireNonNull(interceptorsGroupNode, "interceptorsGroupNode");
    Objects.requireNonNull(nodeChanged, "nodeChanged");
    return new Context() {
      @Override
      public Component ownerComponent() {
        return ownerComponent;
      }

      @Override
      public InterceptorStore interceptorStore() {
        return interceptorStore;
      }

      @Override
      public String interceptorsGroupLabel() {
        return Objects.toString(interceptorsGroupLabel, "Interceptors");
      }

      @Override
      public void ensureNode(TargetRef ref) {
        ensureNode.accept(ref);
      }

      @Override
      public void selectTarget(TargetRef ref) {
        selectTarget.accept(ref);
      }

      @Override
      public void removeTarget(TargetRef ref) {
        removeTarget.accept(ref);
      }

      @Override
      public DefaultMutableTreeNode leafNode(TargetRef ref) {
        return leafNode.apply(ref);
      }

      @Override
      public DefaultMutableTreeNode interceptorsGroupNode(String serverId) {
        return interceptorsGroupNode.apply(serverId);
      }

      @Override
      public void nodeChanged(DefaultMutableTreeNode node) {
        nodeChanged.accept(node);
      }
    };
  }

  private static final Logger log = LoggerFactory.getLogger(ServerTreeInterceptorActions.class);

  public void promptAndAddInterceptor(Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    InterceptorStore interceptorStore = in.interceptorStore();
    if (interceptorStore == null) return;
    String scopeServerId = normalizeScopeServerId(serverId);
    String baseServerId = baseServerId(scopeServerId);
    if (scopeServerId.isEmpty() || baseServerId.isEmpty()) return;

    Window owner = SwingUtilities.getWindowAncestor(in.ownerComponent());
    Object input =
        JOptionPane.showInputDialog(
            owner,
            "Interceptor name:",
            "Add Interceptor",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            "Interceptor");
    if (input == null) return;

    String requested = Objects.toString(input, "").trim();
    if (requested.isEmpty()) return;

    try {
      InterceptorDefinition definition =
          interceptorStore.createInterceptor(scopeServerId, requested);
      TargetRef ref = InterceptorScope.interceptorRef(scopeServerId, definition.id());
      if (ref == null) return;
      in.ensureNode(ref);
      refreshInterceptorNodeLabel(in, scopeServerId, definition.id());
      refreshInterceptorGroupCount(in, scopeServerId);
      in.selectTarget(ref);
    } catch (Exception ex) {
      log.warn("[ircafe] could not add interceptor for server {}", baseServerId, ex);
    }
  }

  public void promptRenameInterceptor(Context context, TargetRef ref, String currentLabel) {
    Context in = Objects.requireNonNull(context, "context");
    InterceptorStore interceptorStore = in.interceptorStore();
    if (interceptorStore == null || ref == null || !ref.isInterceptor()) return;

    String sid = normalizeScopeServerId(InterceptorScope.scopedServerIdForTarget(ref));
    String iid = normalizeInterceptorId(ref.interceptorId());
    if (sid.isEmpty() || iid.isEmpty()) return;

    Window owner = SwingUtilities.getWindowAncestor(in.ownerComponent());
    String before = Objects.toString(currentLabel, "").trim();
    if (before.isEmpty()) before = interceptorStore.interceptorName(sid, iid);
    if (before.isEmpty()) before = "Interceptor";

    Object input =
        JOptionPane.showInputDialog(
            owner,
            "Interceptor name:",
            "Rename Interceptor",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            before);
    if (input == null) return;

    String next = Objects.toString(input, "").trim();
    if (next.isEmpty()) return;

    try {
      if (interceptorStore.renameInterceptor(sid, iid, next)) {
        refreshInterceptorNodeLabel(in, sid, iid);
      }
    } catch (Exception ex) {
      log.warn("[ircafe] could not rename interceptor {} on {}", iid, baseServerId(sid), ex);
    }
  }

  public void setInterceptorEnabled(Context context, TargetRef ref, boolean enabled) {
    Context in = Objects.requireNonNull(context, "context");
    InterceptorStore interceptorStore = in.interceptorStore();
    if (interceptorStore == null || ref == null || !ref.isInterceptor()) return;

    String sid = normalizeScopeServerId(InterceptorScope.scopedServerIdForTarget(ref));
    String iid = normalizeInterceptorId(ref.interceptorId());
    if (sid.isEmpty() || iid.isEmpty()) return;

    try {
      if (interceptorStore.setInterceptorEnabled(sid, iid, enabled)) {
        refreshInterceptorNodeLabel(in, sid, iid);
      }
    } catch (Exception ex) {
      log.warn(
          "[ircafe] could not set interceptor enabled={} for {} on {}",
          enabled,
          iid,
          baseServerId(sid),
          ex);
    }
  }

  public void confirmDeleteInterceptor(Context context, TargetRef ref, String label) {
    Context in = Objects.requireNonNull(context, "context");
    InterceptorStore interceptorStore = in.interceptorStore();
    if (interceptorStore == null || ref == null || !ref.isInterceptor()) return;

    String sid = normalizeScopeServerId(InterceptorScope.scopedServerIdForTarget(ref));
    String iid = normalizeInterceptorId(ref.interceptorId());
    if (sid.isEmpty() || iid.isEmpty()) return;

    String pretty = Objects.toString(label, "").trim();
    if (pretty.isEmpty()) pretty = interceptorStore.interceptorName(sid, iid);
    if (pretty.isEmpty()) pretty = "Interceptor";

    Window owner = SwingUtilities.getWindowAncestor(in.ownerComponent());
    int choice =
        JOptionPane.showConfirmDialog(
            owner,
            "Delete interceptor \"" + pretty + "\"?",
            "Delete Interceptor",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (choice != JOptionPane.YES_OPTION) return;

    try {
      if (interceptorStore.removeInterceptor(sid, iid)) {
        in.selectTarget(new TargetRef(baseServerId(sid), "status"));
        in.removeTarget(ref);
        refreshInterceptorGroupCount(in, sid);
      }
    } catch (Exception ex) {
      log.warn("[ircafe] could not delete interceptor {} on {}", iid, baseServerId(sid), ex);
    }
  }

  public void refreshInterceptorNodeLabel(Context context, String serverId, String interceptorId) {
    Context in = Objects.requireNonNull(context, "context");
    InterceptorStore interceptorStore = in.interceptorStore();
    if (interceptorStore == null) return;

    String sid = normalizeScopeServerId(serverId);
    String iid = normalizeInterceptorId(interceptorId);
    if (sid.isEmpty() || iid.isEmpty()) return;

    TargetRef ref = InterceptorScope.interceptorRef(sid, iid);
    if (ref == null) return;
    DefaultMutableTreeNode node = in.leafNode(ref);
    if (node == null) return;

    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData previous) || previous.ref == null) {
      return;
    }

    String nextLabel = Objects.toString(interceptorStore.interceptorName(sid, iid), "").trim();
    if (nextLabel.isEmpty()) nextLabel = "Interceptor";
    int hitCount = Math.max(0, interceptorStore.hitCount(sid, iid));

    ServerTreeNodeData next = new ServerTreeNodeData(previous.ref, nextLabel);
    next.unread = hitCount;
    next.highlightUnread = 0;
    next.detached = previous.detached;
    next.detachedWarning = previous.detachedWarning;
    next.copyTypingFrom(previous);

    if (!Objects.equals(previous.label, nextLabel)
        || previous.unread != next.unread
        || previous.highlightUnread != next.highlightUnread) {
      node.setUserObject(next);
    }
    in.nodeChanged(node);
  }

  public void refreshInterceptorGroupCount(Context context, String serverId) {
    Context in = Objects.requireNonNull(context, "context");
    InterceptorStore interceptorStore = in.interceptorStore();
    if (interceptorStore == null) return;

    String sid = normalizeScopeServerId(serverId);
    if (sid.isEmpty()) return;

    TargetRef groupRef = InterceptorScope.interceptorsGroupRef(sid);
    DefaultMutableTreeNode node = groupRef == null ? null : in.leafNode(groupRef);
    if (node == null) {
      node = in.interceptorsGroupNode(baseServerId(sid));
    }
    if (node == null) return;

    Object userObject = node.getUserObject();
    ServerTreeNodeData data;
    if (userObject instanceof ServerTreeNodeData existing) {
      data = existing;
    } else {
      data = new ServerTreeNodeData(groupRef, in.interceptorsGroupLabel());
      node.setUserObject(data);
    }

    if (data.unread == 0 && data.highlightUnread == 0) return;
    data.unread = 0;
    data.highlightUnread = 0;
    in.nodeChanged(node);
  }

  private static String normalizeScopeServerId(String serverId) {
    return InterceptorScope.normalizeScopeServerId(serverId);
  }

  private static String baseServerId(String scopeServerId) {
    return InterceptorScope.baseServerId(scopeServerId);
  }

  private static String normalizeInterceptorId(String interceptorId) {
    return Objects.toString(interceptorId, "").trim();
  }
}
