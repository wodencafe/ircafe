package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.coordinator.MessageActionCapabilityPolicy;
import java.util.Objects;
import java.util.function.Function;

/** Shared reaction toggle policy for transcript chips and quick-reaction pickers. */
public final class MessageReactionToggleSupport {

  private MessageReactionToggleSupport() {}

  public static String resolveCommand(
      TargetRef target,
      String messageId,
      String reactionToken,
      boolean explicitRemovalRequested,
      ChatTranscriptStore transcripts,
      MessageActionCapabilityPolicy capabilityPolicy,
      Function<String, String> currentNickLookup) {
    if (target == null || target.isUiOnly() || capabilityPolicy == null) return "";

    String serverId = Objects.toString(target.serverId(), "").trim();
    String msgId = normalizeMessageId(messageId);
    String token = Objects.toString(reactionToken, "").trim();
    if (serverId.isEmpty() || msgId.isEmpty() || token.isEmpty()) return "";

    boolean remove =
        explicitRemovalRequested
            || hasOwnReaction(target, msgId, token, transcripts, currentNickLookup);
    if (remove) {
      if (!capabilityPolicy.canUnreact(serverId)) return "";
      return "/unreact " + msgId + " " + token;
    }
    if (!capabilityPolicy.canReact(serverId)) return "";
    return "/react " + msgId + " " + token;
  }

  private static boolean hasOwnReaction(
      TargetRef target,
      String messageId,
      String reactionToken,
      ChatTranscriptStore transcripts,
      Function<String, String> currentNickLookup) {
    if (target == null || transcripts == null || currentNickLookup == null) return false;
    String currentNick;
    try {
      currentNick = Objects.toString(currentNickLookup.apply(target.serverId()), "").trim();
    } catch (Exception ignored) {
      return false;
    }
    if (currentNick.isEmpty()) return false;
    return transcripts.hasReactionFromNick(target, messageId, reactionToken, currentNick);
  }

  private static String normalizeMessageId(String rawMessageId) {
    String messageId = Objects.toString(rawMessageId, "").trim();
    return messageId.isEmpty() ? "" : messageId;
  }
}
