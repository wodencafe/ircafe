package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.LogProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
@ApplicationLayer
public class ConnectionCoordinator {
  private static final Scheduler EDT_SCHEDULER = Schedulers.from(SwingUtilities::invokeLater);

  public enum ConnectivityChange {
    NONE,
    CHANGED
  }

  private final IrcClientService irc;
  private final UiPort ui;
  private final ServerRegistry serverRegistry;
  private final ServerCatalog serverCatalog;
  private final RuntimeConfigStore runtimeConfig;
  private final LogProperties logProps;
  private final TrayNotificationsPort trayNotificationService;
  private final CompositeDisposable disposables = new CompositeDisposable();

  /** Per-server connection states (missing => {@link ConnectionState#DISCONNECTED}). */
  private final Map<String, ConnectionState> states = new HashMap<>();

  /** Per-server desired online intent (missing => false/offline). */
  private final Set<String> desiredOnline = new HashSet<>();

  /** Last connection-related error/reason shown in UI tooltips. */
  private final Map<String, String> lastErrorByServer = new HashMap<>();

  /** Next reconnect attempt wall-clock (epoch ms), when scheduled. */
  private final Map<String, Long> nextRetryAtByServer = new HashMap<>();

  private final Set<String> configuredServers = new HashSet<>();

  public ConnectionCoordinator(
      IrcClientService irc,
      UiPort ui,
      ServerRegistry serverRegistry,
      ServerCatalog serverCatalog,
      RuntimeConfigStore runtimeConfig,
      LogProperties logProps,
      TrayNotificationsPort trayNotificationService) {
    this.irc = irc;
    this.ui = ui;
    this.serverRegistry = serverRegistry;
    this.serverCatalog = serverCatalog;
    this.runtimeConfig = runtimeConfig;
    this.logProps = logProps;
    this.trayNotificationService = trayNotificationService;

    configuredServers.addAll(serverRegistry.serverIds());
    for (String sid : configuredServers) {
      ui.setServerConnectionState(sid, ConnectionState.DISCONNECTED);
      restoreJoinedChannelTargets(sid);
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
      requestConnect(sid, false, false);
    }
    updateConnectionUi();
  }

  public void connectAutoConnectOnStartServers() {
    Set<String> serverIds = serverRegistry.serverIds();
    if (serverIds.isEmpty()) {
      ui.setConnectionStatusText("No servers configured");
      ui.setConnectionControlsEnabled(false, false);
      return;
    }

    Map<String, Boolean> autoConnectByServer =
        runtimeConfig != null ? runtimeConfig.readServerAutoConnectOnStartByServer() : Map.of();
    for (String sid : serverIds) {
      if (!isStartupAutoConnectEnabled(autoConnectByServer, sid)) continue;
      requestConnect(sid, false, false);
    }
    updateConnectionUi();
  }

  public void connectOne(String serverId) {
    String sid = normalizedKnownServerId(serverId, "(conn)");
    if (sid == null) return;
    requestConnect(sid, true);
  }

  public void disconnectAll() {
    disconnectAll(null);
  }

  public void disconnectAll(String reason) {
    Set<String> serverIds = serverRegistry.serverIds();

    for (String sid : serverIds) {
      requestDisconnect(sid, reason, false);
    }
  }

  public void disconnectOne(String serverId) {
    disconnectOne(serverId, null);
  }

  public void disconnectOne(String serverId, String reason) {
    String sid = normalizedKnownServerId(serverId, "(disc)");
    if (sid == null) return;
    requestDisconnect(sid, reason, true);
  }

  public void reconnectAll() {
    Set<String> serverIds = serverRegistry.serverIds();
    if (serverIds.isEmpty()) {
      ui.setConnectionStatusText("No servers configured");
      ui.setConnectionControlsEnabled(false, false);
      return;
    }

    for (String sid : serverIds) {
      reconnectOne(sid);
    }
  }

  public void reconnectOne(String serverId) {
    String sid = normalizedKnownServerId(serverId, "(reconnect)");
    if (sid == null) return;

    TargetRef status = new TargetRef(sid, "status");
    ui.ensureTargetExists(status);
    setDesiredOnline(sid, true);
    setNextRetryAtMs(sid, null);

    if (stateOf(sid) == ConnectionState.DISCONNECTING) {
      ui.appendStatus(status, "(conn)", "Reconnect requested; waiting for disconnect to finish…");
      updateConnectionUi();
      return;
    }

    ui.appendStatus(status, "(conn)", "Reconnecting…");

    setState(sid, ConnectionState.RECONNECTING);
    updateConnectionUi();

    io.reactivex.rxjava3.core.Completable reconnect =
        irc.disconnect(sid).onErrorComplete().andThen(irc.connect(sid));

    disposables.add(
        reconnect
            .observeOn(EDT_SCHEDULER)
            .subscribe(
                () -> {},
                err -> {
                  ui.appendError(status, "(reconnect-error)", String.valueOf(err));
                  setLastError(sid, String.valueOf(err));
                  setState(sid, ConnectionState.DISCONNECTED);
                  updateConnectionUi();
                }));
  }

  private static boolean isStartupAutoConnectEnabled(
      Map<String, Boolean> autoConnectByServer, String serverId) {
    if (autoConnectByServer == null || autoConnectByServer.isEmpty()) return true;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return true;

    Boolean exact = autoConnectByServer.get(sid);
    if (exact != null) return exact;

    for (Map.Entry<String, Boolean> entry : autoConnectByServer.entrySet()) {
      if (sid.equalsIgnoreCase(Objects.toString(entry.getKey(), "").trim())) {
        return Boolean.TRUE.equals(entry.getValue());
      }
    }
    return true;
  }

  private String normalizedKnownServerId(String serverId, String tag) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return null;
    if (!serverCatalog.containsId(sid)) {
      ui.appendError(new TargetRef("default", "status"), tag, "Unknown server: " + sid);
      return null;
    }
    return sid;
  }

  private void requestConnect(String sid, boolean announceQueued) {
    requestConnect(sid, announceQueued, true);
  }

  private void requestConnect(String sid, boolean announceQueued, boolean refreshUiNow) {
    String id = Objects.toString(sid, "").trim();
    if (id.isEmpty()) return;

    TargetRef status = new TargetRef(id, "status");
    ui.ensureTargetExists(status);
    setDesiredOnline(id, true);
    setNextRetryAtMs(id, null);

    ConnectionState current = stateOf(id);
    if (current == ConnectionState.CONNECTED
        || current == ConnectionState.CONNECTING
        || current == ConnectionState.RECONNECTING) {
      if (refreshUiNow) updateConnectionUi();
      return;
    }

    if (current == ConnectionState.DISCONNECTING) {
      if (announceQueued) {
        ui.appendStatus(status, "(conn)", "Connect requested; waiting for disconnect to finish…");
      }
      if (refreshUiNow) updateConnectionUi();
      return;
    }

    ui.appendStatus(status, "(conn)", "Connecting…");
    setState(id, ConnectionState.CONNECTING);

    disposables.add(
        irc.connect(id)
            .observeOn(EDT_SCHEDULER)
            .subscribe(
                () -> {},
                err -> {
                  ui.appendError(status, "(conn-error)", String.valueOf(err));
                  ui.appendStatus(status, "(conn)", "Connect failed");
                  setLastError(id, String.valueOf(err));
                  setState(id, ConnectionState.DISCONNECTED);
                  updateConnectionUi();
                }));

    if (refreshUiNow) updateConnectionUi();
  }

  private void requestDisconnect(String sid, String reason, boolean announceWhenAlreadyOffline) {
    String id = Objects.toString(sid, "").trim();
    if (id.isEmpty()) return;

    TargetRef status = new TargetRef(id, "status");
    ui.ensureTargetExists(status);
    setDesiredOnline(id, false);
    setNextRetryAtMs(id, null);

    ConnectionState current = stateOf(id);
    boolean transitioning =
        current == ConnectionState.CONNECTED
            || current == ConnectionState.CONNECTING
            || current == ConnectionState.RECONNECTING;

    if (transitioning) {
      setState(id, ConnectionState.DISCONNECTING);
      ui.appendStatus(status, "(conn)", "Disconnecting…");
    } else if (current == ConnectionState.DISCONNECTED && announceWhenAlreadyOffline) {
      ui.appendStatus(status, "(conn)", "Already disconnected");
    }

    disposables.add(
        irc.disconnect(id, reason)
            .observeOn(EDT_SCHEDULER)
            .subscribe(
                () -> {},
                err -> {
                  ui.appendError(status, "(disc-error)", String.valueOf(err));
                  setLastError(id, String.valueOf(err));
                }));

    updateConnectionUi();
  }

  public void onServersUpdated(List<IrcProperties.Server> latest, TargetRef activeTarget) {
    Set<String> current = new HashSet<>();
    if (latest != null) {
      for (var s : latest) {
        if (s == null) continue;
        String id = Objects.toString(s.id(), "").trim();
        if (!id.isEmpty()) current.add(id);
      }
    }

    Set<String> removed = new HashSet<>(configuredServers);
    removed.removeAll(current);

    Set<String> added = new HashSet<>(current);
    added.removeAll(configuredServers);

    configuredServers.clear();
    configuredServers.addAll(current);

    for (String sid : removed) {
      if (sid == null || sid.isBlank()) continue;
      setDesiredOnline(sid, false);
      clearConnectionDiagnostics(sid);
      if (activeTarget != null && Objects.equals(activeTarget.serverId(), sid)) {
        String fallback = current.stream().findFirst().orElse("default");
        TargetRef status = new TargetRef(fallback, "status");
        ui.ensureTargetExists(status);
        ui.selectTarget(status);
      }
      if (stateOf(sid) != ConnectionState.DISCONNECTED) {
        TargetRef status = new TargetRef(sid, "status");
        ui.appendStatus(status, "(servers)", "Server removed from configuration; disconnecting…");
        setDesiredOnline(sid, false);
        disposables.add(
            irc.disconnect(sid)
                .observeOn(EDT_SCHEDULER)
                .subscribe(
                    () -> {}, err -> ui.appendError(status, "(disc-error)", String.valueOf(err))));
      }

      states.remove(sid);
      ui.setServerConnectionState(sid, ConnectionState.DISCONNECTED);
    }

    for (String sid : added) {
      if (sid == null || sid.isBlank()) continue;
      ui.ensureTargetExists(new TargetRef(sid, "status"));
      setDesiredOnline(sid, false);
      clearConnectionDiagnostics(sid);
      ui.setServerConnectionState(sid, ConnectionState.DISCONNECTED);
    }

    updateConnectionUi();
  }

  public ConnectivityChange handleConnectivityEvent(
      String sid, IrcEvent e, TargetRef activeTarget) {
    String id = Objects.toString(sid, "").trim();
    if (id.isEmpty() || e == null) return ConnectivityChange.NONE;

    TargetRef status = new TargetRef(id, "status");
    ConnectionState previous = stateOf(id);

    switch (e) {
      case IrcEvent.Connecting ev -> {
        if (!isDesiredOnline(id) && previous == ConnectionState.DISCONNECTING) {
          ui.ensureTargetExists(status);
          ui.appendStatus(status, "(conn)", "Connection attempt suppressed (server set offline)");
          requestDisconnect(id, null, false);
          return ConnectivityChange.CHANGED;
        }
        if (!isDesiredOnline(id)) {
          setDesiredOnline(id, true);
        }
        setNextRetryAtMs(id, null);

        setState(id, ConnectionState.CONNECTING);
        ui.ensureTargetExists(status);
        String msg = "Connecting to " + ev.serverHost() + ":" + ev.serverPort();
        if (ev.nick() != null && !ev.nick().isBlank()) msg += " as " + ev.nick();
        ui.appendStatus(status, "(conn)", msg);
        if (activeTarget != null
            && Objects.equals(activeTarget.serverId(), id)
            && !activeTarget.isStatus()) {
          ui.appendStatus(activeTarget, "(conn)", msg);
        }

        try {
          trayNotificationService.notifyConnectionState(id, "Connecting", msg);
        } catch (Exception ignored) {
        }
        updateConnectionUi();
        return ConnectivityChange.CHANGED;
      }

      case IrcEvent.Connected ev -> {
        if (!isDesiredOnline(id) && previous == ConnectionState.DISCONNECTING) {
          ui.ensureTargetExists(status);
          ui.appendStatus(status, "(conn)", "Connected while marked offline; disconnecting…");
          requestDisconnect(id, null, false);
          return ConnectivityChange.CHANGED;
        }
        if (!isDesiredOnline(id)) {
          setDesiredOnline(id, true);
        }
        clearConnectionDiagnostics(id);

        setState(id, ConnectionState.CONNECTED);
        ui.ensureTargetExists(status);
        String msg = "Connected as " + ev.nick();
        ui.appendStatus(status, "(conn)", msg);
        ui.setChatCurrentNick(id, ev.nick());
        restorePrivateMessageTargets(id);
        restoreJoinedChannelTargets(id);

        try {
          trayNotificationService.notifyConnectionState(id, "Connected", msg);
        } catch (Exception ignored) {
        }
        updateConnectionUi();
        return ConnectivityChange.CHANGED;
      }

      case IrcEvent.Reconnecting ev -> {
        if (!isDesiredOnline(id)) {
          ui.ensureTargetExists(status);
          ui.appendStatus(status, "(conn)", "Reconnect suppressed (server set offline)");
          requestDisconnect(id, null, false);
          return ConnectivityChange.CHANGED;
        }

        setState(id, ConnectionState.RECONNECTING);
        ui.ensureTargetExists(status);
        long sec = Math.max(0, ev.delayMs() / 1000);
        setNextRetryAtMs(id, System.currentTimeMillis() + Math.max(0, ev.delayMs()));
        if (ev.reason() != null && !ev.reason().isBlank()) {
          setLastError(id, ev.reason());
        }
        String msg = "Reconnecting in " + sec + "s (attempt " + ev.attempt() + ")";
        if (ev.reason() != null && !ev.reason().isBlank()) {
          msg += " — " + ev.reason();
        }
        ui.appendStatus(status, "(conn)", msg);
        if (activeTarget != null
            && Objects.equals(activeTarget.serverId(), id)
            && !activeTarget.isStatus()) {
          ui.appendStatus(activeTarget, "(conn)", msg);
        }

        try {
          trayNotificationService.notifyConnectionState(id, "Reconnecting", msg);
        } catch (Exception ignored) {
        }
        updateConnectionUi();
        return ConnectivityChange.CHANGED;
      }

      case IrcEvent.Disconnected ev -> {
        setState(id, ConnectionState.DISCONNECTED);
        String msg = "Disconnected: " + ev.reason();
        ui.appendStatus(status, "(conn)", msg);
        if (activeTarget != null
            && Objects.equals(activeTarget.serverId(), id)
            && !activeTarget.isStatus()) {
          ui.appendStatus(activeTarget, "(conn)", msg);
        }
        ui.setChatCurrentNick(id, "");
        restoreJoinedChannelTargets(id);

        try {
          trayNotificationService.notifyConnectionState(id, "Disconnected", ev.reason());
        } catch (Exception ignored) {
        }

        boolean reconnectAfterDisconnect =
            isDesiredOnline(id)
                && previous == ConnectionState.DISCONNECTING
                && serverCatalog.containsId(id);
        if (reconnectAfterDisconnect) {
          setNextRetryAtMs(id, null);
          requestConnect(id, false);
        } else {
          setNextRetryAtMs(id, null);
          updateConnectionUi();
        }
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

  private boolean isDesiredOnline(String sid) {
    if (sid == null) return false;
    return desiredOnline.contains(sid);
  }

  public void noteConnectionError(String serverId, String message) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    setLastError(sid, message);
  }

  private void setDesiredOnline(String sid, boolean desired) {
    if (sid == null) return;
    boolean old = desiredOnline.contains(sid);
    if (desired == old) return;
    if (desired) desiredOnline.add(sid);
    else desiredOnline.remove(sid);
    ui.setServerDesiredOnline(sid, desired);
  }

  private void restorePrivateMessageTargets(String serverId) {
    if (runtimeConfig == null
        || logProps == null
        || !Boolean.TRUE.equals(logProps.savePrivateMessageList())) {
      return;
    }
    for (String nick : runtimeConfig.readPrivateMessageTargets(serverId)) {
      String n = Objects.toString(nick, "").trim();
      if (n.isEmpty()) continue;
      try {
        ui.ensureTargetExists(new TargetRef(serverId, n));
      } catch (Exception ignored) {
      }
    }
  }

  private void restoreJoinedChannelTargets(String serverId) {
    if (runtimeConfig == null) {
      return;
    }
    List<String> channels = runtimeConfig.readKnownChannels(serverId);
    if (channels == null || channels.isEmpty()) {
      return;
    }
    for (String channel : channels) {
      String ch = Objects.toString(channel, "").trim();
      if (ch.isEmpty()) continue;
      try {
        TargetRef target = new TargetRef(serverId, ch);
        if (!target.isChannel()) continue;
        ui.ensureTargetExists(target);
        ui.setChannelDisconnected(target, true);
      } catch (Exception ignored) {
      }
    }
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

  private void setLastError(String sid, String error) {
    String id = Objects.toString(sid, "").trim();
    if (id.isEmpty()) return;
    String next = Objects.toString(error, "").trim();
    String prev = lastErrorByServer.getOrDefault(id, "");
    if (next.isEmpty()) {
      if (!prev.isEmpty()) {
        lastErrorByServer.remove(id);
        publishConnectionDiagnostics(id);
      }
      return;
    }
    if (Objects.equals(prev, next)) return;
    lastErrorByServer.put(id, next);
    publishConnectionDiagnostics(id);
  }

  private void setNextRetryAtMs(String sid, Long atMs) {
    String id = Objects.toString(sid, "").trim();
    if (id.isEmpty()) return;
    Long normalized = (atMs == null || atMs <= 0L) ? null : atMs;
    Long prev = nextRetryAtByServer.get(id);
    if (Objects.equals(prev, normalized)) return;
    if (normalized == null) {
      nextRetryAtByServer.remove(id);
    } else {
      nextRetryAtByServer.put(id, normalized);
    }
    publishConnectionDiagnostics(id);
  }

  private void clearConnectionDiagnostics(String sid) {
    String id = Objects.toString(sid, "").trim();
    if (id.isEmpty()) return;
    boolean changed = false;
    if (lastErrorByServer.remove(id) != null) changed = true;
    if (nextRetryAtByServer.remove(id) != null) changed = true;
    if (changed) {
      publishConnectionDiagnostics(id);
    }
  }

  private void publishConnectionDiagnostics(String sid) {
    String id = Objects.toString(sid, "").trim();
    if (id.isEmpty()) return;
    ui.setServerConnectionDiagnostics(
        id, lastErrorByServer.getOrDefault(id, ""), nextRetryAtByServer.get(id));
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
    int desired = 0;

    for (String sid : ids) {
      if (isDesiredOnline(sid)) desired++;
      ConnectionState st = stateOf(sid);
      switch (st) {
        case CONNECTED -> connected++;
        case CONNECTING -> connecting++;
        case RECONNECTING -> reconnecting++;
        case DISCONNECTING -> disconnecting++;
        case DISCONNECTED -> {}
      }
    }

    boolean connectEnabled = desired < total;
    boolean disconnectEnabled = desired > 0;

    ui.setConnectionControlsEnabled(connectEnabled, disconnectEnabled);

    String text;
    if (disconnecting > 0) {
      text =
          total <= 1 ? "Disconnecting…" : ("Disconnecting (" + disconnecting + "/" + total + ")…");
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
