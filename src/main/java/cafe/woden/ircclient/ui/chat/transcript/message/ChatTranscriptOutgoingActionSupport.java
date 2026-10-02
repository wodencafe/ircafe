package cafe.woden.ircclient.ui.chat.transcript.message;

import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptActionHistoryInsertSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptLineMetaSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptOutgoingDeliverySupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptPendingOutgoingSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.LineMeta;
import cafe.woden.ircclient.ui.chat.transcript.line.OutgoingSendIndicator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/** Renders pending actions and replaces them in place with confirmed or failed action lines. */
public final class ChatTranscriptOutgoingActionSupport {
  private final ChatTranscriptActionFlowSupport.Context context;
  private final ChatTranscriptOutgoingDeliverySupport deliverySupport;
  private final BooleanSupplier deliveryIndicatorsEnabled;

  public ChatTranscriptOutgoingActionSupport(
      ChatTranscriptActionFlowSupport.Context context,
      ChatTranscriptOutgoingDeliverySupport deliverySupport,
      BooleanSupplier deliveryIndicatorsEnabled) {
    this.context = Objects.requireNonNull(context, "context");
    this.deliverySupport = Objects.requireNonNull(deliverySupport, "deliverySupport");
    this.deliveryIndicatorsEnabled =
        Objects.requireNonNull(deliveryIndicatorsEnabled, "deliveryIndicatorsEnabled");
  }

  public void appendPendingOutgoingAction(
      TargetRef ref, String pendingId, String from, String action, long tsEpochMs) {
    String id = ChatTranscriptMessageMetadataSupport.normalizePendingId(pendingId);
    if (ref == null || id.isEmpty()) return;
    context.ensureTargetExists().ensure(ref);
    StyledDocument doc = context.document(ref);
    if (doc == null) return;
    LineMeta meta = metadata(ref, from, tsEpochMs, "", Map.of());
    int after = insert(ref, doc.getLength(), from, action, tsEpochMs, meta);
    int lineStart = doc.getParagraphElement(Math.max(0, after - 2)).getStartOffset();
    SimpleAttributeSet pendingAttrs = new SimpleAttributeSet();
    ChatTranscriptPendingOutgoingSupport.markPending(pendingAttrs, id);
    doc.setCharacterAttributes(lineStart, Math.max(0, after - lineStart), pendingAttrs, false);
    if (deliveryIndicatorsEnabled.getAsBoolean()) {
      SimpleAttributeSet tailAttrs =
          ChatTranscriptPendingOutgoingSupport.pendingTailAttrs(
              doc.getCharacterElement(Math.max(lineStart, after - 2)).getAttributes(), id);
      StyleConstants.setComponent(
          tailAttrs,
          new OutgoingSendIndicator.PendingSpinner(
              ChatTranscriptPendingOutgoingSupport.pendingSpinnerColor(tailAttrs)));
      try {
        doc.insertString(after - 1, " ", tailAttrs);
      } catch (BadLocationException ignored) {
      }
    }
    finishAppend(ref, doc, tsEpochMs);
  }

  public boolean resolvePendingOutgoingAction(
      TargetRef ref,
      String pendingId,
      String from,
      String action,
      long tsEpochMs,
      String messageId,
      Map<String, String> ircv3Tags) {
    if (ref == null) return false;
    context.ensureTargetExists().ensure(ref);
    StyledDocument doc = context.document(ref);
    var replacement =
        ChatTranscriptChatFlowSupport.preparePendingReplacement(
            doc, pendingId, tsEpochMs, System::currentTimeMillis);
    if (replacement == null) return false;
    long epochMs = replacement.effectiveEpochMs();
    LineMeta meta = metadata(ref, from, epochMs, messageId, ircv3Tags);
    int after = insert(ref, replacement.lineStart(), from, action, epochMs, meta);
    if (deliveryIndicatorsEnabled.getAsBoolean()) {
      SimpleAttributeSet style =
          ChatTranscriptSenderStyleSupport.prepareAction(
                  context.actionHistoryInsertSupportContext().senderStyleSupportContext(),
                  meta,
                  from,
                  true,
                  null)
              .messageStyle();
      deliverySupport.insertConfirmedDot(ref, after, style, meta);
    }
    var state = context.state(ref);
    ChatTranscriptOutgoingFollowUpSupport.plan(messageId, ircv3Tags)
        .applyPostAppend(
            ref,
            doc,
            context.reactionSummarySupport(),
            state == null ? null : state.reactionSummary(),
            from,
            epochMs);
    finishAppend(ref, doc, epochMs);
    return true;
  }

  public boolean failPendingOutgoingAction(
      TargetRef ref, String pendingId, String from, String action, long tsEpochMs, String reason) {
    if (ref == null) return false;
    context.ensureTargetExists().ensure(ref);
    StyledDocument doc = context.document(ref);
    var replacement =
        ChatTranscriptChatFlowSupport.preparePendingReplacement(
            doc, pendingId, tsEpochMs, System::currentTimeMillis);
    if (replacement == null) return false;
    long epochMs = replacement.effectiveEpochMs();
    LineMeta meta = metadata(ref, from, epochMs, "", Map.of());
    int after =
        insert(
            ref,
            replacement.lineStart(),
            from,
            Objects.toString(action, "")
                + " "
                + ChatTranscriptPendingOutgoingSupport.renderPendingFailure(reason),
            epochMs,
            meta);
    doc.setCharacterAttributes(
        replacement.lineStart(),
        after - replacement.lineStart(),
        context.actionHistoryInsertSupportContext().styles().error(),
        false);
    finishAppend(ref, doc, epochMs);
    return true;
  }

  private LineMeta metadata(
      TargetRef ref, String from, long epochMs, String messageId, Map<String, String> ircv3Tags) {
    return ChatTranscriptLineMetaSupport.create(
        ref, LogKind.ACTION, LogDirection.OUT, from, epochMs, null, messageId, ircv3Tags);
  }

  private int insert(
      TargetRef ref, int insertAt, String from, String action, long epochMs, LineMeta meta) {
    context.noteEpochMs().note(ref, epochMs);
    var state = context.state(ref);
    return ChatTranscriptActionHistoryInsertSupport.insertVisibleAction(
        context.actionHistoryInsertSupportContext(),
        ref,
        context.document(ref),
        state == null ? null : state.messageCatalog(),
        insertAt,
        from,
        action,
        true,
        epochMs,
        meta,
        null,
        context.includeChatTimestamps(),
        false,
        false,
        false);
  }

  private void finishAppend(TargetRef ref, StyledDocument doc, long epochMs) {
    context.actionHistoryInsertSupportContext().transcriptLineCapEnforcer().enforce(ref, doc);
    context.actionHistoryInsertSupportContext().pendingReadMarkerRenderer().render(ref, epochMs);
  }
}
