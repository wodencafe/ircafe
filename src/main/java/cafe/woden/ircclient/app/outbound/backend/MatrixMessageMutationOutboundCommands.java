package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.outbound.MessageMutationOutboundCommands;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.model.TargetRef;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Matrix backend payload shaping for message mutation commands. */
@Component
@SecondaryAdapter
@ApplicationLayer
public final class MatrixMessageMutationOutboundCommands
    implements MessageMutationOutboundCommands {

  @Override
  public IrcProperties.Server.Backend backend() {
    return IrcProperties.Server.Backend.MATRIX;
  }

  @Override
  public String buildReplyRawLine(TargetRef target, String replyToMessageId, String message) {
    return MessageMutationOutboundCommandLineBuilder.buildReplyRawLine(
        target, replyToMessageId, message);
  }

  @Override
  public String buildReactRawLine(TargetRef target, String replyToMessageId, String reaction) {
    return MessageMutationOutboundCommandLineBuilder.buildReactRawLine(
        target, replyToMessageId, reaction);
  }

  @Override
  public String buildUnreactRawLine(TargetRef target, String replyToMessageId, String reaction) {
    return MessageMutationOutboundCommandLineBuilder.buildUnreactRawLine(
        target, replyToMessageId, reaction);
  }

  @Override
  public String buildEditRawLine(TargetRef target, String targetMessageId, String editedText) {
    return MessageMutationOutboundCommandLineBuilder.buildEditRawLine(
        target, targetMessageId, editedText);
  }

  @Override
  public String buildRedactRawLine(TargetRef target, String targetMessageId, String reason) {
    return MessageMutationOutboundCommandLineBuilder.buildRedactRawLine(
        target, targetMessageId, reason);
  }
}
