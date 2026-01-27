package cafe.woden.ircclient.app;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;

/**
 * Boundary between application logic and the Swing UI.
 *
 */
public interface UiPort {
  // User-initiated stuff.
  Flowable<TargetRef> targetSelections();
  /**
   * Requests to activate a target for input/status/user list updates without changing
   * the main Chat dock's displayed transcript.
   *
   * <p>Used by pinned chat docks so you can read multiple channels at once.
   */
  Flowable<TargetRef> targetActivations();

  Flowable<PrivateMessageRequest> privateMessageRequests();
  Flowable<String> outboundLines();
  Flowable<Object> connectClicks();
  Flowable<Object> disconnectClicks();

  Flowable<String> connectServerRequests();
  Flowable<String> disconnectServerRequests();

  Flowable<TargetRef> closeTargetRequests();

  // Rendering and view updates.
  void ensureTargetExists(TargetRef target);
  void selectTarget(TargetRef target);

  void closeTarget(TargetRef target);
  void markUnread(TargetRef target);
  void clearUnread(TargetRef target);

  void setChatActiveTarget(TargetRef target);
  void setChatCurrentNick(String serverId, String nick);

  /** Update the known topic for a channel target (shown in the main chat view when selected). */
  void setChannelTopic(TargetRef target, String topic);

  void setUsersChannel(TargetRef target);
  void setUsersNicks(List<NickInfo> nicks);

  void setStatusBarChannel(String channel);
  void setStatusBarCounts(int users, int ops);
  void setStatusBarServer(String serverText);

  void setConnectedUi(boolean connected);
  void setConnectionStatusText(String text);

  void setServerConnected(String serverId, boolean connected);

  /** Enable/disable the global input bar. */
  void setInputEnabled(boolean enabled);

  void appendChat(TargetRef target, String from, String text);
  void appendNotice(TargetRef target, String from, String text);
  void appendStatus(TargetRef target, String from, String text);
  void appendError(TargetRef target, String from, String text);
}
