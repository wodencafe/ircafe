package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.model.UserListStore;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Mediator.
 *
 * <p>Multi-server support:
 * targets are scoped to a server id via {@link TargetRef}.
 */
@Component
@Lazy
public class IrcMediator {
  private final IrcClientService irc;
  private final UiPort ui;
  private final UserListStore userListStore;
  private final CommandParser commandParser;
  private final ServerRegistry serverRegistry;
  private final RuntimeConfigStore runtimeConfig;
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean(false);

  // Track which servers are currently connected so the UI controls behave sensibly in multi-server mode.
  private final Set<String> connectedServers = new java.util.HashSet<>();

  // Track which servers are configured so we can react to runtime add/edit/remove.
  private final Set<String> configuredServers = new java.util.HashSet<>();

  private TargetRef activeTarget;

  @PostConstruct
  void init() {
    start();
  }

  @PreDestroy
  void shutdown() {
    stop();
  }

  public IrcMediator(
      IrcClientService irc,
      UiPort ui,
      UserListStore userListStore,
      CommandParser commandParser,
      ServerRegistry serverRegistry,
      RuntimeConfigStore runtimeConfig
  ) {
    this.irc = irc;
    this.ui = ui;
    this.userListStore = userListStore;
    this.commandParser = commandParser;
    this.serverRegistry = serverRegistry;
    this.runtimeConfig = runtimeConfig;
  }

  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }

    disposables.add(
        ui.targetSelections()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::onTargetSelected,
                err -> ui.appendError(new TargetRef("default", "status"), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.targetActivations()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::onTargetActivated,
                err -> ui.appendError(safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.privateMessageRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::openPrivateConversation,
                err -> ui.appendError(safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.outboundLines()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::handleOutgoingLine,
                err -> ui.appendError(safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        irc.events()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::onServerIrcEvent,
                err -> ui.appendError(safeStatusTarget(), "(irc-error)", err.toString()))
    );

    disposables.add(
        ui.connectClicks()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(ignored -> connectAll())
    );

    disposables.add(
        ui.disconnectClicks()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(ignored -> disconnectAll())
    );

    disposables.add(
        ui.connectServerRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::connectOne,
                err -> ui.appendError(safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    disposables.add(
        ui.disconnectServerRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::disconnectOne,
                err -> ui.appendError(safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    disposables.add(
        ui.closeTargetRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::handleCloseTarget,
                err -> ui.appendError(safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    // React to runtime server list edits.
    configuredServers.clear();
    configuredServers.addAll(serverRegistry.serverIds());
    disposables.add(
        serverRegistry.updates()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::onServersUpdated,
                err -> ui.appendError(safeStatusTarget(), "(ui-error)", "Server list update failed: " + err))
    );
  }

  private void onServersUpdated(List<cafe.woden.ircclient.config.IrcProperties.Server> latest) {
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
        ensureTargetExists(status);
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
      ensureTargetExists(new TargetRef(sid, "status"));
    }

    // Update global connection UI text/counts.
    updateConnectionUi();
    updateInputEnabledForActiveTarget();
  }

  public void stop() {
    if (!started.compareAndSet(true, false)) {
      return;
    }
    disposables.dispose();
  }

  /**
   * Connect to all configured servers.
   *
   * <p>Safe to call multiple times; connection attempts are idempotent per server.
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
      ensureTargetExists(status);
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

  private void connectOne(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    if (!serverRegistry.containsId(sid)) {
      ui.appendError(safeStatusTarget(), "(conn)", "Unknown server: " + sid);
      return;
    }

    TargetRef status = new TargetRef(sid, "status");
    ensureTargetExists(status);
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

  private void disconnectOne(String serverId) {
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;
    if (!serverRegistry.containsId(sid)) {
      ui.appendError(safeStatusTarget(), "(disc)", "Unknown server: " + sid);
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

  private void openPrivateConversation(PrivateMessageRequest req) {
    if (req == null) return;
    String sid = Objects.toString(req.serverId(), "").trim();
    String nick = Objects.toString(req.nick(), "").trim();
    if (sid.isEmpty() || nick.isEmpty()) return;

    TargetRef pm = new TargetRef(sid, nick);
    ensureTargetExists(pm);
    ui.selectTarget(pm);
  }

  private void onTargetActivated(TargetRef target) {
    if (target == null) return;

    ensureTargetExists(target);
    // Do NOT change the main Chat dock's displayed transcript.
    // Only update the active target for input + status/users panels.
    applyTargetContext(target, false);
  }

  private void onTargetSelected(TargetRef target) {
    if (target == null) return;

    ensureTargetExists(target);

    // Selection in the server tree drives what the main Chat dock is displaying.
    ui.setChatActiveTarget(target);

    // And it also sets the active target used by input + status/users panels.
    applyTargetContext(target, true);
  }


  private void applyTargetContext(TargetRef target, boolean fromSelection) {
    if (target == null) return;

    this.activeTarget = target;

    // Status bar
    ui.setStatusBarChannel(target.target());
    ui.setStatusBarServer(serverDisplay(target.serverId()));

    // Users panel
    ui.setUsersChannel(target);
    if (target.isChannel()) {
      List<IrcEvent.NickInfo> cached = userListStore.get(target.serverId(), target.target());
      ui.setUsersNicks(cached);
      ui.setStatusBarCounts(cached.size(), (int) cached.stream().filter(IrcMediator::isOperatorLike).count());

      // Request names if cache is empty.
      if (cached.isEmpty()) {
        if (connectedServers.contains(target.serverId())) {
          irc.requestNames(target.serverId(), target.target()).subscribe(
              () -> {},
              err -> ui.appendError(safeStatusTarget(), "(names-error)", String.valueOf(err))
          );
        }
      }
    } else {
      ui.setStatusBarCounts(0, 0);
      ui.setUsersNicks(List.of());
    }

    // Mention highlighting uses server-scoped nick.
    irc.currentNick(target.serverId()).ifPresent(nick -> ui.setChatCurrentNick(target.serverId(), nick));

    ui.clearUnread(target);

    // Disable input when the selected server isn't connected
    updateInputEnabledForActiveTarget();
  }

  private void updateInputEnabledForActiveTarget() {
    if (activeTarget == null) {
      ui.setInputEnabled(false);
      return;
    }
    ui.setInputEnabled(connectedServers.contains(activeTarget.serverId()));
  }

  /**
   * pircbotx exposes channel privilege via a prefix (e.g. "@" op, "+" voice).
   * We treat ops as @ (op), & (admin), ~ (owner).
   */
  private static boolean isOperatorLike(IrcEvent.NickInfo n) {
    if (n == null) return false;
    String p = n.prefix();
    if (p == null || p.isBlank()) return false;
    return p.contains("@") || p.contains("&") || p.contains("~");
  }

  private void handleOutgoingLine(String raw) {
    ParsedInput in = commandParser.parse(raw);
    switch (in) {
      case ParsedInput.Join cmd -> handleJoin(cmd.channel());
      case ParsedInput.Nick cmd -> handleNick(cmd.newNick());
      case ParsedInput.Query cmd -> handleQuery(cmd.nick());
      case ParsedInput.Msg cmd -> handleMsg(cmd.nick(), cmd.body());
      case ParsedInput.Me cmd -> handleMe(cmd.action());
      case ParsedInput.Say cmd -> handleSay(cmd.text());
      case ParsedInput.Unknown cmd ->
          ui.appendStatus(safeStatusTarget(), "(system)", "Unknown command: " + cmd.raw());
    }
  }

  private void handleJoin(String channel) {
    TargetRef at = activeTarget;
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(join)", "Select a server first.");
      return;
    }

    String chan = channel == null ? "" : channel.trim();
    if (chan.isEmpty()) {
      ui.appendStatus(safeStatusTarget(), "(join)", "Usage: /join <#channel>");
      return;
    }

    // Persist for auto-join next time.
    runtimeConfig.rememberJoinedChannel(at.serverId(), chan);

    if (!connectedServers.contains(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected (join queued in config only)");
      return;
    }

    disposables.add(
        irc.joinChannel(at.serverId(), chan).subscribe(
            () -> {},
            err -> ui.appendError(safeStatusTarget(), "(join-error)", String.valueOf(err))
        )
    );
  }

  private void handleCloseTarget(TargetRef target) {
    if (target == null || target.isStatus()) return;

    String sid = Objects.toString(target.serverId(), "").trim();
    if (sid.isEmpty()) return;

    TargetRef status = new TargetRef(sid, "status");
    ensureTargetExists(status);

    // If we're closing the currently active target, switch back to status first.
    if (Objects.equals(activeTarget, target)) {
      ui.selectTarget(status);
    }

    if (target.isChannel()) {
      // Remove from auto-join for next startup.
      runtimeConfig.forgetJoinedChannel(sid, target.target());
      userListStore.clear(sid, target.target());

      if (connectedServers.contains(sid)) {
        disposables.add(
            irc.partChannel(sid, target.target()).subscribe(
                () -> ui.appendStatus(status, "(part)", "Left " + target.target()),
                err -> ui.appendError(status, "(part-error)", String.valueOf(err))
            )
        );
      } else {
        ui.appendStatus(status, "(ui)", "Closed " + target.target());
      }
    } else {
      // Private message buffer: local UI-only close.
      ui.appendStatus(status, "(ui)", "Closed " + target.target());
    }

    ui.closeTarget(target);
  }

  private void handleNick(String newNick) {
    TargetRef at = activeTarget;
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(nick)", "Select a server first.");
      return;
    }

    String nick = newNick == null ? "" : newNick.trim();
    if (nick.isEmpty()) {
      ui.appendStatus(safeStatusTarget(), "(nick)", "Usage: /nick <newNick>");
      return;
    }

    // Persist the preferred nick for next time.
    runtimeConfig.rememberNick(at.serverId(), nick);

    if (!connectedServers.contains(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    disposables.add(
        irc.changeNick(at.serverId(), nick).subscribe(
            () -> ui.appendStatus(new TargetRef(at.serverId(), "status"), "(nick)", "Requested nick change to " + nick),
            err -> ui.appendError(safeStatusTarget(), "(nick-error)", String.valueOf(err))
        )
    );
  }

  private void handleQuery(String nick) {
    TargetRef at = activeTarget;
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(query)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) {
      ui.appendStatus(safeStatusTarget(), "(query)", "Usage: /query <nick>");
      return;
    }

    TargetRef pm = new TargetRef(at.serverId(), n);
    ensureTargetExists(pm);
    ui.selectTarget(pm);
  }

  private void handleMsg(String nick, String body) {
    TargetRef at = activeTarget;
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(msg)", "Select a server first.");
      return;
    }

    String n = nick == null ? "" : nick.trim();
    String m = body == null ? "" : body.trim();
    if (n.isEmpty() || m.isEmpty()) {
      ui.appendStatus(safeStatusTarget(), "(msg)", "Usage: /msg <nick> <message>");
      return;
    }

    TargetRef pm = new TargetRef(at.serverId(), n);
    ensureTargetExists(pm);
    ui.selectTarget(pm);
    sendMessage(pm, m);
  }

  private void handleMe(String action) {
    TargetRef at = activeTarget;
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(me)", "Select a server first.");
      return;
    }

    String a = action == null ? "" : action.trim();
    if (a.isEmpty()) {
      ui.appendStatus(safeStatusTarget(), "(me)", "Usage: /me <action>");
      return;
    }

    if (at.isStatus()) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(me)", "Select a channel or PM first.");
      return;
    }

    if (!connectedServers.contains(at.serverId())) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(conn)", "Not connected");
      return;
    }

    String me = irc.currentNick(at.serverId()).orElse("me");
    ui.appendChat(at, "* " + me, a);

    disposables.add(
        irc.sendMessage(at.serverId(), at.target(), "\u0001ACTION " + a + "\u0001").subscribe(
            () -> {},
            err -> ui.appendError(safeStatusTarget(), "(send-error)", String.valueOf(err))
        )
    );
  }

  private void handleSay(String msg) {
    TargetRef at = activeTarget;
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(system)", "Select a server first.");
      return;
    }

    String m = msg == null ? "" : msg.trim();
    if (m.isEmpty()) return;

    if (at.isStatus()) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(system)", "Select a channel, or double-click a nick to PM them.");
      return;
    }

    sendMessage(at, m);
  }

  private void sendMessage(TargetRef target, String message) {
    if (target == null) return;
    String m = message == null ? "" : message.trim();
    if (m.isEmpty()) return;

    if (!connectedServers.contains(target.serverId())) {
      TargetRef status = new TargetRef(target.serverId(), "status");
      ui.appendStatus(status, "(conn)", "Not connected");
      if (!target.isStatus()) {
        ui.appendStatus(target, "(conn)", "Not connected");
      }
      return;
    }

    disposables.add(
        irc.sendMessage(target.serverId(), target.target(), m).subscribe(
            () -> {},
            err -> ui.appendError(safeStatusTarget(), "(send-error)", String.valueOf(err))
        )
    );

    String me = irc.currentNick(target.serverId()).orElse("me");
    ui.appendChat(target, "(" + me + ")", m);
  }

  private void onServerIrcEvent(ServerIrcEvent se) {
    if (se == null) return;

    String sid = se.serverId();
    IrcEvent e = se.event();

    TargetRef status = new TargetRef(sid, "status");

    switch (e) {
      case IrcEvent.Connected ev -> {
        connectedServers.add(sid);
        ui.setServerConnected(sid, true);
        ensureTargetExists(status);
        ui.appendStatus(status, "(conn)", "Connected as " + ev.nick());
        ui.setChatCurrentNick(sid, ev.nick());
        runtimeConfig.rememberNick(sid, ev.nick());
        updateConnectionUi();
        updateInputEnabledForActiveTarget();
      }

      case IrcEvent.Reconnecting ev -> {
        // Surface reconnect attempts in the status buffer and (if applicable) the active chat.
        ensureTargetExists(status);
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
        updateInputEnabledForActiveTarget();
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
        updateInputEnabledForActiveTarget();
        userListStore.clearServer(sid);
        if (activeTarget != null && Objects.equals(activeTarget.serverId(), sid)) {
          ui.setStatusBarCounts(0, 0);
          ui.setUsersNicks(List.of());
        }
      }

      case IrcEvent.NickChanged ev -> {
        irc.currentNick(sid).ifPresent(currentNick -> {
          if (!Objects.equals(currentNick, ev.oldNick()) && !Objects.equals(currentNick, ev.newNick())) {
            ui.appendNotice(status, "(nick)", ev.oldNick() + " is now known as " + ev.newNick());
          } else {
            ui.appendStatus(status, "(nick)", "Now known as " + ev.newNick());
            ui.setChatCurrentNick(sid, ev.newNick());
          }
        });
      }

      case IrcEvent.ChannelMessage ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendChat(chan, ev.from(), ev.text());
        if (!chan.equals(activeTarget)) ui.markUnread(chan);
      }

      case IrcEvent.PrivateMessage ev -> {
        TargetRef pm = new TargetRef(sid, ev.from());
        ensureTargetExists(pm);
        ui.appendChat(pm, ev.from(), ev.text());
        if (!pm.equals(activeTarget)) ui.markUnread(pm);
      }

      case IrcEvent.Notice ev ->
          ui.appendNotice(status, "(notice) " + ev.from(), ev.text());

      case IrcEvent.JoinedChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        runtimeConfig.rememberJoinedChannel(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendStatus(chan, "(join)", "Joined " + ev.channel());
        ui.selectTarget(chan);
      }

      case IrcEvent.NickListUpdated ev -> {
        userListStore.put(sid, ev.channel(), ev.nicks());
        if (activeTarget != null
            && Objects.equals(activeTarget.serverId(), sid)
            && Objects.equals(activeTarget.target(), ev.channel())) {
          ui.setUsersNicks(ev.nicks());
          ui.setStatusBarCounts(ev.totalUsers(), ev.operatorCount());
        }
      }

      case IrcEvent.Error ev ->
          ui.appendError(status, "(error)", ev.message());

      default -> {
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

  private void ensureTargetExists(TargetRef target) {
    ui.ensureTargetExists(target);
  }

  private void setActiveTarget(TargetRef target) {
    this.activeTarget = target;
  }

  private TargetRef safeStatusTarget() {
    if (activeTarget != null) return new TargetRef(activeTarget.serverId(), "status");
    // Fallback if nothing is selected yet.
    String sid = serverRegistry.serverIds().stream().findFirst().orElse("default");
    return new TargetRef(sid, "status");
  }

  private String serverDisplay(String serverId) {
    return serverRegistry.find(serverId)
        .map(s -> serverId + "  (" + s.host() + ":" + s.port() + ")")
        .orElse(serverId);
  }
}
