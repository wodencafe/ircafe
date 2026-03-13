package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.PresenceEvent;
import cafe.woden.ircclient.app.api.UiTranscriptPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatDockManager;
import cafe.woden.ircclient.ui.chat.ChatTranscriptStore;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Swing adapter for transcript rendering and transcript-adjacent ephemeral state. */
final class SwingUiTranscriptPort implements UiTranscriptPort {

  private final SwingEdtExecutor edt;
  private final ServerTreeDockable serverTree;
  private final ChatDockable chat;
  private final ChatTranscriptStore transcripts;
  private final UserListDockable users;
  private final ChatDockManager chatDockManager;

  SwingUiTranscriptPort(
      SwingEdtExecutor edt,
      ServerTreeDockable serverTree,
      ChatDockable chat,
      ChatTranscriptStore transcripts,
      UserListDockable users,
      ChatDockManager chatDockManager) {
    this.edt = Objects.requireNonNull(edt, "edt");
    this.serverTree = serverTree;
    this.chat = chat;
    this.transcripts = Objects.requireNonNull(transcripts, "transcripts");
    this.users = users;
    this.chatDockManager = chatDockManager;
  }

  @Override
  public void clearTranscript(TargetRef target) {
    edt.run(() -> transcripts.clearTarget(target));
  }

  @Override
  public void refreshMatrixTranscriptDisplayName(String serverId, String matrixUserId) {
    edt.run(() -> transcripts.refreshMatrixDisplayNameAcrossServer(serverId, matrixUserId));
  }

  @Override
  public void appendChat(TargetRef target, String from, String text) {
    edt.run(() -> transcripts.appendChat(target, from, text, false));
  }

  @Override
  public void appendChat(TargetRef target, String from, String text, boolean outgoingLocalEcho) {
    edt.run(() -> transcripts.appendChat(target, from, text, outgoingLocalEcho));
  }

  @Override
  public void appendChatAt(
      TargetRef target, Instant at, String from, String text, boolean outgoingLocalEcho) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(() -> transcripts.appendChatAt(target, from, text, outgoingLocalEcho, ts));
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
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(
        () ->
            transcripts.appendChatAt(
                target, from, text, outgoingLocalEcho, ts, messageId, ircv3Tags));
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
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(
        () ->
            transcripts.appendChatAt(
                target,
                from,
                text,
                outgoingLocalEcho,
                ts,
                messageId,
                ircv3Tags,
                notificationRuleHighlightColor));
  }

  @Override
  public void appendPendingOutgoingChat(
      TargetRef target, String pendingId, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(() -> transcripts.appendPendingOutgoingChat(target, pendingId, from, text, ts));
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
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    return edt.call(
        () ->
            transcripts.resolvePendingOutgoingChat(
                target, pendingId, from, text, ts, messageId, ircv3Tags),
        false);
  }

  @Override
  public void failPendingOutgoingChat(
      TargetRef target, String pendingId, Instant at, String from, String text, String reason) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(() -> transcripts.failPendingOutgoingChat(target, pendingId, from, text, ts, reason));
  }

  @Override
  public void appendSpoilerChat(TargetRef target, String from, String text) {
    edt.run(() -> transcripts.appendSpoilerChat(target, from, text));
  }

  @Override
  public void appendSpoilerChatAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(() -> transcripts.appendSpoilerChatFromHistory(target, from, text, ts));
  }

  @Override
  public void appendAction(TargetRef target, String from, String action) {
    edt.run(() -> transcripts.appendAction(target, from, action, false));
  }

  @Override
  public void appendAction(
      TargetRef target, String from, String action, boolean outgoingLocalEcho) {
    edt.run(() -> transcripts.appendAction(target, from, action, outgoingLocalEcho));
  }

  @Override
  public void appendActionAt(
      TargetRef target, Instant at, String from, String action, boolean outgoingLocalEcho) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(() -> transcripts.appendActionAt(target, from, action, outgoingLocalEcho, ts));
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
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(
        () ->
            transcripts.appendActionAt(
                target, from, action, outgoingLocalEcho, ts, messageId, ircv3Tags));
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
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(
        () ->
            transcripts.appendActionAt(
                target,
                from,
                action,
                outgoingLocalEcho,
                ts,
                messageId,
                ircv3Tags,
                notificationRuleHighlightColor));
  }

  @Override
  public void appendPresence(TargetRef target, PresenceEvent event) {
    edt.run(() -> transcripts.appendPresence(target, event));
  }

  @Override
  public void appendNotice(TargetRef target, String from, String text) {
    edt.run(() -> transcripts.appendNotice(target, from, text));
  }

  @Override
  public void appendNoticeAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(() -> transcripts.appendNoticeAt(target, from, text, ts));
  }

  @Override
  public void appendNoticeAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(() -> transcripts.appendNoticeAt(target, from, text, ts, messageId, ircv3Tags));
  }

  @Override
  public void appendStatus(TargetRef target, String from, String text) {
    edt.run(() -> transcripts.appendStatus(target, from, text));
  }

  @Override
  public void appendStatusAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(() -> transcripts.appendStatusAt(target, from, text, ts));
  }

  @Override
  public void appendStatusAt(
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(() -> transcripts.appendStatusAt(target, from, text, ts, messageId, ircv3Tags));
  }

  @Override
  public void appendError(TargetRef target, String from, String text) {
    edt.run(() -> transcripts.appendError(target, from, text));
  }

  @Override
  public void appendErrorAt(TargetRef target, Instant at, String from, String text) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(() -> transcripts.appendErrorAt(target, from, text, ts));
  }

  @Override
  public void showTypingIndicator(TargetRef target, String nick, String state) {
    edt.run(
        () -> {
          if (chat != null) {
            chat.showTypingIndicator(target, nick, state);
          }
          if (chatDockManager != null) {
            chatDockManager.showTypingIndicator(target, nick, state);
          }
        });
  }

  @Override
  public void showTypingActivity(TargetRef target, String state) {
    edt.run(
        () -> {
          if (serverTree != null) {
            serverTree.markTypingActivity(target, state);
          }
        });
  }

  @Override
  public void showUsersTypingIndicator(TargetRef target, String nick, String state) {
    edt.run(
        () -> {
          if (users != null) {
            users.showTypingIndicator(target, nick, state);
          }
        });
  }

  @Override
  public void setReadMarker(TargetRef target, long markerEpochMs) {
    edt.run(() -> transcripts.updateReadMarker(target, markerEpochMs));
  }

  @Override
  public void applyMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(
        () -> transcripts.applyMessageReaction(target, targetMessageId, reaction, fromNick, ts));
  }

  @Override
  public void removeMessageReaction(
      TargetRef target, Instant at, String fromNick, String targetMessageId, String reaction) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    edt.run(
        () -> transcripts.removeMessageReaction(target, targetMessageId, reaction, fromNick, ts));
  }

  @Override
  public boolean isOwnMessage(TargetRef target, String targetMessageId) {
    return edt.call(() -> transcripts.isOwnMessage(target, targetMessageId), false);
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
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    return edt.call(
        () ->
            transcripts.applyMessageEdit(
                target,
                targetMessageId,
                editedText,
                fromNick,
                ts,
                replacementMessageId,
                replacementIrcv3Tags),
        false);
  }

  @Override
  public boolean applyMessageRedaction(
      TargetRef target,
      Instant at,
      String fromNick,
      String targetMessageId,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags) {
    long ts = (at != null) ? at.toEpochMilli() : System.currentTimeMillis();
    return edt.call(
        () ->
            transcripts.applyMessageRedaction(
                target, targetMessageId, fromNick, ts, replacementMessageId, replacementIrcv3Tags),
        false);
  }

  @Override
  public void normalizeIrcv3CapabilityUiState(String serverId, String capability) {
    edt.run(
        () -> {
          if (chat != null) {
            chat.normalizeIrcv3CapabilityUiState(serverId, capability);
          }
          if (chatDockManager != null) {
            chatDockManager.normalizeIrcv3CapabilityUiState(serverId, capability);
          }
        });
  }
}
