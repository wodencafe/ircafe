package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.outbound.backend.BackendNamedOutboundCommandRouter;
import cafe.woden.ircclient.app.outbound.dispatch.OutboundCommandRegistrar;
import cafe.woden.ircclient.app.outbound.dispatch.OutboundCommandRegistry;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Registers lifecycle and backend-management slash commands. */
@Component
@ApplicationLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class LifecycleBackendOutboundCommandRegistrar implements OutboundCommandRegistrar {

  @NonNull private final OutboundJoinPartCommandService outboundJoinPartCommandService;

  @NonNull
  private final OutboundConnectionLifecycleCommandService outboundConnectionLifecycleCommandService;

  @NonNull private final BackendNamedOutboundCommandRouter backendNamedOutboundCommandRouter;

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
