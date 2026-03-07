package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Registers lifecycle and backend-management slash commands. */
@Component
final class LifecycleBackendOutboundCommandRegistrar implements OutboundCommandRegistrar {

  private final OutboundJoinPartCommandService outboundJoinPartCommandService;
  private final OutboundConnectionLifecycleCommandService outboundConnectionLifecycleCommandService;
  private final QuasselOutboundCommandService quasselOutboundCommandService;

  LifecycleBackendOutboundCommandRegistrar(
      OutboundJoinPartCommandService outboundJoinPartCommandService,
      OutboundConnectionLifecycleCommandService outboundConnectionLifecycleCommandService,
      QuasselOutboundCommandService quasselOutboundCommandService) {
    this.outboundJoinPartCommandService =
        Objects.requireNonNull(outboundJoinPartCommandService, "outboundJoinPartCommandService");
    this.outboundConnectionLifecycleCommandService =
        Objects.requireNonNull(
            outboundConnectionLifecycleCommandService, "outboundConnectionLifecycleCommandService");
    this.quasselOutboundCommandService =
        Objects.requireNonNull(quasselOutboundCommandService, "quasselOutboundCommandService");
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
        ParsedInput.QuasselSetup.class,
        (d, cmd) -> quasselOutboundCommandService.handleQuasselSetup(d, cmd.serverId()));
    registry.register(
        ParsedInput.QuasselNetwork.class,
        (d, cmd) -> quasselOutboundCommandService.handleQuasselNetwork(d, cmd.args()));
    registry.register(
        ParsedInput.Quit.class,
        (d, cmd) -> outboundConnectionLifecycleCommandService.handleQuit(cmd.reason()));
  }
}
