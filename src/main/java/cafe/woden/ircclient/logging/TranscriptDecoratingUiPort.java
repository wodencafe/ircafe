package cafe.woden.ircclient.logging;

import cafe.woden.ircclient.app.api.PresenceEvent;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiPortDecorator;
import cafe.woden.ircclient.app.api.UiTranscriptPort;
import cafe.woden.ircclient.model.TargetRef;
import java.time.Instant;
import java.util.Map;

/** Aggregate {@link UiPort} that routes transcript methods through a decorated transcript port. */
final class TranscriptDecoratingUiPort extends UiPortDecorator {

  private final UiTranscriptPort transcriptPort;

  TranscriptDecoratingUiPort(UiPort delegate, UiTranscriptPort transcriptPort) {
    super(delegate);
    this.transcriptPort = transcriptPort;
  }

  @Override
  public void clearTranscript(TargetRef target) {
    transcriptPort.clearTranscript(target);
  }

  @Override
  public void refreshMatrixTranscriptDisplayName(String serverId, String matrixUserId) {
    transcriptPort.refreshMatrixTranscriptDisplayName(serverId, matrixUserId);
  }

  @Override
  public void appendChat(TargetRef target, String from, String text) {
    transcriptPort.appendChat(target, from, text);
  }

  @Override
  public void appendChat(TargetRef target, String from, String text, boolean outgoingLocalEcho) {
    transcriptPort.appendChat(target, from, text, outgoingLocalEcho);
  }

  @Override
  public void appendChatAt(
      TargetRef target, Instant at, String from, String text, boolean outgoingLocalEcho) {
    transcriptPort.appendChatAt(target, at, from, text, outgoingLocalEcho);
  }

  @Override
  public void appendChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags) {
    transcriptPort.appendChatAt(target, at, from, text, outgoingLocalEcho, messageId, ircv3Tags);
  }

  @Override
  public void appendChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    transcriptPort.appendChatAt(
        target,
        at,
        from,
        text,
        outgoingLocalEcho,
        messageId,
        ircv3Tags,
        notificationRuleHighlightColor);
  }

  @Override
  public void appendPendingOutgoingChat(
      TargetRef target, String pendingId, Instant at, String from, String text) {
    transcriptPort.appendPendingOutgoingChat(target, pendingId, at, from, text);
  }

  @Override
  public boolean resolvePendingOutgoingChat(
      TargetRef target,
      String pendingId,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    return transcriptPort.resolvePendingOutgoingChat(
        target, pendingId, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void failPendingOutgoingChat(
      TargetRef target, String pendingId, Instant at, String from, String text, String reason) {
    transcriptPort.failPendingOutgoingChat(target, pendingId, at, from, text, reason);
  }

  @Override
  public void appendSpoilerChat(TargetRef target, String from, String text) {
    transcriptPort.appendSpoilerChat(target, from, text);
  }

  @Override
  public void appendSpoilerChatAt(TargetRef target, Instant at, String from, String text) {
    transcriptPort.appendSpoilerChatAt(target, at, from, text);
  }

  @Override
  public void appendSpoilerChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    transcriptPort.appendSpoilerChatAt(target, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void appendAction(TargetRef target, String from, String action) {
    transcriptPort.appendAction(target, from, action);
  }

  @Override
  public void appendAction(
      TargetRef target, String from, String action, boolean outgoingLocalEcho) {
    transcriptPort.appendAction(target, from, action, outgoingLocalEcho);
  }

  @Override
  public void appendActionAt(
      TargetRef target, Instant at, String from, String action, boolean outgoingLocalEcho) {
    transcriptPort.appendActionAt(target, at, from, action, outgoingLocalEcho);
  }

  @Override
  public void appendActionAt(
      TargetRef target,
      Instant at,
      String from,
      String action,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags) {
    transcriptPort.appendActionAt(
        target, at, from, action, outgoingLocalEcho, messageId, ircv3Tags);
  }

  @Override
  public void appendActionAt(
      TargetRef target,
      Instant at,
      String from,
      String action,
      boolean outgoingLocalEcho,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    transcriptPort.appendActionAt(
        target,
        at,
        from,
        action,
        outgoingLocalEcho,
        messageId,
        ircv3Tags,
        notificationRuleHighlightColor);
  }

  @Override
  public void appendPresence(TargetRef target, PresenceEvent event) {
    transcriptPort.appendPresence(target, event);
  }

  @Override
  public void appendNotice(TargetRef target, String from, String text) {
    transcriptPort.appendNotice(target, from, text);
  }

  @Override
  public void appendNoticeAt(TargetRef target, Instant at, String from, String text) {
    transcriptPort.appendNoticeAt(target, at, from, text);
  }

  @Override
  public void appendNoticeAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    transcriptPort.appendNoticeAt(target, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void appendStatus(TargetRef target, String from, String text) {
    transcriptPort.appendStatus(target, from, text);
  }

  @Override
  public void appendStatusAt(TargetRef target, Instant at, String from, String text) {
    transcriptPort.appendStatusAt(target, at, from, text);
  }

  @Override
  public void appendStatusAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    transcriptPort.appendStatusAt(target, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void appendError(TargetRef target, String from, String text) {
    transcriptPort.appendError(target, from, text);
  }

  @Override
  public void appendErrorAt(TargetRef target, Instant at, String from, String text) {
    transcriptPort.appendErrorAt(target, at, from, text);
  }

  @Override
  public void showTypingIndicator(TargetRef target, String nick, String state) {
    transcriptPort.showTypingIndicator(target, nick, state);
  }

  @Override
  public void showTypingActivity(TargetRef target, String state) {
    transcriptPort.showTypingActivity(target, state);
  }

  @Override
  public void showUsersTypingIndicator(TargetRef target, String nick, String state) {
    transcriptPort.showUsersTypingIndicator(target, nick, state);
  }

  @Override
  public void setReadMarker(TargetRef target, long markerEpochMs) {
    transcriptPort.setReadMarker(target, markerEpochMs);
  }

  @Override
  public void applyMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {
    transcriptPort.applyMessageReaction(target, at, fromNick, targetMessageId, reaction);
  }

  @Override
  public void removeMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {
    transcriptPort.removeMessageReaction(target, at, fromNick, targetMessageId, reaction);
  }

  @Override
  public boolean isOwnMessage(TargetRef target, String targetMessageId) {
    return transcriptPort.isOwnMessage(target, targetMessageId);
  }

  @Override
  public boolean applyMessageEdit(
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String editedText,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags) {
    return transcriptPort.applyMessageEdit(
        target,
        at,
        fromNick,
        targetMessageId,
        editedText,
        replacementMessageId,
        replacementIrcv3Tags);
  }

  @Override
  public boolean applyMessageRedaction(
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags) {
    return transcriptPort.applyMessageRedaction(
        target, at, fromNick, targetMessageId, replacementMessageId, replacementIrcv3Tags);
  }

  @Override
  public void normalizeIrcv3CapabilityUiState(String serverId, String capability) {
    transcriptPort.normalizeIrcv3CapabilityUiState(serverId, capability);
  }
}
