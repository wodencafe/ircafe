package cafe.woden.ircclient.app.outbound.backend;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cafe.woden.ircclient.app.outbound.mutation.MessageMutationOutboundCommands;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.model.TargetRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageMutationOutboundCommandsRouterTest {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  @Test
  void routesToBackendSpecificHandlers() {
    MessageMutationOutboundCommandsRouter router =
        cafe.woden.ircclient.app.outbound.TestBackendSupport.messageMutationOutboundCommandsRouter(
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
        cafe.woden.ircclient.app.outbound.TestBackendSupport.messageMutationOutboundCommandsRouter(
            List.of(
                new IrcMessageMutationOutboundCommands(),
                new MatrixMessageMutationOutboundCommands()));

    assertInstanceOf(
        IrcMessageMutationOutboundCommands.class,
        router.commandsFor((IrcProperties.Server.Backend) null));
    assertInstanceOf(
        IrcMessageMutationOutboundCommands.class,
        router.commandsFor(IrcProperties.Server.Backend.QUASSEL_CORE));
  }

  @Test
  void rejectsDuplicateBackendHandlers() {
    assertThrows(
        IllegalStateException.class,
        () ->
            cafe.woden.ircclient.app.outbound.TestBackendSupport
                .messageMutationOutboundCommandsRouter(
                    List.of(
                        new IrcMessageMutationOutboundCommands(),
                        new DuplicateIrcMessageMutationOutboundCommands())));
  }

  @Test
  void defaultsToBuiltInIrcHandlerWhenCatalogHasNoExplicitIrcHandler() {
    MessageMutationOutboundCommandsRouter router =
        cafe.woden.ircclient.app.outbound.TestBackendSupport.messageMutationOutboundCommandsRouter(
            List.of(new MatrixMessageMutationOutboundCommands()));

    assertInstanceOf(
        IrcMessageMutationOutboundCommands.class,
        router.commandsFor(IrcProperties.Server.Backend.IRC));
  }

  @Test
  void routesCustomBackendIds() {
    MessageMutationOutboundCommands pluginCommands = new PluginMessageMutationOutboundCommands();
    MessageMutationOutboundCommandsRouter router =
        cafe.woden.ircclient.app.outbound.TestBackendSupport.messageMutationOutboundCommandsRouter(
            List.of(new IrcMessageMutationOutboundCommands(), pluginCommands));

    assertInstanceOf(PluginMessageMutationOutboundCommands.class, router.commandsFor("plugin"));
  }

  private static final class DuplicateIrcMessageMutationOutboundCommands
      implements MessageMutationOutboundCommands {

    @Override
    public String backendId() {
      return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC);
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

  private static final class PluginMessageMutationOutboundCommands
      implements MessageMutationOutboundCommands {
    @Override
    public String backendId() {
      return "plugin";
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
