package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.PrivateMessageRequest;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.MentionPatternRegistry;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import javax.swing.SwingUtilities;
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
  private final ChatTranscriptStore transcripts;
  private final MentionPatternRegistry mentions;
  private final UserListDockable users;
  private final MessageInputDockable input;
  private final StatusBar statusBar;
  private final ConnectButton connectBtn;
  private final DisconnectButton disconnectBtn;
  private final TargetActivationBus activationBus;


  private void onEdt(Runnable r) {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
    } else {
      SwingUtilities.invokeLater(r);
    }
  }

  public SwingUiPort(
      ServerTreeDockable serverTree,
      ChatDockable chat,
      ChatTranscriptStore transcripts,
      MentionPatternRegistry mentions,
      UserListDockable users,
      MessageInputDockable input,
      StatusBar statusBar,
      ConnectButton connectBtn,
      DisconnectButton disconnectBtn,
      TargetActivationBus activationBus
  ) {
    this.serverTree = serverTree;
    this.chat = chat;
    this.transcripts = transcripts;
    this.mentions = mentions;
    this.users = users;
    this.input = input;
    this.statusBar = statusBar;
    this.connectBtn = connectBtn;
    this.disconnectBtn = disconnectBtn;
    this.activationBus = activationBus;
  }

  @Override
  public Flowable<TargetRef> targetSelections() {
    return serverTree.selectionStream();
  }

  @Override
  public Flowable<TargetRef> targetActivations() {
    return activationBus.stream();
  }

  @Override
  public Flowable<PrivateMessageRequest> privateMessageRequests() {
    // Users can request PMs from multiple UI surfaces (user list, transcript nick clicks, etc.)
    return Flowable.merge(
        users.privateMessageRequests(),
        chat.privateMessageRequests()
    );
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
  public Flowable<TargetRef> closeTargetRequests() {
    return serverTree.closeTargetRequests();
  }

  @Override
  public void ensureTargetExists(TargetRef target) {
    onEdt(() -> {
      transcripts.ensureTargetExists(target);
      serverTree.ensureNode(target);
    });
  }

  @Override
  public void selectTarget(TargetRef target) {
    onEdt(() -> serverTree.selectTarget(target));
  }

  @Override
  public void closeTarget(TargetRef target) {
    onEdt(() -> {
      serverTree.removeTarget(target);
      chat.clearTopic(target);
      transcripts.closeTarget(target);
    });
  }

  @Override
  public void markUnread(TargetRef target) {
    onEdt(() -> serverTree.markUnread(target));
  }

  @Override
  public void clearUnread(TargetRef target) {
    onEdt(() -> serverTree.clearUnread(target));
  }

  @Override
  public void setChatActiveTarget(TargetRef target) {
    onEdt(() -> chat.setActiveTarget(target));
  }

  @Override
  public void setChatCurrentNick(String serverId, String nick) {
    onEdt(() -> mentions.setCurrentNick(serverId, nick));
  }

  @Override
  public void setChannelTopic(TargetRef target, String topic) {
    onEdt(() -> chat.setTopic(target, topic));
  }

  @Override
  public void setUsersChannel(TargetRef target) {
    onEdt(() -> users.setChannel(target));
  }

  @Override
  public void setUsersNicks(List<NickInfo> nicks) {
    onEdt(() -> users.setNicks(nicks));
  }

  @Override
  public void setStatusBarChannel(String channel) {
    onEdt(() -> statusBar.setChannel(channel));
  }

  @Override
  public void setStatusBarCounts(int users, int ops) {
    onEdt(() -> statusBar.setCounts(users, ops));
  }

  @Override
  public void setStatusBarServer(String serverText) {
    onEdt(() -> statusBar.setServer(serverText));
  }

  @Override
  public void setConnectedUi(boolean connected) {
    onEdt(() -> serverTree.setConnectedUi(connected));
  }

  @Override
  public void setConnectionStatusText(String text) {
    onEdt(() -> serverTree.setStatusText(text));
  }

  @Override
  public void setServerConnected(String serverId, boolean connected) {
    onEdt(() -> serverTree.setServerConnected(serverId, connected));
  }

  @Override
  public void setInputEnabled(boolean enabled) {
    onEdt(() -> input.setInputEnabled(enabled));
  }

  @Override
  public void appendChat(TargetRef target, String from, String text) {
    onEdt(() -> transcripts.appendChat(target, from, text));
  }

  @Override
  public void appendNotice(TargetRef target, String from, String text) {
    onEdt(() -> transcripts.appendNotice(target, from, text));
  }

  @Override
  public void appendStatus(TargetRef target, String from, String text) {
    onEdt(() -> transcripts.appendStatus(target, from, text));
  }

  @Override
  public void appendError(TargetRef target, String from, String text) {
    onEdt(() -> transcripts.appendError(target, from, text));
  }
}
