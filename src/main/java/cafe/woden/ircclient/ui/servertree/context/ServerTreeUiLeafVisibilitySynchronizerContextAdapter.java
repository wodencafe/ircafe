package cafe.woden.ircclient.ui.servertree.context;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.servertree.coordinator.ServerTreeUiLeafVisibilitySynchronizer;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adapter for {@link ServerTreeUiLeafVisibilitySynchronizer.Context}. */
public final class ServerTreeUiLeafVisibilitySynchronizerContextAdapter
    implements ServerTreeUiLeafVisibilitySynchronizer.Context {

  private final Supplier<TargetRef> selectedTargetRef;
  private final Supplier<DefaultMutableTreeNode> selectedTreeNode;
  private final Predicate<DefaultMutableTreeNode> isMonitorGroupNode;
  private final Predicate<DefaultMutableTreeNode> isInterceptorsGroupNode;
  private final Function<DefaultMutableTreeNode, String> owningServerIdForNode;
  private final Supplier<List<String>> serverIdsSnapshot;
  private final Consumer<String> syncServerUiLeafVisibility;
  private final Predicate<String> statusVisible;
  private final Predicate<String> notificationsVisible;
  private final Predicate<String> logViewerVisible;
  private final Predicate<String> monitorVisible;
  private final Predicate<String> interceptorsVisible;
  private final BooleanSupplier showDccTransfersNodes;
  private final Consumer<String> selectBestFallbackForServer;

  public ServerTreeUiLeafVisibilitySynchronizerContextAdapter(
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
    this.selectedTargetRef = Objects.requireNonNull(selectedTargetRef, "selectedTargetRef");
    this.selectedTreeNode = Objects.requireNonNull(selectedTreeNode, "selectedTreeNode");
    this.isMonitorGroupNode = Objects.requireNonNull(isMonitorGroupNode, "isMonitorGroupNode");
    this.isInterceptorsGroupNode =
        Objects.requireNonNull(isInterceptorsGroupNode, "isInterceptorsGroupNode");
    this.owningServerIdForNode =
        Objects.requireNonNull(owningServerIdForNode, "owningServerIdForNode");
    this.serverIdsSnapshot = Objects.requireNonNull(serverIdsSnapshot, "serverIdsSnapshot");
    this.syncServerUiLeafVisibility =
        Objects.requireNonNull(syncServerUiLeafVisibility, "syncServerUiLeafVisibility");
    this.statusVisible = Objects.requireNonNull(statusVisible, "statusVisible");
    this.notificationsVisible =
        Objects.requireNonNull(notificationsVisible, "notificationsVisible");
    this.logViewerVisible = Objects.requireNonNull(logViewerVisible, "logViewerVisible");
    this.monitorVisible = Objects.requireNonNull(monitorVisible, "monitorVisible");
    this.interceptorsVisible = Objects.requireNonNull(interceptorsVisible, "interceptorsVisible");
    this.showDccTransfersNodes =
        Objects.requireNonNull(showDccTransfersNodes, "showDccTransfersNodes");
    this.selectBestFallbackForServer =
        Objects.requireNonNull(selectBestFallbackForServer, "selectBestFallbackForServer");
  }

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
}
