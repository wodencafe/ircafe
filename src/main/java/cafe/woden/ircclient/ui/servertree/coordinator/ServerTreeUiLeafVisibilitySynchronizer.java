package cafe.woden.ircclient.ui.servertree.coordinator;

import cafe.woden.ircclient.app.api.TargetRef;
import java.util.List;
import java.util.Objects;
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
