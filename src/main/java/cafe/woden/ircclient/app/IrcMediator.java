package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.model.UserListStore;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Mediator.
 *
 */
@Component
@Lazy
public class IrcMediator {
  private final IrcClientService irc;
  private final UiPort ui;
  private final UserListStore userListStore;
  private final CommandParser commandParser;
  private final CompositeDisposable disposables = new CompositeDisposable();

  private String activeTarget = "status";

  public IrcMediator(
      IrcClientService irc,
      UiPort ui,
      UserListStore userListStore,
      CommandParser commandParser
  ) {
    this.irc = irc;
    this.ui = ui;
    this.userListStore = userListStore;
    this.commandParser = commandParser;
  }

  public void start() {
    ensureTargetExists("status");
    setActiveTarget("status");

    disposables.add(
        ui.targetSelections()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::onTargetSelected,
                err -> ui.appendError("status", "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.privateMessageRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::openPrivateConversation,
                err -> ui.appendError("status", "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.outboundLines()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::handleOutgoingLine,
                err -> ui.appendError("status", "(ui-error)", err.toString()))
    );

    disposables.add(
        irc.events()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::onIrcEvent,
                err -> ui.appendError("status", "(irc-error)", err.toString()))
    );

    disposables.add(
        ui.connectClicks()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(ignored -> {
              ui.setConnectionStatusText("Connecting…");
              ui.setConnectedUi(false);

              disposables.add(
                  irc.connect().subscribe(
                      () -> {},
                      err -> {
                        ui.appendError("status", "(conn-error)", String.valueOf(err));
                        ui.setConnectionStatusText("Connect failed");
                        ui.setConnectedUi(false);
                      }
                  )
              );
            })
    );

    disposables.add(
        ui.disconnectClicks()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(ignored -> {
              ui.setConnectionStatusText("Disconnecting…");

              disposables.add(
                  irc.disconnect().subscribe(
                      () -> {},
                      err -> ui.appendError("status", "(disc-error)", String.valueOf(err))
                  )
              );
            })
    );
  }

  public void stop() {
    disposables.dispose();
  }

  private void openPrivateConversation(String nick) {
    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) return;

    ensureTargetExists(n);
    ui.selectTarget(n);
  }

  private void onTargetSelected(String target) {
    if (target == null || target.isBlank()) return;

    ensureTargetExists(target);
    setActiveTarget(target);

    ui.setUsersChannel(activeTarget);
    ui.setStatusBarChannel(activeTarget);

    if (isChannel(activeTarget)) {
      int total = userListStore.userCount(activeTarget);
      int ops = userListStore.opCount(activeTarget);
      ui.setStatusBarCounts(total, ops);
      ui.setUsersNicks(userListStore.get(activeTarget));
    } else {
      ui.setStatusBarCounts(0, 0);
      ui.setUsersNicks(java.util.List.of());
    }

    // No cache? Request names on first view.
    if (isChannel(activeTarget) && !userListStore.has(activeTarget)) {
      irc.requestNames(activeTarget).subscribe(
          () -> {},
          err -> ui.appendError("status", "(names-error)", String.valueOf(err))
      );
    }

    ui.clearUnread(activeTarget);
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
          ui.appendStatus("status", "(system)", "Unknown command: " + cmd.raw());
    }
  }

  private void handleJoin(String channel) {
    String chan = channel == null ? "" : channel.trim();
    if (chan.isEmpty()) {
      ui.appendStatus("status", "(join)", "Usage: /join <#channel>");
      return;
    }

    irc.joinChannel(chan).subscribe(
        () -> {},
        err -> ui.appendError("status", "(join-error)", String.valueOf(err))
    );
  }

  private void handleNick(String newNick) {
    String nick = newNick == null ? "" : newNick.trim();
    if (nick.isEmpty()) {
      ui.appendStatus("status", "(nick)", "Usage: /nick <newNick>");
      return;
    }

    irc.changeNick(nick).subscribe(
        () -> ui.appendStatus("status", "(nick)", "Requested nick change to " + nick),
        err -> ui.appendError("status", "(nick-error)", String.valueOf(err))
    );
  }

  private void handleQuery(String nick) {
    String n = nick == null ? "" : nick.trim();
    if (n.isEmpty()) {
      ui.appendStatus("status", "(query)", "Usage: /query <nick>");
      return;
    }
    openPrivateConversation(n);
  }

  private void handleMsg(String nick, String body) {
    String n = nick == null ? "" : nick.trim();
    String m = body == null ? "" : body.trim();
    if (n.isEmpty() || m.isEmpty()) {
      ui.appendStatus("status", "(msg)", "Usage: /msg <nick> <message>");
      return;
    }
    openPrivateConversation(n);
    sendPrivate(n, m);
  }

  private void handleMe(String action) {
    String a = action == null ? "" : action.trim();
    if (a.isEmpty()) {
      ui.appendStatus("status", "(me)", "Usage: /me <action>");
      return;
    }

    if ("status".equals(activeTarget)) {
      ui.appendStatus("status", "(me)", "Select a channel or PM first.");
      return;
    }

    // TODO: Wire CTCP ACTION.
    String me = irc.currentNick().orElse("me");
    ui.appendChat(activeTarget, "* " + me, a);

    if (isChannel(activeTarget)) {
      irc.sendToChannel(activeTarget, a).subscribe(
          () -> {},
          err -> ui.appendError("status", "(send-error)", String.valueOf(err))
      );
    } else {
      irc.sendPrivateMessage(activeTarget, a).subscribe(
          () -> {},
          err -> ui.appendError("status", "(pm-send-error)", String.valueOf(err))
      );
    }
  }

  private void handleSay(String msg) {
    String m = msg == null ? "" : msg.trim();
    if (m.isEmpty()) return;

    if (isChannel(activeTarget)) {
      irc.sendToChannel(activeTarget, m).subscribe(
          () -> {},
          err -> ui.appendError("status", "(send-error)", String.valueOf(err))
      );

      String me = irc.currentNick().orElse("me");
      ui.appendChat(activeTarget, "(" + me + ")", m);
      return;
    }

    if (!"status".equals(activeTarget)) {
      sendPrivate(activeTarget, m);
      return;
    }

    ui.appendStatus("status", "(system)", "Select a channel, or double-click a nick to PM them.");
  }

  private void sendPrivate(String nick, String message) {
    String n = nick == null ? "" : nick.trim();
    String m = message == null ? "" : message.trim();
    if (n.isEmpty() || m.isEmpty()) return;

    ensureTargetExists(n);

    irc.sendPrivateMessage(n, m).subscribe(
        () -> {},
        err -> ui.appendError("status", "(pm-send-error)", String.valueOf(err))
    );

    String me = irc.currentNick().orElse("me");
    ui.appendChat(n, "(" + me + ")", m);
  }

  private void onIrcEvent(IrcEvent e) {
    switch (e) {
      case IrcEvent.Connected ev -> {
        ui.appendStatus("status", "(conn)", "Connected as " + ev.nick());
        ui.setChatCurrentNick(ev.nick());
        ui.setStatusBarServer(ev.serverHost() + ":" + ev.serverPort());
        ui.setConnectedUi(true);
        ui.setConnectionStatusText("Connected");
      }

      case IrcEvent.Disconnected ev -> {
        ui.appendStatus("status", "(conn)", "Disconnected: " + ev.reason());
        ui.setChatCurrentNick("");
        ui.setStatusBarServer("(disconnected)");
        ui.setConnectedUi(false);
        ui.setConnectionStatusText("Disconnected");
        ui.setStatusBarCounts(0, 0);
      }

      case IrcEvent.NickChanged ev -> {
        irc.currentNick().ifPresent(currentNick -> {
          if (!Objects.equals(currentNick, ev.oldNick()) && !Objects.equals(currentNick, ev.newNick())) {
            ui.appendNotice("status", "(nick)", ev.oldNick() + " is now known as " + ev.newNick());
          } else {
            ui.appendStatus("status", "(nick)", "Now known as " + ev.newNick());
            ui.setChatCurrentNick(ev.newNick());
          }
        });
      }

      case IrcEvent.ChannelMessage ev -> {
        ensureTargetExists(ev.channel());
        ui.appendChat(ev.channel(), ev.from(), ev.text());
        if (!ev.channel().equals(activeTarget)) ui.markUnread(ev.channel());
      }

      case IrcEvent.PrivateMessage ev -> {
        ensureTargetExists(ev.from());
        ui.appendChat(ev.from(), ev.from(), ev.text());
        if (!ev.from().equals(activeTarget)) ui.markUnread(ev.from());
      }

      case IrcEvent.Notice ev ->
          ui.appendNotice("status", "(notice) " + ev.from(), ev.text());

      case IrcEvent.JoinedChannel ev -> {
        ensureTargetExists(ev.channel());
        ui.appendStatus(ev.channel(), "(join)", "Joined " + ev.channel());
        ui.selectTarget(ev.channel());
      }

      case IrcEvent.NickListUpdated ev -> {
        userListStore.put(ev.channel(), ev.nicks());
        if (ev.channel().equals(activeTarget)) {
          ui.setUsersNicks(ev.nicks());
          ui.setStatusBarCounts(ev.totalUsers(), ev.operatorCount());
        }
      }

      case IrcEvent.Error ev ->
          ui.appendError("status", "(error)", ev.message());

      default -> {
      }
    }
  }

  private void ensureTargetExists(String target) {
    ui.ensureTargetExists(target);
  }

  private void setActiveTarget(String target) {
    this.activeTarget = target;
    ui.setChatActiveTarget(target);
  }

  private boolean isChannel(String target) {
    return target != null && (target.startsWith("#"));
    // TODO: Targets that start with ampersand &?
  }
}
