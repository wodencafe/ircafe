package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Registers ignore/filter and CTCP/DCC command handlers. */
@Component
@ApplicationLayer
final class IgnoreCtcpOutboundCommandRegistrar implements OutboundCommandRegistrar {

  private final OutboundIgnoreCommandService outboundIgnoreCommandService;
  private final LocalFilterCommandHandler localFilterCommandService;
  private final OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService;
  private final OutboundDccCommandService outboundDccCommandService;

  IgnoreCtcpOutboundCommandRegistrar(
      OutboundIgnoreCommandService outboundIgnoreCommandService,
      LocalFilterCommandHandler localFilterCommandService,
      OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService,
      OutboundDccCommandService outboundDccCommandService) {
    this.outboundIgnoreCommandService =
        Objects.requireNonNull(outboundIgnoreCommandService, "outboundIgnoreCommandService");
    this.localFilterCommandService =
        Objects.requireNonNull(localFilterCommandService, "localFilterCommandService");
    this.outboundCtcpWhoisCommandService =
        Objects.requireNonNull(outboundCtcpWhoisCommandService, "outboundCtcpWhoisCommandService");
    this.outboundDccCommandService =
        Objects.requireNonNull(outboundDccCommandService, "outboundDccCommandService");
  }

  @Override
  public void registerCommands(OutboundCommandRegistry registry) {
    registry.register(
        ParsedInput.Ignore.class,
        (d, cmd) -> outboundIgnoreCommandService.handleIgnore(cmd.maskOrNick()));
    registry.register(
        ParsedInput.Unignore.class,
        (d, cmd) -> outboundIgnoreCommandService.handleUnignore(cmd.maskOrNick()));
    registry.register(
        ParsedInput.IgnoreList.class, (d, cmd) -> outboundIgnoreCommandService.handleIgnoreList());
    registry.register(
        ParsedInput.SoftIgnore.class,
        (d, cmd) -> outboundIgnoreCommandService.handleSoftIgnore(cmd.maskOrNick()));
    registry.register(
        ParsedInput.UnsoftIgnore.class,
        (d, cmd) -> outboundIgnoreCommandService.handleUnsoftIgnore(cmd.maskOrNick()));
    registry.register(
        ParsedInput.SoftIgnoreList.class,
        (d, cmd) -> outboundIgnoreCommandService.handleSoftIgnoreList());
    registry.register(
        ParsedInput.Filter.class, (d, cmd) -> localFilterCommandService.handle(cmd.command()));
    registry.register(
        ParsedInput.CtcpVersion.class,
        (d, cmd) -> outboundCtcpWhoisCommandService.handleCtcpVersion(d, cmd.nick()));
    registry.register(
        ParsedInput.CtcpPing.class,
        (d, cmd) -> outboundCtcpWhoisCommandService.handleCtcpPing(d, cmd.nick()));
    registry.register(
        ParsedInput.CtcpTime.class,
        (d, cmd) -> outboundCtcpWhoisCommandService.handleCtcpTime(d, cmd.nick()));
    registry.register(
        ParsedInput.Ctcp.class,
        (d, cmd) ->
            outboundCtcpWhoisCommandService.handleCtcp(d, cmd.nick(), cmd.command(), cmd.args()));
    registry.register(
        ParsedInput.Dcc.class,
        (d, cmd) ->
            outboundDccCommandService.handleDcc(d, cmd.subcommand(), cmd.nick(), cmd.argument()));
  }
}
