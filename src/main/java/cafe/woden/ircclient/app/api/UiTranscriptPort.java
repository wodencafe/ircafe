package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.model.TargetRef;
import java.time.Instant;
import java.util.Map;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Transcript rendering and transcript-adjacent ephemeral state. */
@ApplicationLayer
public interface UiTranscriptPort {

  void clearTranscript(TargetRef target);

  default void refreshMatrixTranscriptDisplayName(String serverId, String matrixUserId) {}

  default void appendChat(TargetRef target, String from, String text) {
    appendChat(target, from, text, false);
  }

  void appendChat(TargetRef target, String from, String text, boolean outgoingLocalEcho);

  default void appendChatAt(
      TargetRef target, Instant at, String from, String text, boolean outgoingLocalEcho) {
    appendChat(target, from, text, outgoingLocalEcho);
  }

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

  default void appendPendingOutgoingChat(
      TargetRef target, String pendingId, Instant at, String from, String text) {
    appendChatAt(target, at, from, text + " [pending]", true);
  }

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

  default void failPendingOutgoingChat(
      TargetRef target, String pendingId, Instant at, String from, String text, String reason) {
    appendErrorAt(target, at, "(send-error)", "Failed to send: " + text);
  }

  void appendSpoilerChat(TargetRef target, String from, String text);

  default void appendSpoilerChatAt(TargetRef target, Instant at, String from, String text) {
    appendSpoilerChat(target, from, text);
  }

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

  default void appendActionAt(
      TargetRef target, Instant at, String from, String action, boolean outgoingLocalEcho) {
    appendAction(target, from, action, outgoingLocalEcho);
  }

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

  default void showTypingIndicator(TargetRef target, String nick, String state) {}

  default void showTypingActivity(TargetRef target, String state) {}

  default void showUsersTypingIndicator(TargetRef target, String nick, String state) {}

  default void setReadMarker(TargetRef target, long markerEpochMs) {}

  default void applyMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {}

  default void removeMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {}

  default boolean isOwnMessage(TargetRef target, String targetMessageId) {
    return false;
  }

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

  default boolean applyMessageRedaction(
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags) {
    return false;
  }

  default void normalizeIrcv3CapabilityUiState(String serverId, String capability) {}
}
