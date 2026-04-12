package cafe.woden.ircclient.ui.chat;

import static cafe.woden.ircclient.ui.chat.ChatTranscriptMessageMetadataSupport.normalizeMessageId;

import cafe.woden.ircclient.model.LogKind;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.swing.text.AttributeSet;

/** Shared transcript message-preview and snapshot state update helpers. */
final class ChatTranscriptMessageStateSupport {

  record Context(
      int replyPreviewTextMaxChars,
      String redactedMessagePlaceholder,
      LongSupplier currentTimeMillis) {
    Context {
      Objects.requireNonNull(redactedMessagePlaceholder, "redactedMessagePlaceholder");
      Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
    }
  }

  private ChatTranscriptMessageStateSupport() {}

  static void rememberMessagePreview(
      Context context,
      Map<String, String> messagePreviewByMsgId,
      ChatTranscriptStore.LineMeta meta,
      String from,
      String text) {
    if (context == null || messagePreviewByMsgId == null || meta == null) return;
    String msgId = normalizeMessageId(meta.messageId());
    if (msgId.isEmpty()) return;
    LogKind kind = meta.kind();
    if (!isPreviewableKind(kind)) return;

    String preview =
        ChatTranscriptReplyPreviewSupport.formatReplyPreviewSnippet(
            kind, from, text, context.replyPreviewTextMaxChars());
    if (preview.isBlank()) return;
    messagePreviewByMsgId.put(msgId, preview);
  }

  static void rememberCurrentMessageContent(
      Map<String, ChatTranscriptStore.MessageContentSnapshot> currentMessageContentByMsgId,
      ChatTranscriptStore.LineMeta meta,
      String from,
      String renderedText) {
    if (currentMessageContentByMsgId == null || meta == null) return;
    String msgId = normalizeMessageId(meta.messageId());
    if (msgId.isEmpty()) return;
    LogKind kind = meta.kind();
    if (!isPreviewableKind(kind)) return;

    currentMessageContentByMsgId.put(
        msgId,
        new ChatTranscriptStore.MessageContentSnapshot(
            kind,
            Objects.toString(from, "").trim(),
            Objects.toString(renderedText, ""),
            meta.epochMs()));
  }

  static void rememberEditedCurrentMessageContent(
      Map<String, ChatTranscriptStore.MessageContentSnapshot> currentMessageContentByMsgId,
      String targetMsgId,
      AttributeSet existingAttrs,
      String renderedEditedText) {
    if (currentMessageContentByMsgId == null) return;
    String msgId = normalizeMessageId(targetMsgId);
    if (msgId.isEmpty()) return;

    ChatTranscriptStore.MessageContentSnapshot existing = currentMessageContentByMsgId.get(msgId);
    LogKind kind =
        (existing != null && existing.kind() != null)
            ? existing.kind()
            : ChatTranscriptAttrSupport.logKindFromAttrs(existingAttrs);
    String fromNick =
        (existing != null && existing.fromNick() != null && !existing.fromNick().isBlank())
            ? existing.fromNick()
            : Objects.toString(
                existingAttrs == null
                    ? null
                    : existingAttrs.getAttribute(ChatStyles.ATTR_META_FROM),
                "");
    Long epochMs =
        (existing != null && existing.epochMs() != null)
            ? existing.epochMs()
            : ChatTranscriptAttrSupport.lineEpochMs(existingAttrs);

    currentMessageContentByMsgId.put(
        msgId,
        new ChatTranscriptStore.MessageContentSnapshot(
            kind, Objects.toString(fromNick, "").trim(), renderedEditedText, epochMs));
  }

  static void rememberRedactedOriginal(
      Context context,
      Map<String, ChatTranscriptStore.MessageContentSnapshot> currentMessageContentByMsgId,
      Map<String, ChatTranscriptStore.RedactedMessageContent> redactedOriginalByMsgId,
      String targetMsgId,
      AttributeSet existingAttrs,
      String redactedBy,
      long redactedAtEpochMs) {
    if (context == null
        || currentMessageContentByMsgId == null
        || redactedOriginalByMsgId == null) {
      return;
    }
    String msgId = normalizeMessageId(targetMsgId);
    if (msgId.isEmpty()) return;

    ChatTranscriptStore.MessageContentSnapshot current = currentMessageContentByMsgId.get(msgId);
    LogKind originalKind =
        current != null && current.kind() != null
            ? current.kind()
            : ChatTranscriptAttrSupport.logKindFromAttrs(existingAttrs);
    String originalFromNick =
        current != null && current.fromNick() != null && !current.fromNick().isBlank()
            ? current.fromNick()
            : Objects.toString(
                    existingAttrs == null
                        ? null
                        : existingAttrs.getAttribute(ChatStyles.ATTR_META_FROM),
                    "")
                .trim();
    String originalText =
        current != null
            ? Objects.toString(current.renderedText(), "")
            : context.redactedMessagePlaceholder();
    if (originalText.isBlank() || context.redactedMessagePlaceholder().equals(originalText)) {
      return;
    }
    Long originalEpochMs =
        current != null && current.epochMs() != null
            ? current.epochMs()
            : ChatTranscriptAttrSupport.lineEpochMs(existingAttrs);
    long effectiveRedactedAt =
        redactedAtEpochMs > 0 ? redactedAtEpochMs : context.currentTimeMillis().getAsLong();

    redactedOriginalByMsgId.put(
        msgId,
        new ChatTranscriptStore.RedactedMessageContent(
            msgId,
            originalKind,
            originalFromNick,
            originalText,
            originalEpochMs,
            Objects.toString(redactedBy, "").trim(),
            effectiveRedactedAt));
  }

  private static boolean isPreviewableKind(LogKind kind) {
    return kind == LogKind.CHAT
        || kind == LogKind.ACTION
        || kind == LogKind.NOTICE
        || kind == LogKind.SPOILER;
  }
}
