package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.model.TargetRef;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Decorator base for {@link UiTranscriptPort}. */
public abstract class UiTranscriptPortDecorator implements UiTranscriptPort {

  protected final UiTranscriptPort delegate;

  protected UiTranscriptPortDecorator(UiTranscriptPort delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public void clearTranscript(TargetRef target) {
    delegate.clearTranscript(target);
  }

  @Override
  public void refreshMatrixTranscriptDisplayName(String serverId, String matrixUserId) {
    delegate.refreshMatrixTranscriptDisplayName(serverId, matrixUserId);
  }

  @Override
  public void appendChat(TargetRef target, String from, String text) {
    delegate.appendChat(target, from, text);
  }

  @Override
  public void appendChat(TargetRef target, String from, String text, boolean outgoingLocalEcho) {
    delegate.appendChat(target, from, text, outgoingLocalEcho);
  }

  @Override
  public void appendChatAt(
      TargetRef target, Instant at, String from, String text, boolean outgoingLocalEcho) {
    delegate.appendChatAt(target, at, from, text, outgoingLocalEcho);
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
    delegate.appendChatAt(target, at, from, text, outgoingLocalEcho, messageId, ircv3Tags);
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
    delegate.appendChatAt(
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
    delegate.appendPendingOutgoingChat(target, pendingId, at, from, text);
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
    return delegate.resolvePendingOutgoingChat(
        target, pendingId, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void failPendingOutgoingChat(
      TargetRef target, String pendingId, Instant at, String from, String text, String reason) {
    delegate.failPendingOutgoingChat(target, pendingId, at, from, text, reason);
  }

  @Override
  public void appendSpoilerChat(TargetRef target, String from, String text) {
    delegate.appendSpoilerChat(target, from, text);
  }

  @Override
  public void appendSpoilerChatAt(TargetRef target, Instant at, String from, String text) {
    delegate.appendSpoilerChatAt(target, at, from, text);
  }

  @Override
  public void appendSpoilerChatAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    delegate.appendSpoilerChatAt(target, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void appendAction(TargetRef target, String from, String action) {
    delegate.appendAction(target, from, action);
  }

  @Override
  public void appendAction(
      TargetRef target, String from, String action, boolean outgoingLocalEcho) {
    delegate.appendAction(target, from, action, outgoingLocalEcho);
  }

  @Override
  public void appendActionAt(
      TargetRef target, Instant at, String from, String action, boolean outgoingLocalEcho) {
    delegate.appendActionAt(target, at, from, action, outgoingLocalEcho);
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
    delegate.appendActionAt(target, at, from, action, outgoingLocalEcho, messageId, ircv3Tags);
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
    delegate.appendActionAt(
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
    delegate.appendPresence(target, event);
  }

  @Override
  public void appendNotice(TargetRef target, String from, String text) {
    delegate.appendNotice(target, from, text);
  }

  @Override
  public void appendNoticeAt(TargetRef target, Instant at, String from, String text) {
    delegate.appendNoticeAt(target, at, from, text);
  }

  @Override
  public void appendNoticeAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    delegate.appendNoticeAt(target, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void appendStatus(TargetRef target, String from, String text) {
    delegate.appendStatus(target, from, text);
  }

  @Override
  public void appendStatusAt(TargetRef target, Instant at, String from, String text) {
    delegate.appendStatusAt(target, at, from, text);
  }

  @Override
  public void appendStatusAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    delegate.appendStatusAt(target, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void appendError(TargetRef target, String from, String text) {
    delegate.appendError(target, from, text);
  }

  @Override
  public void appendErrorAt(TargetRef target, Instant at, String from, String text) {
    delegate.appendErrorAt(target, at, from, text);
  }

  @Override
  public void showTypingIndicator(TargetRef target, String nick, String state) {
    delegate.showTypingIndicator(target, nick, state);
  }

  @Override
  public void showTypingActivity(TargetRef target, String state) {
    delegate.showTypingActivity(target, state);
  }

  @Override
  public void showUsersTypingIndicator(TargetRef target, String nick, String state) {
    delegate.showUsersTypingIndicator(target, nick, state);
  }

  @Override
  public void setReadMarker(TargetRef target, long markerEpochMs) {
    delegate.setReadMarker(target, markerEpochMs);
  }

  @Override
  public void applyMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {
    delegate.applyMessageReaction(target, at, fromNick, targetMessageId, reaction);
  }

  @Override
  public void removeMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {
    delegate.removeMessageReaction(target, at, fromNick, targetMessageId, reaction);
  }

  @Override
  public boolean isOwnMessage(TargetRef target, String targetMessageId) {
    return delegate.isOwnMessage(target, targetMessageId);
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
    return delegate.applyMessageEdit(
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
    return delegate.applyMessageRedaction(
        target, at, fromNick, targetMessageId, replacementMessageId, replacementIrcv3Tags);
  }

  @Override
  public void normalizeIrcv3CapabilityUiState(String serverId, String capability) {
    delegate.normalizeIrcv3CapabilityUiState(serverId, capability);
  }
}
