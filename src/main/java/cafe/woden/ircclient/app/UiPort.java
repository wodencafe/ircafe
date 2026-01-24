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
  Flowable<String> targetSelections();
  Flowable<String> privateMessageRequests();
  Flowable<String> outboundLines();
  Flowable<Object> connectClicks();
  Flowable<Object> disconnectClicks();

  // Rendering and view updates.
  void ensureTargetExists(String target);
  void selectTarget(String target);
  void markUnread(String target);
  void clearUnread(String target);

  void setChatActiveTarget(String target);
  void setChatCurrentNick(String nick);

  void setUsersChannel(String channel);
  void setUsersNicks(List<NickInfo> nicks);

  void setStatusBarChannel(String channel);
  void setStatusBarCounts(int users, int ops);
  void setStatusBarServer(String serverText);

  void setConnectedUi(boolean connected);
  void setConnectionStatusText(String text);

  void appendChat(String target, String from, String text);
  void appendNotice(String target, String from, String text);
  void appendStatus(String target, String from, String text);
  void appendError(String target, String from, String text);
}
