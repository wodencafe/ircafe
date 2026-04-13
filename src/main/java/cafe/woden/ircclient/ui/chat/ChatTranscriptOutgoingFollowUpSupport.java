package cafe.woden.ircclient.ui.chat;

import static cafe.woden.ircclient.ui.chat.ChatTranscriptMessageMetadataSupport.firstIrcv3TagValue;
import static cafe.woden.ircclient.ui.chat.ChatTranscriptMessageMetadataSupport.normalizeMessageId;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Shared helpers for outgoing transcript follow-up triggered by IRCv3 message tags. */
final class ChatTranscriptOutgoingFollowUpSupport {

  record Plan(String normalizedMessageId, String replyToMessageId, String reactionToken) {
    boolean hasReplyContext() {
      return !replyToMessageId.isBlank();
    }

    boolean hasMaterializedMessageId() {
      return !normalizedMessageId.isBlank();
    }

    boolean hasReplyReaction() {
      return !replyToMessageId.isBlank() && !reactionToken.isBlank();
    }

    void runReplyContext(Consumer<String> callback) {
      if (callback != null && hasReplyContext()) {
        callback.accept(replyToMessageId);
      }
    }

    void runPendingMaterialization(Runnable callback) {
      if (callback != null && hasMaterializedMessageId()) {
        callback.run();
      }
    }

    void runReplyReaction(Runnable callback) {
      if (callback != null && hasReplyReaction()) {
        callback.run();
      }
    }
  }

  private ChatTranscriptOutgoingFollowUpSupport() {}

  static Plan plan(String messageId, Map<String, String> ircv3Tags) {
    String normalizedMessageId = normalizeMessageId(messageId);
    String replyToMessageId =
        firstIrcv3TagValue(ircv3Tags, "reply", "+reply", "draft/reply", "+draft/reply");
    String reactionToken = firstIrcv3TagValue(ircv3Tags, "draft/react", "+draft/react");
    return new Plan(
        normalizedMessageId,
        Objects.toString(replyToMessageId, "").trim(),
        Objects.toString(reactionToken, "").trim());
  }
}
