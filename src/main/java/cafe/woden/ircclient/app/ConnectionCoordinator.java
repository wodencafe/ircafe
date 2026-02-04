package cafe.woden.ircclient.app;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.ui.SwingEdt;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

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

  /** Per-server connection states (missing => {@link ConnectionState#DISCONNECTED}). */
  private final Map<String, ConnectionState> states = new HashMap<>();

  private final Set<String> configuredServers = new HashSet<>();

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

    configuredServers.addAll(serverRegistry.serverIds());

    // Initialize known servers to DISCONNECTED so server-node context menus are correct from the start.
    for (String sid : configuredServers) {
      ui.setServerConnectionState(sid, ConnectionState.DISCONNECTED);
    }

    updateConnectionUi();
  }

  @PreDestroy
  void shutdown() {
    disposables.dispose();
  }

  public boolean isConnected(String serverId) {
    return stateOf(serverId) == ConnectionState.CONNECTED;
  }

  public int connectedCount() {
    int n = 0;
    for (ConnectionState s : states.values()) {
      if (s == ConnectionState.CONNECTED) n++;
    }
    return n;
  }

  public Set<String> connectedServerIdsSnapshot() {
    Set<String> out = new HashSet<>();
    for (var e : states.entrySet()) {
      if (e.getValue() == ConnectionState.CONNECTED) {
        out.add(e.getKey());
      }
    }
    return Set.copyOf(out);
  }

  public void connectAll() {
    Set<String> serverIds = serverRegistry.serverIds();
    if (serverIds.isEmpty()) {
      ui.setConnectionStatusText("No servers configured");
      ui.setConnectionControlsEnabled(false, false);
      return;
    }

    for (String sid : serverIds) {
      setState(sid, ConnectionState.CONNECTING);

      TargetRef status = new TargetRef(sid, "status");
      ui.ensureTargetExists(status);
      ui.appendStatus(status, "(conn)", "Connecting…");

      disposables.add(
          irc.connect(sid)
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  () -> {},
                  err -> {
                    ui.appendError(status, "(conn-error)", String.valueOf(err));
                    ui.appendStatus(status, "(conn)", "Connect failed");
                    setState(sid, ConnectionState.DISCONNECTED);
                    updateConnectionUi();
                  }
              )
      );
    }

    updateConnectionUi();
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

    setState(sid, ConnectionState.CONNECTING);
    updateConnectionUi();

    disposables.add(
        irc.connect(sid)
            .observeOn(SwingEdt.scheduler())
            .subscribe(
                () -> {},
                err -> {
                  ui.appendError(status, "(conn-error)", String.valueOf(err));
                  ui.appendStatus(status, "(conn)", "Connect failed");
                  setState(sid, ConnectionState.DISCONNECTED);
                  updateConnectionUi();
                }
            )
    );
  }

  public void disconnectAll() {
    Set<String> serverIds = serverRegistry.serverIds();

    for (String sid : serverIds) {
      if (stateOf(sid) != ConnectionState.DISCONNECTED) {
        setState(sid, ConnectionState.DISCONNECTING);
      }

      TargetRef status = new TargetRef(sid, "status");
      ui.appendStatus(status, "(conn)", "Disconnecting…");

      disposables.add(
          irc.disconnect(sid)
              .observeOn(SwingEdt.scheduler())
              .subscribe(
                  () -> {},
                  err -> ui.appendError(status, "(disc-error)", String.valueOf(err))
              )
      );
    }

    updateConnectionUi();
  }

  public void disconnectOne(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    if (!serverRegistry.containsId(sid)) {
      ui.appendError(new TargetRef("default", "status"), "(disc)", "Unknown server: " + sid);
      return;
    }

    TargetRef status = new TargetRef(sid, "status");
    ui.ensureTargetExists(status);
    ui.appendStatus(status, "(conn)", "Disconnecting…");

    setState(sid, ConnectionState.DISCONNECTING);
    updateConnectionUi();

    disposables.add(
        irc.disconnect(sid)
            .observeOn(SwingEdt.scheduler())
            .subscribe(
                () -> {},
                err -> ui.appendError(status, "(disc-error)", String.valueOf(err))
            )
    );
  }

  public void onServersUpdated(List<IrcProperties.Server> latest, TargetRef activeTarget) {
    // Compute current ids.
    Set<String> current = new HashSet<>();
    if (latest != null) {
      for (var s : latest) {
        if (s == null) continue;
        String id = Objects.toString(s.id(), "").trim();
        if (!id.isEmpty()) current.add(id);
      }
    }

    // Removed
    Set<String> removed = new HashSet<>(configuredServers);
    removed.removeAll(current);

    // Added
    Set<String> added = new HashSet<>(current);
    added.removeAll(configuredServers);

    configuredServers.clear();
    configuredServers.addAll(current);

    for (String sid : removed) {
      if (sid == null || sid.isBlank()) continue;

      // If the active target is on the removed server, switch to a safe target.
      if (activeTarget != null && Objects.equals(activeTarget.serverId(), sid)) {
        String fallback = current.stream().findFirst().orElse("default");
        TargetRef status = new TargetRef(fallback, "status");
        ui.ensureTargetExists(status);
        ui.selectTarget(status);
      }

      // Request disconnect if it wasn't already disconnected.
      if (stateOf(sid) != ConnectionState.DISCONNECTED) {
        TargetRef status = new TargetRef(sid, "status");
        ui.appendStatus(status, "(servers)", "Server removed from configuration; disconnecting…");
        disposables.add(
            irc.disconnect(sid)
                .observeOn(SwingEdt.scheduler())
                .subscribe(
                    () -> {},
                    err -> ui.appendError(status, "(disc-error)", String.valueOf(err))
                )
        );
      }

      states.remove(sid);
      ui.setServerConnectionState(sid, ConnectionState.DISCONNECTED);
    }

    for (String sid : added) {
      if (sid == null || sid.isBlank()) continue;
      ui.ensureTargetExists(new TargetRef(sid, "status"));
      ui.setServerConnectionState(sid, ConnectionState.DISCONNECTED);
    }

    updateConnectionUi();
  }

  /** Handle the subset of IRC events that represent connectivity changes. */
  public ConnectivityChange handleConnectivityEvent(String sid, IrcEvent e, TargetRef activeTarget) {
    TargetRef status = new TargetRef(sid, "status");

    switch (e) {
      case IrcEvent.Connecting ev -> {
        setState(sid, ConnectionState.CONNECTING);
        ui.ensureTargetExists(status);
        String msg = "Connecting to " + ev.serverHost() + ":" + ev.serverPort();
        if (ev.nick() != null && !ev.nick().isBlank()) msg += " as " + ev.nick();
        ui.appendStatus(status, "(conn)", msg);
        if (activeTarget != null && Objects.equals(activeTarget.serverId(), sid) && !activeTarget.isStatus()) {
          ui.appendStatus(activeTarget, "(conn)", msg);
        }
        updateConnectionUi();
        return ConnectivityChange.CHANGED;
      }

      case IrcEvent.Connected ev -> {
        setState(sid, ConnectionState.CONNECTED);
        ui.ensureTargetExists(status);
        ui.appendStatus(status, "(conn)", "Connected as " + ev.nick());
        ui.setChatCurrentNick(sid, ev.nick());
        runtimeConfig.rememberNick(sid, ev.nick());
        updateConnectionUi();
        return ConnectivityChange.CHANGED;
      }

      case IrcEvent.Reconnecting ev -> {
        setState(sid, ConnectionState.RECONNECTING);
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
        updateConnectionUi();
        return ConnectivityChange.CHANGED;
      }

      case IrcEvent.Disconnected ev -> {
        setState(sid, ConnectionState.DISCONNECTED);
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

  private ConnectionState stateOf(String sid) {
    if (sid == null) return ConnectionState.DISCONNECTED;
    return states.getOrDefault(sid, ConnectionState.DISCONNECTED);
  }

  private void setState(String sid, ConnectionState state) {
    if (sid == null) return;
    ConnectionState st = state == null ? ConnectionState.DISCONNECTED : state;
    if (st == ConnectionState.DISCONNECTED) {
      states.remove(sid);
    } else {
      states.put(sid, st);
    }
    ui.setServerConnectionState(sid, st);
  }

  private void updateConnectionUi() {
    List<String> ids = List.copyOf(serverRegistry.serverIds());
    int total = ids.size();

    if (total == 0) {
      ui.setConnectionControlsEnabled(false, false);
      ui.setConnectionStatusText("No servers configured");
      return;
    }

    int connected = 0;
    int connecting = 0;
    int reconnecting = 0;
    int disconnecting = 0;

    for (String sid : ids) {
      ConnectionState st = stateOf(sid);
      switch (st) {
        case CONNECTED -> connected++;
        case CONNECTING -> connecting++;
        case RECONNECTING -> reconnecting++;
        case DISCONNECTING -> disconnecting++;
        case DISCONNECTED -> {
          // handled via totals
        }
      }
    }

    int disconnected = total - (connected + connecting + reconnecting + disconnecting);

    // Allow Connect if there is at least one disconnected server and we are not currently disconnecting.
    boolean connectEnabled = disconnected > 0 && disconnecting == 0;

    // Allow Disconnect if anything is connected/connecting/reconnecting and we are not currently disconnecting.
    boolean disconnectEnabled = (connected + connecting + reconnecting) > 0 && disconnecting == 0;

    ui.setConnectionControlsEnabled(connectEnabled, disconnectEnabled);

    String text;
    if (disconnecting > 0) {
      text = total <= 1 ? "Disconnecting…" : ("Disconnecting (" + disconnecting + "/" + total + ")…");
    } else if (connecting > 0 && connected == 0 && reconnecting == 0) {
      text = total <= 1 ? "Connecting…" : ("Connecting (" + connecting + "/" + total + ")…");
    } else if (reconnecting > 0 && connected == 0 && connecting == 0) {
      text = total <= 1 ? "Reconnecting…" : ("Reconnecting (" + reconnecting + "/" + total + ")…");
    } else if (connected > 0) {
      text = total <= 1 ? "Connected" : ("Connected (" + connected + "/" + total + ")");
      if (connecting > 0) text += ", connecting…";
      if (reconnecting > 0) text += ", reconnecting…";
    } else {
      text = "Disconnected";
    }

    ui.setConnectionStatusText(text);
  }
}
