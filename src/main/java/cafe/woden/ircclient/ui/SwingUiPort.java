package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Adapter that exposes the Swing UI to the application layer.
 *
 */
@Component
@Lazy
public class SwingUiPort implements UiPort {
  private final ServerTreeDockable serverTree;
  private final ChatDockable chat;
  private final UserListDockable users;
  private final MessageInputDockable input;
  private final StatusBar statusBar;
  private final ConnectButton connectBtn;
  private final DisconnectButton disconnectBtn;

  public SwingUiPort(
      ServerTreeDockable serverTree,
      ChatDockable chat,
      UserListDockable users,
      MessageInputDockable input,
      StatusBar statusBar,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn
  ) {
    this.serverTree = serverTree;
    this.chat = chat;
    this.users = users;
    this.input = input;
    this.statusBar = statusBar;
    this.connectBtn = connectBtn;
    this.disconnectBtn = disconnectBtn;
  }

  @Override
  public Flowable<String> targetSelections() {
    return serverTree.selectionStream();
  }

  @Override
  public Flowable<String> privateMessageRequests() {
    return users.privateMessageRequests();
  }

  @Override
  public Flowable<String> outboundLines() {
    return input.outboundMessages();
  }

  @Override
  public Flowable<Object> connectClicks() {
    return connectBtn.onClick();
  }

  @Override
  public Flowable<Object> disconnectClicks() {
    return disconnectBtn.onClick();
  }

  @Override
  public void ensureTargetExists(String target) {
    chat.ensureBuffer(target);
    serverTree.ensureNode(target);
  }

  @Override
  public void selectTarget(String target) {
    serverTree.selectTarget(target);
  }

  @Override
  public void markUnread(String target) {
    serverTree.markUnread(target);
  }

  @Override
  public void clearUnread(String target) {
    serverTree.clearUnread(target);
  }

  @Override
  public void setChatActiveTarget(String target) {
    chat.setActiveTarget(target);
  }

  @Override
  public void setChatCurrentNick(String nick) {
    chat.setCurrentNick(nick);
  }

  @Override
  public void setUsersChannel(String channel) {
    users.setChannel(channel);
  }

  @Override
  public void setUsersNicks(List<NickInfo> nicks) {
    users.setNicks(nicks);
  }

  @Override
  public void setStatusBarChannel(String channel) {
    statusBar.setChannel(channel);
  }

  @Override
  public void setStatusBarCounts(int users, int ops) {
    statusBar.setCounts(users, ops);
  }

  @Override
  public void setStatusBarServer(String serverText) {
    statusBar.setServer(serverText);
  }

  @Override
  public void setConnectedUi(boolean connected) {
    serverTree.setConnectedUi(connected);
  }

  @Override
  public void setConnectionStatusText(String text) {
    serverTree.setStatusText(text);
  }

  @Override
  public void appendChat(String target, String from, String text) {
    chat.append(target, from, text);
  }

  @Override
  public void appendNotice(String target, String from, String text) {
    chat.appendNotice(target, from, text);
  }

  @Override
  public void appendStatus(String target, String from, String text) {
    chat.appendStatus(target, from, text);
  }

  @Override
  public void appendError(String target, String from, String text) {
    chat.appendError(target, from, text);
  }
}
