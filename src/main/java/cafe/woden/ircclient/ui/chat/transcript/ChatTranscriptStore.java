package cafe.woden.ircclient.ui.chat.transcript;

import cafe.woden.ircclient.app.api.ChatTranscriptHistoryPort;
import cafe.woden.ircclient.app.api.MessageTranslation;
import cafe.woden.ircclient.app.api.PresenceEvent;
import cafe.woden.ircclient.irc.roster.UserListPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.NickColorSettingsBus;
import cafe.woden.ircclient.ui.chat.embed.ChatImageEmbedder;
import cafe.woden.ircclient.ui.chat.embed.ChatLinkPreviewEmbedder;
import cafe.woden.ircclient.ui.chat.fold.HistoryDividerComponent;
import cafe.woden.ircclient.ui.chat.fold.LoadOlderMessagesComponent;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.transcript.filter.ChatTranscriptFilteredFlowCoordinator;
import cafe.woden.ircclient.ui.chat.transcript.internal.ChatTranscriptStoreComposition;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptMatrixDisplayNameCoordinator;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptMessageInteractionCoordinator;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptMessageLineCoordinator;
import cafe.woden.ircclient.ui.chat.transcript.message.MessageTranslationSource;
import cafe.woden.ircclient.ui.chat.transcript.message.ReactionChipActionHandler;
import cafe.woden.ircclient.ui.chat.transcript.message.RedactedMessageContent;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTimestampFormatter;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTranscriptRuntimeFlowCoordinator;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTranscriptTargetRuntimeCoordinator;
import cafe.woden.ircclient.ui.chat.transcript.spoiler.ChatTranscriptPlainSpoilerCoordinator;
import cafe.woden.ircclient.ui.filter.FilterEngine;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.OptionalLong;
import javax.swing.text.AttributeSet;
import javax.swing.text.StyledDocument;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@SecondaryAdapter
@InterfaceLayer
@Lazy
public class ChatTranscriptStore implements ChatTranscriptHistoryPort {

  private final ChatTranscriptFilteredFlowCoordinator filteredFlowCoordinator;
  private final ChatTranscriptMatrixDisplayNameCoordinator matrixDisplayNameCoordinator;
  private final ChatTranscriptMessageInteractionCoordinator messageInteractionCoordinator;
  private final ChatTranscriptMessageLineCoordinator messageLineCoordinator;
  private final ChatTranscriptPlainSpoilerCoordinator plainSpoilerCoordinator;
  private final ChatTranscriptRuntimeFlowCoordinator runtimeFlowCoordinator;
  private final ChatTranscriptTargetRuntimeCoordinator targetRuntimeCoordinator;

  public ChatTranscriptStore(
      ChatStyles styles,
      ChatRichTextRenderer renderer,
      ChatTimestampFormatter ts,
      NickColorService nickColors,
      NickColorSettingsBus nickColorSettings,
      ChatImageEmbedder imageEmbeds,
      ChatLinkPreviewEmbedder linkPreviews,
      UiSettingsBus uiSettings,
      FilterEngine filterEngine,
      UserListPort userListStore) {
    ChatTranscriptStoreComposition.Components components =
        ChatTranscriptStoreComposition.create(
            this,
            styles,
            renderer,
            ts,
            nickColors,
            nickColorSettings,
            imageEmbeds,
            linkPreviews,
            uiSettings,
            filterEngine,
            userListStore);

    this.filteredFlowCoordinator = components.filteredFlowCoordinator();
    this.matrixDisplayNameCoordinator = components.matrixDisplayNameCoordinator();
    this.messageInteractionCoordinator = components.messageInteractionCoordinator();
    this.messageLineCoordinator = components.messageLineCoordinator();
    this.plainSpoilerCoordinator = components.plainSpoilerCoordinator();
    this.runtimeFlowCoordinator = components.runtimeFlowCoordinator();
    this.targetRuntimeCoordinator = components.targetRuntimeCoordinator();
  }

  @PreDestroy
  void shutdown() {
    targetRuntimeCoordinator.shutdown();
  }

  public synchronized void ensureTargetExists(TargetRef ref) {
    targetRuntimeCoordinator.ensureTargetExists(ref);
  }

  /**
   * Explicit batch boundary for history/backfill insertion.
   *
   * <p>History loaders typically prepend many lines in a tight loop. We want filtered
   * placeholders/hints to group consecutive hidden lines within that loop, but we do <b>not</b>
   * want a filtered run from a previous load to keep growing across separate paging operations.
   *
   * <p>Call this once before a batch of {@code insert*FromHistoryAt(...)} calls.
   */
  public synchronized void beginHistoryInsertBatch(TargetRef ref) {
    beginHistoryInsertBatch(ref, false);
  }

  public synchronized void beginHistoryInsertBatch(TargetRef ref, boolean forceDeferRichText) {
    filteredFlowCoordinator.beginHistoryInsertBatch(ref, forceDeferRichText);
  }

  /**
   * Optional end-of-batch signal for history/backfill insertion.
   *
   * <p>Calling this is safe but not strictly required as long as callers invoke {@link
   * #beginHistoryInsertBatch(TargetRef)} before each subsequent batch.
   */
  public synchronized void endHistoryInsertBatch(TargetRef ref) {
    filteredFlowCoordinator.endHistoryInsertBatch(ref);
  }

  /**
   * Re-renders already-inserted Matrix sender labels in this transcript using the latest roster
   * real-name knowledge.
   *
   * <p>This is used after startup roster refreshes so initial persisted scrollback rows can switch
   * from raw Matrix IDs to display names without waiting for new message traffic.
   *
   * @return number of sender-label runs updated
   */
  public synchronized int refreshMatrixDisplayNames(TargetRef ref) {
    return matrixDisplayNameCoordinator.refreshMatrixDisplayNames(ref);
  }

  /**
   * Re-renders already-inserted Matrix sender labels for a specific Matrix user ID across all open
   * transcripts on one server.
   *
   * @return number of sender-label runs updated
   */
  public synchronized int refreshMatrixDisplayNameAcrossServer(
      String serverId, String matrixUserId) {
    return matrixDisplayNameCoordinator.refreshMatrixDisplayNamesAcrossServer(
        serverId, matrixUserId);
  }

  public synchronized StyledDocument document(TargetRef ref) {
    return targetRuntimeCoordinator.document(ref);
  }

  public synchronized OptionalLong earliestTimestampEpochMs(TargetRef ref) {
    return targetRuntimeCoordinator.earliestTimestampEpochMs(ref);
  }

  public synchronized LoadOlderMessagesComponent ensureLoadOlderMessagesControl(TargetRef ref) {
    return runtimeFlowCoordinator.ensureLoadOlderMessagesControl(ref);
  }

  public synchronized HistoryDividerComponent ensureHistoryDivider(
      TargetRef ref, int insertAt, String labelText) {
    return runtimeFlowCoordinator.ensureHistoryDivider(ref, insertAt, labelText);
  }

  /**
   * Mark that a history divider should be inserted before the next live append for this target.
   * This is used when history is loaded into an otherwise-empty transcript.
   */
  public synchronized void markHistoryDividerPending(TargetRef ref, String labelText) {
    runtimeFlowCoordinator.markHistoryDividerPending(ref, labelText);
  }

  /** Returns true if there is content after the given offset in the transcript document. */
  public synchronized boolean hasContentAfterOffset(TargetRef ref, int offset) {
    return runtimeFlowCoordinator.hasContentAfterOffset(ref, offset);
  }

  public synchronized void updateReadMarker(TargetRef ref, long markerEpochMs) {
    runtimeFlowCoordinator.updateReadMarker(ref, markerEpochMs);
  }

  public synchronized void clearReadMarker(TargetRef ref) {
    runtimeFlowCoordinator.clearReadMarker(ref);
  }

  public synchronized void clearReadMarkersForServer(String serverId) {
    runtimeFlowCoordinator.clearReadMarkersForServer(serverId);
  }

  public synchronized int readMarkerJumpOffset(TargetRef ref) {
    return runtimeFlowCoordinator.readMarkerJumpOffset(ref);
  }

  public synchronized int messageOffsetById(TargetRef ref, String messageId) {
    return messageInteractionCoordinator.messageOffsetById(ref, messageId);
  }

  public synchronized String messagePreviewById(TargetRef ref, String messageId) {
    return messageInteractionCoordinator.messagePreviewById(ref, messageId);
  }

  public synchronized RedactedMessageContent redactedOriginalById(TargetRef ref, String messageId) {
    return messageInteractionCoordinator.redactedOriginalById(ref, messageId);
  }

  public synchronized MessageTranslationSource translationSourceById(
      TargetRef ref, String messageId) {
    return messageInteractionCoordinator.translationSourceById(ref, messageId);
  }

  public synchronized boolean hasReactionFromNick(
      TargetRef ref, String messageId, String reaction, String nick) {
    return messageInteractionCoordinator.hasReactionFromNick(ref, messageId, reaction, nick);
  }

  public synchronized void setReactionChipActionHandler(ReactionChipActionHandler handler) {
    messageInteractionCoordinator.setReactionChipActionHandler(handler);
  }

  public synchronized boolean isOwnMessage(TargetRef ref, String messageId) {
    return messageInteractionCoordinator.isOwnMessage(ref, messageId);
  }

  public synchronized void applyMessageReaction(
      TargetRef ref, String targetMessageId, String reaction, String fromNick, long tsEpochMs) {
    messageInteractionCoordinator.applyMessageReaction(
        ref, targetMessageId, reaction, fromNick, tsEpochMs);
  }

  public synchronized void removeMessageReaction(
      TargetRef ref, String targetMessageId, String reaction, String fromNick, long tsEpochMs) {
    messageInteractionCoordinator.removeMessageReaction(
        ref, targetMessageId, reaction, fromNick, tsEpochMs);
  }

  public synchronized boolean applyMessageTranslation(
      TargetRef ref, MessageTranslation translation, long translatedAtEpochMs) {
    return messageInteractionCoordinator.applyMessageTranslation(
        ref, translation, translatedAtEpochMs);
  }

  public synchronized boolean applyMessageEdit(
      TargetRef ref,
      String targetMessageId,
      String editedText,
      String fromNick,
      long tsEpochMs,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags) {
    return messageInteractionCoordinator.applyMessageEdit(
        ref,
        targetMessageId,
        editedText,
        fromNick,
        tsEpochMs,
        replacementMessageId,
        replacementIrcv3Tags);
  }

  public synchronized boolean applyMessageRedaction(
      TargetRef ref,
      String targetMessageId,
      String fromNick,
      long tsEpochMs,
      String replacementMessageId,
      Map<String, String> replacementIrcv3Tags) {
    return messageInteractionCoordinator.applyMessageRedaction(
        ref, targetMessageId, fromNick, tsEpochMs, replacementMessageId, replacementIrcv3Tags);
  }

  public synchronized int loadOlderInsertOffset(TargetRef ref) {
    return runtimeFlowCoordinator.loadOlderInsertOffset(ref);
  }

  public synchronized void setLoadOlderMessagesControlState(
      TargetRef ref, LoadOlderMessagesComponent.State s) {
    runtimeFlowCoordinator.setLoadOlderMessagesControlState(ref, s);
  }

  public synchronized void setLoadOlderMessagesControlHandler(
      TargetRef ref, java.util.function.BooleanSupplier onLoad) {
    runtimeFlowCoordinator.setLoadOlderMessagesControlHandler(ref, onLoad);
  }

  public synchronized void appendPlain(TargetRef ref, String text) {
    plainSpoilerCoordinator.appendPlain(ref, text);
  }

  public synchronized void closeTarget(TargetRef ref) {
    targetRuntimeCoordinator.closeTarget(ref);
  }

  public synchronized void clearTarget(TargetRef ref) {
    targetRuntimeCoordinator.clearTarget(ref);
  }

  public synchronized void appendPresence(TargetRef ref, PresenceEvent event) {
    runtimeFlowCoordinator.appendPresence(ref, event);
  }

  public synchronized void appendLine(
      TargetRef ref, String from, String text, AttributeSet fromStyle, AttributeSet msgStyle) {
    runtimeFlowCoordinator.appendLine(ref, from, text, fromStyle, msgStyle, true, null);
  }

  public synchronized boolean insertManualPreviewAt(TargetRef ref, int insertAt, String rawUrl) {
    return messageLineCoordinator.insertManualPreviewAt(ref, insertAt, rawUrl);
  }

  public void appendChat(TargetRef ref, String from, String text) {
    messageLineCoordinator.appendChat(ref, from, text);
  }

  public void appendChat(TargetRef ref, String from, String text, boolean outgoingLocalEcho) {
    messageLineCoordinator.appendChat(ref, from, text, outgoingLocalEcho);
  }

  public void appendChatFromHistory(
      TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs) {
    appendChatFromHistory(ref, from, text, outgoingLocalEcho, tsEpochMs, "", Map.of());
  }

  public void appendChatFromHistory(
      TargetRef ref,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    messageLineCoordinator.appendChatFromHistory(
        ref, from, text, outgoingLocalEcho, tsEpochMs, messageId, ircv3Tags);
  }

  /**
   * Append a chat message with a timestamp, allowing embeds (link previews / images).
   *
   * <p>This is used for inbound "live" messages where we have an Instant from the server. We keep
   * the history-loading paths (DB backfill / "load older") embed-free to avoid fetch storms.
   */
  public void appendChatAt(
      TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs) {
    appendChatAt(ref, from, text, outgoingLocalEcho, tsEpochMs, "", Map.of(), null);
  }

  public void appendChatAt(
      TargetRef ref,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    appendChatAt(ref, from, text, outgoingLocalEcho, tsEpochMs, messageId, ircv3Tags, null);
  }

  public void appendChatAt(
      TargetRef ref,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    messageLineCoordinator.appendChatAt(
        ref,
        from,
        text,
        outgoingLocalEcho,
        tsEpochMs,
        messageId,
        ircv3Tags,
        notificationRuleHighlightColor);
  }

  public synchronized void appendPendingOutgoingChat(
      TargetRef ref, String pendingId, String from, String text, long tsEpochMs) {
    messageLineCoordinator.appendPendingOutgoingChat(ref, pendingId, from, text, tsEpochMs);
  }

  public synchronized void appendPendingOutgoingAction(
      TargetRef ref, String pendingId, String from, String text, long tsEpochMs) {
    messageLineCoordinator.appendPendingOutgoingAction(ref, pendingId, from, text, tsEpochMs);
  }

  public synchronized boolean resolvePendingOutgoingChat(
      TargetRef ref,
      String pendingId,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    return messageLineCoordinator.resolvePendingOutgoingChat(
        ref, pendingId, from, text, tsEpochMs, messageId, ircv3Tags);
  }

  public synchronized boolean resolvePendingOutgoingAction(
      TargetRef ref,
      String pendingId,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    return messageLineCoordinator.resolvePendingOutgoingAction(
        ref, pendingId, from, text, tsEpochMs, messageId, ircv3Tags);
  }

  public synchronized boolean failPendingOutgoingChat(
      TargetRef ref, String pendingId, String from, String text, long tsEpochMs, String reason) {
    return messageLineCoordinator.failPendingOutgoingChat(
        ref, pendingId, from, text, tsEpochMs, reason);
  }

  public synchronized boolean failPendingOutgoingAction(
      TargetRef ref, String pendingId, String from, String text, long tsEpochMs, String reason) {
    return messageLineCoordinator.failPendingOutgoingAction(
        ref, pendingId, from, text, tsEpochMs, reason);
  }

  public synchronized int insertChatFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs) {
    return insertChatFromHistoryAt(
        ref, insertAt, from, text, outgoingLocalEcho, tsEpochMs, "", Map.of());
  }

  public synchronized int insertChatFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    return messageLineCoordinator.insertChatFromHistoryAt(
        ref, insertAt, from, text, outgoingLocalEcho, tsEpochMs, messageId, ircv3Tags);
  }

  public synchronized int prependChatFromHistory(
      TargetRef ref, String from, String text, boolean outgoingLocalEcho, long tsEpochMs) {
    return insertChatFromHistoryAt(ref, 0, from, text, outgoingLocalEcho, tsEpochMs);
  }

  public synchronized int insertActionFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long tsEpochMs) {
    return insertActionFromHistoryAt(
        ref, insertAt, from, action, outgoingLocalEcho, tsEpochMs, "", Map.of());
  }

  public synchronized int insertActionFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    return messageLineCoordinator.insertActionFromHistoryAt(
        ref, insertAt, from, action, outgoingLocalEcho, tsEpochMs, messageId, ircv3Tags);
  }

  public synchronized int prependActionFromHistory(
      TargetRef ref, String from, String action, boolean outgoingLocalEcho, long tsEpochMs) {
    return insertActionFromHistoryAt(ref, 0, from, action, outgoingLocalEcho, tsEpochMs);
  }

  public synchronized int insertNoticeFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    return insertNoticeFromHistoryAt(ref, insertAt, from, text, tsEpochMs, "", Map.of());
  }

  public synchronized int insertNoticeFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    return messageLineCoordinator.insertNoticeFromHistoryAt(
        ref, insertAt, from, text, tsEpochMs, messageId, ircv3Tags);
  }

  public synchronized int prependNoticeFromHistory(
      TargetRef ref, String from, String text, long tsEpochMs) {
    return insertNoticeFromHistoryAt(ref, 0, from, text, tsEpochMs);
  }

  public synchronized int insertStatusFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    return messageLineCoordinator.insertStatusFromHistoryAt(ref, insertAt, from, text, tsEpochMs);
  }

  public synchronized int prependStatusFromHistory(
      TargetRef ref, String from, String text, long tsEpochMs) {
    return insertStatusFromHistoryAt(ref, 0, from, text, tsEpochMs);
  }

  public synchronized int insertErrorFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    return messageLineCoordinator.insertErrorFromHistoryAt(ref, insertAt, from, text, tsEpochMs);
  }

  public synchronized int prependErrorFromHistory(
      TargetRef ref, String from, String text, long tsEpochMs) {
    return insertErrorFromHistoryAt(ref, 0, from, text, tsEpochMs);
  }

  public synchronized int insertPresenceFromHistoryAt(
      TargetRef ref, int insertAt, String displayText, long tsEpochMs) {
    return runtimeFlowCoordinator.insertPresenceFromHistoryAt(
        ref, insertAt, displayText, tsEpochMs);
  }

  public synchronized int prependPresenceFromHistory(
      TargetRef ref, String displayText, long tsEpochMs) {
    return insertPresenceFromHistoryAt(ref, 0, displayText, tsEpochMs);
  }

  public synchronized int insertSpoilerChatFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    return plainSpoilerCoordinator.insertSpoilerChatFromHistoryAt(
        ref, insertAt, from, text, tsEpochMs);
  }

  public synchronized int prependSpoilerChatFromHistory(
      TargetRef ref, String from, String text, long tsEpochMs) {
    return insertSpoilerChatFromHistoryAt(ref, 0, from, text, tsEpochMs);
  }

  public void appendSpoilerChat(TargetRef ref, String from, String text) {
    plainSpoilerCoordinator.appendSpoilerChat(ref, from, text);
  }

  public void appendSpoilerChatFromHistory(
      TargetRef ref, String from, String text, long tsEpochMs) {
    plainSpoilerCoordinator.appendSpoilerChatFromHistory(ref, from, text, tsEpochMs);
  }

  public void appendAction(TargetRef ref, String from, String action) {
    messageLineCoordinator.appendAction(ref, from, action);
  }

  public void appendAction(TargetRef ref, String from, String action, boolean outgoingLocalEcho) {
    messageLineCoordinator.appendAction(ref, from, action, outgoingLocalEcho);
  }

  public void appendActionFromHistory(
      TargetRef ref, String from, String action, boolean outgoingLocalEcho, long tsEpochMs) {
    appendActionFromHistory(ref, from, action, outgoingLocalEcho, tsEpochMs, "", Map.of());
  }

  public void appendActionFromHistory(
      TargetRef ref,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    messageLineCoordinator.appendActionFromHistory(
        ref, from, action, outgoingLocalEcho, tsEpochMs, messageId, ircv3Tags);
  }

  /** Append an action (/me) with a timestamp, allowing embeds. */
  public void appendActionAt(
      TargetRef ref, String from, String action, boolean outgoingLocalEcho, long tsEpochMs) {
    appendActionAt(ref, from, action, outgoingLocalEcho, tsEpochMs, "", Map.of(), null);
  }

  public void appendActionAt(
      TargetRef ref,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    appendActionAt(ref, from, action, outgoingLocalEcho, tsEpochMs, messageId, ircv3Tags, null);
  }

  public void appendActionAt(
      TargetRef ref,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags,
      String notificationRuleHighlightColor) {
    messageLineCoordinator.appendActionAt(
        ref,
        from,
        action,
        outgoingLocalEcho,
        tsEpochMs,
        messageId,
        ircv3Tags,
        notificationRuleHighlightColor);
  }

  public void appendNotice(TargetRef ref, String from, String text) {
    messageLineCoordinator.appendNotice(ref, from, text);
  }

  public void appendStatus(TargetRef ref, String from, String text) {
    messageLineCoordinator.appendStatus(ref, from, text);
  }

  public void appendError(TargetRef ref, String from, String text) {
    messageLineCoordinator.appendError(ref, from, text);
  }

  public void appendNoticeFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    messageLineCoordinator.appendNoticeFromHistory(ref, from, text, tsEpochMs, "", Map.of());
  }

  public void appendNoticeFromHistory(
      TargetRef ref,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    messageLineCoordinator.appendNoticeFromHistory(
        ref, from, text, tsEpochMs, messageId, ircv3Tags);
  }

  public void appendStatusFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    messageLineCoordinator.appendStatusFromHistory(ref, from, text, tsEpochMs);
  }

  public void appendErrorFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    messageLineCoordinator.appendErrorFromHistory(ref, from, text, tsEpochMs);
  }

  /** Append a notice with a timestamp, allowing embeds. */
  public void appendNoticeAt(TargetRef ref, String from, String text, long tsEpochMs) {
    messageLineCoordinator.appendNoticeAt(ref, from, text, tsEpochMs);
  }

  public void appendNoticeAt(
      TargetRef ref,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    messageLineCoordinator.appendNoticeAt(ref, from, text, tsEpochMs, messageId, ircv3Tags);
  }

  /** Append a status line with a timestamp, allowing embeds. */
  public void appendStatusAt(TargetRef ref, String from, String text, long tsEpochMs) {
    messageLineCoordinator.appendStatusAt(ref, from, text, tsEpochMs);
  }

  public void appendStatusAt(
      TargetRef ref,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    messageLineCoordinator.appendStatusAt(ref, from, text, tsEpochMs, messageId, ircv3Tags);
  }

  /** Append an error line with a timestamp, allowing embeds. */
  public void appendErrorAt(TargetRef ref, String from, String text, long tsEpochMs) {
    messageLineCoordinator.appendErrorAt(ref, from, text, tsEpochMs);
  }

  public void appendPresenceFromHistory(TargetRef ref, String displayText, long tsEpochMs) {
    runtimeFlowCoordinator.appendPresenceFromHistory(ref, displayText, tsEpochMs);
  }

  public synchronized void restyleAllDocuments() {
    targetRuntimeCoordinator.restyleAllDocuments();
  }

  public void restyleAllDocumentsCoalesced() {
    targetRuntimeCoordinator.restyleAllDocumentsCoalesced();
  }
}
