package cafe.woden.ircclient.ui.chat.transcript.line;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.chat.render.ChatRichTextRenderer;
import cafe.woden.ircclient.ui.chat.transcript.message.ChatTranscriptReplyContextSupport;
import cafe.woden.ircclient.ui.chat.transcript.runtime.ChatTimestampFormatter;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;

/** Shared document-writing helpers for action transcript lines. */
public final class ChatTranscriptActionLineSupport {

  public record WriteResult(int nextOffset, int lineEndOffset) {}

  private ChatTranscriptActionLineSupport() {}

  public static WriteResult writeLineAt(
      StyledDocument doc,
      TargetRef ref,
      int insertAt,
      String action,
      String renderedFrom,
      AttributeSet timestampStyle,
      AttributeSet fromStyle,
      AttributeSet messageStyle,
      long epochMs,
      ChatTimestampFormatter timestampFormatter,
      ChatRichTextRenderer richTextRenderer,
      boolean includeChatTimestamps,
      boolean deferRichText)
      throws BadLocationException {
    if (doc == null) {
      int clamped = Math.max(0, insertAt);
      return new WriteResult(clamped, clamped);
    }

    int pos = Math.max(0, Math.min(insertAt, doc.getLength()));
    int start = pos;
    if (timestampFormatter != null && timestampFormatter.enabled() && includeChatTimestamps) {
      String prefix = timestampFormatter.prefixAt(epochMs);
      doc.insertString(pos, prefix, timestampStyle);
      pos += prefix.length();
    }

    doc.insertString(pos, "* ", messageStyle);
    pos += 2;

    if (renderedFrom != null && !renderedFrom.isBlank()) {
      doc.insertString(pos, renderedFrom, fromStyle);
      pos += renderedFrom.length();
      doc.insertString(pos, " ", messageStyle);
      pos += 1;
    }

    String actionText = action == null ? "" : action;
    if (richTextRenderer != null && !deferRichText) {
      pos = richTextRenderer.insertRichTextAt(doc, ref, actionText, messageStyle, pos);
    } else {
      pos = ChatRichTextRenderer.insertStyledTextAt(doc, actionText, messageStyle, pos);
    }

    int lineEndOffset = pos;
    doc.insertString(pos, "\n", timestampStyle);
    pos += 1;
    ChatTranscriptReplyContextSupport.styleReplyBody(doc, start, lineEndOffset);
    return new WriteResult(pos, lineEndOffset);
  }
}
