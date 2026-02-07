package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.ignore.InboundIgnorePolicy;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.app.state.AwayRoutingState;
import cafe.woden.ircclient.app.state.JoinRoutingState;
import cafe.woden.ircclient.app.outbound.OutboundModeCommandService;
import cafe.woden.ircclient.app.outbound.OutboundCtcpWhoisCommandService;
import cafe.woden.ircclient.app.outbound.OutboundChatCommandService;
import cafe.woden.ircclient.app.outbound.OutboundIgnoreCommandService;
import cafe.woden.ircclient.app.state.CtcpRoutingState;
import cafe.woden.ircclient.app.state.CtcpRoutingState.PendingCtcp;
import cafe.woden.ircclient.app.state.ModeRoutingState;
import cafe.woden.ircclient.app.state.WhoisRoutingState;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Mediator.
 *
 * <p>Multi-server support:
 * targets are scoped to a server id via {@link TargetRef}.
 */
@Component
@Lazy
public class IrcMediator {
  private final IrcClientService irc;
  private final UiPort ui;
  private final CommandParser commandParser;
  private final ServerRegistry serverRegistry;
  private final RuntimeConfigStore runtimeConfig;
  private final ConnectionCoordinator connectionCoordinator;
  private final TargetCoordinator targetCoordinator;
  private final UiSettingsBus uiSettingsBus;
  private final UserInfoEnrichmentService userInfoEnrichmentService;
  private final InboundIgnorePolicy inboundIgnorePolicy;
  private final CompositeDisposable disposables = new CompositeDisposable();

  // Routing/correlation state extracted from IrcMediator.
  private final WhoisRoutingState whoisRoutingState;
  private final CtcpRoutingState ctcpRoutingState;
  private final ModeRoutingState modeRoutingState;
  private final AwayRoutingState awayRoutingState;
  private final JoinRoutingState joinRoutingState;

  // Inbound MODE-related event handler (join-burst buffering + MODE pretty printing + 324 routing).
  private final InboundModeEventHandler inboundModeEventHandler;
  private final OutboundModeCommandService outboundModeCommandService;
  private final OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService;
  private final OutboundChatCommandService outboundChatCommandService;
  private final OutboundIgnoreCommandService outboundIgnoreCommandService;

  private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean(false);

  // Active target state is owned by TargetCoordinator.

  @PostConstruct
  void init() {
    start();
  }

  @PreDestroy
  void shutdown() {
    stop();
  }

  public IrcMediator(
      IrcClientService irc,
      UiPort ui,
      CommandParser commandParser,
      ServerRegistry serverRegistry,
      RuntimeConfigStore runtimeConfig,
      ConnectionCoordinator connectionCoordinator,
      TargetCoordinator targetCoordinator,
      UiSettingsBus uiSettingsBus,
      UserInfoEnrichmentService userInfoEnrichmentService,
      WhoisRoutingState whoisRoutingState,
      CtcpRoutingState ctcpRoutingState,
      ModeRoutingState modeRoutingState,
      AwayRoutingState awayRoutingState,
      JoinRoutingState joinRoutingState,
      InboundModeEventHandler inboundModeEventHandler,
      OutboundModeCommandService outboundModeCommandService,
      OutboundCtcpWhoisCommandService outboundCtcpWhoisCommandService,
      OutboundChatCommandService outboundChatCommandService,
      OutboundIgnoreCommandService outboundIgnoreCommandService,
      InboundIgnorePolicy inboundIgnorePolicy
  ) {

    this.irc = irc;
    this.ui = ui;
    this.commandParser = commandParser;
    this.serverRegistry = serverRegistry;
    this.runtimeConfig = runtimeConfig;
    this.connectionCoordinator = connectionCoordinator;
    this.targetCoordinator = targetCoordinator;
    this.uiSettingsBus = uiSettingsBus;
    this.userInfoEnrichmentService = userInfoEnrichmentService;
    this.whoisRoutingState = whoisRoutingState;
    this.ctcpRoutingState = ctcpRoutingState;
    this.modeRoutingState = modeRoutingState;
    this.awayRoutingState = awayRoutingState;
    this.joinRoutingState = joinRoutingState;
    this.inboundModeEventHandler = inboundModeEventHandler;
    this.outboundModeCommandService = outboundModeCommandService;
    this.outboundCtcpWhoisCommandService = outboundCtcpWhoisCommandService;
    this.outboundChatCommandService = outboundChatCommandService;
    this.outboundIgnoreCommandService = outboundIgnoreCommandService;
    this.inboundIgnorePolicy = inboundIgnorePolicy;
  }

  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }

    disposables.add(
        ui.targetSelections()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::onTargetSelected,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.targetActivations()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::onTargetActivated,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.privateMessageRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::openPrivateConversation,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.userActionRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::handleUserActionRequest,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        ui.outboundLines()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::handleOutgoingLine,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", err.toString()))
    );

    disposables.add(
        irc.events()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::onServerIrcEvent,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(irc-error)", err.toString()))
    );

    disposables.add(
        ui.connectClicks()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(ignored -> connectionCoordinator.connectAll())
    );

    disposables.add(
        ui.disconnectClicks()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(ignored -> connectionCoordinator.disconnectAll())
    );

    disposables.add(
        ui.connectServerRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(connectionCoordinator::connectOne,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    disposables.add(
        ui.disconnectServerRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(connectionCoordinator::disconnectOne,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    disposables.add(
        ui.closeTargetRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::closeTarget,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    disposables.add(
        ui.clearLogRequests()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(targetCoordinator::clearLog,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err)))
    );

    // React to runtime server list edits.
    disposables.add(
        serverRegistry.updates()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(latest -> {
                  connectionCoordinator.onServersUpdated(latest, targetCoordinator.getActiveTarget());
                  targetCoordinator.refreshInputEnabledForActiveTarget();
                },
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(ui-error)", "Server list update failed: " + err))
    );
  }

  private void handleUserActionRequest(UserActionRequest req) {
    if (req == null) return;
    TargetRef ctx = req.contextTarget() != null ? req.contextTarget() : targetCoordinator.getActiveTarget();
    if (ctx == null) ctx = targetCoordinator.safeStatusTarget();
    final var fCtx = ctx;
    String sid = ctx.serverId();
    String nick = req.nick() == null ? "" : req.nick().trim();
    if (sid == null || sid.isBlank() || nick.isBlank()) return;

    switch (req.action()) {
      case OPEN_QUERY -> targetCoordinator.openPrivateConversation(new PrivateMessageRequest(sid, nick));

      case WHOIS -> {
        whoisRoutingState.put(sid, nick, ctx);
        ui.appendStatus(ctx, "(whois)", "Requesting WHOIS for " + nick + "...");
        Disposable d = irc.whois(sid, nick)
            .subscribe(
                () -> {},
                err -> ui.appendError(fCtx, "(whois)", err.toString())
            );
        disposables.add(d);
      }

      case CTCP_VERSION -> {
        ctcpRoutingState.put(sid, nick, "VERSION", null, ctx);
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " VERSION");
        disposables.add(irc.sendPrivateMessage(sid, nick, "\u0001VERSION\u0001")
            .subscribe(() -> {}, err -> ui.appendError(fCtx, "(ctcp)", err.toString())));
      }

      case CTCP_PING -> {
        String token = Long.toString(System.currentTimeMillis());
        ctcpRoutingState.put(sid, nick, "PING", token, ctx);
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " PING");
        disposables.add(irc.sendPrivateMessage(sid, nick, "\u0001PING " + token + "\u0001")
            .subscribe(() -> {}, err -> ui.appendError(fCtx, "(ctcp)", err.toString())));
      }

      case CTCP_TIME -> {
        ctcpRoutingState.put(sid, nick, "TIME", null, ctx);
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " TIME");
        disposables.add(irc.sendPrivateMessage(sid, nick, "\u0001TIME\u0001")
            .subscribe(() -> {}, err -> ui.appendError(fCtx, "(ctcp)", err.toString())));
      }
    }
  }

  private record ParsedCtcp(String commandUpper, String arg) {}

  private ParsedCtcp parseCtcp(String text) {
    if (text == null || text.length() < 2) return null;
    if (text.charAt(0) != 0x01 || text.charAt(text.length() - 1) != 0x01) return null;
    String inner = text.substring(1, text.length() - 1).trim();
    if (inner.isEmpty()) return null;
    int sp = inner.indexOf(' ');
    String cmd = (sp >= 0) ? inner.substring(0, sp) : inner;
    String arg = (sp >= 0) ? inner.substring(sp + 1).trim() : "";
    cmd = cmd.trim().toUpperCase(Locale.ROOT);
    return new ParsedCtcp(cmd, arg);
  }

private InboundIgnorePolicy.Decision decideInbound(String sid, String from, boolean isCtcp) {
    if (inboundIgnorePolicy == null) return InboundIgnorePolicy.Decision.ALLOW;
    String f = Objects.toString(from, "").trim();
    if (f.isEmpty()) return InboundIgnorePolicy.Decision.ALLOW;
    // PircBotX uses "server" when no user prefix is present; don't apply user ignore rules to that.
    if ("server".equalsIgnoreCase(f)) return InboundIgnorePolicy.Decision.ALLOW;
    return inboundIgnorePolicy.decide(sid, f, null, isCtcp);
  }

  private void handleNoticeOrSpoiler(String sid, TargetRef status, String from, String text, boolean spoiler, boolean suppressOutput) {
    // CTCP replies come back as NOTICE with 0x01-wrapped payload.
    // Route them to the chat target where the request originated.
    ParsedCtcp ctcp = parseCtcp(text);
    if (ctcp != null) {
      String cmd = ctcp.commandUpper();
      String arg = ctcp.arg();

      TargetRef dest = null;
      String rendered = null;

      if ("VERSION".equals(cmd)) {
        PendingCtcp p = ctcpRoutingState.remove(sid, from, cmd, null);
        if (p != null) {
          dest = p.target();
          rendered = from + " VERSION: " + (arg.isBlank() ? "(no version)" : arg);
        }
      } else if ("PING".equals(cmd)) {
        String token = arg;
        int sp = token.indexOf(' ');
        if (sp >= 0) token = token.substring(0, sp);
        PendingCtcp p = ctcpRoutingState.remove(sid, from, cmd, token);
        if (p != null) {
          dest = p.target();
          long rtt = Math.max(0L, System.currentTimeMillis() - p.startedMs());
          rendered = from + " PING reply: " + rtt + "ms";
        }
      } else if ("TIME".equals(cmd)) {
        PendingCtcp p = ctcpRoutingState.remove(sid, from, cmd, null);
        if (p != null) {
          dest = p.target();
          rendered = from + " TIME: " + (arg.isBlank() ? "(no time)" : arg);
        }
      }

      // If we received a CTCP reply we recognize but didn't have a pending request for,
      // still render a clean status line to the server status window (better than raw 0x01).
      if (dest == null && rendered == null) {
        if ("VERSION".equals(cmd)) {
          dest = status;
          rendered = from + " VERSION: " + (arg.isBlank() ? "(no version)" : arg);
        } else if ("PING".equals(cmd)) {
          dest = status;
          rendered = from + " PING: " + (arg.isBlank() ? "(no payload)" : arg);
        } else if ("TIME".equals(cmd)) {
          dest = status;
          rendered = from + " TIME: " + (arg.isBlank() ? "(no time)" : arg);
        } else {
          PendingCtcp p = ctcpRoutingState.remove(sid, from, cmd, null);
          dest = (p != null) ? p.target() : status;
          rendered = from + " " + cmd + (arg.isBlank() ? "" : ": " + arg);
        }
      }

      if (dest != null && rendered != null) {
        if (suppressOutput) return;
        ensureTargetExists(dest);
        if (spoiler) {
          ui.appendSpoilerChat(dest, "(ctcp)", rendered);
        } else {
          ui.appendStatus(dest, "(ctcp)", rendered);
        }
        if (!dest.equals(targetCoordinator.getActiveTarget())) ui.markUnread(dest);
        return;
      }
    }

    if (suppressOutput) return;

    if (spoiler) {
      ui.appendSpoilerChat(status, "(notice) " + from, text);
    } else {
      ui.appendNotice(status, "(notice) " + from, text);
    }
  }
  public void stop() {
    if (!started.compareAndSet(true, false)) {
      return;
    }
    disposables.dispose();
  }

  public void connectAll() {
    connectionCoordinator.connectAll();
  }

  public void disconnectAll() {
    connectionCoordinator.disconnectAll();
  }

  private void handleOutgoingLine(String raw) {
    ParsedInput in = commandParser.parse(raw);
    switch (in) {
      case ParsedInput.Join cmd -> outboundChatCommandService.handleJoin(disposables, cmd.channel());
      case ParsedInput.Part cmd -> outboundChatCommandService.handlePart(disposables, cmd.channel(), cmd.reason());
      case ParsedInput.Nick cmd -> outboundChatCommandService.handleNick(disposables, cmd.newNick());
      case ParsedInput.Away cmd -> outboundChatCommandService.handleAway(disposables, cmd.message());
      case ParsedInput.Query cmd -> outboundChatCommandService.handleQuery(cmd.nick());
      case ParsedInput.Msg cmd -> outboundChatCommandService.handleMsg(disposables, cmd.nick(), cmd.body());
      case ParsedInput.Me cmd -> outboundChatCommandService.handleMe(disposables, cmd.action());
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
      case ParsedInput.CtcpVersion cmd -> outboundCtcpWhoisCommandService.handleCtcpVersion(disposables, cmd.nick());
      case ParsedInput.CtcpPing cmd -> outboundCtcpWhoisCommandService.handleCtcpPing(disposables, cmd.nick());
      case ParsedInput.CtcpTime cmd -> outboundCtcpWhoisCommandService.handleCtcpTime(disposables, cmd.nick());
      case ParsedInput.Ctcp cmd -> outboundCtcpWhoisCommandService.handleCtcp(disposables, cmd.nick(), cmd.command(), cmd.args());
      case ParsedInput.Quote cmd -> outboundChatCommandService.handleQuote(disposables, cmd.rawLine());
      case ParsedInput.Say cmd -> outboundChatCommandService.handleSay(disposables, cmd.text());
      case ParsedInput.Unknown cmd ->
          ui.appendStatus(safeStatusTarget(), "(system)", "Unknown command: " + cmd.raw());
    }
  }

  // --- Chatty slash commands extracted to OutboundChatCommandService --------------------

  // --- MODE slash commands extracted to OutboundModeCommandService --------------------

  private void handleSay(String msg) {
    TargetRef at = targetCoordinator.getActiveTarget();
    if (at == null) {
      ui.appendStatus(safeStatusTarget(), "(system)", "Select a server first.");
      return;
    }

    String m = msg == null ? "" : msg.trim();
    if (m.isEmpty()) return;

    if (at.isStatus()) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(system)", "Select a channel, or double-click a nick to PM them.");
      return;
    }

    if (at.isUiOnly()) {
      ui.appendStatus(new TargetRef(at.serverId(), "status"), "(system)", "That view does not accept chat input.");
      return;
    }

    sendMessage(at, m);
  }

  private void sendMessage(TargetRef target, String message) {
    if (target == null) return;
    if (target.isUiOnly()) return;
    String m = message == null ? "" : message.trim();
    if (m.isEmpty()) return;

    if (!connectionCoordinator.isConnected(target.serverId())) {
      TargetRef status = new TargetRef(target.serverId(), "status");
      ui.appendStatus(status, "(conn)", "Not connected");
      if (!target.isStatus()) {
        ui.appendStatus(target, "(conn)", "Not connected");
      }
      return;
    }

    disposables.add(
        irc.sendMessage(target.serverId(), target.target(), m).subscribe(
            () -> {},
            err -> ui.appendError(safeStatusTarget(), "(send-error)", String.valueOf(err))
        )
    );

    String me = irc.currentNick(target.serverId()).orElse("me");
    ui.appendChat(target, "(" + me + ")", m, true);
  }

  private void onServerIrcEvent(ServerIrcEvent se) {
    if (se == null) return;

    String sid = se.serverId();
    IrcEvent e = se.event();

    TargetRef status = new TargetRef(sid, "status");

    // Delegate connectivity state changes.
    if (e instanceof IrcEvent.Connected
        || e instanceof IrcEvent.Connecting
        || e instanceof IrcEvent.Reconnecting
        || e instanceof IrcEvent.Disconnected) {
      connectionCoordinator.handleConnectivityEvent(sid, e, targetCoordinator.getActiveTarget());
      if (e instanceof IrcEvent.Disconnected) {
        targetCoordinator.onServerDisconnected(sid);
        // Drop any per-server correlation state so it doesn't stick across reconnects.
        whoisRoutingState.clearServer(sid);
        ctcpRoutingState.clearServer(sid);
        modeRoutingState.clearServer(sid);
        awayRoutingState.clearServer(sid);
        joinRoutingState.clearServer(sid);
        inboundModeEventHandler.clearServer(sid);
      }
      targetCoordinator.refreshInputEnabledForActiveTarget();
      return;
    }

    switch (e) {
      case IrcEvent.NickChanged ev -> {
        irc.currentNick(sid).ifPresent(currentNick -> {
          if (!Objects.equals(currentNick, ev.oldNick()) && !Objects.equals(currentNick, ev.newNick())) {
            ui.appendNotice(status, "(nick)", ev.oldNick() + " is now known as " + ev.newNick());
          } else {
            ui.appendStatus(status, "(nick)", "Now known as " + ev.newNick());
            ui.setChatCurrentNick(sid, ev.newNick());
          }
        });
      }
      case IrcEvent.ChannelMessage ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        TargetRef active = targetCoordinator.getActiveTarget();

        // Step 3B: track recent activity to prioritize optional WHOIS fallback.
        userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());

        InboundIgnorePolicy.Decision decision = decideInbound(sid, ev.from(), false);
        if (decision == InboundIgnorePolicy.Decision.HARD_DROP) return;

        if (decision == InboundIgnorePolicy.Decision.SOFT_SPOILER) {
          postTo(chan, active, true, d -> ui.appendSpoilerChat(d, ev.from(), ev.text()));
        } else {
          postTo(chan, active, true, d -> ui.appendChat(d, ev.from(), ev.text()));
        }

        if (!chan.equals(active) && containsSelfMention(sid, ev.from(), ev.text())) {
          ui.markHighlight(chan);
          ui.recordHighlight(chan, ev.from());
        }
      }
      case IrcEvent.ChannelAction ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        TargetRef active = targetCoordinator.getActiveTarget();

        // Step 3B: track recent activity to prioritize optional WHOIS fallback.
        userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());

        InboundIgnorePolicy.Decision decision = decideInbound(sid, ev.from(), true);
        if (decision == InboundIgnorePolicy.Decision.HARD_DROP) return;

        if (decision == InboundIgnorePolicy.Decision.SOFT_SPOILER) {
          postTo(chan, active, true, d -> ui.appendSpoilerChat(d, ev.from(), "* " + ev.action()));
        } else {
          postTo(chan, active, true, d -> ui.appendAction(d, ev.from(), ev.action()));
        }

        if (!chan.equals(active) && containsSelfMention(sid, ev.from(), ev.action())) {
          ui.markHighlight(chan);
          ui.recordHighlight(chan, ev.from());
        }
      }
      case IrcEvent.ChannelModeChanged ev -> {
        inboundModeEventHandler.handleChannelModeChanged(sid, ev);
      }

      case IrcEvent.ChannelModesListed ev -> {
        inboundModeEventHandler.handleChannelModesListed(sid, ev);
      }

      case IrcEvent.ChannelTopicUpdated ev -> {
        inboundModeEventHandler.onChannelTopicUpdated(sid, ev.channel());
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.setChannelTopic(chan, ev.topic());
      }
      case IrcEvent.PrivateMessage ev -> {
        TargetRef pm = new TargetRef(sid, ev.from());

        // Step 3B: track recent activity to prioritize optional WHOIS fallback.
        userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());

        InboundIgnorePolicy.Decision decision = decideInbound(sid, ev.from(), false);
        if (decision == InboundIgnorePolicy.Decision.HARD_DROP) return;

        if (decision == InboundIgnorePolicy.Decision.SOFT_SPOILER) {
          postTo(pm, true, d -> ui.appendSpoilerChat(d, ev.from(), ev.text()));
        } else {
          postTo(pm, true, d -> ui.appendChat(d, ev.from(), ev.text()));
        }
      }
      case IrcEvent.PrivateAction ev -> {
        TargetRef pm = new TargetRef(sid, ev.from());

        // Step 3B: track recent activity to prioritize optional WHOIS fallback.
        userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());

        InboundIgnorePolicy.Decision decision = decideInbound(sid, ev.from(), true);
        if (decision == InboundIgnorePolicy.Decision.HARD_DROP) return;

        if (decision == InboundIgnorePolicy.Decision.SOFT_SPOILER) {
          postTo(pm, true, d -> ui.appendSpoilerChat(d, ev.from(), "* " + ev.action()));
        } else {
          postTo(pm, true, d -> ui.appendAction(d, ev.from(), ev.action()));
        }
      }
      case IrcEvent.Notice ev -> {
        boolean isCtcp = parseCtcp(ev.text()) != null;
        InboundIgnorePolicy.Decision d = decideInbound(sid, ev.from(), isCtcp);
        boolean spoiler = d == InboundIgnorePolicy.Decision.SOFT_SPOILER;
        boolean suppress = d == InboundIgnorePolicy.Decision.HARD_DROP;
        handleNoticeOrSpoiler(sid, status, ev.from(), ev.text(), spoiler, suppress);
      }
      case IrcEvent.CtcpRequestReceived ev -> {
        InboundIgnorePolicy.Decision decision = decideInbound(sid, ev.from(), true);
        if (decision == InboundIgnorePolicy.Decision.HARD_DROP) return;

        TargetRef dest;
        if (uiSettingsBus.get().ctcpRequestsInActiveTargetEnabled()) {
          // Prefer the currently active target on the same server, otherwise fall back to status.
          dest = resolveActiveOrStatus(sid, status);
        } else {
          // Route to the origin target instead (channel/PM), falling back to status.
          if (ev.channel() != null && !ev.channel().isBlank()) {
            dest = new TargetRef(sid, ev.channel());
          } else if (ev.from() != null && !ev.from().isBlank()) {
            dest = new TargetRef(sid, ev.from());
          } else {
            dest = status != null ? status : safeStatusTarget();
          }
        }

        StringBuilder sb = new StringBuilder()
            .append("\u2190 ")
            .append(ev.from())
            .append(" CTCP ")
            .append(ev.command());
        if (ev.argument() != null && !ev.argument().isBlank()) sb.append(' ').append(ev.argument());
        if (ev.channel() != null && !ev.channel().isBlank()) sb.append(" in ").append(ev.channel());
        final String rendered = sb.toString();

        if (decision == InboundIgnorePolicy.Decision.SOFT_SPOILER) {
          postTo(dest, true, d -> ui.appendSpoilerChat(d, "(ctcp)", rendered));
        } else {
          postTo(dest, true, d -> ui.appendStatus(d, "(ctcp)", rendered));
        }
      }
      case IrcEvent.AwayStatusChanged ev -> {
        awayRoutingState.setAway(sid, ev.away());
        if (!ev.away()) awayRoutingState.setLastReason(sid, null);
        TargetRef dest = null;

        // Prefer routing back to where the user initiated /away (if recent), otherwise
        // fall back to the currently active target on the same server.
        TargetRef origin = awayRoutingState.recentOriginIfFresh(sid, Duration.ofSeconds(15));
        if (origin != null && Objects.equals(origin.serverId(), sid)) {
          dest = origin;
        }

        if (dest == null) {
          dest = resolveActiveOrStatus(sid, status);
        }

        final String rendered;
        if (ev.away()) {
          String reason = awayRoutingState.getLastReason(sid);
          if (reason != null && !reason.isBlank()) {
            rendered = "You are now marked as being away (Reason: " + reason + ")";
          } else {
            rendered = ev.message();
          }
        } else {
          rendered = "You are no longer marked as being away";
        }

        TargetRef finalDest = dest;
        postTo(finalDest, true, d -> ui.appendStatus(d, "(away)", rendered));
      }
      case IrcEvent.WhoisResult ev -> {
        TargetRef dest = whoisRoutingState.remove(sid, ev.nick());
        if (dest == null) dest = status;
        postTo(dest, true, d -> {
          ui.appendStatus(d, "(whois)", "WHOIS for " + ev.nick());
          for (String line : ev.lines()) ui.appendStatus(d, "(whois)", line);
        });
      }

      case IrcEvent.UserJoinedChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendPresence(chan, PresenceEvent.join(ev.nick()));
      }

      case IrcEvent.UserPartedChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendPresence(chan, PresenceEvent.part(ev.nick(), ev.reason()));
      }

      case IrcEvent.UserQuitChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendPresence(chan, PresenceEvent.quit(ev.nick(), ev.reason()));
      }

      case IrcEvent.UserNickChangedChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendPresence(chan, PresenceEvent.nick(ev.oldNick(), ev.newNick()));
      }

      case IrcEvent.JoinedChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        runtimeConfig.rememberJoinedChannel(sid, ev.channel());

        // Clear any pending /join routing state now that we've actually joined.
        joinRoutingState.clear(sid, ev.channel());

        // Buffer the initial channel-flag modes so the join doesn't spam the view.
        inboundModeEventHandler.onJoinedChannel(sid, ev.channel());

        // Join-time roster enrichment (rate limited): populate away/account/hostmask quickly.
        userInfoEnrichmentService.enqueueWhoChannelPrioritized(sid, ev.channel());

        ensureTargetExists(chan);
        ui.appendStatus(chan, "(join)", "Joined " + ev.channel());
        ui.selectTarget(chan);
      }

      case IrcEvent.JoinFailed ev -> {
        // Prefer routing back to where the user initiated /join (if recent), otherwise fall back
        // to the currently active target on the same server.
        TargetRef origin = joinRoutingState.recentOriginIfFresh(sid, ev.channel(), Duration.ofSeconds(15));
        joinRoutingState.clear(sid, ev.channel());

        TargetRef dest = origin;
        if (dest == null) dest = resolveActiveOrStatus(sid, status);
        if (dest == null) dest = safeStatusTarget();

        String msg = (ev.message() == null) ? "" : ev.message().trim();
        if (msg.isEmpty()) {
          msg = "Join failed";
        }

        String rendered;
        String msgLower = msg.toLowerCase(Locale.ROOT);
        if (msgLower.startsWith("cannot join")) {
          rendered = msg + " [" + ev.code() + "]";
        } else {
          rendered = "Cannot join " + ev.channel() + " [" + ev.code() + "]: " + msg;
        }

        ensureTargetExists(dest);
        ui.appendError(dest, "(join)", rendered);

        // Always mirror to status (unless it's the same target).
        if (!dest.equals(status)) {
          ui.appendError(status, "(join)", rendered);
        }
      }

      case IrcEvent.NickListUpdated ev -> {
        inboundModeEventHandler.onNickListUpdated(sid, ev.channel());
        targetCoordinator.onNickListUpdated(sid, ev);
      }

      case IrcEvent.UserHostmaskObserved ev -> {
        targetCoordinator.onUserHostmaskObserved(sid, ev);
      }

      case IrcEvent.UserAwayStateObserved ev -> {
        targetCoordinator.onUserAwayStateObserved(sid, ev);
      }

      case IrcEvent.UserAccountStateObserved ev -> {
        targetCoordinator.onUserAccountStateObserved(sid, ev);
      }

      case IrcEvent.Error ev -> {
          ui.appendError(status, "(error)", ev.message());
      }

      default -> {
      }
    }
  }

  private boolean containsSelfMention(String serverId, String from, String message) {
    if (serverId == null || message == null || message.isEmpty()) return false;
    String me = irc.currentNick(serverId).orElse(null);
    if (me == null || me.isBlank()) return false;

    String fromNorm = normalizeNickForCompare(from);
    if (fromNorm != null && fromNorm.equalsIgnoreCase(me)) return false;

    return containsNickToken(message, me);
  }

  private static String normalizeNickForCompare(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isEmpty()) return s;

    // Some UI paths wrap self-nick in parentheses.
    if (s.startsWith("(") && s.endsWith(")") && s.length() > 2) {
      s = s.substring(1, s.length() - 1).trim();
    }

    // Strip common IRC user-mode prefixes if they appear.
    while (!s.isEmpty()) {
      char c = s.charAt(0);
      if (c == '@' || c == '+' || c == '%' || c == '~' || c == '&') {
        s = s.substring(1);
      } else {
        break;
      }
    }
    return s;
  }

  private static boolean containsNickToken(String message, String nick) {
    if (message == null || nick == null || nick.isEmpty()) return false;

    String nickLower = nick.toLowerCase(Locale.ROOT);
    int nlen = nickLower.length();

    int i = 0;
    final int len = message.length();
    while (i < len) {
      // Skip non-nick chars.
      while (i < len && !isNickChar(message.charAt(i))) i++;
      if (i >= len) break;
      int start = i;
      while (i < len && isNickChar(message.charAt(i))) i++;
      int end = i;
      int tokLen = end - start;
      if (tokLen == nlen) {
        String tokenLower = message.substring(start, end).toLowerCase(Locale.ROOT);
        if (tokenLower.equals(nickLower)) return true;
      }
    }
    return false;
  }

  private static boolean isNickChar(char ch) {
    if (ch >= '0' && ch <= '9') return true;
    if (ch >= 'A' && ch <= 'Z') return true;
    if (ch >= 'a' && ch <= 'z') return true;
    return ch == '[' || ch == ']' || ch == '\\' || ch == '`' || ch == '_' || ch == '^' || ch == '{' || ch == '|' || ch == '}' || ch == '-';
  }

  private void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
    postTo(dest, targetCoordinator.getActiveTarget(), markUnreadIfNotActive, write);
  }

  /** Prefer the active target for {@code sid}, otherwise fall back to {@code status}. */
  private TargetRef resolveActiveOrStatus(String sid, TargetRef status) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active != null && Objects.equals(active.serverId(), sid)) return active;
    return status != null ? status : safeStatusTarget();
  }

  private void postTo(TargetRef dest, TargetRef active, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
    if (dest == null) dest = safeStatusTarget();
    ensureTargetExists(dest);

    if (write != null) {
      write.accept(dest);
    }

    if (markUnreadIfNotActive && active != null && !dest.equals(active)) {
      ui.markUnread(dest);
    }
  }

  private void ensureTargetExists(TargetRef target) {
    ui.ensureTargetExists(target);
  }

  private TargetRef safeStatusTarget() {
    return targetCoordinator.safeStatusTarget();
  }
}
