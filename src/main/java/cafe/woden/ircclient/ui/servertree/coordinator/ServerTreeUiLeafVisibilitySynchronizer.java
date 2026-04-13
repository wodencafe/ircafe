package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.model.TargetRef;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;
import org.springframework.stereotype.Component;

/** Orchestrates built-in leaf visibility sync and selected-target fallback behavior. */
@Component
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

  public void syncUiLeafVisibility(Context context) {
    Context in = Objects.requireNonNull(context, "context");
    TargetRef selected = in.selectedTargetRef();
    DefaultMutableTreeNode selectedNode = in.selectedTreeNode();
    boolean selectedMonitorGroup = selectedNode != null && in.isMonitorGroupNode(selectedNode);
    boolean selectedInterceptorsGroup =
        selectedNode != null && in.isInterceptorsGroupNode(selectedNode);
    String selectedGroupServerId =
        (selectedMonitorGroup || selectedInterceptorsGroup)
            ? normalize(in.owningServerIdForNode(selectedNode))
            : "";

    for (String serverId : in.serverIdsSnapshot()) {
      in.syncServerUiLeafVisibility(serverId);
    }

    if (selected != null) {
      String sid = normalize(selected.serverId());
      if (sid.isEmpty()) return;
      if (selected.isStatus() && !in.statusVisible(sid)) {
        in.selectBestFallbackForServer(sid);
      } else if (selected.isNotifications() && !in.notificationsVisible(sid)) {
        in.selectBestFallbackForServer(sid);
      } else if (selected.isLogViewer() && !in.logViewerVisible(sid)) {
        in.selectBestFallbackForServer(sid);
      } else if (selected.isDccTransfers() && !in.showDccTransfersNodes()) {
        in.selectBestFallbackForServer(sid);
      } else if (selected.isMonitorGroup() && !in.monitorVisible(sid)) {
        in.selectBestFallbackForServer(sid);
      } else if ((selected.isInterceptorsGroup() || selected.isInterceptor())
          && !in.interceptorsVisible(sid)) {
        in.selectBestFallbackForServer(sid);
      }
      return;
    }

    if (selectedMonitorGroup && !selectedGroupServerId.isBlank()) {
      if (!in.monitorVisible(selectedGroupServerId)) {
        in.selectBestFallbackForServer(selectedGroupServerId);
      }
      return;
    }

    if (selectedInterceptorsGroup && !selectedGroupServerId.isBlank()) {
      if (!in.interceptorsVisible(selectedGroupServerId)) {
        in.selectBestFallbackForServer(selectedGroupServerId);
      }
    }
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
