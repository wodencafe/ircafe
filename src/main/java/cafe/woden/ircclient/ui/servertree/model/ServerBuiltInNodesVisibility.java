package cafe.woden.ircclient.ui.servertree.model;

import cafe.woden.ircclient.config.RuntimeConfigStore;

public record ServerBuiltInNodesVisibility(
    boolean server,
    boolean notifications,
    boolean logViewer,
    boolean monitor,
    boolean interceptors) {

  public static ServerBuiltInNodesVisibility defaults() {
    return new ServerBuiltInNodesVisibility(true, true, true, true, true);
  }

  public ServerBuiltInNodesVisibility withServer(boolean visible) {
    return new ServerBuiltInNodesVisibility(
        visible, notifications, logViewer, monitor, interceptors);
  }

  public ServerBuiltInNodesVisibility withNotifications(boolean visible) {
    return new ServerBuiltInNodesVisibility(server, visible, logViewer, monitor, interceptors);
  }

  public ServerBuiltInNodesVisibility withLogViewer(boolean visible) {
    return new ServerBuiltInNodesVisibility(server, notifications, visible, monitor, interceptors);
  }

  public ServerBuiltInNodesVisibility withMonitor(boolean visible) {
    return new ServerBuiltInNodesVisibility(
        server, notifications, logViewer, visible, interceptors);
  }

  public ServerBuiltInNodesVisibility withInterceptors(boolean visible) {
    return new ServerBuiltInNodesVisibility(server, notifications, logViewer, monitor, visible);
  }

  public RuntimeConfigStore.ServerTreeBuiltInNodesVisibility toRuntimeVisibility() {
    return new RuntimeConfigStore.ServerTreeBuiltInNodesVisibility(
        server, notifications, logViewer, monitor, interceptors);
  }
}
