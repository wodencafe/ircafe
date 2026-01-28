package cafe.woden.ircclient.app;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Coordinates connection lifecycle (connect/disconnect/reconnect state, multi-server UI).
 *
 */
@Component
@Lazy
public class ConnectionCoordinator {
  public enum ConnectivityChange {
    NONE,
    CHANGED
  }

  private final IrcClientService irc;
  private final UiPort ui;
  private final ServerRegistry serverRegistry;
  private final RuntimeConfigStore runtimeConfig;
  private final CompositeDisposable disposables = new CompositeDisposable();

  // Track which servers are currently connected so the UI controls behave sensibly in multi-server mode.
  private final Set<String> connectedServers = new java.util.HashSet<>();

  // Track which servers are configured so we can react to runtime add/edit/remove.
  private final Set<String> configuredServers = new java.util.HashSet<>();

  public ConnectionCoordinator(
      IrcClientService irc,
      UiPort ui,
      ServerRegistry serverRegistry,
      RuntimeConfigStore runtimeConfig
  ) {
    this.irc = irc;
    this.ui = ui;
    this.serverRegistry = serverRegistry;
    this.runtimeConfig = runtimeConfig;

    // Initialize with the currently configured server ids.
    configuredServers.addAll(serverRegistry.serverIds());
  }

  @PreDestroy
  void shutdown() {
    disposables.dispose();
  }

  public boolean isConnected(String serverId) {
    return connectedServers.contains(serverId);
  }

  public int connectedCount() {
    return connectedServers.size();
  }

  public Set<String> connectedServerIdsSnapshot() {
    return Set.copyOf(connectedServers);
  }

  /**
   * Connect to all configured servers.
   *
   */
  public void connectAll() {
    Set<String> serverIds = serverRegistry.serverIds();
    if (serverIds.isEmpty()) {
      ui.setConnectionStatusText("No servers configured");
      return;
    }

    ui.setConnectionStatusText("Connecting…");
    // Disable Connect while we're attempting connections; allow Disconnect.
    ui.setConnectedUi(true);

    for (String sid : serverIds) {
      TargetRef status = new TargetRef(sid, "status");
      ui.ensureTargetExists(status);
      ui.appendStatus(status, "(conn)", "Connecting…");

      disposables.add(
          irc.connect(sid).subscribe(
              () -> {},
              err -> {
                ui.appendError(status, "(conn-error)", String.valueOf(err));
                ui.appendStatus(status, "(conn)", "Connect failed");
              }
          )
      );
    }
  }

  public void connectOne(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    if (!serverRegistry.containsId(sid)) {
      ui.appendError(new TargetRef("default", "status"), "(conn)", "Unknown server: " + sid);
      return;
    }

    TargetRef status = new TargetRef(sid, "status");
    ui.ensureTargetExists(status);
    ui.appendStatus(status, "(conn)", "Connecting…");
    ui.setConnectionStatusText("Connecting…");

    disposables.add(
        irc.connect(sid).subscribe(
            () -> {},
            err -> {
              ui.appendError(status, "(conn-error)", String.valueOf(err));
              ui.appendStatus(status, "(conn)", "Connect failed");
            }
        )
    );
  }

  /** Disconnect from all configured servers. */
  public void disconnectAll() {
    Set<String> serverIds = serverRegistry.serverIds();
    ui.setConnectionStatusText("Disconnecting…");

    // While disconnecting, keep Connect disabled to avoid racing actions.
    ui.setConnectedUi(true);

    for (String sid : serverIds) {
      TargetRef status = new TargetRef(sid, "status");
      disposables.add(
          irc.disconnect(sid).subscribe(
              () -> {},
              err -> ui.appendError(status, "(disc-error)", String.valueOf(err))
          )
      );
    }
  }

  public void disconnectOne(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    if (!serverRegistry.containsId(sid)) {
      ui.appendError(new TargetRef("default", "status"), "(disc)", "Unknown server: " + sid);
      return;
    }

    TargetRef status = new TargetRef(sid, "status");
    ui.appendStatus(status, "(conn)", "Disconnecting…");
    ui.setConnectionStatusText("Disconnecting…");

    disposables.add(
        irc.disconnect(sid).subscribe(
            () -> {},
            err -> ui.appendError(status, "(disc-error)", String.valueOf(err))
        )
    );
  }

  /**
   * React to runtime server list edits.
   *
   */
  public void onServersUpdated(List<IrcProperties.Server> latest, TargetRef activeTarget) {
    // Compute current ids
    Set<String> current = new java.util.HashSet<>();
    if (latest != null) {
      for (var s : latest) {
        if (s == null) continue;
        String id = Objects.toString(s.id(), "").trim();
        if (!id.isEmpty()) current.add(id);
      }
    }

    // Removed
    Set<String> removed = new java.util.HashSet<>(configuredServers);
    removed.removeAll(current);

    // Added
    Set<String> added = new java.util.HashSet<>(current);
    added.removeAll(configuredServers);

    configuredServers.clear();
    configuredServers.addAll(current);

    // If servers were removed, disconnect them (if connected) and tidy UI state.
    for (String sid : removed) {
      if (sid == null || sid.isBlank()) continue;

      // Disable input if we were on that server.
      if (activeTarget != null && Objects.equals(activeTarget.serverId(), sid)) {
        // Switch to a safe target.
        String fallback = current.stream().findFirst().orElse("default");
        TargetRef status = new TargetRef(fallback, "status");
        ui.ensureTargetExists(status);
        ui.selectTarget(status);
      }

      // If we think it's connected, request a disconnect.
      if (connectedServers.contains(sid)) {
        TargetRef status = new TargetRef(sid, "status");
        ui.appendStatus(status, "(servers)", "Server removed from configuration; disconnecting…");
        disposables.add(
            irc.disconnect(sid).subscribe(
                () -> {},
                err -> ui.appendError(status, "(disc-error)", String.valueOf(err))
            )
        );
      }

      connectedServers.remove(sid);
      ui.setServerConnected(sid, false);
    }

    // For newly added servers, create their status buffers so the UI has a landing place.
    for (String sid : added) {
      if (sid == null || sid.isBlank()) continue;
      ui.ensureTargetExists(new TargetRef(sid, "status"));
    }

    // Update global connection UI text/counts.
    updateConnectionUi();
  }

  /**
   * Handle the subset of IRC events that represent connectivity changes.
   *
   * @return whether connectivity changed.
   */
  public ConnectivityChange handleConnectivityEvent(String sid, IrcEvent e, TargetRef activeTarget) {
    TargetRef status = new TargetRef(sid, "status");

    switch (e) {
      case IrcEvent.Connected ev -> {
        connectedServers.add(sid);
        ui.setServerConnected(sid, true);
        ui.ensureTargetExists(status);
        ui.appendStatus(status, "(conn)", "Connected as " + ev.nick());
        ui.setChatCurrentNick(sid, ev.nick());
        runtimeConfig.rememberNick(sid, ev.nick());
        updateConnectionUi();
        return ConnectivityChange.CHANGED;
      }

      case IrcEvent.Reconnecting ev -> {
        ui.ensureTargetExists(status);
        long sec = Math.max(0, ev.delayMs() / 1000);
        String msg = "Reconnecting in " + sec + "s (attempt " + ev.attempt() + ")";
        if (ev.reason() != null && !ev.reason().isBlank()) {
          msg += " — " + ev.reason();
        }
        ui.appendStatus(status, "(conn)", msg);
        if (activeTarget != null && Objects.equals(activeTarget.serverId(), sid) && !activeTarget.isStatus()) {
          ui.appendStatus(activeTarget, "(conn)", msg);
        }
        ui.setConnectionStatusText("Reconnecting…");
        return ConnectivityChange.NONE;
      }

      case IrcEvent.Disconnected ev -> {
        connectedServers.remove(sid);
        ui.setServerConnected(sid, false);
        String msg = "Disconnected: " + ev.reason();
        ui.appendStatus(status, "(conn)", msg);
        if (activeTarget != null && Objects.equals(activeTarget.serverId(), sid) && !activeTarget.isStatus()) {
          ui.appendStatus(activeTarget, "(conn)", msg);
        }
        ui.setChatCurrentNick(sid, "");
        updateConnectionUi();
        return ConnectivityChange.CHANGED;
      }

      default -> {
        return ConnectivityChange.NONE;
      }
    }
  }

  private void updateConnectionUi() {
    int total = serverRegistry.serverIds().size();
    int connected = connectedServers.size();

    // Enable/disable Connect/Disconnect buttons.
    ui.setConnectedUi(connected > 0);

    if (connected <= 0) {
      ui.setConnectionStatusText("Disconnected");
      return;
    }

    if (total <= 1) {
      ui.setConnectionStatusText("Connected");
    } else {
      ui.setConnectionStatusText("Connected (" + connected + "/" + total + ")");
    }
  }
}
