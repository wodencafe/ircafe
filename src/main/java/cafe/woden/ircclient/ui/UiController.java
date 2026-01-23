package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class UiController {
  private final IrcClientService irc;
  private final ServerTreeDockable serverTree;
  private final ChatDockable chat;
  private final UserListDockable users;
  private final MessageInputDockable input;
  private final UserListStore userListStore;
  private final StatusBar statusBar;
  private final ConnectButton connectBtn;
  private final DisconnectButton disconnectBtn;
  private final CompositeDisposable disposables = new CompositeDisposable();

  private String activeTarget = "status";

  public UiController(
      IrcClientService irc,
      ServerTreeDockable serverTree,
      ChatDockable chat,
      UserListDockable users,
      MessageInputDockable input,
      UserListStore userListStore,
      StatusBar statusBar,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn
  ) {
    this.irc = irc;
    this.serverTree = serverTree;
    this.chat = chat;
    this.users = users;
    this.input = input;
    this.userListStore = userListStore;
    this.statusBar = statusBar;
    this.connectBtn = connectBtn;
    this.disconnectBtn = disconnectBtn;
  }

  public void start() {
    ensureTargetExists("status");
    setActiveTarget("status");

    disposables.add(
        serverTree.selectionStream()
            .observeOn(SwingEdt.scheduler())
            .subscribe(this::onTargetSelected,
                err -> chat.appendError("status", "(ui-error)", err.toString()))
    );

    disposables.add(
        users.privateMessageRequests()
            .observeOn(SwingEdt.scheduler())
            .subscribe(this::openPrivateConversation,
                err -> chat.appendError("status", "(ui-error)", err.toString()))
    );

    disposables.add(
        input.outboundMessages()
            .observeOn(SwingEdt.scheduler())
            .subscribe(this::handleOutgoingLine,
                err -> chat.appendError("status", "(ui-error)", err.toString()))
    );

    disposables.add(
        irc.events()
            .observeOn(SwingEdt.scheduler())
            .subscribe(this::onIrcEvent,
                err -> chat.appendError("status", "(irc-error)", err.toString()))
    );

    // Connect button
    disposables.add(
        connectBtn.onClick()
            .observeOn(SwingEdt.scheduler())
            .subscribe(ignored -> {
              serverTree.setStatusText("Connecting…");
              serverTree.setConnectedUi(false);

              disposables.add(
                  irc.connect().subscribe(
                      () -> {},
                      err -> {
                        chat.appendError("status", "(conn-error)", String.valueOf(err));
                        serverTree.setStatusText("Connect failed");
                        serverTree.setConnectedUi(false);
                      }
                  )
              );
            })
    );

    // Disconnect button
    disposables.add(
        disconnectBtn.onClick()
            .observeOn(SwingEdt.scheduler())
            .subscribe(ignored -> {
              serverTree.setStatusText("Disconnecting…");

              disposables.add(
                  irc.disconnect().subscribe(
                      () -> {},
                      err -> chat.appendError("status", "(disc-error)", String.valueOf(err))
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
    serverTree.selectTarget(n);
  }

  private void onTargetSelected(String target) {
    if (target == null || target.isBlank()) return;

    ensureTargetExists(target);
    setActiveTarget(target);

    users.setChannel(activeTarget);

    statusBar.setChannel(activeTarget);
    if (isChannel(activeTarget)) {
      int total = userListStore.userCount(activeTarget);
      int ops = userListStore.opCount(activeTarget);
      statusBar.setCounts(total, ops);
      users.setNicks(userListStore.get(activeTarget));
    } else {
      statusBar.setCounts(0, 0);
      users.setNicks(java.util.List.of());
    }

    // No cache? Request names on first view.
    if (isChannel(activeTarget) && !userListStore.has(activeTarget)) {
      irc.requestNames(activeTarget).subscribe(
          () -> {},
          err -> chat.appendError("status", "(names-error)", String.valueOf(err))
      );
    }

    serverTree.clearUnread(activeTarget);
  }

  private void handleOutgoingLine(String raw) {
    String msg = raw == null ? "" : raw.trim();
    if (msg.isEmpty()) return;

    if (msg.startsWith("/join ")) {
      String chan = msg.substring("/join ".length()).trim();
      if (!chan.isEmpty()) irc.joinChannel(chan).subscribe(
          () -> {},
          err -> chat.appendError("status", "(join-error)", String.valueOf(err))
      );
      return;
    }

    if (msg.startsWith("/nick ")) {
      String newNick = msg.substring("/nick ".length()).trim();
      if (newNick.isEmpty()) {
        chat.appendStatus("status", "(nick)", "Usage: /nick <newNick>");
        return;
      }

      irc.changeNick(newNick).subscribe(
          () -> chat.appendStatus("status", "(nick)", "Requested nick change to " + newNick),
          err -> chat.appendError("status", "(nick-error)", String.valueOf(err))
      );
      return;
    }

    if (msg.startsWith("/query ")) {
      String nick = msg.substring("/query ".length()).trim();
      if (nick.isEmpty()) {
        chat.appendStatus("status", "(query)", "Usage: /query <nick>");
        return;
      }
      openPrivateConversation(nick);
      return;
    }

    if (msg.startsWith("/msg ")) {
      String rest = msg.substring("/msg ".length()).trim();
      int sp = rest.indexOf(' ');
      if (sp <= 0) {
        chat.appendStatus("status", "(msg)", "Usage: /msg <nick> <message>");
        return;
      }
      String nick = rest.substring(0, sp).trim();
      String body = rest.substring(sp + 1).trim();
      if (nick.isEmpty() || body.isEmpty()) {
        chat.appendStatus("status", "(msg)", "Usage: /msg <nick> <message>");
        return;
      }
      openPrivateConversation(nick);
      sendPrivate(nick, body);
      return;
    }

    if (isChannel(activeTarget)) {
      irc.sendToChannel(activeTarget, msg).subscribe(
          () -> {},
          err -> chat.appendError("status", "(send-error)", String.valueOf(err))
      );

      String me = irc.currentNick().orElse("me");
      chat.append(activeTarget, "(" + me + ")", msg);
      return;
    }

    if (!"status".equals(activeTarget)) {
      sendPrivate(activeTarget, msg);
      return;
    }

    chat.appendStatus("status", "(system)", "Select a channel, or double-click a nick to PM them.");
  }

  private void sendPrivate(String nick, String message) {
    String n = nick == null ? "" : nick.trim();
    String m = message == null ? "" : message.trim();
    if (n.isEmpty() || m.isEmpty()) return;

    ensureTargetExists(n);

    irc.sendPrivateMessage(n, m).subscribe(
        () -> {},
        err -> chat.appendError("status", "(pm-send-error)", String.valueOf(err))
    );

    String me = irc.currentNick().orElse("me");
    chat.append(n, "(" + me + ")", m);
  }

  private void onIrcEvent(IrcEvent e) {
    switch (e) {
      case IrcEvent.Connected ev -> {
        chat.appendStatus("status", "(conn)", "Connected as " + ev.nick());
        chat.setCurrentNick(ev.nick());
        statusBar.setServer(ev.serverHost() + ":" + ev.serverPort());
        serverTree.setConnectedUi(true);
        serverTree.setStatusText("Connected");
      }

      case IrcEvent.Disconnected ev -> {
        chat.appendStatus("status", "(conn)", "Disconnected: " + ev.reason());
        chat.setCurrentNick("");
        statusBar.setServer("(disconnected)");
        serverTree.setConnectedUi(false);
        serverTree.setStatusText("Disconnected");
        statusBar.setCounts(0, 0);
      }

      case IrcEvent.NickChanged ev -> {
        irc.currentNick().ifPresent(currentNick -> {
          if (!Objects.equals(currentNick, ev.oldNick()) && !Objects.equals(currentNick, ev.newNick())) {
            chat.appendNotice("status", "(nick)", ev.oldNick() + " is now known as " + ev.newNick());
          } else {
            chat.appendStatus("status", "(nick)", "Now known as " + ev.newNick());
            chat.setCurrentNick(ev.newNick());
          }
        });
      }

      case IrcEvent.ChannelMessage ev -> {
        ensureTargetExists(ev.channel());
        chat.append(ev.channel(), ev.from(), ev.text());
        if (!ev.channel().equals(activeTarget)) serverTree.markUnread(ev.channel());
      }

      case IrcEvent.PrivateMessage ev -> {
        ensureTargetExists(ev.from());
        chat.append(ev.from(), ev.from(), ev.text());
        if (!ev.from().equals(activeTarget)) serverTree.markUnread(ev.from());
      }

      case IrcEvent.Notice ev ->
          chat.appendNotice("status", "(notice) " + ev.from(), ev.text());

      case IrcEvent.JoinedChannel ev -> {
        ensureTargetExists(ev.channel());
        chat.appendStatus(ev.channel(), "(join)", "Joined " + ev.channel());
        serverTree.selectTarget(ev.channel());
      }

      case IrcEvent.NickListUpdated ev -> {
        userListStore.put(ev.channel(), ev.nicks());
        if (ev.channel().equals(activeTarget)) {
          users.setNicks(ev.nicks());
          statusBar.setCounts(ev.totalUsers(), ev.operatorCount());
        }
      }

      case IrcEvent.Error ev ->
          chat.appendError("status", "(error)", ev.message());

      default -> {
      }
    }
  }

  private void ensureTargetExists(String target) {
    chat.ensureBuffer(target);
    serverTree.ensureNode(target);
  }

  private void setActiveTarget(String target) {
    this.activeTarget = target;
    chat.setActiveTarget(target);
  }

  private boolean isChannel(String target) {
    return target != null && (target.startsWith("#"));
    // TODO: Targets that start with ampersand &?
  }
}
