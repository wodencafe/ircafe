package cafe.woden.ircclient.app.outbound;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.model.TargetRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageMutationOutboundCommandsRouterTest {

  @Test
  void routesToBackendSpecificHandlers() {
    MessageMutationOutboundCommandsRouter router =
        new MessageMutationOutboundCommandsRouter(
            List.of(
                new IrcMessageMutationOutboundCommands(),
                new MatrixMessageMutationOutboundCommands(),
                new QuasselMessageMutationOutboundCommands()));

    assertInstanceOf(
        IrcMessageMutationOutboundCommands.class,
        router.commandsFor(IrcProperties.Server.Backend.IRC));
    assertInstanceOf(
        MatrixMessageMutationOutboundCommands.class,
        router.commandsFor(IrcProperties.Server.Backend.MATRIX));
    assertInstanceOf(
        QuasselMessageMutationOutboundCommands.class,
        router.commandsFor(IrcProperties.Server.Backend.QUASSEL_CORE));
  }

  @Test
  void fallsBackToIrcHandlerWhenBackendHasNoRegisteredHandler() {
    MessageMutationOutboundCommandsRouter router =
        new MessageMutationOutboundCommandsRouter(
            List.of(
                new IrcMessageMutationOutboundCommands(),
                new MatrixMessageMutationOutboundCommands()));

    assertInstanceOf(IrcMessageMutationOutboundCommands.class, router.commandsFor(null));
    assertInstanceOf(
        IrcMessageMutationOutboundCommands.class,
        router.commandsFor(IrcProperties.Server.Backend.QUASSEL_CORE));
  }

  @Test
  void rejectsDuplicateBackendHandlers() {
    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () ->
                new MessageMutationOutboundCommandsRouter(
                    List.of(
                        new IrcMessageMutationOutboundCommands(),
                        new DuplicateIrcMessageMutationOutboundCommands())));

    assertTrue(err.getMessage().contains("Duplicate message mutation outbound handler"));
  }

  @Test
  void requiresIrcDefaultHandler() {
    IllegalStateException err =
        assertThrows(
            IllegalStateException.class,
            () ->
                new MessageMutationOutboundCommandsRouter(
                    List.of(new MatrixMessageMutationOutboundCommands())));

    assertTrue(
        err.getMessage().contains("Missing message mutation outbound handler for backend IRC"));
  }

  private static final class DuplicateIrcMessageMutationOutboundCommands
      implements MessageMutationOutboundCommands {

    @Override
    public IrcProperties.Server.Backend backend() {
      return IrcProperties.Server.Backend.IRC;
    }

    @Override
    public String buildReplyRawLine(TargetRef target, String replyToMessageId, String message) {
      return "";
    }

    @Override
    public String buildReactRawLine(TargetRef target, String replyToMessageId, String reaction) {
      return "";
    }

    @Override
    public String buildUnreactRawLine(TargetRef target, String replyToMessageId, String reaction) {
      return "";
    }

    @Override
    public String buildEditRawLine(TargetRef target, String targetMessageId, String editedText) {
      return "";
    }

    @Override
    public String buildRedactRawLine(TargetRef target, String targetMessageId, String reason) {
      return "";
    }
  }
}
