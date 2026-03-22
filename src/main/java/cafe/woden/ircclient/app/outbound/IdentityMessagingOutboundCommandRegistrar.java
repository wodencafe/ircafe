package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.outbound.dispatch.OutboundCommandRegistrar;
import cafe.woden.ircclient.app.outbound.dispatch.OutboundCommandRegistry;
import cafe.woden.ircclient.app.outbound.identity.OutboundNickAwayCommandService;
import cafe.woden.ircclient.app.outbound.messaging.OutboundMessagingCommandService;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Registers identity, user lookups, and chat messaging slash commands. */
@Component
@ApplicationLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class IdentityMessagingOutboundCommandRegistrar implements OutboundCommandRegistrar {

  @NonNull private final OutboundNickAwayCommandService outboundNickAwayCommandService;
  @NonNull private final OutboundMessagingCommandService outboundMessagingCommandService;
  @NonNull private final OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService;

  @Override
  public void registerCommands(OutboundCommandRegistry registry) {
    registry.register(
        ParsedInput.Nick.class,
        (d, cmd) -> outboundNickAwayCommandService.handleNick(d, cmd.newNick()));
    registry.register(
        ParsedInput.Away.class,
        (d, cmd) -> outboundNickAwayCommandService.handleAway(d, cmd.message()));
    registry.register(
        ParsedInput.Query.class,
        (d, cmd) -> outboundMessagingCommandService.handleQuery(cmd.nick()));
    registry.register(
        ParsedInput.Whois.class,
        (d, cmd) -> outboundCtcpWhoisCommandService.handleWhois(d, cmd.nick()));
    registry.register(
        ParsedInput.Whowas.class,
        (d, cmd) -> outboundCtcpWhoisCommandService.handleWhowas(d, cmd.nick(), cmd.count()));
    registry.register(
        ParsedInput.Msg.class,
        (d, cmd) -> outboundMessagingCommandService.handleMsg(d, cmd.nick(), cmd.body()));
    registry.register(
        ParsedInput.Notice.class,
        (d, cmd) -> outboundMessagingCommandService.handleNotice(d, cmd.target(), cmd.body()));
    registry.register(
        ParsedInput.Me.class,
        (d, cmd) -> outboundMessagingCommandService.handleMe(d, cmd.action()));
  }
}
