package cafe.woden.ircclient.ui.chat;

import static cafe.woden.ircclient.ui.chat.ChatTranscriptMessageMetadataSupport.normalizePendingId;

import java.util.function.LongSupplier;
import javax.swing.text.StyledDocument;

/** Shared helpers for replacing a pending outgoing transcript line in place. */
final class ChatTranscriptPendingReplacementSupport {

  record ReplacementPlan(int lineStart, long effectiveEpochMs) {}

  private ChatTranscriptPendingReplacementSupport() {}

  static ReplacementPlan prepareReplacement(
      StyledDocument doc, String pendingId, long tsEpochMs, LongSupplier currentTimeMillis) {
    if (doc == null || currentTimeMillis == null) return null;
    String normalizedPendingId = normalizePendingId(pendingId);
    if (normalizedPendingId.isEmpty()) return null;

    int lineStart =
        ChatTranscriptDocumentSupport.findLineStartByPendingId(doc, normalizedPendingId);
    if (lineStart < 0) return null;
    int lineEnd = ChatTranscriptDocumentSupport.lineEndOffsetForLineStart(doc, lineStart);
    try {
      doc.remove(lineStart, Math.max(0, lineEnd - lineStart));
    } catch (Exception ignored) {
      return null;
    }

    long effectiveEpochMs = tsEpochMs > 0 ? tsEpochMs : currentTimeMillis.getAsLong();
    return new ReplacementPlan(lineStart, effectiveEpochMs);
  }
}
