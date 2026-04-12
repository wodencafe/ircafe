package cafe.woden.ircclient.ui.chat;

import static cafe.woden.ircclient.ui.chat.ChatTranscriptMessageMetadataSupport.normalizeMessageId;
import static cafe.woden.ircclient.ui.chat.ChatTranscriptMessageMetadataSupport.normalizePendingId;

import java.awt.Component;
import java.util.Objects;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/** Shared transcript document scan helpers for line and component lookups. */
final class ChatTranscriptDocumentSupport {

  private ChatTranscriptDocumentSupport() {}

  static int findLineStartByMessageId(StyledDocument doc, String messageId) {
    if (doc == null) return -1;
    String want = normalizeMessageId(messageId);
    if (want.isEmpty()) return -1;
    try {
      Element root = doc.getDefaultRootElement();
      if (root == null) return -1;
      int len = doc.getLength();
      for (int i = 0; i < root.getElementCount(); i++) {
        Element line = root.getElement(i);
        if (line == null) continue;
        int start = Math.max(0, line.getStartOffset());
        if (start >= len) continue;
        AttributeSet attrs = doc.getCharacterElement(start).getAttributes();
        String got = Objects.toString(attrs.getAttribute(ChatStyles.ATTR_META_MSGID), "").trim();
        if (want.equals(got)) return start;
      }
    } catch (Exception ignored) {
    }
    return -1;
  }

  static int lineEndOffsetForLineStart(StyledDocument doc, int lineStart) {
    if (doc == null) return Math.max(0, lineStart);
    try {
      Element root = doc.getDefaultRootElement();
      if (root == null) return Math.max(0, lineStart);
      int idx = root.getElementIndex(Math.max(0, lineStart));
      Element line = root.getElement(idx);
      if (line == null) return Math.max(0, lineStart);
      int end = line.getEndOffset();
      return Math.max(0, Math.min(end, doc.getLength()));
    } catch (Exception ignored) {
      return Math.max(0, lineStart);
    }
  }

  static int findLineStartByPendingId(StyledDocument doc, String pendingId) {
    if (doc == null) return -1;
    String want = normalizePendingId(pendingId);
    if (want.isEmpty()) return -1;
    try {
      Element root = doc.getDefaultRootElement();
      if (root == null) return -1;
      int len = doc.getLength();
      for (int i = 0; i < root.getElementCount(); i++) {
        Element line = root.getElement(i);
        if (line == null) continue;
        int start = Math.max(0, line.getStartOffset());
        int end = Math.max(start, Math.min(line.getEndOffset(), len));
        if (start >= end) continue;
        for (int p = start; p < end; p++) {
          AttributeSet attrs = doc.getCharacterElement(p).getAttributes();
          String got =
              Objects.toString(attrs.getAttribute(ChatStyles.ATTR_META_PENDING_ID), "").trim();
          if (want.equals(got)) return start;
        }
      }
    } catch (Exception ignored) {
    }
    return -1;
  }

  static int findInlineComponentOffset(StyledDocument doc, int start, int end, Component expected) {
    if (doc == null || expected == null) return -1;
    int len = doc.getLength();
    if (len <= 0) return -1;

    int s = Math.max(0, Math.min(start, len - 1));
    int e = Math.max(0, Math.min(end, len - 1));
    if (e < s) {
      int tmp = s;
      s = e;
      e = tmp;
    }
    for (int i = s; i <= e; i++) {
      try {
        Element el = doc.getCharacterElement(i);
        if (el == null) continue;
        AttributeSet attrs = el.getAttributes();
        Object component = attrs != null ? StyleConstants.getComponent(attrs) : null;
        if (component == expected) return i;
      } catch (Exception ignored) {
      }
    }
    return -1;
  }
}
