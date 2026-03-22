package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.outbound.dcc.OutboundDccCommandService;
import cafe.woden.ircclient.app.outbound.dispatch.OutboundCommandRegistrar;
import cafe.woden.ircclient.app.outbound.dispatch.OutboundCommandRegistry;
import cafe.woden.ircclient.app.outbound.ignore.OutboundIgnoreCommandService;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Registers ignore/filter and CTCP/DCC command handlers. */
@Component
@ApplicationLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class IgnoreCtcpOutboundCommandRegistrar implements OutboundCommandRegistrar {

  @NonNull private final OutboundIgnoreCommandService outboundIgnoreCommandService;
  @NonNull private final LocalFilterCommandHandler localFilterCommandService;
  @NonNull private final OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService;
  @NonNull private final OutboundDccCommandService outboundDccCommandService;

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
