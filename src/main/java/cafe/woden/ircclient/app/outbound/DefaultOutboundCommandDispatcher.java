package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.app.commands.ParsedInput;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.springframework.stereotype.Component;

/** Default outbound dispatcher backed by the concrete outbound command services. */
@Component("defaultOutboundCommandDispatcher")
public class DefaultOutboundCommandDispatcher implements OutboundCommandDispatcher {

  private final OutboundModeCommandService outboundModeCommandService;
  private final OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService;
  private final OutboundChatCommandService outboundChatCommandService;
  private final OutboundIgnoreCommandService outboundIgnoreCommandService;
  private final LocalFilterCommandService localFilterCommandService;
  private final TargetCoordinator targetCoordinator;
  private final UiPort ui;

  public DefaultOutboundCommandDispatcher(
      OutboundModeCommandService outboundModeCommandService,
      OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService,
      OutboundChatCommandService outboundChatCommandService,
      OutboundIgnoreCommandService outboundIgnoreCommandService,
      LocalFilterCommandService localFilterCommandService,
      TargetCoordinator targetCoordinator,
      UiPort ui) {
    this.outboundModeCommandService = outboundModeCommandService;
    this.outboundCtcpWhoisCommandService = outboundCtcpWhoisCommandService;
    this.outboundChatCommandService = outboundChatCommandService;
    this.outboundIgnoreCommandService = outboundIgnoreCommandService;
    this.localFilterCommandService = localFilterCommandService;
    this.targetCoordinator = targetCoordinator;
    this.ui = ui;
  }

  @Override
  public void dispatch(CompositeDisposable disposables, ParsedInput in) {
    if (in == null) return;

    switch (in) {
      case ParsedInput.Join cmd -> outboundChatCommandService.handleJoin(disposables, cmd.channel());
      case ParsedInput.Part cmd -> outboundChatCommandService.handlePart(disposables, cmd.channel(), cmd.reason());
      case ParsedInput.Nick cmd -> outboundChatCommandService.handleNick(disposables, cmd.newNick());
      case ParsedInput.Away cmd -> outboundChatCommandService.handleAway(disposables, cmd.message());
      case ParsedInput.Query cmd -> outboundChatCommandService.handleQuery(cmd.nick());
      case ParsedInput.Whois cmd -> outboundCtcpWhoisCommandService.handleWhois(disposables, cmd.nick());
      case ParsedInput.Msg cmd -> outboundChatCommandService.handleMsg(disposables, cmd.nick(), cmd.body());
      case ParsedInput.Notice cmd -> outboundChatCommandService.handleNotice(disposables, cmd.target(), cmd.body());
      case ParsedInput.Me cmd -> outboundChatCommandService.handleMe(disposables, cmd.action());
      case ParsedInput.Topic cmd -> outboundChatCommandService.handleTopic(disposables, cmd.first(), cmd.rest());
      case ParsedInput.Kick cmd -> outboundChatCommandService.handleKick(disposables, cmd.channel(), cmd.nick(), cmd.reason());
      case ParsedInput.Invite cmd -> outboundChatCommandService.handleInvite(disposables, cmd.nick(), cmd.channel());
      case ParsedInput.Names cmd -> outboundChatCommandService.handleNames(disposables, cmd.channel());
      case ParsedInput.Who cmd -> outboundChatCommandService.handleWho(disposables, cmd.args());
      case ParsedInput.ListCmd cmd -> outboundChatCommandService.handleList(disposables, cmd.args());
      case ParsedInput.Mode cmd -> outboundModeCommandService.handleMode(disposables, cmd.first(), cmd.rest());
      case ParsedInput.Op cmd -> outboundModeCommandService.handleOp(disposables, cmd.channel(), cmd.nicks());
      case ParsedInput.Deop cmd -> outboundModeCommandService.handleDeop(disposables, cmd.channel(), cmd.nicks());
      case ParsedInput.Voice cmd -> outboundModeCommandService.handleVoice(disposables, cmd.channel(), cmd.nicks());
      case ParsedInput.Devoice cmd -> outboundModeCommandService.handleDevoice(disposables, cmd.channel(), cmd.nicks());
      case ParsedInput.Ban cmd -> outboundModeCommandService.handleBan(disposables, cmd.channel(), cmd.masksOrNicks());
      case ParsedInput.Unban cmd -> outboundModeCommandService.handleUnban(disposables, cmd.channel(), cmd.masksOrNicks());
      case ParsedInput.Ignore cmd -> outboundIgnoreCommandService.handleIgnore(cmd.maskOrNick());
      case ParsedInput.Unignore cmd -> outboundIgnoreCommandService.handleUnignore(cmd.maskOrNick());
      case ParsedInput.IgnoreList cmd -> outboundIgnoreCommandService.handleIgnoreList();
      case ParsedInput.SoftIgnore cmd -> outboundIgnoreCommandService.handleSoftIgnore(cmd.maskOrNick());
      case ParsedInput.UnsoftIgnore cmd -> outboundIgnoreCommandService.handleUnsoftIgnore(cmd.maskOrNick());
      case ParsedInput.SoftIgnoreList cmd -> outboundIgnoreCommandService.handleSoftIgnoreList();
      case ParsedInput.Filter cmd -> localFilterCommandService.handle(cmd.command());
      case ParsedInput.CtcpVersion cmd -> outboundCtcpWhoisCommandService.handleCtcpVersion(disposables, cmd.nick());
      case ParsedInput.CtcpPing cmd -> outboundCtcpWhoisCommandService.handleCtcpPing(disposables, cmd.nick());
      case ParsedInput.CtcpTime cmd -> outboundCtcpWhoisCommandService.handleCtcpTime(disposables, cmd.nick());
      case ParsedInput.Ctcp cmd -> outboundCtcpWhoisCommandService.handleCtcp(disposables, cmd.nick(), cmd.command(), cmd.args());
      case ParsedInput.ChatHistoryBefore cmd -> outboundChatCommandService.handleChatHistoryBefore(disposables, cmd.limit(), cmd.selector());
      case ParsedInput.Quote cmd -> outboundChatCommandService.handleQuote(disposables, cmd.rawLine());
      case ParsedInput.Say cmd -> outboundChatCommandService.handleSay(disposables, cmd.text());
      case ParsedInput.Unknown cmd -> {
        TargetRef at = targetCoordinator.getActiveTarget();
        TargetRef tgt = (at != null) ? at : targetCoordinator.safeStatusTarget();
        ui.appendStatus(tgt, "(system)", "Unknown command: " + cmd.raw());
      }
    }
  }
}
