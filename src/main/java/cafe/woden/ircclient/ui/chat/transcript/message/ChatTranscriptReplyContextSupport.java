package cafe.woden.ircclient.ui.chat.transcript.message;

import static cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptMessageMetadataSupport.normalizeMessageId;
import static cafe.woden.ircclient.util.Ircv3CapabilityNames.DRAFT_REPLY;

import cafe.woden.ircclient.model.LogDirection;
import cafe.woden.ircclient.model.LogKind;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptLineMetaSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.ChatTranscriptReplyPreviewSupport;
import cafe.woden.ircclient.ui.chat.transcript.line.LineMeta;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public final class ChatTranscriptReplyContextSupport {

  public record Context(ChatStyles styles) {
    public Context {
      Objects.requireNonNull(styles, "styles");
    }
  }

  private ChatTranscriptReplyContextSupport() {}

  public static void appendReplyContextLine(
      Context context,
      StyledDocument doc,
      TargetRef ref,
      String fromNick,
      String replyToMsgId,
      long tsEpochMs,
      Function<String, String> previewLookup) {
    if (context == null || doc == null || previewLookup == null) return;

    String targetMsgId = normalizeMessageId(replyToMsgId);
    if (targetMsgId.isEmpty()) return;

    Map<String, String> tags = Map.of(DRAFT_REPLY, targetMsgId);
    LineMeta meta =
        ChatTranscriptLineMetaSupport.create(
            ref, LogKind.STATUS, LogDirection.SYSTEM, fromNick, tsEpochMs, null, targetMsgId, tags);
    SimpleAttributeSet msgRefStyle =
        ChatTranscriptLineMetaSupport.bind(
            context.styles().byStyleId(ChatStyles.STYLE_REPLY_QUOTE), meta);
    msgRefStyle.addAttribute(ChatStyles.ATTR_MSG_REF, targetMsgId);

    String preview =
        ChatTranscriptReplyPreviewSupport.normalizeReplyPreviewText(
            Objects.toString(previewLookup.apply(targetMsgId), ""), 160);
    String quote = preview.isBlank() ? "↪ Reply to an earlier message" : "↪ Reply to " + preview;

    try {
      int start = doc.getLength();
      doc.insertString(start, quote + "\n", msgRefStyle);
      doc.setParagraphAttributes(start, quote.length(), blockParagraph(true), false);
    } catch (Exception ignored) {
    }
  }

  /** Attach a completed message to the immediately preceding quote without styling future rows. */
  public static void styleReplyBody(StyledDocument doc, int start, int end) {
    if (start <= 0 || end <= start) return;
    if (!ChatStyles.REPLY_BLOCK_QUOTE.equals(
        doc.getParagraphElement(start - 1)
            .getAttributes()
            .getAttribute(ChatStyles.ATTR_REPLY_BLOCK))) return;
    doc.setParagraphAttributes(start, end - start, blockParagraph(false), false);
  }

  private static SimpleAttributeSet blockParagraph(boolean quote) {
    SimpleAttributeSet attrs = new SimpleAttributeSet();
    attrs.addAttribute(
        ChatStyles.ATTR_REPLY_BLOCK,
        quote ? ChatStyles.REPLY_BLOCK_QUOTE : ChatStyles.REPLY_BLOCK_BODY);
    StyleConstants.setLeftIndent(attrs, 14);
    StyleConstants.setRightIndent(attrs, 8);
    StyleConstants.setSpaceAbove(attrs, quote ? 8 : 0);
    StyleConstants.setSpaceBelow(attrs, quote ? 2 : 8);
    return attrs;
  }
}
