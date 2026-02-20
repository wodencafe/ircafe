package cafe.woden.ircclient.app;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

  /**
   * Record a notification rule match (for the per-server Notifications view).
   *
   * <p>This should be called when an inbound channel message/action matches a user-configured
   * WORD/REGEX rule and that match is considered unread (e.g. the channel is not currently active).</p>
   */
  void recordRuleMatch(TargetRef target, String fromNick, String ruleLabel, String snippet);

  void clearUnread(TargetRef target);

  void clearTranscript(TargetRef target);

  void setChatActiveTarget(TargetRef target);
  void setChatCurrentNick(String serverId, String nick);

  
  void setChannelTopic(TargetRef target, String topic);

  void setUsersChannel(TargetRef target);
  void setUsersNicks(List<NickInfo> nicks);

  /** Reset/prepare the per-server channel list view for a new /LIST response stream. */
  default void beginChannelList(String serverId, String banner) {}

  /** Append one channel row to the per-server channel list view. */
  default void appendChannelListEntry(String serverId, String channel, int visibleUsers, String topic) {}

  /** Mark completion of a /LIST response stream for the given server. */
  default void endChannelList(String serverId, String summary) {}

  void setStatusBarChannel(String channel);
  void setStatusBarCounts(int users, int ops);
  void setStatusBarServer(String serverText);

  
  void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled);

  
  void setConnectionStatusText(String text);

  void setServerConnectionState(String serverId, ConnectionState state);

  /**
   * Update desired connection intent for a server.
   *
   * <p>{@code true} means the app should keep this server online; {@code false} means it should
   * stay disconnected.</p>
   */
  default void setServerDesiredOnline(String serverId, boolean desiredOnline) {}

  /**
   * Update best-effort online state for a private-message target icon in the server tree.
   */
  default void setPrivateMessageOnlineState(String serverId, String nick, boolean online) {}

  /**
   * Clear all cached private-message online states for one server.
   */
  default void clearPrivateMessageOnlineStates(String serverId) {}

  
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

  /**
   * Append a chat message line with an explicit timestamp and IRCv3 identity metadata.
   *
   * <p>Default implementation falls back to {@link #appendChatAt(TargetRef, Instant, String, String, boolean)}.
   */
  default void appendChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    appendChatAt(target, at, from, text, outgoingLocalEcho);
  }

  /**
   * Append an outbound chat line in a temporary "pending send" state while waiting for server echo.
   */
  default void appendPendingOutgoingChat(
      TargetRef target,
      String pendingId,
      Instant at,
      String from,
      String text
  ) {
    appendChatAt(target, at, from, text + " [pending]", true);
  }

  /**
   * Replace a pending outbound line with the canonical server-echoed copy.
   *
   * @return true when the pending line was found/replaced.
   */
  default boolean resolvePendingOutgoingChat(
      TargetRef target,
      String pendingId,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    return false;
  }

  /**
   * Mark a pending outbound line as failed.
   */
  default void failPendingOutgoingChat(
      TargetRef target,
      String pendingId,
      Instant at,
      String from,
      String text,
      String reason
  ) {
    appendErrorAt(target, at, "(send-error)", "Failed to send: " + text);
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

  /** Append an action line with an explicit timestamp and IRCv3 identity metadata. */
  default void appendActionAt(
      TargetRef target,
      Instant at,
      String from,
      String action,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    appendActionAt(target, at, from, action, outgoingLocalEcho);
  }

  void appendPresence(TargetRef target, PresenceEvent event);

  void appendNotice(TargetRef target, String from, String text);
  void appendStatus(TargetRef target, String from, String text);
  void appendError(TargetRef target, String from, String text);

  default void appendNoticeAt(TargetRef target, Instant at, String from, String text) {
    appendNotice(target, from, text);
  }

  /** Append a notice line with an explicit timestamp and IRCv3 identity metadata. */
  default void appendNoticeAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    appendNoticeAt(target, at, from, text);
  }

  default void appendStatusAt(TargetRef target, Instant at, String from, String text) {
    appendStatus(target, from, text);
  }

  /** Append a status line with an explicit timestamp and IRCv3 identity metadata. */
  default void appendStatusAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
    appendStatusAt(target, at, from, text);
  }

  default void appendErrorAt(TargetRef target, Instant at, String from, String text) {
    appendError(target, from, text);
  }

  /**
   * Show an ephemeral IRCv3 typing indicator for a target.
   *
   * <p>This is intentionally non-transcript UI state.
   */
  default void showTypingIndicator(TargetRef target, String nick, String state) {}

  /**
   * Update the read-marker boundary for a target using epoch milliseconds.
   */
  default void setReadMarker(TargetRef target, long markerEpochMs) {}

  /**
   * Update inline reaction state for a target message identified by IRCv3 {@code msgid}.
   */
  default void applyMessageReaction(
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String reaction
  ) {}

  /**
   * Check whether the transcript line identified by IRCv3 {@code msgid} belongs to the local user.
   *
   * <p>Implementations typically infer ownership from outbound line metadata.
   */
  default boolean isOwnMessage(TargetRef target, String targetMessageId) {
    return false;
  }

  /**
   * Apply an IRCv3 message edit to an existing transcript line identified by {@code msgid}.
   *
   * @return true when a matching line was found and updated.
   */
  default boolean applyMessageEdit(
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String editedText,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags
  ) {
    return false;
  }

  /**
   * Apply an IRCv3 message redaction to an existing transcript line identified by {@code msgid}.
   *
   * @return true when a matching line was found and redacted.
   */
  default boolean applyMessageRedaction(
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags
  ) {
    return false;
  }

  /**
   * Normalize UI affordances when an IRCv3 capability is disabled/removed.
   *
   * <p>This is used to clear ephemeral per-capability state (e.g. typing hints, staged reply/react drafts).
   */
  default void normalizeIrcv3CapabilityUiState(String serverId, String capability) {}
}
