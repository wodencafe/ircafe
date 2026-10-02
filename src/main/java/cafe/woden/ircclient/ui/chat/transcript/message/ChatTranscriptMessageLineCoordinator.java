package cafe.woden.ircclient.ui.chat.transcript.message;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.embed.ChatImageEmbedder;
import cafe.woden.ircclient.ui.chat.embed.ChatLinkPreviewEmbedder;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.transcript.filter.ChatTranscriptFilterRoutingSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptActionAppendSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptActionHistoryInsertSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptDocumentLineSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptManualPreviewFlowSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptManualPreviewSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptOutgoingChatSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptOutgoingDeliverySupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptRenderedFromResolver;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptSystemLineSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptTextAppendSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptTextInsertSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.LineMeta;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTimestampFormatter;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTranscriptLineCapSupport;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTranscriptRuntimeFlowCoordinator;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTranscriptRuntimeSettingsSupport;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTranscriptState;
import cafe.woden.ircclient.ui.chat.transcript.style.ChatTranscriptStyleRoutingSupport;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.text.StyledDocument;

/** Owns chat/action/system/manual-preview flow assembly and delegation. */
public final class ChatTranscriptMessageLineCoordinator {

  private final ChatTranscriptChatFlowSupport chatFlowSupport = new ChatTranscriptChatFlowSupport();
  private final ChatTranscriptActionFlowSupport actionFlowSupport =
      new ChatTranscriptActionFlowSupport();
  private final ChatTranscriptManualPreviewFlowSupport manualPreviewFlowSupport =
      new ChatTranscriptManualPreviewFlowSupport();
  private final ChatTranscriptSystemLineSupport systemLineSupport;
  private final ChatTranscriptOutgoingActionSupport outgoingActionSupport;
  private final ChatTranscriptManualPreviewFlowSupport.Context manualPreviewFlowContext;
  private final ChatTranscriptChatFlowSupport.Context chatFlowContext;
  private final ChatTranscriptActionFlowSupport.Context actionFlowContext;
  private final ChatTranscriptTextAppendSupport.Context textAppendSupportContext;
  private final ChatTranscriptTextInsertSupport.Context textInsertSupportContext;

  public record Dependencies(
      Object mutationLock,
      ChatStyles styles,
      ChatTimestampFormatter ts,
      ChatRichTextRenderer renderer,
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
      Map<TargetRef, StyledDocument> docs,
      Map<TargetRef, ChatTranscriptState> stateByTarget,
      Consumer<TargetRef> ensureTargetExists,
      BiConsumer<TargetRef, Long> noteEpochMs,
      ChatTranscriptActionFlowSupport.ReplyContextAppender appendReplyContextLine,
      ChatTranscriptRenderedFromResolver renderedFromResolver,
      Consumer<TargetRef> endFilteredInsertRun,
      Predicate<TargetRef> deferRichTextDuringHistoryBatch) {
    public Dependencies {
      Objects.requireNonNull(mutationLock, "mutationLock");
      Objects.requireNonNull(styles, "styles");
      Objects.requireNonNull(styleRoutingSupport, "styleRoutingSupport");
      Objects.requireNonNull(runtimeSettingsSupport, "runtimeSettingsSupport");
      Objects.requireNonNull(filterRoutingSupport, "filterRoutingSupport");
      Objects.requireNonNull(documentLineSupport, "documentLineSupport");
      Objects.requireNonNull(lineCapSupport, "lineCapSupport");
      Objects.requireNonNull(runtimeFlowCoordinator, "runtimeFlowCoordinator");
      Objects.requireNonNull(senderStyleSupportContext, "senderStyleSupportContext");
      Objects.requireNonNull(messageCatalogSupport, "messageCatalogSupport");
      Objects.requireNonNull(reactionSummarySupport, "reactionSummarySupport");
      Objects.requireNonNull(docs, "docs");
      Objects.requireNonNull(stateByTarget, "stateByTarget");
      Objects.requireNonNull(ensureTargetExists, "ensureTargetExists");
      Objects.requireNonNull(noteEpochMs, "noteEpochMs");
      Objects.requireNonNull(appendReplyContextLine, "appendReplyContextLine");
      Objects.requireNonNull(renderedFromResolver, "renderedFromResolver");
      Objects.requireNonNull(endFilteredInsertRun, "endFilteredInsertRun");
      Objects.requireNonNull(deferRichTextDuringHistoryBatch, "deferRichTextDuringHistoryBatch");
    }
  }

  public ChatTranscriptMessageLineCoordinator(Dependencies dependencies) {
    Dependencies deps = Objects.requireNonNull(dependencies, "dependencies");
    ChatTranscriptManualPreviewSupport manualPreviewSupport = createManualPreviewSupport(deps);
    ChatTranscriptOutgoingChatSupport outgoingChatSupport = createOutgoingChatSupport(deps);
    ChatTranscriptActionAppendSupport.Context actionAppendSupportContext =
        createActionAppendSupportContext(deps, manualPreviewSupport);
    this.textAppendSupportContext = createTextAppendSupportContext(deps, manualPreviewSupport);
    ChatTranscriptActionHistoryInsertSupport.Context actionHistoryInsertSupportContext =
        createActionHistoryInsertSupportContext(deps);
    this.textInsertSupportContext = createTextInsertSupportContext(deps);
    this.manualPreviewFlowContext = createManualPreviewFlowContext(deps, manualPreviewSupport);
    this.chatFlowContext = createChatFlowContext(deps, outgoingChatSupport);
    this.actionFlowContext =
        createActionFlowContext(
            deps, actionAppendSupportContext, actionHistoryInsertSupportContext);
    this.outgoingActionSupport =
        new ChatTranscriptOutgoingActionSupport(
            actionFlowContext,
            new ChatTranscriptOutgoingDeliverySupport(deps.docs(), deps.mutationLock()),
            deps.runtimeSettingsSupport()::outgoingDeliveryIndicatorsEnabled);
    this.systemLineSupport = createSystemLineSupport(deps);
  }

  private static ChatTranscriptManualPreviewSupport createManualPreviewSupport(Dependencies deps) {
    return new ChatTranscriptManualPreviewSupport(
        deps.styles(), deps.imageEmbeds(), deps.linkPreviews());
  }

  private static ChatTranscriptOutgoingChatSupport createOutgoingChatSupport(Dependencies deps) {
    ChatTranscriptOutgoingDeliverySupport outgoingDeliverySupport =
        new ChatTranscriptOutgoingDeliverySupport(deps.docs(), deps.mutationLock());
    return new ChatTranscriptOutgoingChatSupport(
        deps.styles(),
        deps.senderStyleSupportContext(),
        deps.ensureTargetExists()::accept,
        deps.noteEpochMs()::accept,
        deps.runtimeFlowCoordinator()::breakPresenceRun,
        deps.runtimeFlowCoordinator()::appendLineWithTail,
        deps.runtimeFlowCoordinator()::insertLineAt,
        outgoingDeliverySupport::insertConfirmedDot);
  }

  private static ChatTranscriptActionAppendSupport.Context createActionAppendSupportContext(
      Dependencies deps, ChatTranscriptManualPreviewSupport manualPreviewSupport) {
    return new ChatTranscriptActionAppendSupport.Context(
        deps.styles(),
        deps.senderStyleSupportContext(),
        deps.ts(),
        deps.renderer(),
        manualPreviewSupport,
        deps.messageCatalogSupport(),
        (ref, from) -> deps.renderedFromResolver().render(ref, from),
        deps.styleRoutingSupport()::withFilterMatch,
        deps.documentLineSupport()::ensureAtLineStart,
        deps.lineCapSupport()::enforceTranscriptLineCap,
        deps.runtimeFlowCoordinator()::maybeRenderPendingReadMarker);
  }

  private static ChatTranscriptTextAppendSupport.Context createTextAppendSupportContext(
      Dependencies deps, ChatTranscriptManualPreviewSupport manualPreviewSupport) {
    return new ChatTranscriptTextAppendSupport.Context(
        deps.styles(),
        deps.ts(),
        deps.renderer(),
        deps.messageCatalogSupport(),
        manualPreviewSupport,
        deps.renderedFromResolver(),
        deps.styleRoutingSupport()::withFilterMatch,
        deps.lineCapSupport()::enforceTranscriptLineCap,
        deps.runtimeFlowCoordinator()::maybeRenderPendingReadMarker);
  }

  private static ChatTranscriptActionHistoryInsertSupport.Context
      createActionHistoryInsertSupportContext(Dependencies deps) {
    return new ChatTranscriptActionHistoryInsertSupport.Context(
        deps.styles(),
        deps.senderStyleSupportContext(),
        deps.ts(),
        deps.renderer(),
        deps.messageCatalogSupport(),
        (ref, from) -> deps.renderedFromResolver().render(ref, from),
        deps.styleRoutingSupport()::withFilterMatch,
        deps.documentLineSupport()::normalizeInsertAtLineStart,
        deps.documentLineSupport()::ensureAtLineStartForInsert,
        deps.runtimeFlowCoordinator()::shiftCurrentBlock,
        deps.lineCapSupport()::enforceTranscriptLineCap,
        deps.runtimeFlowCoordinator()::maybeRenderPendingReadMarker);
  }

  private static ChatTranscriptTextInsertSupport.Context createTextInsertSupportContext(
      Dependencies deps) {
    return new ChatTranscriptTextInsertSupport.Context(
        deps.styles(),
        deps.ts(),
        deps.renderer(),
        deps.messageCatalogSupport(),
        (ref, from) -> deps.renderedFromResolver().render(ref, from),
        deps.styleRoutingSupport()::withFilterMatch,
        deps.documentLineSupport()::normalizeInsertAtLineStart,
        deps.documentLineSupport()::ensureAtLineStartForInsert,
        deps.runtimeFlowCoordinator()::shiftCurrentBlock,
        deps.lineCapSupport()::enforceTranscriptLineCap);
  }

  private static ChatTranscriptManualPreviewFlowSupport.Context createManualPreviewFlowContext(
      Dependencies deps, ChatTranscriptManualPreviewSupport manualPreviewSupport) {
    return new ChatTranscriptManualPreviewFlowSupport.Context(
        deps.docs(),
        deps.ensureTargetExists()::accept,
        manualPreviewSupport,
        deps.runtimeFlowCoordinator()::shiftCurrentBlock,
        deps.lineCapSupport());
  }

  private static ChatTranscriptChatFlowSupport.Context createChatFlowContext(
      Dependencies deps, ChatTranscriptOutgoingChatSupport outgoingChatSupport) {
    return new ChatTranscriptChatFlowSupport.Context(
        deps.filterRoutingSupport(),
        deps.senderStyleSupportContext(),
        outgoingChatSupport,
        deps.reactionSummarySupport(),
        deps.docs()::get,
        deps.stateByTarget()::get,
        deps.ensureTargetExists()::accept,
        deps.noteEpochMs()::accept,
        deps.runtimeFlowCoordinator()::appendLine,
        deps.runtimeFlowCoordinator()::insertLineAt,
        (ref, fromNick, replyToMsgId, tsEpochMs) ->
            deps.appendReplyContextLine().append(ref, fromNick, replyToMsgId, tsEpochMs),
        deps.runtimeSettingsSupport()::outgoingDeliveryIndicatorsEnabled);
  }

  private static ChatTranscriptActionFlowSupport.Context createActionFlowContext(
      Dependencies deps,
      ChatTranscriptActionAppendSupport.Context actionAppendSupportContext,
      ChatTranscriptActionHistoryInsertSupport.Context actionHistoryInsertSupportContext) {
    return new ChatTranscriptActionFlowSupport.Context(
        deps.filterRoutingSupport(),
        actionAppendSupportContext,
        actionHistoryInsertSupportContext,
        deps.reactionSummarySupport(),
        deps.docs()::get,
        deps.stateByTarget()::get,
        deps.ensureTargetExists()::accept,
        deps.noteEpochMs()::accept,
        deps.appendReplyContextLine(),
        deps.endFilteredInsertRun()::accept,
        deps.deferRichTextDuringHistoryBatch(),
        deps.runtimeSettingsSupport()::timestampsIncludeChatMessages,
        deps.runtimeSettingsSupport()::imageEmbedsEnabled,
        deps.runtimeSettingsSupport()::linkPreviewsEnabled);
  }

  private static ChatTranscriptSystemLineSupport createSystemLineSupport(Dependencies deps) {
    return new ChatTranscriptSystemLineSupport(
        deps.filterRoutingSupport(),
        deps.ensureTargetExists()::accept,
        deps.noteEpochMs()::accept,
        deps.runtimeFlowCoordinator()::appendLine,
        deps.runtimeFlowCoordinator()::insertLineAt,
        deps.appendReplyContextLine()::append,
        deps.docs()::get,
        deps.stateByTarget()::get,
        deps.reactionSummarySupport(),
        ref ->
            new ChatTranscriptSystemLineSupport.LineStyles(
                deps.styles().noticeFrom(), deps.styles().noticeMessage()),
        ref ->
            new ChatTranscriptSystemLineSupport.LineStyles(
                deps.styleRoutingSupport().statusFromStyleFor(ref), deps.styles().status()),
        ref ->
            new ChatTranscriptSystemLineSupport.LineStyles(
                deps.styleRoutingSupport().errorFromStyleFor(ref), deps.styles().error()),
        System::currentTimeMillis);
  }

  public ChatTranscriptTextAppendSupport.Context textAppendSupportContext() {
    return textAppendSupportContext;
  }

  public ChatTranscriptTextInsertSupport.Context textInsertSupportContext() {
    return textInsertSupportContext;
  }

  public boolean insertManualPreviewAt(TargetRef ref, int insertAt, String rawUrl) {
    return manualPreviewFlowSupport.insertManualPreviewAt(
        manualPreviewFlowContext, ref, insertAt, rawUrl);
  }

  public void appendChat(TargetRef ref, String from, String text) {
    chatFlowSupport.appendChat(chatFlowContext, ref, from, text);
  }

  public void appendChat(TargetRef ref, String from, String text, boolean outgoingLocalEcho) {
    chatFlowSupport.appendChat(chatFlowContext, ref, from, text, outgoingLocalEcho);
  }

  public void appendChatFromHistory(
      TargetRef ref,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    chatFlowSupport.appendChatFromHistory(
        chatFlowContext, ref, from, text, outgoingLocalEcho, tsEpochMs, messageId, ircv3Tags);
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
    chatFlowSupport.appendChatAt(
        chatFlowContext,
        ref,
        from,
        text,
        outgoingLocalEcho,
        tsEpochMs,
        messageId,
        ircv3Tags,
        notificationRuleHighlightColor);
  }

  public void appendPendingOutgoingChat(
      TargetRef ref, String pendingId, String from, String text, long tsEpochMs) {
    chatFlowSupport.appendPendingOutgoingChat(
        chatFlowContext, ref, pendingId, from, text, tsEpochMs);
  }

  public void appendPendingOutgoingAction(
      TargetRef ref, String pendingId, String from, String text, long tsEpochMs) {
    outgoingActionSupport.appendPendingOutgoingAction(ref, pendingId, from, text, tsEpochMs);
  }

  public boolean resolvePendingOutgoingChat(
      TargetRef ref,
      String pendingId,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    return chatFlowSupport.resolvePendingOutgoingChat(
        chatFlowContext, ref, pendingId, from, text, tsEpochMs, messageId, ircv3Tags);
  }

  public boolean resolvePendingOutgoingAction(
      TargetRef ref,
      String pendingId,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    return outgoingActionSupport.resolvePendingOutgoingAction(
        ref, pendingId, from, text, tsEpochMs, messageId, ircv3Tags);
  }

  public boolean failPendingOutgoingChat(
      TargetRef ref, String pendingId, String from, String text, long tsEpochMs, String reason) {
    return chatFlowSupport.failPendingOutgoingChat(
        chatFlowContext, ref, pendingId, from, text, tsEpochMs, reason);
  }

  public boolean failPendingOutgoingAction(
      TargetRef ref, String pendingId, String from, String text, long tsEpochMs, String reason) {
    return outgoingActionSupport.failPendingOutgoingAction(
        ref, pendingId, from, text, tsEpochMs, reason);
  }

  public int insertChatFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    return chatFlowSupport.insertChatFromHistoryAt(
        chatFlowContext,
        ref,
        insertAt,
        from,
        text,
        outgoingLocalEcho,
        tsEpochMs,
        messageId,
        ircv3Tags);
  }

  public void appendAction(TargetRef ref, String from, String action) {
    actionFlowSupport.appendAction(actionFlowContext, ref, from, action);
  }

  public void appendAction(TargetRef ref, String from, String action, boolean outgoingLocalEcho) {
    actionFlowSupport.appendAction(actionFlowContext, ref, from, action, outgoingLocalEcho);
  }

  public void appendActionFromHistory(
      TargetRef ref,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    actionFlowSupport.appendActionFromHistory(
        actionFlowContext, ref, from, action, outgoingLocalEcho, tsEpochMs, messageId, ircv3Tags);
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
    actionFlowSupport.appendActionAt(
        actionFlowContext,
        ref,
        from,
        action,
        outgoingLocalEcho,
        tsEpochMs,
        messageId,
        ircv3Tags,
        notificationRuleHighlightColor);
  }

  public int insertActionFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String action,
      boolean outgoingLocalEcho,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    return actionFlowSupport.insertActionFromHistoryAt(
        actionFlowContext,
        ref,
        insertAt,
        from,
        action,
        outgoingLocalEcho,
        tsEpochMs,
        messageId,
        ircv3Tags);
  }

  public void insertReplacementAction(
      TargetRef ref,
      int insertAt,
      String from,
      String action,
      boolean outgoingLocalEcho,
      LineMeta meta) {
    actionFlowSupport.insertReplacementAction(
        actionFlowContext, ref, insertAt, from, action, outgoingLocalEcho, meta);
  }

  public void appendNotice(TargetRef ref, String from, String text) {
    systemLineSupport.appendNotice(ref, from, text);
  }

  public void appendStatus(TargetRef ref, String from, String text) {
    systemLineSupport.appendStatus(ref, from, text);
  }

  public void appendError(TargetRef ref, String from, String text) {
    systemLineSupport.appendError(ref, from, text);
  }

  public void appendNoticeFromHistory(
      TargetRef ref,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    systemLineSupport.appendNoticeFromHistory(ref, from, text, tsEpochMs, messageId, ircv3Tags);
  }

  public void appendStatusFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    systemLineSupport.appendStatusFromHistory(ref, from, text, tsEpochMs);
  }

  public void appendErrorFromHistory(TargetRef ref, String from, String text, long tsEpochMs) {
    systemLineSupport.appendErrorFromHistory(ref, from, text, tsEpochMs);
  }

  public void appendNoticeAt(TargetRef ref, String from, String text, long tsEpochMs) {
    appendNoticeAt(ref, from, text, tsEpochMs, "", Map.of());
  }

  public void appendNoticeAt(
      TargetRef ref,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    systemLineSupport.appendNoticeAt(ref, from, text, tsEpochMs, messageId, ircv3Tags);
  }

  public void appendStatusAt(TargetRef ref, String from, String text, long tsEpochMs) {
    appendStatusAt(ref, from, text, tsEpochMs, "", Map.of());
  }

  public void appendStatusAt(
      TargetRef ref,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    systemLineSupport.appendStatusAt(ref, from, text, tsEpochMs, messageId, ircv3Tags);
  }

  public void appendErrorAt(TargetRef ref, String from, String text, long tsEpochMs) {
    systemLineSupport.appendErrorAt(ref, from, text, tsEpochMs);
  }

  public int insertNoticeFromHistoryAt(
      TargetRef ref,
      int insertAt,
      String from,
      String text,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    return systemLineSupport.insertNoticeFromHistoryAt(
        ref, insertAt, from, text, tsEpochMs, messageId, ircv3Tags);
  }

  public int insertStatusFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    return systemLineSupport.insertStatusFromHistoryAt(ref, insertAt, from, text, tsEpochMs);
  }

  public int insertErrorFromHistoryAt(
      TargetRef ref, int insertAt, String from, String text, long tsEpochMs) {
    return systemLineSupport.insertErrorFromHistoryAt(ref, insertAt, from, text, tsEpochMs);
  }
}
