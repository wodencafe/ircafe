package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.springframework.stereotype.Component;

/** Default outbound dispatcher backed by the concrete outbound command services. */
@Component("defaultOutboundCommandDispatcher")
public class DefaultOutboundCommandDispatcher implements OutboundCommandDispatcher {

  private final OutboundModeCommandService outboundModeCommandService;
  private final OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService;
  private final OutboundDccCommandService outboundDccCommandService;
  private final OutboundChatCommandService outboundChatCommandService;
  private final OutboundMonitorCommandService outboundMonitorCommandService;
  private final OutboundIgnoreCommandService outboundIgnoreCommandService;
  private final LocalFilterCommandHandler localFilterCommandService;
  private final TargetCoordinator targetCoordinator;
  private final UiPort ui;
  private final UserCommandAliasesBus userCommandAliasesBus;

  public DefaultOutboundCommandDispatcher(
      OutboundModeCommandService outboundModeCommandService,
      OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService,
      OutboundDccCommandService outboundDccCommandService,
      OutboundChatCommandService outboundChatCommandService,
      OutboundMonitorCommandService outboundMonitorCommandService,
      OutboundIgnoreCommandService outboundIgnoreCommandService,
      LocalFilterCommandHandler localFilterCommandService,
      TargetCoordinator targetCoordinator,
      UiPort ui,
      UserCommandAliasesBus userCommandAliasesBus) {
    this.outboundModeCommandService = outboundModeCommandService;
    this.outboundCtcpWhoisCommandService = outboundCtcpWhoisCommandService;
    this.outboundDccCommandService = outboundDccCommandService;
    this.outboundChatCommandService = outboundChatCommandService;
    this.outboundMonitorCommandService = outboundMonitorCommandService;
    this.outboundIgnoreCommandService = outboundIgnoreCommandService;
    this.localFilterCommandService = localFilterCommandService;
    this.targetCoordinator = targetCoordinator;
    this.ui = ui;
    this.userCommandAliasesBus = userCommandAliasesBus;
  }

  @Override
  public void dispatch(CompositeDisposable disposables, ParsedInput in) {
    if (in == null) return;

    switch (in) {
      case ParsedInput.Join cmd ->
          outboundChatCommandService.handleJoin(disposables, cmd.channel(), cmd.key());
      case ParsedInput.Part cmd ->
          outboundChatCommandService.handlePart(disposables, cmd.channel(), cmd.reason());
      case ParsedInput.Connect cmd -> outboundChatCommandService.handleConnect(cmd.target());
      case ParsedInput.Disconnect cmd -> outboundChatCommandService.handleDisconnect(cmd.target());
      case ParsedInput.Reconnect cmd -> outboundChatCommandService.handleReconnect(cmd.target());
      case ParsedInput.Quit cmd -> outboundChatCommandService.handleQuit(cmd.reason());
      case ParsedInput.Nick cmd ->
          outboundChatCommandService.handleNick(disposables, cmd.newNick());
      case ParsedInput.Away cmd ->
          outboundChatCommandService.handleAway(disposables, cmd.message());
      case ParsedInput.Query cmd -> outboundChatCommandService.handleQuery(cmd.nick());
      case ParsedInput.Whois cmd ->
          outboundCtcpWhoisCommandService.handleWhois(disposables, cmd.nick());
      case ParsedInput.Whowas cmd ->
          outboundCtcpWhoisCommandService.handleWhowas(disposables, cmd.nick(), cmd.count());
      case ParsedInput.Msg cmd ->
          outboundChatCommandService.handleMsg(disposables, cmd.nick(), cmd.body());
      case ParsedInput.Notice cmd ->
          outboundChatCommandService.handleNotice(disposables, cmd.target(), cmd.body());
      case ParsedInput.Me cmd -> outboundChatCommandService.handleMe(disposables, cmd.action());
      case ParsedInput.Topic cmd ->
          outboundChatCommandService.handleTopic(disposables, cmd.first(), cmd.rest());
      case ParsedInput.Kick cmd ->
          outboundChatCommandService.handleKick(
              disposables, cmd.channel(), cmd.nick(), cmd.reason());
      case ParsedInput.Invite cmd ->
          outboundChatCommandService.handleInvite(disposables, cmd.nick(), cmd.channel());
      case ParsedInput.InviteList cmd ->
          outboundChatCommandService.handleInviteList(cmd.serverId());
      case ParsedInput.InviteJoin cmd ->
          outboundChatCommandService.handleInviteJoin(disposables, cmd.inviteToken());
      case ParsedInput.InviteIgnore cmd ->
          outboundChatCommandService.handleInviteIgnore(cmd.inviteToken());
      case ParsedInput.InviteWhois cmd ->
          outboundChatCommandService.handleInviteWhois(disposables, cmd.inviteToken());
      case ParsedInput.InviteBlock cmd ->
          outboundChatCommandService.handleInviteBlock(cmd.inviteToken());
      case ParsedInput.InviteAutoJoin cmd ->
          outboundChatCommandService.handleInviteAutoJoin(cmd.mode());
      case ParsedInput.Names cmd ->
          outboundChatCommandService.handleNames(disposables, cmd.channel());
      case ParsedInput.Who cmd -> outboundChatCommandService.handleWho(disposables, cmd.args());
      case ParsedInput.ListCmd cmd ->
          outboundChatCommandService.handleList(disposables, cmd.args());
      case ParsedInput.Monitor cmd ->
          outboundMonitorCommandService.handleMonitor(disposables, cmd.args());
      case ParsedInput.Mode cmd ->
          outboundModeCommandService.handleMode(disposables, cmd.first(), cmd.rest());
      case ParsedInput.Op cmd ->
          outboundModeCommandService.handleOp(disposables, cmd.channel(), cmd.nicks());
      case ParsedInput.Deop cmd ->
          outboundModeCommandService.handleDeop(disposables, cmd.channel(), cmd.nicks());
      case ParsedInput.Voice cmd ->
          outboundModeCommandService.handleVoice(disposables, cmd.channel(), cmd.nicks());
      case ParsedInput.Devoice cmd ->
          outboundModeCommandService.handleDevoice(disposables, cmd.channel(), cmd.nicks());
      case ParsedInput.Ban cmd ->
          outboundModeCommandService.handleBan(disposables, cmd.channel(), cmd.masksOrNicks());
      case ParsedInput.Unban cmd ->
          outboundModeCommandService.handleUnban(disposables, cmd.channel(), cmd.masksOrNicks());
      case ParsedInput.Ignore cmd -> outboundIgnoreCommandService.handleIgnore(cmd.maskOrNick());
      case ParsedInput.Unignore cmd ->
          outboundIgnoreCommandService.handleUnignore(cmd.maskOrNick());
      case ParsedInput.IgnoreList cmd -> outboundIgnoreCommandService.handleIgnoreList();
      case ParsedInput.SoftIgnore cmd ->
          outboundIgnoreCommandService.handleSoftIgnore(cmd.maskOrNick());
      case ParsedInput.UnsoftIgnore cmd ->
          outboundIgnoreCommandService.handleUnsoftIgnore(cmd.maskOrNick());
      case ParsedInput.SoftIgnoreList cmd -> outboundIgnoreCommandService.handleSoftIgnoreList();
      case ParsedInput.Filter cmd -> localFilterCommandService.handle(cmd.command());
      case ParsedInput.CtcpVersion cmd ->
          outboundCtcpWhoisCommandService.handleCtcpVersion(disposables, cmd.nick());
      case ParsedInput.CtcpPing cmd ->
          outboundCtcpWhoisCommandService.handleCtcpPing(disposables, cmd.nick());
      case ParsedInput.CtcpTime cmd ->
          outboundCtcpWhoisCommandService.handleCtcpTime(disposables, cmd.nick());
      case ParsedInput.Ctcp cmd ->
          outboundCtcpWhoisCommandService.handleCtcp(
              disposables, cmd.nick(), cmd.command(), cmd.args());
      case ParsedInput.Dcc cmd ->
          outboundDccCommandService.handleDcc(
              disposables, cmd.subcommand(), cmd.nick(), cmd.argument());
      case ParsedInput.ChatHistoryBefore cmd ->
          outboundChatCommandService.handleChatHistoryBefore(
              disposables, cmd.limit(), cmd.selector());
      case ParsedInput.ChatHistoryLatest cmd ->
          outboundChatCommandService.handleChatHistoryLatest(
              disposables, cmd.limit(), cmd.selector());
      case ParsedInput.ChatHistoryBetween cmd ->
          outboundChatCommandService.handleChatHistoryBetween(
              disposables, cmd.startSelector(), cmd.endSelector(), cmd.limit());
      case ParsedInput.ChatHistoryAround cmd ->
          outboundChatCommandService.handleChatHistoryAround(
              disposables, cmd.selector(), cmd.limit());
      case ParsedInput.Help cmd -> outboundChatCommandService.handleHelp(cmd.topic());
      case ParsedInput.ReplyMessage cmd ->
          outboundChatCommandService.handleReplyMessage(disposables, cmd.messageId(), cmd.body());
      case ParsedInput.ReactMessage cmd ->
          outboundChatCommandService.handleReactMessage(
              disposables, cmd.messageId(), cmd.reaction());
      case ParsedInput.EditMessage cmd ->
          outboundChatCommandService.handleEditMessage(disposables, cmd.messageId(), cmd.body());
      case ParsedInput.RedactMessage cmd ->
          outboundChatCommandService.handleRedactMessage(
              disposables, cmd.messageId(), cmd.reason());
      case ParsedInput.Quote cmd ->
          outboundChatCommandService.handleQuote(disposables, cmd.rawLine());
      case ParsedInput.Say cmd -> outboundChatCommandService.handleSay(disposables, cmd.text());
      case ParsedInput.Unknown cmd -> {
        if (userCommandAliasesBus != null && userCommandAliasesBus.unknownCommandAsRawEnabled()) {
          String rawLine = unknownCommandAsRawLine(cmd.raw());
          if (!rawLine.isBlank()) {
            outboundChatCommandService.handleQuote(disposables, rawLine);
            break;
          }
        }
        TargetRef at = targetCoordinator.getActiveTarget();
        TargetRef tgt = (at != null) ? at : targetCoordinator.safeStatusTarget();
        ui.appendStatus(tgt, "(system)", "Unknown command: " + cmd.raw());
      }
    }
  }

  private static String unknownCommandAsRawLine(String rawUnknown) {
    String line = rawUnknown == null ? "" : rawUnknown.trim();
    if (line.startsWith("/")) line = line.substring(1);
    return line.trim();
  }
}
