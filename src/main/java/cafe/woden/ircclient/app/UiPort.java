package cafe.woden.ircclient.app;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Instant;
import java.util.List;

/**
 * Boundary between application logic and the Swing UI.
 */
public interface UiPort {
  // User-initiated stuff.
  Flowable<TargetRef> targetSelections();
  /** Requests to activate a target for input/status/user list updates without changing the main Chat dock's displayed transcript. */
Flowable<TargetRef> targetActivations();

  Flowable<PrivateMessageRequest> privateMessageRequests();

  Flowable<UserActionRequest> userActionRequests();

  Flowable<String> outboundLines();
  Flowable<Object> connectClicks();
  Flowable<Object> disconnectClicks();

  Flowable<String> connectServerRequests();
  Flowable<String> disconnectServerRequests();

  Flowable<TargetRef> closeTargetRequests();

  Flowable<TargetRef> clearLogRequests();

  // Rendering and view updates.
  void ensureTargetExists(TargetRef target);
  void selectTarget(TargetRef target);

  void closeTarget(TargetRef target);
  void markUnread(TargetRef target);
  void markHighlight(TargetRef target);

  /**
   * Record a highlight notification (for the per-server Notifications view).
   *
   * <p>This should be called when the user is mentioned in a channel message/action and that
   * highlight is considered unread (e.g. the channel is not currently active).</p>
   */
  void recordHighlight(TargetRef target, String fromNick);

  void clearUnread(TargetRef target);

  void clearTranscript(TargetRef target);

  void setChatActiveTarget(TargetRef target);
  void setChatCurrentNick(String serverId, String nick);

  
  void setChannelTopic(TargetRef target, String topic);

  void setUsersChannel(TargetRef target);
  void setUsersNicks(List<NickInfo> nicks);

  void setStatusBarChannel(String channel);
  void setStatusBarCounts(int users, int ops);
  void setStatusBarServer(String serverText);

  
  void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled);

  
  void setConnectionStatusText(String text);

  void setServerConnectionState(String serverId, ConnectionState state);

  
  void setInputEnabled(boolean enabled);

  /**
   * Append a chat message line.
   *
   * <p>By default this is treated as an <em>incoming</em> line. Use the overload with
   * {@code outgoingLocalEcho=true} for locally-echoed lines you just sent.</p>
   */
  default void appendChat(TargetRef target, String from, String text) {
    appendChat(target, from, text, false);
  }

  /**
   * Append a chat message line.
   *
   * @param outgoingLocalEcho true when the line is locally echoed (you just sent it)
   */
  void appendChat(TargetRef target, String from, String text, boolean outgoingLocalEcho);

  /**
   * Append a chat message line with an explicit timestamp (e.g. IRCv3 server-time).
   *
   * <p>Default implementation falls back to {@link #appendChat(TargetRef, String, String, boolean)}.
   */
  default void appendChatAt(TargetRef target, Instant at, String from, String text, boolean outgoingLocalEcho) {
    appendChat(target, from, text, outgoingLocalEcho);
  }

  
  void appendSpoilerChat(TargetRef target, String from, String text);

  /** Append a spoiler chat line with an explicit timestamp. */
  default void appendSpoilerChatAt(TargetRef target, Instant at, String from, String text) {
    appendSpoilerChat(target, from, text);
  }

  default void appendAction(TargetRef target, String from, String action) {
    appendAction(target, from, action, false);
  }

  void appendAction(TargetRef target, String from, String action, boolean outgoingLocalEcho);

  /** Append an action line with an explicit timestamp. */
  default void appendActionAt(TargetRef target, Instant at, String from, String action, boolean outgoingLocalEcho) {
    appendAction(target, from, action, outgoingLocalEcho);
  }

  void appendPresence(TargetRef target, PresenceEvent event);

  void appendNotice(TargetRef target, String from, String text);
  void appendStatus(TargetRef target, String from, String text);
  void appendError(TargetRef target, String from, String text);

  default void appendNoticeAt(TargetRef target, Instant at, String from, String text) {
    appendNotice(target, from, text);
  }

  default void appendStatusAt(TargetRef target, Instant at, String from, String text) {
    appendStatus(target, from, text);
  }

  default void appendErrorAt(TargetRef target, Instant at, String from, String text) {
    appendError(target, from, text);
  }
}
