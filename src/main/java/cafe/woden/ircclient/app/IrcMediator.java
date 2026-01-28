package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Objects;
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
  private final CommandParser commandParser;
  private final ServerRegistry serverRegistry;
  private final RuntimeConfigStore runtimeConfig;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final CompositeDisposable disposables = new CompositeDisposable();

  private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean(false);

  // Active target state is owned by TargetCoordinator.

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
      CommandParser commandParser,
      ServerRegistry serverRegistry,
      RuntimeConfigStore runtimeConfig,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator
  ) {
    this.irc = irc;
    this.ui = ui;
    this.commandParser = commandParser;
    this.serverRegistry = serverRegistry;
    this.runtimeConfig = runtimeConfig;
    this.connectionCoordinator = connectionCoordinator;
    this.targetCoordinator = targetCoordinator;
  }

  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }

    disposables.add(
        ui.targetSelections()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::onTargetSelected,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.targetActivations()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::onTargetActivated,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.privateMessageRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::openPrivateConversation,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.outboundLines()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::handleOutgoingLine,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        irc.events()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::onServerIrcEvent,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(irc-error)", err.toString()))
    );

    disposables.add(
        ui.connectClicks()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(ignored -> connectionCoordinator.connectAll())
    );

    disposables.add(
        ui.disconnectClicks()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(ignored -> connectionCoordinator.disconnectAll())
    );

    disposables.add(
        ui.connectServerRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(connectionCoordinator::connectOne,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    disposables.add(
        ui.disconnectServerRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(connectionCoordinator::disconnectOne,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    disposables.add(
        ui.closeTargetRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::closeTarget,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    // React to runtime server list edits.
    disposables.add(
        serverRegistry.updates()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(latest -> {
                  connectionCoordinator.onServersUpdated(latest, targetCoordinator.getActiveTarget());
                  targetCoordinator.refreshInputEnabledForActiveTarget();
                },
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", "Server list update failed: " + err))
    );
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
    connectionCoordinator.connectAll();
  }

  /** Disconnect from all configured servers. */
  public void disconnectAll() {
    connectionCoordinator.disconnectAll();
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
    TargetRef at = targetCoordinator.getActiveTarget();
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

    if (!connectionCoordinator.isConnected(at.serverId())) {
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

  private void handleNick(String newNick) {
    TargetRef at = targetCoordinator.getActiveTarget();
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

    if (!connectionCoordinator.isConnected(at.serverId())) {
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
    TargetRef at = targetCoordinator.getActiveTarget();
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
    TargetRef at = targetCoordinator.getActiveTarget();
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
    TargetRef at = targetCoordinator.getActiveTarget();
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

    if (!connectionCoordinator.isConnected(at.serverId())) {
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
    TargetRef at = targetCoordinator.getActiveTarget();
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

    if (!connectionCoordinator.isConnected(target.serverId())) {
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

    // Delegate connectivity state changes.
    if (e instanceof IrcEvent.Connected
        || e instanceof IrcEvent.Reconnecting
        || e instanceof IrcEvent.Disconnected) {
      connectionCoordinator.handleConnectivityEvent(sid, e, targetCoordinator.getActiveTarget());
      if (e instanceof IrcEvent.Disconnected) {
        targetCoordinator.onServerDisconnected(sid);
      }
      targetCoordinator.refreshInputEnabledForActiveTarget();
      return;
    }

    switch (e) {
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
        if (!chan.equals(targetCoordinator.getActiveTarget())) ui.markUnread(chan);
      }

      case IrcEvent.ChannelTopicUpdated ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.setChannelTopic(chan, ev.topic());
      }

      case IrcEvent.PrivateMessage ev -> {
        TargetRef pm = new TargetRef(sid, ev.from());
        ensureTargetExists(pm);
        ui.appendChat(pm, ev.from(), ev.text());
        if (!pm.equals(targetCoordinator.getActiveTarget())) ui.markUnread(pm);
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
        targetCoordinator.onNickListUpdated(sid, ev);
      }

      case IrcEvent.Error ev ->
          ui.appendError(status, "(error)", ev.message());

      default -> {
      }
    }
  }

  private void ensureTargetExists(TargetRef target) {
    ui.ensureTargetExists(target);
  }

  private TargetRef safeStatusTarget() {
    return targetCoordinator.safeStatusTarget();
  }
}
