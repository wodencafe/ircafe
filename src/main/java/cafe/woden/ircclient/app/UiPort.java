package cafe.woden.ircclient.app;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;

/**
 * Boundary between application logic and the Swing UI.
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

  /** UI-originated context menu actions (e.g., WHOIS/CTCP) for a nick. */
  Flowable<UserActionRequest> userActionRequests();

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

  /** Enable/disable the global Connect/Disconnect buttons. */
  void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled);

  /** Text shown next to the global connect/disconnect controls. */
  void setConnectionStatusText(String text);

  /** Per-server connection state for context menu enabling/disabling. */
  void setServerConnectionState(String serverId, ConnectionState state);

  /** Enable/disable the global input bar. */
  void setInputEnabled(boolean enabled);

  void appendChat(TargetRef target, String from, String text);

  /** Append a chat message as a click-to-reveal spoiler block (used by soft-ignore). */
  void appendSpoilerChat(TargetRef target, String from, String text);

  /** Append a CTCP ACTION (/me) line (rendered as '* nick action'). */
  void appendAction(TargetRef target, String from, String action);

  /** Append a foldable presence/system event (join/part/quit/nick) in a channel transcript. */
  void appendPresence(TargetRef target, PresenceEvent event);

  void appendNotice(TargetRef target, String from, String text);
  void appendStatus(TargetRef target, String from, String text);
  void appendError(TargetRef target, String from, String text);
}
