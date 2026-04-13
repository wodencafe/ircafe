package cafe.woden.ircclient.ui.chat;

import static cafe.woden.ircclient.ui.chat.ChatTranscriptMessageMetadataSupport.normalizeMessageId;

import javax.swing.text.StyledDocument;

/** Shared append preflight helpers for duplicate message-id suppression. */
final class ChatTranscriptAppendGuardSupport {

  private ChatTranscriptAppendGuardSupport() {}

  static boolean shouldSkipAppendByMessageId(StyledDocument doc, String messageId) {
    String normalizedMessageId = normalizeMessageId(messageId);
    return !normalizedMessageId.isBlank()
        && ChatTranscriptDocumentSupport.findLineStartByMessageId(doc, normalizedMessageId) >= 0;
  }
}
