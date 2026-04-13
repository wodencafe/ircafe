package cafe.woden.ircclient.ui.servertree.state;

import cafe.woden.ircclient.ui.servertree.model.ServerBuiltInNodesVisibility;
import java.util.Objects;

/** Encapsulates node-visibility API behavior used by {@code ServerTreeDockable}. */
public final class ServerTreeNodeVisibilityApi {

  @FunctionalInterface
  public interface BooleanPropertyChangePublisher {
    void firePropertyChange(String propertyName, boolean oldValue, boolean newValue);
  }

  private final ServerTreeBuiltInVisibilitySettings builtInVisibilitySettings;
  private final ServerTreeBuiltInVisibilitySettings.Context builtInVisibilitySettingsContext;
  private final Runnable syncUiLeafVisibility;
  private final Runnable syncApplicationRootVisibility;
  private final BooleanPropertyChangePublisher propertyChangePublisher;
  private final String propChannelListNodesVisible;
  private final String propDccTransfersNodesVisible;
  private final String propLogViewerNodesVisible;
  private final String propNotificationsNodesVisible;
  private final String propMonitorNodesVisible;
  private final String propInterceptorsNodesVisible;
  private final String propApplicationRootVisible;

  private boolean showChannelListNodes = true;
  private boolean showDccTransfersNodes = false;
  private boolean showApplicationRoot = true;

  public ServerTreeNodeVisibilityApi(
      ServerTreeBuiltInVisibilitySettings builtInVisibilitySettings,
      ServerTreeBuiltInVisibilitySettings.Context builtInVisibilitySettingsContext,
      Runnable syncUiLeafVisibility,
      Runnable syncApplicationRootVisibility,
      BooleanPropertyChangePublisher propertyChangePublisher,
      String propChannelListNodesVisible,
      String propDccTransfersNodesVisible,
      String propLogViewerNodesVisible,
      String propNotificationsNodesVisible,
      String propMonitorNodesVisible,
      String propInterceptorsNodesVisible,
      String propApplicationRootVisible) {
    this.builtInVisibilitySettings =
        Objects.requireNonNull(builtInVisibilitySettings, "builtInVisibilitySettings");
    this.builtInVisibilitySettingsContext =
        Objects.requireNonNull(
            builtInVisibilitySettingsContext, "builtInVisibilitySettingsContext");
    this.syncUiLeafVisibility =
        Objects.requireNonNull(syncUiLeafVisibility, "syncUiLeafVisibility");
    this.syncApplicationRootVisibility =
        Objects.requireNonNull(syncApplicationRootVisibility, "syncApplicationRootVisibility");
    this.propertyChangePublisher =
        Objects.requireNonNull(propertyChangePublisher, "propertyChangePublisher");
    this.propChannelListNodesVisible =
        Objects.requireNonNull(propChannelListNodesVisible, "propChannelListNodesVisible");
    this.propDccTransfersNodesVisible =
        Objects.requireNonNull(propDccTransfersNodesVisible, "propDccTransfersNodesVisible");
    this.propLogViewerNodesVisible =
        Objects.requireNonNull(propLogViewerNodesVisible, "propLogViewerNodesVisible");
    this.propNotificationsNodesVisible =
        Objects.requireNonNull(propNotificationsNodesVisible, "propNotificationsNodesVisible");
    this.propMonitorNodesVisible =
        Objects.requireNonNull(propMonitorNodesVisible, "propMonitorNodesVisible");
    this.propInterceptorsNodesVisible =
        Objects.requireNonNull(propInterceptorsNodesVisible, "propInterceptorsNodesVisible");
    this.propApplicationRootVisible =
        Objects.requireNonNull(propApplicationRootVisible, "propApplicationRootVisible");
  }

  public boolean isChannelListNodesVisible() {
    return true;
  }

  public boolean isDccTransfersNodesVisible() {
    return showDccTransfersNodes;
  }

  public boolean isServerNodesVisible() {
    return builtInVisibilitySettings.defaultVisibility(
        builtInVisibilitySettingsContext, ServerBuiltInNodesVisibility::server);
  }

  public boolean isLogViewerNodesVisible() {
    return builtInVisibilitySettings.defaultVisibility(
        builtInVisibilitySettingsContext, ServerBuiltInNodesVisibility::logViewer);
  }

  public boolean isNotificationsNodesVisible() {
    return builtInVisibilitySettings.defaultVisibility(
        builtInVisibilitySettingsContext, ServerBuiltInNodesVisibility::notifications);
  }

  public boolean isMonitorNodesVisible() {
    return builtInVisibilitySettings.defaultVisibility(
        builtInVisibilitySettingsContext, ServerBuiltInNodesVisibility::monitor);
  }

  public boolean isInterceptorsNodesVisible() {
    return builtInVisibilitySettings.defaultVisibility(
        builtInVisibilitySettingsContext, ServerBuiltInNodesVisibility::interceptors);
  }

  public boolean isServerNodeVisibleForServer(String serverId) {
    return builtInVisibilitySettings.serverVisibility(
        builtInVisibilitySettingsContext, serverId, ServerBuiltInNodesVisibility::server);
  }

  public boolean isLogViewerNodeVisibleForServer(String serverId) {
    return builtInVisibilitySettings.serverVisibility(
        builtInVisibilitySettingsContext, serverId, ServerBuiltInNodesVisibility::logViewer);
  }

  public boolean isNotificationsNodeVisibleForServer(String serverId) {
    return builtInVisibilitySettings.serverVisibility(
        builtInVisibilitySettingsContext, serverId, ServerBuiltInNodesVisibility::notifications);
  }

  public boolean isMonitorNodeVisibleForServer(String serverId) {
    return builtInVisibilitySettings.serverVisibility(
        builtInVisibilitySettingsContext, serverId, ServerBuiltInNodesVisibility::monitor);
  }

  public boolean isInterceptorsNodeVisibleForServer(String serverId) {
    return builtInVisibilitySettings.serverVisibility(
        builtInVisibilitySettingsContext, serverId, ServerBuiltInNodesVisibility::interceptors);
  }

  public void setServerNodeVisibleForServer(String serverId, boolean visible) {
    builtInVisibilitySettings.setVisibilityForServer(
        builtInVisibilitySettingsContext,
        serverId,
        visible,
        ServerBuiltInNodesVisibility::withServer);
  }

  public void setLogViewerNodeVisibleForServer(String serverId, boolean visible) {
    builtInVisibilitySettings.setVisibilityForServer(
        builtInVisibilitySettingsContext,
        serverId,
        visible,
        ServerBuiltInNodesVisibility::withLogViewer);
  }

  public void setNotificationsNodeVisibleForServer(String serverId, boolean visible) {
    builtInVisibilitySettings.setVisibilityForServer(
        builtInVisibilitySettingsContext,
        serverId,
        visible,
        ServerBuiltInNodesVisibility::withNotifications);
  }

  public void setMonitorNodeVisibleForServer(String serverId, boolean visible) {
    builtInVisibilitySettings.setVisibilityForServer(
        builtInVisibilitySettingsContext,
        serverId,
        visible,
        ServerBuiltInNodesVisibility::withMonitor);
  }

  public void setInterceptorsNodeVisibleForServer(String serverId, boolean visible) {
    builtInVisibilitySettings.setVisibilityForServer(
        builtInVisibilitySettingsContext,
        serverId,
        visible,
        ServerBuiltInNodesVisibility::withInterceptors);
  }

  public boolean isApplicationRootVisible() {
    return showApplicationRoot;
  }

  public void setChannelListNodesVisible(boolean visible) {
    // Channel List is always visible for each server.
    if (!visible) return;
    boolean old = showChannelListNodes;
    showChannelListNodes = true;
    if (!old) {
      syncUiLeafVisibility.run();
      propertyChangePublisher.firePropertyChange(propChannelListNodesVisible, false, true);
    }
  }

  public void setDccTransfersNodesVisible(boolean visible) {
    boolean old = showDccTransfersNodes;
    boolean next = visible;
    if (old == next) return;
    showDccTransfersNodes = next;
    syncUiLeafVisibility.run();
    propertyChangePublisher.firePropertyChange(propDccTransfersNodesVisible, old, next);
  }

  public void setServerNodesVisible(boolean visible) {
    builtInVisibilitySettings.setDefaultVisibility(
        builtInVisibilitySettingsContext,
        visible,
        ServerBuiltInNodesVisibility::server,
        ServerBuiltInNodesVisibility::withServer,
        null);
  }

  public void setLogViewerNodesVisible(boolean visible) {
    builtInVisibilitySettings.setDefaultVisibility(
        builtInVisibilitySettingsContext,
        visible,
        ServerBuiltInNodesVisibility::logViewer,
        ServerBuiltInNodesVisibility::withLogViewer,
        propLogViewerNodesVisible);
  }

  public void setNotificationsNodesVisible(boolean visible) {
    builtInVisibilitySettings.setDefaultVisibility(
        builtInVisibilitySettingsContext,
        visible,
        ServerBuiltInNodesVisibility::notifications,
        ServerBuiltInNodesVisibility::withNotifications,
        propNotificationsNodesVisible);
  }

  public void setMonitorNodesVisible(boolean visible) {
    builtInVisibilitySettings.setDefaultVisibility(
        builtInVisibilitySettingsContext,
        visible,
        ServerBuiltInNodesVisibility::monitor,
        ServerBuiltInNodesVisibility::withMonitor,
        propMonitorNodesVisible);
  }

  public void setInterceptorsNodesVisible(boolean visible) {
    builtInVisibilitySettings.setDefaultVisibility(
        builtInVisibilitySettingsContext,
        visible,
        ServerBuiltInNodesVisibility::interceptors,
        ServerBuiltInNodesVisibility::withInterceptors,
        propInterceptorsNodesVisible);
  }

  public void setApplicationRootVisible(boolean visible) {
    boolean old = showApplicationRoot;
    boolean next = visible;
    if (old == next) return;
    showApplicationRoot = next;
    syncApplicationRootVisibility.run();
    propertyChangePublisher.firePropertyChange(propApplicationRootVisible, old, next);
  }
}
