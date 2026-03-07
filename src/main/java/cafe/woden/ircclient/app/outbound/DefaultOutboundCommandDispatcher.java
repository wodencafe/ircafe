package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.commands.UserCommandAliasesBus;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Default outbound dispatcher backed by the concrete outbound command services. */
@Component("defaultOutboundCommandDispatcher")
public class DefaultOutboundCommandDispatcher implements OutboundCommandDispatcher {

  private final OutboundModeCommandService outboundModeCommandService;
  private final OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService;
  private final OutboundDccCommandService outboundDccCommandService;
  private final OutboundChatCommandService outboundChatCommandService;
  private final OutboundChatHistoryCommandService outboundChatHistoryCommandService;
  private final OutboundInviteCommandService outboundInviteCommandService;
  private final QuasselOutboundCommandService quasselOutboundCommandService;
  private final OutboundUploadCommandService outboundUploadCommandService;
  private final OutboundMessageMutationCommandService outboundMessageMutationCommandService;
  private final OutboundReadMarkerCommandService outboundReadMarkerCommandService;
  private final OutboundMonitorCommandService outboundMonitorCommandService;
  private final OutboundIgnoreCommandService outboundIgnoreCommandService;
  private final LocalFilterCommandHandler localFilterCommandService;
  private final TargetCoordinator targetCoordinator;
  private final UiPort ui;
  private final UserCommandAliasesBus userCommandAliasesBus;
  private final Map<Class<? extends ParsedInput>, CommandHandler<? extends ParsedInput>> handlers;

  public DefaultOutboundCommandDispatcher(
      OutboundModeCommandService outboundModeCommandService,
      OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService,
      OutboundDccCommandService outboundDccCommandService,
      OutboundChatCommandService outboundChatCommandService,
      OutboundChatHistoryCommandService outboundChatHistoryCommandService,
      OutboundInviteCommandService outboundInviteCommandService,
      QuasselOutboundCommandService quasselOutboundCommandService,
      OutboundUploadCommandService outboundUploadCommandService,
      OutboundMessageMutationCommandService outboundMessageMutationCommandService,
      OutboundReadMarkerCommandService outboundReadMarkerCommandService,
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
    this.outboundChatHistoryCommandService = outboundChatHistoryCommandService;
    this.outboundInviteCommandService = outboundInviteCommandService;
    this.quasselOutboundCommandService = quasselOutboundCommandService;
    this.outboundUploadCommandService = outboundUploadCommandService;
    this.outboundMessageMutationCommandService = outboundMessageMutationCommandService;
    this.outboundReadMarkerCommandService = outboundReadMarkerCommandService;
    this.outboundMonitorCommandService = outboundMonitorCommandService;
    this.outboundIgnoreCommandService = outboundIgnoreCommandService;
    this.localFilterCommandService = localFilterCommandService;
    this.targetCoordinator = targetCoordinator;
    this.ui = ui;
    this.userCommandAliasesBus = userCommandAliasesBus;
    this.handlers = buildHandlers();
  }

  @Override
  public void dispatch(CompositeDisposable disposables, ParsedInput in) {
    if (in == null) return;

    CommandHandler<? extends ParsedInput> handler = handlers.get(in.getClass());
    if (handler == null) {
      throw new IllegalStateException(
          "No outbound command handler registered for " + in.getClass().getName());
    }
    dispatchTo(handler, disposables, in);
  }

  private Map<Class<? extends ParsedInput>, CommandHandler<? extends ParsedInput>> buildHandlers() {
    LinkedHashMap<Class<? extends ParsedInput>, CommandHandler<? extends ParsedInput>> map =
        new LinkedHashMap<>();

    register(
        map,
        ParsedInput.Join.class,
        (d, cmd) -> outboundChatCommandService.handleJoin(d, cmd.channel(), cmd.key()));
    register(
        map,
        ParsedInput.Part.class,
        (d, cmd) -> outboundChatCommandService.handlePart(d, cmd.channel(), cmd.reason()));
    register(
        map,
        ParsedInput.Connect.class,
        (d, cmd) -> outboundChatCommandService.handleConnect(cmd.target()));
    register(
        map,
        ParsedInput.Disconnect.class,
        (d, cmd) -> outboundChatCommandService.handleDisconnect(cmd.target()));
    register(
        map,
        ParsedInput.Reconnect.class,
        (d, cmd) -> outboundChatCommandService.handleReconnect(cmd.target()));
    register(
        map,
        ParsedInput.QuasselSetup.class,
        (d, cmd) -> quasselOutboundCommandService.handleQuasselSetup(d, cmd.serverId()));
    register(
        map,
        ParsedInput.QuasselNetwork.class,
        (d, cmd) -> quasselOutboundCommandService.handleQuasselNetwork(d, cmd.args()));
    register(
        map,
        ParsedInput.Quit.class,
        (d, cmd) -> outboundChatCommandService.handleQuit(cmd.reason()));
    register(
        map,
        ParsedInput.Nick.class,
        (d, cmd) -> outboundChatCommandService.handleNick(d, cmd.newNick()));
    register(
        map,
        ParsedInput.Away.class,
        (d, cmd) -> outboundChatCommandService.handleAway(d, cmd.message()));
    register(
        map,
        ParsedInput.Query.class,
        (d, cmd) -> outboundChatCommandService.handleQuery(cmd.nick()));
    register(
        map,
        ParsedInput.Whois.class,
        (d, cmd) -> outboundCtcpWhoisCommandService.handleWhois(d, cmd.nick()));
    register(
        map,
        ParsedInput.Whowas.class,
        (d, cmd) -> outboundCtcpWhoisCommandService.handleWhowas(d, cmd.nick(), cmd.count()));
    register(
        map,
        ParsedInput.Msg.class,
        (d, cmd) -> outboundChatCommandService.handleMsg(d, cmd.nick(), cmd.body()));
    register(
        map,
        ParsedInput.Notice.class,
        (d, cmd) -> outboundChatCommandService.handleNotice(d, cmd.target(), cmd.body()));
    register(
        map,
        ParsedInput.Me.class,
        (d, cmd) -> outboundChatCommandService.handleMe(d, cmd.action()));
    register(
        map,
        ParsedInput.Topic.class,
        (d, cmd) -> outboundChatCommandService.handleTopic(d, cmd.first(), cmd.rest()));
    register(
        map,
        ParsedInput.Kick.class,
        (d, cmd) ->
            outboundChatCommandService.handleKick(d, cmd.channel(), cmd.nick(), cmd.reason()));
    register(
        map,
        ParsedInput.Invite.class,
        (d, cmd) -> outboundInviteCommandService.handleInvite(d, cmd.nick(), cmd.channel()));
    register(
        map,
        ParsedInput.InviteList.class,
        (d, cmd) -> outboundInviteCommandService.handleInviteList(cmd.serverId()));
    register(
        map,
        ParsedInput.InviteJoin.class,
        (d, cmd) -> outboundInviteCommandService.handleInviteJoin(d, cmd.inviteToken()));
    register(
        map,
        ParsedInput.InviteIgnore.class,
        (d, cmd) -> outboundInviteCommandService.handleInviteIgnore(cmd.inviteToken()));
    register(
        map,
        ParsedInput.InviteWhois.class,
        (d, cmd) -> outboundInviteCommandService.handleInviteWhois(d, cmd.inviteToken()));
    register(
        map,
        ParsedInput.InviteBlock.class,
        (d, cmd) -> outboundInviteCommandService.handleInviteBlock(cmd.inviteToken()));
    register(
        map,
        ParsedInput.InviteAutoJoin.class,
        (d, cmd) -> outboundInviteCommandService.handleInviteAutoJoin(cmd.mode()));
    register(
        map,
        ParsedInput.Names.class,
        (d, cmd) -> outboundChatCommandService.handleNames(d, cmd.channel()));
    register(
        map,
        ParsedInput.Who.class,
        (d, cmd) -> outboundChatCommandService.handleWho(d, cmd.args()));
    register(
        map,
        ParsedInput.ListCmd.class,
        (d, cmd) -> outboundChatCommandService.handleList(d, cmd.args()));
    register(
        map,
        ParsedInput.Monitor.class,
        (d, cmd) -> outboundMonitorCommandService.handleMonitor(d, cmd.args()));
    register(
        map,
        ParsedInput.Mode.class,
        (d, cmd) -> outboundModeCommandService.handleMode(d, cmd.first(), cmd.rest()));
    register(
        map,
        ParsedInput.Op.class,
        (d, cmd) -> outboundModeCommandService.handleOp(d, cmd.channel(), cmd.nicks()));
    register(
        map,
        ParsedInput.Deop.class,
        (d, cmd) -> outboundModeCommandService.handleDeop(d, cmd.channel(), cmd.nicks()));
    register(
        map,
        ParsedInput.Voice.class,
        (d, cmd) -> outboundModeCommandService.handleVoice(d, cmd.channel(), cmd.nicks()));
    register(
        map,
        ParsedInput.Devoice.class,
        (d, cmd) -> outboundModeCommandService.handleDevoice(d, cmd.channel(), cmd.nicks()));
    register(
        map,
        ParsedInput.Ban.class,
        (d, cmd) -> outboundModeCommandService.handleBan(d, cmd.channel(), cmd.masksOrNicks()));
    register(
        map,
        ParsedInput.Unban.class,
        (d, cmd) -> outboundModeCommandService.handleUnban(d, cmd.channel(), cmd.masksOrNicks()));
    register(
        map,
        ParsedInput.Ignore.class,
        (d, cmd) -> outboundIgnoreCommandService.handleIgnore(cmd.maskOrNick()));
    register(
        map,
        ParsedInput.Unignore.class,
        (d, cmd) -> outboundIgnoreCommandService.handleUnignore(cmd.maskOrNick()));
    register(
        map,
        ParsedInput.IgnoreList.class,
        (d, cmd) -> outboundIgnoreCommandService.handleIgnoreList());
    register(
        map,
        ParsedInput.SoftIgnore.class,
        (d, cmd) -> outboundIgnoreCommandService.handleSoftIgnore(cmd.maskOrNick()));
    register(
        map,
        ParsedInput.UnsoftIgnore.class,
        (d, cmd) -> outboundIgnoreCommandService.handleUnsoftIgnore(cmd.maskOrNick()));
    register(
        map,
        ParsedInput.SoftIgnoreList.class,
        (d, cmd) -> outboundIgnoreCommandService.handleSoftIgnoreList());
    register(
        map, ParsedInput.Filter.class, (d, cmd) -> localFilterCommandService.handle(cmd.command()));
    register(
        map,
        ParsedInput.CtcpVersion.class,
        (d, cmd) -> outboundCtcpWhoisCommandService.handleCtcpVersion(d, cmd.nick()));
    register(
        map,
        ParsedInput.CtcpPing.class,
        (d, cmd) -> outboundCtcpWhoisCommandService.handleCtcpPing(d, cmd.nick()));
    register(
        map,
        ParsedInput.CtcpTime.class,
        (d, cmd) -> outboundCtcpWhoisCommandService.handleCtcpTime(d, cmd.nick()));
    register(
        map,
        ParsedInput.Ctcp.class,
        (d, cmd) ->
            outboundCtcpWhoisCommandService.handleCtcp(d, cmd.nick(), cmd.command(), cmd.args()));
    register(
        map,
        ParsedInput.Dcc.class,
        (d, cmd) ->
            outboundDccCommandService.handleDcc(d, cmd.subcommand(), cmd.nick(), cmd.argument()));
    register(
        map,
        ParsedInput.ChatHistoryBefore.class,
        (d, cmd) ->
            outboundChatHistoryCommandService.handleChatHistoryBefore(
                d, cmd.limit(), cmd.selector()));
    register(
        map,
        ParsedInput.ChatHistoryLatest.class,
        (d, cmd) ->
            outboundChatHistoryCommandService.handleChatHistoryLatest(
                d, cmd.limit(), cmd.selector()));
    register(
        map,
        ParsedInput.ChatHistoryBetween.class,
        (d, cmd) ->
            outboundChatHistoryCommandService.handleChatHistoryBetween(
                d, cmd.startSelector(), cmd.endSelector(), cmd.limit()));
    register(
        map,
        ParsedInput.ChatHistoryAround.class,
        (d, cmd) ->
            outboundChatHistoryCommandService.handleChatHistoryAround(
                d, cmd.selector(), cmd.limit()));
    register(
        map,
        ParsedInput.MarkRead.class,
        (d, cmd) -> outboundReadMarkerCommandService.handleMarkRead(d));
    register(
        map,
        ParsedInput.Help.class,
        (d, cmd) -> outboundChatCommandService.handleHelp(cmd.topic()));
    register(
        map,
        ParsedInput.Upload.class,
        (d, cmd) ->
            outboundUploadCommandService.handleUpload(d, cmd.msgType(), cmd.path(), cmd.caption()));
    register(
        map,
        ParsedInput.ReplyMessage.class,
        (d, cmd) ->
            outboundMessageMutationCommandService.handleReplyMessage(
                d, cmd.messageId(), cmd.body()));
    register(
        map,
        ParsedInput.ReactMessage.class,
        (d, cmd) ->
            outboundMessageMutationCommandService.handleReactMessage(
                d, cmd.messageId(), cmd.reaction()));
    register(
        map,
        ParsedInput.UnreactMessage.class,
        (d, cmd) ->
            outboundMessageMutationCommandService.handleUnreactMessage(
                d, cmd.messageId(), cmd.reaction()));
    register(
        map,
        ParsedInput.EditMessage.class,
        (d, cmd) ->
            outboundMessageMutationCommandService.handleEditMessage(
                d, cmd.messageId(), cmd.body()));
    register(
        map,
        ParsedInput.RedactMessage.class,
        (d, cmd) ->
            outboundMessageMutationCommandService.handleRedactMessage(
                d, cmd.messageId(), cmd.reason()));
    register(
        map,
        ParsedInput.Quote.class,
        (d, cmd) -> outboundChatCommandService.handleQuote(d, cmd.rawLine()));
    register(
        map,
        ParsedInput.Say.class,
        (d, cmd) -> outboundChatCommandService.handleSay(d, cmd.text()));
    register(map, ParsedInput.Unknown.class, this::handleUnknown);

    validateHandlerCoverage(map);
    return Map.copyOf(map);
  }

  private void validateHandlerCoverage(
      Map<Class<? extends ParsedInput>, CommandHandler<? extends ParsedInput>> map) {
    Class<?>[] permitted = ParsedInput.class.getPermittedSubclasses();
    if (permitted == null || permitted.length == 0) {
      return;
    }

    LinkedHashSet<Class<?>> expected = new LinkedHashSet<>(Arrays.asList(permitted));
    LinkedHashSet<Class<?>> registered = new LinkedHashSet<>(map.keySet());

    LinkedHashSet<Class<?>> missing = new LinkedHashSet<>(expected);
    missing.removeAll(registered);

    LinkedHashSet<Class<?>> extra = new LinkedHashSet<>(registered);
    extra.removeAll(expected);

    if (!missing.isEmpty() || !extra.isEmpty()) {
      throw new IllegalStateException(
          "Outbound command handler registry mismatch. missing=" + missing + ", extra=" + extra);
    }
  }

  private void handleUnknown(CompositeDisposable disposables, ParsedInput.Unknown cmd) {
    if (userCommandAliasesBus != null && userCommandAliasesBus.unknownCommandAsRawEnabled()) {
      String rawLine = unknownCommandAsRawLine(cmd.raw());
      if (!rawLine.isBlank()) {
        outboundChatCommandService.handleQuote(disposables, rawLine);
        return;
      }
    }
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef tgt = (at != null) ? at : targetCoordinator.safeStatusTarget();
    ui.appendStatus(tgt, "(system)", "Unknown command: " + cmd.raw());
  }

  @SuppressWarnings("unchecked")
  private static <T extends ParsedInput> void dispatchTo(
      CommandHandler<? extends ParsedInput> handler,
      CompositeDisposable disposables,
      ParsedInput input) {
    CommandHandler<T> typedHandler = (CommandHandler<T>) handler;
    typedHandler.handle(disposables, (T) input);
  }

  private static <T extends ParsedInput> void register(
      Map<Class<? extends ParsedInput>, CommandHandler<? extends ParsedInput>> map,
      Class<T> commandType,
      CommandHandler<T> handler) {
    map.put(commandType, handler);
  }

  private static String unknownCommandAsRawLine(String rawUnknown) {
    String line = rawUnknown == null ? "" : rawUnknown.trim();
    if (line.startsWith("/")) line = line.substring(1);
    return line.trim();
  }

  @Override
  public void openQuasselSetup(CompositeDisposable disposables, String serverId) {
    quasselOutboundCommandService.handleQuasselSetup(disposables, serverId);
  }

  @Override
  public void openQuasselNetworkManager(CompositeDisposable disposables, String serverId) {
    quasselOutboundCommandService.handleQuasselNetworkManager(disposables, serverId);
  }

  @FunctionalInterface
  private interface CommandHandler<T extends ParsedInput> {
    void handle(CompositeDisposable disposables, T input);
  }
}
