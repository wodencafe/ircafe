package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;

/** Orchestrates built-in leaf visibility sync and selected-target fallback behavior. */
public final class ServerTreeUiLeafVisibilitySynchronizer {

  public interface Context {
    TargetRef selectedTargetRef();

    DefaultMutableTreeNode selectedTreeNode();

    boolean isMonitorGroupNode(DefaultMutableTreeNode node);

    boolean isInterceptorsGroupNode(DefaultMutableTreeNode node);

    String owningServerIdForNode(DefaultMutableTreeNode node);

    List<String> serverIdsSnapshot();

    void syncServerUiLeafVisibility(String serverId);

    boolean statusVisible(String serverId);

    boolean notificationsVisible(String serverId);

    boolean logViewerVisible(String serverId);

    boolean monitorVisible(String serverId);

    boolean interceptorsVisible(String serverId);

    boolean showDccTransfersNodes();

    void selectBestFallbackForServer(String serverId);
  }

  public static Context context(
      Supplier<TargetRef> selectedTargetRef,
      Supplier<DefaultMutableTreeNode> selectedTreeNode,
      Predicate<DefaultMutableTreeNode> isMonitorGroupNode,
      Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode,
      Function<DefaultMutableTreeNode, String> owningServerIdForNode,
      Supplier<List<String>> serverIdsSnapshot,
      Consumer<String> syncServerUiLeafVisibility,
      Predicate<String> statusVisible,
      Predicate<String> notificationsVisible,
      Predicate<String> logViewerVisible,
      Predicate<String> monitorVisible,
      Predicate<String> interceptorsVisible,
      BooleanSupplier showDccTransfersNodes,
      Consumer<String> selectBestFallbackForServer) {
    Objects.requireNonNull(selectedTargetRef, "selectedTargetRef");
    Objects.requireNonNull(selectedTreeNode, "selectedTreeNode");
    Objects.requireNonNull(isMonitorGroupNode, "isMonitorGroupNode");
    Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    Objects.requireNonNull(owningServerIdForNode, "owningServerIdForNode");
    Objects.requireNonNull(serverIdsSnapshot, "serverIdsSnapshot");
    Objects.requireNonNull(syncServerUiLeafVisibility, "syncServerUiLeafVisibility");
    Objects.requireNonNull(statusVisible, "statusVisible");
    Objects.requireNonNull(notificationsVisible, "notificationsVisible");
    Objects.requireNonNull(logViewerVisible, "logViewerVisible");
    Objects.requireNonNull(monitorVisible, "monitorVisible");
    Objects.requireNonNull(interceptorsVisible, "interceptorsVisible");
    Objects.requireNonNull(showDccTransfersNodes, "showDccTransfersNodes");
    Objects.requireNonNull(selectBestFallbackForServer, "selectBestFallbackForServer");
    return new Context() {
      @Override
      public TargetRef selectedTargetRef() {
        return selectedTargetRef.get();
      }

      @Override
      public DefaultMutableTreeNode selectedTreeNode() {
        return selectedTreeNode.get();
      }

      @Override
      public boolean isMonitorGroupNode(DefaultMutableTreeNode node) {
        return isMonitorGroupNode.test(node);
      }

      @Override
      public boolean isInterceptorsGroupNode(DefaultMutableTreeNode node) {
        return isInterceptorsGroupNode.test(node);
      }

      @Override
      public String owningServerIdForNode(DefaultMutableTreeNode node) {
        return owningServerIdForNode.apply(node);
      }

      @Override
      public List<String> serverIdsSnapshot() {
        return serverIdsSnapshot.get();
      }

      @Override
      public void syncServerUiLeafVisibility(String serverId) {
        syncServerUiLeafVisibility.accept(serverId);
      }

      @Override
      public boolean statusVisible(String serverId) {
        return statusVisible.test(serverId);
      }

      @Override
      public boolean notificationsVisible(String serverId) {
        return notificationsVisible.test(serverId);
      }

      @Override
      public boolean logViewerVisible(String serverId) {
        return logViewerVisible.test(serverId);
      }

      @Override
      public boolean monitorVisible(String serverId) {
        return monitorVisible.test(serverId);
      }

      @Override
      public boolean interceptorsVisible(String serverId) {
        return interceptorsVisible.test(serverId);
      }

      @Override
      public boolean showDccTransfersNodes() {
        return showDccTransfersNodes.getAsBoolean();
      }

      @Override
      public void selectBestFallbackForServer(String serverId) {
        selectBestFallbackForServer.accept(serverId);
      }
    };
  }

  private final Context context;

  public ServerTreeUiLeafVisibilitySynchronizer(Context context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  public void syncUiLeafVisibility() {
    TargetRef selected = context.selectedTargetRef();
    DefaultMutableTreeNode selectedNode = context.selectedTreeNode();
    boolean selectedMonitorGroup = selectedNode != null && context.isMonitorGroupNode(selectedNode);
    boolean selectedInterceptorsGroup =
        selectedNode != null && context.isInterceptorsGroupNode(selectedNode);
    String selectedGroupServerId =
        (selectedMonitorGroup || selectedInterceptorsGroup)
            ? normalize(context.owningServerIdForNode(selectedNode))
            : "";

    for (String serverId : context.serverIdsSnapshot()) {
      context.syncServerUiLeafVisibility(serverId);
    }

    if (selected != null) {
      String sid = normalize(selected.serverId());
      if (sid.isEmpty()) return;
      if (selected.isStatus() && !context.statusVisible(sid)) {
        context.selectBestFallbackForServer(sid);
      } else if (selected.isNotifications() && !context.notificationsVisible(sid)) {
        context.selectBestFallbackForServer(sid);
      } else if (selected.isLogViewer() && !context.logViewerVisible(sid)) {
        context.selectBestFallbackForServer(sid);
      } else if (selected.isDccTransfers() && !context.showDccTransfersNodes()) {
        context.selectBestFallbackForServer(sid);
      } else if (selected.isMonitorGroup() && !context.monitorVisible(sid)) {
        context.selectBestFallbackForServer(sid);
      } else if ((selected.isInterceptorsGroup() || selected.isInterceptor())
          && !context.interceptorsVisible(sid)) {
        context.selectBestFallbackForServer(sid);
      }
      return;
    }

    if (selectedMonitorGroup && !selectedGroupServerId.isBlank()) {
      if (!context.monitorVisible(selectedGroupServerId)) {
        context.selectBestFallbackForServer(selectedGroupServerId);
      }
      return;
    }

    if (selectedInterceptorsGroup && !selectedGroupServerId.isBlank()) {
      if (!context.interceptorsVisible(selectedGroupServerId)) {
        context.selectBestFallbackForServer(selectedGroupServerId);
      }
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
