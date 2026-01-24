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
  Flowable<PrivateMessageRequest> privateMessageRequests();
  Flowable<String> outboundLines();
  Flowable<Object> connectClicks();
  Flowable<Object> disconnectClicks();

  /** Per-server connect/disconnect requests initiated from the server tree context menu. */
  Flowable<String> connectServerRequests();
  Flowable<String> disconnectServerRequests();

  // Rendering and view updates.
  void ensureTargetExists(TargetRef target);
  void selectTarget(TargetRef target);
  void markUnread(TargetRef target);
  void clearUnread(TargetRef target);

  void setChatActiveTarget(TargetRef target);
  void setChatCurrentNick(String serverId, String nick);

  void setUsersChannel(TargetRef target);
  void setUsersNicks(List<NickInfo> nicks);

  void setStatusBarChannel(String channel);
  void setStatusBarCounts(int users, int ops);
  void setStatusBarServer(String serverText);

  void setConnectedUi(boolean connected);
  void setConnectionStatusText(String text);

  /** Update per-server connection state (used to enable/disable context menu items). */
  void setServerConnected(String serverId, boolean connected);

  void appendChat(TargetRef target, String from, String text);
  void appendNotice(TargetRef target, String from, String text);
  void appendStatus(TargetRef target, String from, String text);
  void appendError(TargetRef target, String from, String text);
}
