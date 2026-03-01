package cafe.woden.ircclient.ui.servertree.actions;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import java.awt.Component;
import java.awt.Window;
import java.util.Objects;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Encapsulates interceptor CRUD actions and tree-label refresh behavior. */
public final class ServerTreeInterceptorActions {

  public interface Context {
    void ensureNode(TargetRef ref);

    void selectTarget(TargetRef ref);

    void removeTarget(TargetRef ref);

    DefaultMutableTreeNode leafNode(TargetRef ref);

    DefaultMutableTreeNode interceptorsGroupNode(String serverId);

    void nodeChanged(DefaultMutableTreeNode node);
  }

  private static final Logger log = LoggerFactory.getLogger(ServerTreeInterceptorActions.class);

  private final Component ownerComponent;
  private final InterceptorStore interceptorStore;
  private final String interceptorsGroupLabel;
  private final Context context;

  public ServerTreeInterceptorActions(
      Component ownerComponent,
      InterceptorStore interceptorStore,
      String interceptorsGroupLabel,
      Context context) {
    this.ownerComponent = ownerComponent;
    this.interceptorStore = interceptorStore;
    this.interceptorsGroupLabel = Objects.toString(interceptorsGroupLabel, "Interceptors");
    this.context = Objects.requireNonNull(context, "context");
  }

  public void promptAndAddInterceptor(String serverId) {
    if (interceptorStore == null) return;
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    Window owner = SwingUtilities.getWindowAncestor(ownerComponent);
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
      InterceptorDefinition definition = interceptorStore.createInterceptor(sid, requested);
      TargetRef ref = TargetRef.interceptor(sid, definition.id());
      context.ensureNode(ref);
      refreshInterceptorNodeLabel(sid, definition.id());
      refreshInterceptorGroupCount(sid);
      context.selectTarget(ref);
    } catch (Exception ex) {
      log.warn("[ircafe] could not add interceptor for server {}", sid, ex);
    }
  }

  public void promptRenameInterceptor(TargetRef ref, String currentLabel) {
    if (interceptorStore == null || ref == null || !ref.isInterceptor()) return;

    String sid = normalizeServerId(ref.serverId());
    String iid = normalizeInterceptorId(ref.interceptorId());
    if (sid.isEmpty() || iid.isEmpty()) return;

    Window owner = SwingUtilities.getWindowAncestor(ownerComponent);
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
        refreshInterceptorNodeLabel(sid, iid);
      }
    } catch (Exception ex) {
      log.warn("[ircafe] could not rename interceptor {} on {}", iid, sid, ex);
    }
  }

  public void setInterceptorEnabled(TargetRef ref, boolean enabled) {
    if (interceptorStore == null || ref == null || !ref.isInterceptor()) return;

    String sid = normalizeServerId(ref.serverId());
    String iid = normalizeInterceptorId(ref.interceptorId());
    if (sid.isEmpty() || iid.isEmpty()) return;

    try {
      if (interceptorStore.setInterceptorEnabled(sid, iid, enabled)) {
        refreshInterceptorNodeLabel(sid, iid);
      }
    } catch (Exception ex) {
      log.warn("[ircafe] could not set interceptor enabled={} for {} on {}", enabled, iid, sid, ex);
    }
  }

  public void confirmDeleteInterceptor(TargetRef ref, String label) {
    if (interceptorStore == null || ref == null || !ref.isInterceptor()) return;

    String sid = normalizeServerId(ref.serverId());
    String iid = normalizeInterceptorId(ref.interceptorId());
    if (sid.isEmpty() || iid.isEmpty()) return;

    String pretty = Objects.toString(label, "").trim();
    if (pretty.isEmpty()) pretty = interceptorStore.interceptorName(sid, iid);
    if (pretty.isEmpty()) pretty = "Interceptor";

    Window owner = SwingUtilities.getWindowAncestor(ownerComponent);
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
        context.selectTarget(new TargetRef(sid, "status"));
        context.removeTarget(ref);
        refreshInterceptorGroupCount(sid);
      }
    } catch (Exception ex) {
      log.warn("[ircafe] could not delete interceptor {} on {}", iid, sid, ex);
    }
  }

  public void refreshInterceptorNodeLabel(String serverId, String interceptorId) {
    if (interceptorStore == null) return;

    String sid = normalizeServerId(serverId);
    String iid = normalizeInterceptorId(interceptorId);
    if (sid.isEmpty() || iid.isEmpty()) return;

    TargetRef ref = TargetRef.interceptor(sid, iid);
    DefaultMutableTreeNode node = context.leafNode(ref);
    if (node == null) return;

    Object userObject = node.getUserObject();
    if (!(userObject instanceof ServerTreeNodeData previous) || previous.ref == null) {
      return;
    }

    String nextLabel = Objects.toString(interceptorStore.interceptorName(sid, iid), "").trim();
    if (nextLabel.isEmpty()) nextLabel = "Interceptor";

    ServerTreeNodeData next = new ServerTreeNodeData(previous.ref, nextLabel);
    next.unread = previous.unread;
    next.highlightUnread = previous.highlightUnread;
    next.detached = previous.detached;
    next.detachedWarning = previous.detachedWarning;
    next.copyTypingFrom(previous);

    if (!Objects.equals(previous.label, nextLabel)) {
      node.setUserObject(next);
    }
    context.nodeChanged(node);
  }

  public void refreshInterceptorGroupCount(String serverId) {
    if (interceptorStore == null) return;

    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return;

    DefaultMutableTreeNode node = context.interceptorsGroupNode(sid);
    if (node == null) return;

    Object userObject = node.getUserObject();
    ServerTreeNodeData data;
    if (userObject instanceof ServerTreeNodeData existing) {
      data = existing;
    } else {
      data = new ServerTreeNodeData(null, interceptorsGroupLabel);
      node.setUserObject(data);
    }

    int total = Math.max(0, interceptorStore.totalHitCount(sid));
    if (data.unread == total && data.highlightUnread == 0) return;

    data.unread = total;
    data.highlightUnread = 0;
    context.nodeChanged(node);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static String normalizeInterceptorId(String interceptorId) {
    return Objects.toString(interceptorId, "").trim();
  }
}
