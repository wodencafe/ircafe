package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.model.TargetRef;
import org.springframework.stereotype.Component;

/** Quassel backend payload shaping for message mutation commands. */
@Component
final class QuasselMessageMutationOutboundCommands implements MessageMutationOutboundCommands {

  @Override
  public IrcProperties.Server.Backend backend() {
    return IrcProperties.Server.Backend.QUASSEL_CORE;
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
