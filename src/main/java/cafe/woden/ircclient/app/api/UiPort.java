package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Boundary between application logic and the Swing UI. */
@ApplicationLayer
public interface UiPort {
  // User-initiated stuff.
  Flowable<TargetRef> targetSelections();

  /**
   * Requests to activate a target for input/status/user list updates without changing the main Chat
   * dock's displayed transcript.
   */
  Flowable<TargetRef> targetActivations();

  Flowable<PrivateMessageRequest> privateMessageRequests();

  Flowable<UserActionRequest> userActionRequests();

  Flowable<String> outboundLines();

  /**
   * Ask the user whether a multiline message should be sent as separate single-line messages.
   *
   * <p>This is used when IRCv3 multiline cannot be used for the current payload (not negotiated or
   * over negotiated limits).
   *
   * @return {@code true} to send split lines, {@code false} to cancel sending.
   */
  default boolean confirmMultilineSplitFallback(
      TargetRef target, int lineCount, long payloadUtf8Bytes, String reason) {
    return false;
  }

  Flowable<Object> connectClicks();

  Flowable<Object> disconnectClicks();

  Flowable<String> connectServerRequests();

  Flowable<String> disconnectServerRequests();

  Flowable<TargetRef> closeTargetRequests();

  /** User-initiated request to join a detached channel target. */
  default Flowable<TargetRef> joinChannelRequests() {
    return Flowable.empty();
  }

  /** User-initiated request to detach from an attached channel target. */
  default Flowable<TargetRef> detachChannelRequests() {
    return Flowable.empty();
  }

  /** User-initiated request to close a channel target (remove it from the tree/transcript). */
  default Flowable<TargetRef> closeChannelRequests() {
    return Flowable.empty();
  }

  Flowable<TargetRef> clearLogRequests();

  /** User-initiated IRCv3 capability toggle requests from Network Info UI. */
  default Flowable<Ircv3CapabilityToggleRequest> ircv3CapabilityToggleRequests() {
    return Flowable.empty();
  }

  // Rendering and view updates.
  void ensureTargetExists(TargetRef target);

  void selectTarget(TargetRef target);

  void closeTarget(TargetRef target);

  /** Mark a channel target as detached/attached in the tree without removing it. */
  default void setChannelDetached(TargetRef target, boolean detached) {}

  /**
   * Mark a channel target as detached/attached and optionally attach a warning reason shown in the
   * tree UI.
   */
  default void setChannelDetached(TargetRef target, boolean detached, String warningReason) {
    setChannelDetached(target, detached);
  }

  /**
   * @return true if the channel target is currently marked detached in the tree.
   */
  default boolean isChannelDetached(TargetRef target) {
    return false;
  }

  void markUnread(TargetRef target);

  void markHighlight(TargetRef target);

  /**
   * Record a highlight notification (for the per-server Notifications view).
   *
   * <p>This should be called when the user is mentioned in a channel message/action. Callers may
   * independently decide whether the highlight should also count as unread.
   */
  void recordHighlight(TargetRef target, String fromNick);

  /**
   * Record a highlight notification with optional snippet context.
   *
   * <p>Default implementation falls back to {@link #recordHighlight(TargetRef, String)}.
   */
  default void recordHighlight(TargetRef target, String fromNick, String snippet) {
    recordHighlight(target, fromNick);
  }

  /**
   * Record a notification rule match (for the per-server Notifications view).
   *
   * <p>This should be called when an inbound channel message/action matches a user-configured
   * WORD/REGEX rule and that match is considered unread (e.g. the channel is not currently active).
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
  default void appendChannelListEntry(
      String serverId, String channel, int visibleUsers, String topic) {}

  /** Mark completion of a /LIST response stream for the given server. */
  default void endChannelList(String serverId, String summary) {}

  /** Reset/prepare the cached ban-list details for one channel (RPL_BANLIST/367 stream). */
  default void beginChannelBanList(String serverId, String channel) {}

  /** Append one ban-list row for the given server/channel. */
  default void appendChannelBanListEntry(
      String serverId, String channel, String mask, String setBy, Long setAtEpochSeconds) {}

  /** Mark completion of a ban-list stream for one channel. */
  default void endChannelBanList(String serverId, String channel, String summary) {}

  void setStatusBarChannel(String channel);

  void setStatusBarCounts(int users, int ops);

  void setStatusBarServer(String serverText);

  /**
   * Enqueue a transient status notice in the bottom status bar.
   *
   * <p>If {@code clickTarget} is provided, clicking the notice should navigate to that target.
   */
  default void enqueueStatusNotice(String text, TargetRef clickTarget) {}

  void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled);

  void setConnectionStatusText(String text);

  void setServerConnectionState(String serverId, ConnectionState state);

  /**
   * Update desired connection intent for a server.
   *
   * <p>{@code true} means the app should keep this server online; {@code false} means it should
   * stay disconnected.
   */
  default void setServerDesiredOnline(String serverId, boolean desiredOnline) {}

  /**
   * Update per-server connection diagnostics shown in server tree tooltips.
   *
   * @param lastError non-empty when a recent connection-related error/reason is available
   * @param nextRetryEpochMs epoch millis for next reconnect attempt; {@code null} when not
   *     scheduled
   */
  default void setServerConnectionDiagnostics(
      String serverId, String lastError, Long nextRetryEpochMs) {}

  /** Update best-effort online state for a private-message target icon in the server tree. */
  default void setPrivateMessageOnlineState(String serverId, String nick, boolean online) {}

  /** Clear all cached private-message online states for one server. */
  default void clearPrivateMessageOnlineStates(String serverId) {}

  /** Update connection identity metadata for a server/network. */
  default void setServerConnectedIdentity(
      String serverId, String connectedHost, int connectedPort, String nick, Instant at) {}

  /**
   * Update one IRCv3 capability status observed from CAP events.
   *
   * @param subcommand normalized CAP verb (ACK/NEW/DEL/etc) when known
   */
  default void setServerIrcv3Capability(
      String serverId, String capability, String subcommand, boolean enabled) {}

  /**
   * Update one RPL_ISUPPORT (005) token.
   *
   * @param tokenValue null to remove/clear
   */
  default void setServerIsupportToken(String serverId, String tokenName, String tokenValue) {}

  /** Update server version/details parsed from numerics (for example RPL_MYINFO/004). */
  default void setServerVersionDetails(
      String serverId,
      String serverName,
      String serverVersion,
      String userModes,
      String channelModes) {}

  void setInputEnabled(boolean enabled);

  /**
   * Append a chat message line.
   *
   * <p>By default this is treated as an <em>incoming</em> line. Use the overload with {@code
   * outgoingLocalEcho=true} for locally-echoed lines you just sent.
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
   * <p>Default implementation falls back to {@link #appendChat(TargetRef, String, String,
   * boolean)}.
   */
  default void appendChatAt(
      TargetRef target, Instant at, String from, String text, boolean outgoingLocalEcho) {
    appendChat(target, from, text, outgoingLocalEcho);
  }

  /**
   * Append a chat message line with an explicit timestamp and IRCv3 identity metadata.
   *
   * <p>Default implementation falls back to {@link #appendChatAt(TargetRef, Instant, String,
   * String, boolean)}.
   */
  default void appendChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags) {
    appendChatAt(target, at, from, text, outgoingLocalEcho);
  }

  /**
   * Append a chat message line with optional notification-rule highlight background.
   *
   * <p>Implementations may ignore the color when unsupported.
   */
  default void appendChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    appendChatAt(target, at, from, text, outgoingLocalEcho, messageId, ircv3Tags);
  }

  /**
   * Append an outbound chat line in a temporary "pending send" state while waiting for server echo.
   */
  default void appendPendingOutgoingChat(
      TargetRef target, String pendingId, Instant at, String from, String text) {
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
      Map<String, String> ircv3Tags) {
    return false;
  }

  /** Mark a pending outbound line as failed. */
  default void failPendingOutgoingChat(
      TargetRef target, String pendingId, Instant at, String from, String text, String reason) {
    appendErrorAt(target, at, "(send-error)", "Failed to send: " + text);
  }

  void appendSpoilerChat(TargetRef target, String from, String text);

  /** Append a spoiler chat line with an explicit timestamp. */
  default void appendSpoilerChatAt(TargetRef target, Instant at, String from, String text) {
    appendSpoilerChat(target, from, text);
  }

  /**
   * Append a spoiler chat line with an explicit timestamp and IRCv3 identity metadata.
   *
   * <p>Default implementation falls back to {@link #appendSpoilerChatAt(TargetRef, Instant, String,
   * String)}.
   */
  default void appendSpoilerChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    appendSpoilerChatAt(target, at, from, text);
  }

  default void appendAction(TargetRef target, String from, String action) {
    appendAction(target, from, action, false);
  }

  void appendAction(TargetRef target, String from, String action, boolean outgoingLocalEcho);

  /** Append an action line with an explicit timestamp. */
  default void appendActionAt(
      TargetRef target, Instant at, String from, String action, boolean outgoingLocalEcho) {
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
      Map<String, String> ircv3Tags) {
    appendActionAt(target, at, from, action, outgoingLocalEcho);
  }

  /**
   * Append an action line with optional notification-rule highlight background.
   *
   * <p>Implementations may ignore the color when unsupported.
   */
  default void appendActionAt(
      TargetRef target,
      Instant at,
      String from,
      String action,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    appendActionAt(target, at, from, action, outgoingLocalEcho, messageId, ircv3Tags);
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
      Map<String, String> ircv3Tags) {
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
      Map<String, String> ircv3Tags) {
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
   * Mark target-level typing activity (for buffer list indicators).
   *
   * <p>This is intentionally non-transcript UI state.
   */
  default void showTypingActivity(TargetRef target, String state) {}

  /**
   * Show typing activity for nicks in the active channel user list.
   *
   * <p>This is intentionally non-transcript UI state.
   */
  default void showUsersTypingIndicator(TargetRef target, String nick, String state) {}

  /** Update the read-marker boundary for a target using epoch milliseconds. */
  default void setReadMarker(TargetRef target, long markerEpochMs) {}

  /** Update inline reaction state for a target message identified by IRCv3 {@code msgid}. */
  default void applyMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {}

  /** Remove inline reaction state for a target message identified by IRCv3 {@code msgid}. */
  default void removeMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {}

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
      Map<String, String> replacementIrcv3Tags) {
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
      Map<String, String> replacementIrcv3Tags) {
    return false;
  }

  /**
   * Normalize UI affordances when an IRCv3 capability is disabled/removed.
   *
   * <p>This is used to clear ephemeral per-capability state (e.g. typing hints, staged reply/react
   * drafts).
   */
  default void normalizeIrcv3CapabilityUiState(String serverId, String capability) {}
}
