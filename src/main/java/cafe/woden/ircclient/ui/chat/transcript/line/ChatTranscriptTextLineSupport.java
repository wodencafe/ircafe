package cafe.woden.ircclient.ui.chat.transcript.line;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.ChatStyles;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptReplyContextSupport;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTimestampFormatter;
import java.util.Objects;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/** Shared document-writing helpers for standard timestamp/from/message transcript lines. */
public final class ChatTranscriptTextLineSupport {

  public record WriteResult(int nextOffset, int lineEndOffset) {}

  private ChatTranscriptTextLineSupport() {}

  public static WriteResult writeLineAt(
      StyledDocument doc,
      TargetRef ref,
      int insertAt,
      String text,
      String renderedFrom,
      AttributeSet timestampStyle,
      AttributeSet fromStyle,
      AttributeSet messageStyle,
      Long epochMs,
      ChatTimestampFormatter timestampFormatter,
      ChatRichTextRenderer richTextRenderer,
      boolean timestampsIncludeChatMessages,
      boolean timestampsIncludePresenceMessages,
      boolean deferRichText,
      java.awt.Component tailComponent,
      AttributeSet tailStyle)
      throws BadLocationException {
    if (doc == null) {
      int clamped = Math.max(0, insertAt);
      return new WriteResult(clamped, clamped);
    }

    int pos = Math.max(0, Math.min(insertAt, doc.getLength()));
    int start = pos;
    if (shouldInsertTimestamp(
        timestampFormatter,
        messageStyle,
        timestampsIncludeChatMessages,
        timestampsIncludePresenceMessages)) {
      String prefix =
          (epochMs != null) ? timestampFormatter.prefixAt(epochMs) : timestampFormatter.prefixNow();
      doc.insertString(pos, prefix, timestampStyle);
      pos += prefix.length();
    }

    if (renderedFrom != null && !renderedFrom.isBlank()) {
      String prefix = renderedFrom + ": ";
      doc.insertString(pos, prefix, fromStyle);
      pos += prefix.length();
    }

    if (richTextRenderer != null && !deferRichText) {
      pos = richTextRenderer.insertRichTextAt(doc, ref, text, messageStyle, pos);
    } else {
      pos =
          ChatRichTextRenderer.insertStyledTextAt(doc, text == null ? "" : text, messageStyle, pos);
    }

    if (tailComponent != null) {
      SimpleAttributeSet attrs =
          new SimpleAttributeSet(tailStyle != null ? tailStyle : messageStyle);
      StyleConstants.setComponent(attrs, tailComponent);
      doc.insertString(pos, " ", attrs);
      pos += 1;
    }

    int lineEndOffset = pos;
    doc.insertString(pos, "\n", timestampStyle);
    pos += 1;
    ChatTranscriptReplyContextSupport.styleReplyBody(doc, start, lineEndOffset);
    return new WriteResult(pos, lineEndOffset);
  }

  private static boolean shouldInsertTimestamp(
      ChatTimestampFormatter timestampFormatter,
      AttributeSet messageStyle,
      boolean timestampsIncludeChatMessages,
      boolean timestampsIncludePresenceMessages) {
    if (timestampFormatter == null || !timestampFormatter.enabled()) {
      return false;
    }

    String styleId =
        Objects.toString(
            messageStyle != null ? messageStyle.getAttribute(ChatStyles.ATTR_STYLE) : null, "");
    return ChatStyles.STYLE_STATUS.equals(styleId)
        || ChatStyles.STYLE_ERROR.equals(styleId)
        || ChatStyles.STYLE_NOTICE_MESSAGE.equals(styleId)
        || (timestampsIncludePresenceMessages && ChatStyles.STYLE_PRESENCE.equals(styleId))
        || (timestampsIncludeChatMessages && ChatStyles.STYLE_MESSAGE.equals(styleId));
  }
}
