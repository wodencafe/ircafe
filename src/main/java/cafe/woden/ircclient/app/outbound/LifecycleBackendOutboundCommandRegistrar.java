package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Registers lifecycle and backend-management slash commands. */
@Component
final class LifecycleBackendOutboundCommandRegistrar implements OutboundCommandRegistrar {

  private final OutboundJoinPartCommandService outboundJoinPartCommandService;
  private final OutboundConnectionLifecycleCommandService outboundConnectionLifecycleCommandService;
  private final BackendNamedOutboundCommandRouter backendNamedOutboundCommandRouter;

  LifecycleBackendOutboundCommandRegistrar(
      OutboundJoinPartCommandService outboundJoinPartCommandService,
      OutboundConnectionLifecycleCommandService outboundConnectionLifecycleCommandService,
      BackendNamedOutboundCommandRouter backendNamedOutboundCommandRouter) {
    this.outboundJoinPartCommandService =
        Objects.requireNonNull(outboundJoinPartCommandService, "outboundJoinPartCommandService");
    this.outboundConnectionLifecycleCommandService =
        Objects.requireNonNull(
            outboundConnectionLifecycleCommandService, "outboundConnectionLifecycleCommandService");
    this.backendNamedOutboundCommandRouter =
        Objects.requireNonNull(
            backendNamedOutboundCommandRouter, "backendNamedOutboundCommandRouter");
  }

  @Override
  public void registerCommands(OutboundCommandRegistry registry) {
    registry.register(
        ParsedInput.Join.class,
        (d, cmd) -> outboundJoinPartCommandService.handleJoin(d, cmd.channel(), cmd.key()));
    registry.register(
        ParsedInput.Part.class,
        (d, cmd) -> outboundJoinPartCommandService.handlePart(d, cmd.channel(), cmd.reason()));
    registry.register(
        ParsedInput.Connect.class,
        (d, cmd) -> outboundConnectionLifecycleCommandService.handleConnect(cmd.target()));
    registry.register(
        ParsedInput.Disconnect.class,
        (d, cmd) -> outboundConnectionLifecycleCommandService.handleDisconnect(cmd.target()));
    registry.register(
        ParsedInput.Reconnect.class,
        (d, cmd) -> outboundConnectionLifecycleCommandService.handleReconnect(cmd.target()));
    registry.register(
        ParsedInput.BackendNamed.class,
        (d, cmd) -> backendNamedOutboundCommandRouter.handle(d, cmd));
    registry.register(
        ParsedInput.Quit.class,
        (d, cmd) -> outboundConnectionLifecycleCommandService.handleQuit(cmd.reason()));
  }
}
