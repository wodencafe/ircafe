package cafe.woden.ircclient.ui.chat.transcript.internal;

import static cafe.woden.ircclient.util.Ircv3CapabilityNames.DRAFT_REACT;

import cafe.woden.ircclient.irc.roster.UserListPort;
import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.chat.embed.ChatImageEmbedder;
import cafe.woden.ircclient.ui.chat.embed.ChatLinkPreviewEmbedder;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.transcript.ChatTranscriptStore;
import cafe.woden.ircclient.ui.chat.transcript.filter.ChatTranscriptFilterRoutingSupport;
import cafe.woden.ircclient.ui.chat.transcript.filter.ChatTranscriptFilteredFlowCoordinator;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptDocumentLineSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptLineMetaSupport;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptActionFlowSupport;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptMatrixDisplayNameCoordinator;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptMessageCatalogSupport;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptMessageInteractionCoordinator;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptMessageLineCoordinator;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptMessageTranslationSupport;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptReactionSummarySupport;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptReplyContextSupport;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptReplyFlowSupport;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptSenderStyleSupport;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTimestampFormatter;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTranscriptLineCapSupport;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTranscriptRuntimeFlowCoordinator;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTranscriptRuntimeSettingsSupport;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTranscriptTargetRuntimeCoordinator;
import cafe.woden.ircclient.ui.chat.transcript.style.ChatTranscriptStyleRoutingSupport;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.util.Map;

/** Builds message-oriented collaborators for the transcript store composition. */
final class ChatTranscriptMessageComposition {

  private static final String REDACTED_MESSAGE_PLACEHOLDER = "[message redacted]";

  record Components(
      ChatTranscriptMatrixDisplayNameCoordinator matrixDisplayNameCoordinator,
      ChatTranscriptMessageLineCoordinator messageLineCoordinator,
      ChatTranscriptMessageInteractionCoordinator messageInteractionCoordinator) {}

  private ChatTranscriptMessageComposition() {}

  static Components create(
      ChatTranscriptStore store,
      ChatStyles styles,
      ChatRichTextRenderer renderer,
      ChatTimestampFormatter ts,
      NickColorService nickColors,
      ChatImageEmbedder imageEmbeds,
      ChatLinkPreviewEmbedder linkPreviews,
      UiSettingsBus uiSettings,
      UserListPort userListStore,
      ChatTranscriptStyleRoutingSupport styleRoutingSupport,
      ChatTranscriptFilterRoutingSupport filterRoutingSupport,
      ChatTranscriptDocumentLineSupport documentLineSupport,
      ChatTranscriptLineCapSupport lineCapSupport,
      ChatTranscriptRuntimeFlowCoordinator runtimeFlowCoordinator,
      ChatTranscriptTargetRuntimeCoordinator targetRuntimeCoordinator,
      ChatTranscriptRuntimeSettingsSupport runtimeSettingsSupport,
      ChatTranscriptMessageCatalogSupport messageCatalogSupport,
      ChatTranscriptFilteredFlowCoordinator filteredFlowCoordinator) {
    ChatTranscriptMatrixDisplayNameCoordinator matrixDisplayNameCoordinator =
        new ChatTranscriptMatrixDisplayNameCoordinator(
            uiSettings, userListStore, targetRuntimeCoordinator.docs());
    ChatTranscriptReplyContextSupport.Context replyContextSupportContext =
        new ChatTranscriptReplyContextSupport.Context(styles);
    ChatTranscriptSenderStyleSupport.Context senderStyleSupportContext =
        createSenderStyleSupportContext(styles, nickColors, styleRoutingSupport);
    ChatTranscriptReactionSummarySupport reactionSummarySupport =
        createReactionSummarySupport(
            styles, styleRoutingSupport, documentLineSupport, runtimeFlowCoordinator);
    ChatTranscriptMessageTranslationSupport messageTranslationSupport =
        createMessageTranslationSupport(
            styles, renderer, documentLineSupport, runtimeFlowCoordinator);
    ChatTranscriptActionFlowSupport.ReplyContextAppender appendReplyContextLine =
        createReplyContextAppender(
            targetRuntimeCoordinator,
            documentLineSupport,
            replyContextSupportContext,
            messageCatalogSupport);
    ChatTranscriptMessageLineCoordinator messageLineCoordinator =
        createMessageLineCoordinator(
            store,
            styles,
            renderer,
            ts,
            imageEmbeds,
            linkPreviews,
            styleRoutingSupport,
            runtimeSettingsSupport,
            filterRoutingSupport,
            documentLineSupport,
            lineCapSupport,
            runtimeFlowCoordinator,
            senderStyleSupportContext,
            messageCatalogSupport,
            reactionSummarySupport,
            targetRuntimeCoordinator,
            appendReplyContextLine,
            matrixDisplayNameCoordinator,
            filteredFlowCoordinator);
    ChatTranscriptMessageInteractionCoordinator messageInteractionCoordinator =
        createMessageInteractionCoordinator(
            targetRuntimeCoordinator,
            messageCatalogSupport,
            reactionSummarySupport,
            senderStyleSupportContext,
            matrixDisplayNameCoordinator,
            messageLineCoordinator,
            runtimeFlowCoordinator,
            messageTranslationSupport);
    return new Components(
        matrixDisplayNameCoordinator, messageLineCoordinator, messageInteractionCoordinator);
  }

  private static ChatTranscriptSenderStyleSupport.Context createSenderStyleSupportContext(
      ChatStyles styles,
      NickColorService nickColors,
      ChatTranscriptStyleRoutingSupport styleRoutingSupport) {
    return new ChatTranscriptSenderStyleSupport.Context(
        styles,
        nickColors,
        ChatTranscriptLineMetaSupport::bind,
        styleRoutingSupport::applyOutgoingLineColor,
        styleRoutingSupport::applyNotificationRuleHighlightColor);
  }

  private static ChatTranscriptReactionSummarySupport createReactionSummarySupport(
      ChatStyles styles,
      ChatTranscriptStyleRoutingSupport styleRoutingSupport,
      ChatTranscriptDocumentLineSupport documentLineSupport,
      ChatTranscriptRuntimeFlowCoordinator runtimeFlowCoordinator) {
    return new ChatTranscriptReactionSummarySupport(
        styles,
        styleRoutingSupport::safeTranscriptFont,
        (ref, epochMs, targetMessageId) ->
            ChatTranscriptLineMetaSupport.create(
                ref,
                LogKind.STATUS,
                LogDirection.SYSTEM,
                null,
                epochMs,
                null,
                targetMessageId,
                Map.of(DRAFT_REACT, "1")),
        ChatTranscriptLineMetaSupport::bind,
        ChatTranscriptLineMetaSupport::withAuxiliaryRowKind,
        documentLineSupport::normalizeInsertAtLineStart,
        documentLineSupport::ensureAtLineStartForInsert,
        runtimeFlowCoordinator::shiftCurrentBlock);
  }

  private static ChatTranscriptMessageTranslationSupport createMessageTranslationSupport(
      ChatStyles styles,
      ChatRichTextRenderer renderer,
      ChatTranscriptDocumentLineSupport documentLineSupport,
      ChatTranscriptRuntimeFlowCoordinator runtimeFlowCoordinator) {
    return new ChatTranscriptMessageTranslationSupport(
        styles,
        renderer,
        ChatTranscriptLineMetaSupport::bind,
        ChatTranscriptLineMetaSupport::withAuxiliaryRowKind,
        documentLineSupport::ensureAtLineStartForInsert,
        runtimeFlowCoordinator::shiftCurrentBlock);
  }

  private static ChatTranscriptActionFlowSupport.ReplyContextAppender createReplyContextAppender(
      ChatTranscriptTargetRuntimeCoordinator targetRuntimeCoordinator,
      ChatTranscriptDocumentLineSupport documentLineSupport,
      ChatTranscriptReplyContextSupport.Context replyContextSupportContext,
      ChatTranscriptMessageCatalogSupport messageCatalogSupport) {
    ChatTranscriptReplyFlowSupport replyFlowSupport = new ChatTranscriptReplyFlowSupport();
    ChatTranscriptReplyFlowSupport.Context replyFlowContext =
        new ChatTranscriptReplyFlowSupport.Context(
            targetRuntimeCoordinator.docs(),
            targetRuntimeCoordinator.stateByTarget(),
            targetRuntimeCoordinator::ensureTargetExists,
            documentLineSupport,
            replyContextSupportContext,
            messageCatalogSupport);
    return (ref, fromNick, replyToMsgId, tsEpochMs) ->
        replyFlowSupport.appendReplyContextLine(
            replyFlowContext, ref, fromNick, replyToMsgId, tsEpochMs);
  }

  private static ChatTranscriptMessageLineCoordinator createMessageLineCoordinator(
      ChatTranscriptStore store,
      ChatStyles styles,
      ChatRichTextRenderer renderer,
      ChatTimestampFormatter ts,
      ChatImageEmbedder imageEmbeds,
      ChatLinkPreviewEmbedder linkPreviews,
      ChatTranscriptStyleRoutingSupport styleRoutingSupport,
      ChatTranscriptRuntimeSettingsSupport runtimeSettingsSupport,
      ChatTranscriptFilterRoutingSupport filterRoutingSupport,
      ChatTranscriptDocumentLineSupport documentLineSupport,
      ChatTranscriptLineCapSupport lineCapSupport,
      ChatTranscriptRuntimeFlowCoordinator runtimeFlowCoordinator,
      ChatTranscriptSenderStyleSupport.Context senderStyleSupportContext,
      ChatTranscriptMessageCatalogSupport messageCatalogSupport,
      ChatTranscriptReactionSummarySupport reactionSummarySupport,
      ChatTranscriptTargetRuntimeCoordinator targetRuntimeCoordinator,
      ChatTranscriptActionFlowSupport.ReplyContextAppender appendReplyContextLine,
      ChatTranscriptMatrixDisplayNameCoordinator matrixDisplayNameCoordinator,
      ChatTranscriptFilteredFlowCoordinator filteredFlowCoordinator) {
    return new ChatTranscriptMessageLineCoordinator(
        new ChatTranscriptMessageLineCoordinator.Dependencies(
            store,
            styles,
            ts,
            renderer,
            imageEmbeds,
            linkPreviews,
            styleRoutingSupport,
            runtimeSettingsSupport,
            filterRoutingSupport,
            documentLineSupport,
            lineCapSupport,
            runtimeFlowCoordinator,
            senderStyleSupportContext,
            messageCatalogSupport,
            reactionSummarySupport,
            targetRuntimeCoordinator.docs(),
            targetRuntimeCoordinator.stateByTarget(),
            targetRuntimeCoordinator::ensureTargetExists,
            targetRuntimeCoordinator::noteEpochMs,
            appendReplyContextLine,
            matrixDisplayNameCoordinator::renderTranscriptFrom,
            filteredFlowCoordinator::endInsertRun,
            filteredFlowCoordinator::shouldDeferRichTextDuringHistoryBatch));
  }

  private static ChatTranscriptMessageInteractionCoordinator createMessageInteractionCoordinator(
      ChatTranscriptTargetRuntimeCoordinator targetRuntimeCoordinator,
      ChatTranscriptMessageCatalogSupport messageCatalogSupport,
      ChatTranscriptReactionSummarySupport reactionSummarySupport,
      ChatTranscriptSenderStyleSupport.Context senderStyleSupportContext,
      ChatTranscriptMatrixDisplayNameCoordinator matrixDisplayNameCoordinator,
      ChatTranscriptMessageLineCoordinator messageLineCoordinator,
      ChatTranscriptRuntimeFlowCoordinator runtimeFlowCoordinator,
      ChatTranscriptMessageTranslationSupport messageTranslationSupport) {
    return new ChatTranscriptMessageInteractionCoordinator(
        targetRuntimeCoordinator.docs(),
        targetRuntimeCoordinator.stateByTarget(),
        targetRuntimeCoordinator::ensureTargetExists,
        targetRuntimeCoordinator::noteEpochMs,
        messageCatalogSupport,
        reactionSummarySupport,
        senderStyleSupportContext,
        matrixDisplayNameCoordinator::renderTranscriptFrom,
        messageLineCoordinator::insertReplacementAction,
        (ref, insertAt, from, text, fromStyle, messageStyle, meta) ->
            runtimeFlowCoordinator.insertLineAt(
                ref, insertAt, from, text, fromStyle, messageStyle, meta),
        REDACTED_MESSAGE_PLACEHOLDER,
        messageTranslationSupport);
  }
}
