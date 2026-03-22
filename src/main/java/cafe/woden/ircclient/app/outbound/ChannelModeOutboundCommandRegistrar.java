package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.outbound.dispatch.OutboundCommandRegistrar;
import cafe.woden.ircclient.app.outbound.dispatch.OutboundCommandRegistry;
import cafe.woden.ircclient.app.outbound.invite.OutboundInviteCommandService;
import cafe.woden.ircclient.app.outbound.monitor.OutboundMonitorCommandService;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Registers channel moderation, invite, and mode/list commands. */
@Component
@ApplicationLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class ChannelModeOutboundCommandRegistrar implements OutboundCommandRegistrar {

  @NonNull private final OutboundTopicKickCommandService outboundTopicKickCommandService;
  @NonNull private final OutboundInviteCommandService outboundInviteCommandService;
  @NonNull private final OutboundNamesWhoListCommandService outboundNamesWhoListCommandService;
  @NonNull private final OutboundMonitorCommandService outboundMonitorCommandService;
  @NonNull private final OutboundModeCommandService outboundModeCommandService;

  @Override
  public void registerCommands(OutboundCommandRegistry registry) {
    registry.register(
        ParsedInput.Topic.class,
        (d, cmd) -> outboundTopicKickCommandService.handleTopic(d, cmd.first(), cmd.rest()));
    registry.register(
        ParsedInput.Kick.class,
        (d, cmd) ->
            outboundTopicKickCommandService.handleKick(d, cmd.channel(), cmd.nick(), cmd.reason()));
    registry.register(
        ParsedInput.Invite.class,
        (d, cmd) -> outboundInviteCommandService.handleInvite(d, cmd.nick(), cmd.channel()));
    registry.register(
        ParsedInput.InviteList.class,
        (d, cmd) -> outboundInviteCommandService.handleInviteList(cmd.serverId()));
    registry.register(
        ParsedInput.InviteJoin.class,
        (d, cmd) -> outboundInviteCommandService.handleInviteJoin(d, cmd.inviteToken()));
    registry.register(
        ParsedInput.InviteIgnore.class,
        (d, cmd) -> outboundInviteCommandService.handleInviteIgnore(cmd.inviteToken()));
    registry.register(
        ParsedInput.InviteWhois.class,
        (d, cmd) -> outboundInviteCommandService.handleInviteWhois(d, cmd.inviteToken()));
    registry.register(
        ParsedInput.InviteBlock.class,
        (d, cmd) -> outboundInviteCommandService.handleInviteBlock(cmd.inviteToken()));
    registry.register(
        ParsedInput.InviteAutoJoin.class,
        (d, cmd) -> outboundInviteCommandService.handleInviteAutoJoin(cmd.mode()));
    registry.register(
        ParsedInput.Names.class,
        (d, cmd) -> outboundNamesWhoListCommandService.handleNames(d, cmd.channel()));
    registry.register(
        ParsedInput.Who.class,
        (d, cmd) -> outboundNamesWhoListCommandService.handleWho(d, cmd.args()));
    registry.register(
        ParsedInput.ListCmd.class,
        (d, cmd) -> outboundNamesWhoListCommandService.handleList(d, cmd.args()));
    registry.register(
        ParsedInput.Monitor.class,
        (d, cmd) -> outboundMonitorCommandService.handleMonitor(d, cmd.args()));
    registry.register(
        ParsedInput.Mode.class,
        (d, cmd) -> outboundModeCommandService.handleMode(d, cmd.first(), cmd.rest()));
    registry.register(
        ParsedInput.Op.class,
        (d, cmd) -> outboundModeCommandService.handleOp(d, cmd.channel(), cmd.nicks()));
    registry.register(
        ParsedInput.Deop.class,
        (d, cmd) -> outboundModeCommandService.handleDeop(d, cmd.channel(), cmd.nicks()));
    registry.register(
        ParsedInput.Voice.class,
        (d, cmd) -> outboundModeCommandService.handleVoice(d, cmd.channel(), cmd.nicks()));
    registry.register(
        ParsedInput.Devoice.class,
        (d, cmd) -> outboundModeCommandService.handleDevoice(d, cmd.channel(), cmd.nicks()));
    registry.register(
        ParsedInput.Ban.class,
        (d, cmd) -> outboundModeCommandService.handleBan(d, cmd.channel(), cmd.masksOrNicks()));
    registry.register(
        ParsedInput.Unban.class,
        (d, cmd) -> outboundModeCommandService.handleUnban(d, cmd.channel(), cmd.masksOrNicks()));
  }
}
