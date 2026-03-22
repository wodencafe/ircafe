package cafe.woden.ircclient.app.outbound.mutation;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Backend-specific payload shaping for reply/react/edit/redact outbound commands. */
@SecondaryPort
@ApplicationLayer
public interface MessageMutationOutboundCommands {

  IrcProperties.Server.Backend backend();

  String buildReplyRawLine(TargetRef target, String replyToMessageId, String message);

  String buildReactRawLine(TargetRef target, String replyToMessageId, String reaction);

  String buildUnreactRawLine(TargetRef target, String replyToMessageId, String reaction);

  String buildEditRawLine(TargetRef target, String targetMessageId, String editedText);

  String buildRedactRawLine(TargetRef target, String targetMessageId, String reason);

  default Map<String, String> localEchoEditTags(String targetMessageId) {
    String msgId = Objects.toString(targetMessageId, "").trim();
    if (msgId.isEmpty()) return Map.of();
    return Map.of("draft/edit", msgId);
  }

  default Map<String, String> localEchoRedactionTags(String targetMessageId) {
    String msgId = Objects.toString(targetMessageId, "").trim();
    if (msgId.isEmpty()) return Map.of();
    return Map.of("draft/delete", msgId);
  }
}
