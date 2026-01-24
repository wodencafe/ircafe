package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.PrivateMessageRequest;
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
  public Flowable<TargetRef> targetSelections() {
    return serverTree.selectionStream();
  }

  @Override
  public Flowable<PrivateMessageRequest> privateMessageRequests() {
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
  public Flowable<String> connectServerRequests() {
    return serverTree.connectServerRequests();
  }

  @Override
  public Flowable<String> disconnectServerRequests() {
    return serverTree.disconnectServerRequests();
  }

  @Override
  public void ensureTargetExists(TargetRef target) {
    chat.ensureBuffer(target);
    serverTree.ensureNode(target);
  }

  @Override
  public void selectTarget(TargetRef target) {
    serverTree.selectTarget(target);
  }

  @Override
  public void markUnread(TargetRef target) {
    serverTree.markUnread(target);
  }

  @Override
  public void clearUnread(TargetRef target) {
    serverTree.clearUnread(target);
  }

  @Override
  public void setChatActiveTarget(TargetRef target) {
    chat.setActiveTarget(target);
  }

  @Override
  public void setChatCurrentNick(String serverId, String nick) {
    chat.setCurrentNick(serverId, nick);
  }

  @Override
  public void setUsersChannel(TargetRef target) {
    users.setChannel(target);
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
  public void setServerConnected(String serverId, boolean connected) {
    serverTree.setServerConnected(serverId, connected);
  }

  @Override
  public void appendChat(TargetRef target, String from, String text) {
    chat.append(target, from, text);
  }

  @Override
  public void appendNotice(TargetRef target, String from, String text) {
    chat.appendNotice(target, from, text);
  }

  @Override
  public void appendStatus(TargetRef target, String from, String text) {
    chat.appendStatus(target, from, text);
  }

  @Override
  public void appendError(TargetRef target, String from, String text) {
    chat.appendError(target, from, text);
  }
}
