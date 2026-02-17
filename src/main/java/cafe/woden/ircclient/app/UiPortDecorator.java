package cafe.woden.ircclient.app;

import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import io.reactivex.rxjava3.core.Flowable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Decorator base for {@link UiPort}.
 *
 */
public abstract class UiPortDecorator implements UiPort {

  protected final UiPort delegate;

  protected UiPortDecorator(UiPort delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public Flowable<TargetRef> targetSelections() {
    return delegate.targetSelections();
  }

  @Override
  public Flowable<TargetRef> targetActivations() {
    return delegate.targetActivations();
  }

  @Override
  public Flowable<PrivateMessageRequest> privateMessageRequests() {
    return delegate.privateMessageRequests();
  }

  @Override
  public Flowable<UserActionRequest> userActionRequests() {
    return delegate.userActionRequests();
  }

  @Override
  public Flowable<String> outboundLines() {
    return delegate.outboundLines();
  }

  @Override
  public Flowable<Object> connectClicks() {
    return delegate.connectClicks();
  }

  @Override
  public Flowable<Object> disconnectClicks() {
    return delegate.disconnectClicks();
  }

  @Override
  public Flowable<String> connectServerRequests() {
    return delegate.connectServerRequests();
  }

  @Override
  public Flowable<String> disconnectServerRequests() {
    return delegate.disconnectServerRequests();
  }

  @Override
  public Flowable<TargetRef> closeTargetRequests() {
    return delegate.closeTargetRequests();
  }

  @Override
  public Flowable<TargetRef> clearLogRequests() {
    return delegate.clearLogRequests();
  }

  @Override
  public void ensureTargetExists(TargetRef target) {
    delegate.ensureTargetExists(target);
  }

  @Override
  public void selectTarget(TargetRef target) {
    delegate.selectTarget(target);
  }

  @Override
  public void closeTarget(TargetRef target) {
    delegate.closeTarget(target);
  }

  @Override
  public void markUnread(TargetRef target) {
    delegate.markUnread(target);
  }

  @Override
  public void markHighlight(TargetRef target) {
    delegate.markHighlight(target);
  }

  @Override
  public void recordHighlight(TargetRef target, String fromNick) {
    delegate.recordHighlight(target, fromNick);
  }

  @Override
  public void recordRuleMatch(TargetRef target, String fromNick, String ruleLabel, String snippet) {
    delegate.recordRuleMatch(target, fromNick, ruleLabel, snippet);
  }

  @Override
  public void clearUnread(TargetRef target) {
    delegate.clearUnread(target);
  }

  @Override
  public void clearTranscript(TargetRef target) {
    delegate.clearTranscript(target);
  }

  @Override
  public void setChatActiveTarget(TargetRef target) {
    delegate.setChatActiveTarget(target);
  }

  @Override
  public void setChatCurrentNick(String serverId, String nick) {
    delegate.setChatCurrentNick(serverId, nick);
  }

  @Override
  public void setChannelTopic(TargetRef target, String topic) {
    delegate.setChannelTopic(target, topic);
  }

  @Override
  public void setUsersChannel(TargetRef target) {
    delegate.setUsersChannel(target);
  }

  @Override
  public void setUsersNicks(List<NickInfo> nicks) {
    delegate.setUsersNicks(nicks);
  }

  @Override
  public void setStatusBarChannel(String channel) {
    delegate.setStatusBarChannel(channel);
  }

  @Override
  public void setStatusBarCounts(int users, int ops) {
    delegate.setStatusBarCounts(users, ops);
  }

  @Override
  public void setStatusBarServer(String serverText) {
    delegate.setStatusBarServer(serverText);
  }

  @Override
  public void setConnectionControlsEnabled(boolean connectEnabled, boolean disconnectEnabled) {
    delegate.setConnectionControlsEnabled(connectEnabled, disconnectEnabled);
  }

  @Override
  public void setConnectionStatusText(String text) {
    delegate.setConnectionStatusText(text);
  }

  @Override
  public void setServerConnectionState(String serverId, ConnectionState state) {
    delegate.setServerConnectionState(serverId, state);
  }

  @Override
  public void setPrivateMessageOnlineState(String serverId, String nick, boolean online) {
    delegate.setPrivateMessageOnlineState(serverId, nick, online);
  }

  @Override
  public void clearPrivateMessageOnlineStates(String serverId) {
    delegate.clearPrivateMessageOnlineStates(serverId);
  }

  @Override
  public void setInputEnabled(boolean enabled) {
    delegate.setInputEnabled(enabled);
  }

  @Override
  public void appendChat(TargetRef target, String from, String text, boolean outgoingLocalEcho) {
    delegate.appendChat(target, from, text, outgoingLocalEcho);
  }

  @Override
  public void appendChatAt(TargetRef target, Instant at, String from, String text, boolean outgoingLocalEcho) {
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
      Map<String, String> ircv3Tags
  ) {
    delegate.appendChatAt(target, at, from, text, outgoingLocalEcho, messageId, ircv3Tags);
  }

  @Override
  public void appendPendingOutgoingChat(
      TargetRef target,
      String pendingId,
      Instant at,
      String from,
      String text
  ) {
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
      Map<String, String> ircv3Tags
  ) {
    return delegate.resolvePendingOutgoingChat(target, pendingId, at, from, text, messageId, ircv3Tags);
  }

  @Override
  public void failPendingOutgoingChat(
      TargetRef target,
      String pendingId,
      Instant at,
      String from,
      String text,
      String reason
  ) {
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
  public void appendAction(TargetRef target, String from, String action, boolean outgoingLocalEcho) {
    delegate.appendAction(target, from, action, outgoingLocalEcho);
  }

  @Override
  public void appendActionAt(TargetRef target, Instant at, String from, String action, boolean outgoingLocalEcho) {
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
      Map<String, String> ircv3Tags
  ) {
    delegate.appendActionAt(target, at, from, action, outgoingLocalEcho, messageId, ircv3Tags);
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
      Map<String, String> ircv3Tags
  ) {
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
      Map<String, String> ircv3Tags
  ) {
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
  public void setReadMarker(TargetRef target, long markerEpochMs) {
    delegate.setReadMarker(target, markerEpochMs);
  }

  @Override
  public void applyMessageReaction(
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String reaction
  ) {
    delegate.applyMessageReaction(target, at, fromNick, targetMessageId, reaction);
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
      Map<String, String> replacementIrcv3Tags
  ) {
    return delegate.applyMessageEdit(
        target, at, fromNick, targetMessageId, editedText, replacementMessageId, replacementIrcv3Tags);
  }

  @Override
  public boolean applyMessageRedaction(
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags
  ) {
    return delegate.applyMessageRedaction(
        target, at, fromNick, targetMessageId, replacementMessageId, replacementIrcv3Tags);
  }

  @Override
  public void normalizeIrcv3CapabilityUiState(String serverId, String capability) {
    delegate.normalizeIrcv3CapabilityUiState(serverId, capability);
  }
}
