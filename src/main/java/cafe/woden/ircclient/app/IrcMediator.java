package cafe.woden.ircclient.app;

import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.notifications.NotificationRuleMatch;
import cafe.woden.ircclient.app.notifications.NotificationRuleMatcher;
import cafe.woden.ircclient.app.outbound.OutboundCommandDispatcher;
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
import cafe.woden.ircclient.app.state.CtcpRoutingState;
import cafe.woden.ircclient.app.state.CtcpRoutingState.PendingCtcp;
import cafe.woden.ircclient.app.state.ModeRoutingState;
import cafe.woden.ircclient.app.state.PendingEchoMessageState;
import cafe.woden.ircclient.app.state.WhoisRoutingState;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** App mediator. */
@Component
@Lazy
public class IrcMediator {
  private final IrcClientService irc;
  private final UiPort ui;
  private final CommandParser commandParser;
  private final ServerRegistry serverRegistry;
  private final RuntimeConfigStore runtimeConfig;
  private final ConnectionCoordinator connectionCoordinator;
  private final MediatorConnectionSubscriptionBinder mediatorConnectionSubscriptionBinder;
  private final MediatorUiSubscriptionBinder mediatorUiSubscriptionBinder;
  private final MediatorHistoryIngestOrchestrator mediatorHistoryIngestOrchestrator;
  private final OutboundCommandDispatcher outboundCommandDispatcher;
  private final TargetCoordinator targetCoordinator;
  private final UiSettingsBus uiSettingsBus;
  private final UserInfoEnrichmentService userInfoEnrichmentService;
  private final InboundIgnorePolicy inboundIgnorePolicy;
  private final CompositeDisposable disposables = new CompositeDisposable();
  private final WhoisRoutingState whoisRoutingState;
  private final CtcpRoutingState ctcpRoutingState;
  private final ModeRoutingState modeRoutingState;
  private final AwayRoutingState awayRoutingState;
  private final JoinRoutingState joinRoutingState;
  private final PendingEchoMessageState pendingEchoMessageState;
  private final InboundModeEventHandler inboundModeEventHandler;

  private final NotificationRuleMatcher notificationRuleMatcher;

  private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean(false);

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
      MediatorConnectionSubscriptionBinder mediatorConnectionSubscriptionBinder,
      MediatorUiSubscriptionBinder mediatorUiSubscriptionBinder,
      MediatorHistoryIngestOrchestrator mediatorHistoryIngestOrchestrator,
      OutboundCommandDispatcher outboundCommandDispatcher,
      TargetCoordinator targetCoordinator,
      UiSettingsBus uiSettingsBus,
      NotificationRuleMatcher notificationRuleMatcher,
      UserInfoEnrichmentService userInfoEnrichmentService,
      WhoisRoutingState whoisRoutingState,
      CtcpRoutingState ctcpRoutingState,
      ModeRoutingState modeRoutingState,
      AwayRoutingState awayRoutingState,
      JoinRoutingState joinRoutingState,
      PendingEchoMessageState pendingEchoMessageState,
      InboundModeEventHandler inboundModeEventHandler,
      InboundIgnorePolicy inboundIgnorePolicy
  ) {

    this.irc = irc;
    this.ui = ui;
    this.commandParser = commandParser;
    this.serverRegistry = serverRegistry;
    this.runtimeConfig = runtimeConfig;
    this.connectionCoordinator = connectionCoordinator;
    this.mediatorConnectionSubscriptionBinder = mediatorConnectionSubscriptionBinder;
    this.mediatorUiSubscriptionBinder = mediatorUiSubscriptionBinder;
    this.mediatorHistoryIngestOrchestrator = mediatorHistoryIngestOrchestrator;
    this.outboundCommandDispatcher = outboundCommandDispatcher;
    this.targetCoordinator = targetCoordinator;
    this.uiSettingsBus = uiSettingsBus;
    this.notificationRuleMatcher = notificationRuleMatcher;
    this.userInfoEnrichmentService = userInfoEnrichmentService;
    this.whoisRoutingState = whoisRoutingState;
    this.ctcpRoutingState = ctcpRoutingState;
    this.modeRoutingState = modeRoutingState;
    this.awayRoutingState = awayRoutingState;
    this.joinRoutingState = joinRoutingState;
    this.pendingEchoMessageState = pendingEchoMessageState;
    this.inboundModeEventHandler = inboundModeEventHandler;
    this.inboundIgnorePolicy = inboundIgnorePolicy;
  }

  public void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }

    mediatorUiSubscriptionBinder.bind(
        ui,
        targetCoordinator,
        disposables,
        this::handleUserActionRequest,
        this::handleOutgoingLine
    );
    bindIrcEventSubscriptions();
    mediatorConnectionSubscriptionBinder.bind(
        ui,
        connectionCoordinator,
        targetCoordinator,
        serverRegistry,
        disposables
    );
  }

  private void bindIrcEventSubscriptions() {
    disposables.add(
        irc.events()
            .observeOn(cafe.woden.ircclient.ui.SwingEdt.scheduler())
            .subscribe(this::onServerIrcEvent,
                err -> ui.appendError(targetCoordinator.safeStatusTarget(), "(irc-error)", err.toString()))
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
    if ("server".equalsIgnoreCase(f)) return InboundIgnorePolicy.Decision.ALLOW;
    return inboundIgnorePolicy.decide(sid, f, null, isCtcp);
  }

  private void handleNoticeOrSpoiler(
      String sid,
      TargetRef status,
      Instant at,
      String from,
      String text,
      boolean spoiler,
      boolean suppressOutput,
      String messageId,
      Map<String, String> ircv3Tags
  ) {
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
          ui.appendSpoilerChatAt(dest, at, "(ctcp)", rendered);
        } else {
          ui.appendStatusAt(dest, at, "(ctcp)", rendered);
        }
        if (!dest.equals(targetCoordinator.getActiveTarget())) ui.markUnread(dest);
        return;
      }
    }

    if (suppressOutput) return;

    if (spoiler) {
      ui.appendSpoilerChatAt(status, at, "(notice) " + from, text);
    } else {
      ui.appendNoticeAt(status, at, "(notice) " + from, text, messageId, ircv3Tags);
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
    outboundCommandDispatcher.dispatch(disposables, commandParser.parse(raw));
  }

  
  private TargetRef activeTargetForServerOrStatus(String sid, TargetRef status) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active != null && Objects.equals(active.serverId(), sid)) {
      return active;
    }
    return status;
  }


  private void onServerIrcEvent(ServerIrcEvent se) {
    if (se == null) return;

    String sid = se.serverId();
    IrcEvent e = se.event();

    TargetRef status = new TargetRef(sid, "status");
    if (e instanceof IrcEvent.Connected
        || e instanceof IrcEvent.Connecting
        || e instanceof IrcEvent.Reconnecting
        || e instanceof IrcEvent.Disconnected) {
      connectionCoordinator.handleConnectivityEvent(sid, e, targetCoordinator.getActiveTarget());
      if (e instanceof IrcEvent.Disconnected) {
        failPendingEchoesForServer(sid, "disconnected before echo");
        targetCoordinator.onServerDisconnected(sid);
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

        userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());

        InboundIgnorePolicy.Decision decision = decideInbound(sid, ev.from(), false);
        if (decision == InboundIgnorePolicy.Decision.HARD_DROP) return;

        if (tryResolvePendingEchoChannelMessage(sid, chan, active, ev)) {
          return;
        }

        if (decision == InboundIgnorePolicy.Decision.SOFT_SPOILER) {
          postTo(chan, active, true, d -> ui.appendSpoilerChatAt(d, ev.at(), ev.from(), ev.text()));
        } else {
          postTo(
              chan,
              active,
              true,
              d -> ui.appendChatAt(d, ev.at(), ev.from(), ev.text(), false, ev.messageId(), ev.ircv3Tags()));
        }

        if (!chan.equals(active)) {
          maybeRecordRuleMatch(sid, chan, active, ev.from(), ev.text());
        }

        if (!chan.equals(active) && containsSelfMention(sid, ev.from(), ev.text())) {
          ui.markHighlight(chan);
          ui.recordHighlight(chan, ev.from());
        }
      }
      case IrcEvent.ChannelAction ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        TargetRef active = targetCoordinator.getActiveTarget();

        userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());

        InboundIgnorePolicy.Decision decision = decideInbound(sid, ev.from(), true);
        if (decision == InboundIgnorePolicy.Decision.HARD_DROP) return;

        if (decision == InboundIgnorePolicy.Decision.SOFT_SPOILER) {
          postTo(chan, active, true, d -> ui.appendSpoilerChatAt(d, ev.at(), ev.from(), "* " + ev.action()));
        } else {
          postTo(
              chan,
              active,
              true,
              d -> ui.appendActionAt(d, ev.at(), ev.from(), ev.action(), false, ev.messageId(), ev.ircv3Tags()));
        }

        if (!chan.equals(active)) {
          maybeRecordRuleMatch(sid, chan, active, ev.from(), ev.action());
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

        userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());

        InboundIgnorePolicy.Decision decision = decideInbound(sid, ev.from(), false);
        if (decision == InboundIgnorePolicy.Decision.HARD_DROP) return;

        if (tryResolvePendingEchoPrivateMessage(sid, pm, ev)) {
          return;
        }

        if (decision == InboundIgnorePolicy.Decision.SOFT_SPOILER) {
          postTo(pm, true, d -> ui.appendSpoilerChatAt(d, ev.at(), ev.from(), ev.text()));
        } else {
          postTo(pm, true, d -> ui.appendChatAt(d, ev.at(), ev.from(), ev.text(), false, ev.messageId(), ev.ircv3Tags()));
        }
      }
      case IrcEvent.PrivateAction ev -> {
        TargetRef pm = new TargetRef(sid, ev.from());

        userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());

        InboundIgnorePolicy.Decision decision = decideInbound(sid, ev.from(), true);
        if (decision == InboundIgnorePolicy.Decision.HARD_DROP) return;

        if (decision == InboundIgnorePolicy.Decision.SOFT_SPOILER) {
          postTo(pm, true, d -> ui.appendSpoilerChatAt(d, ev.at(), ev.from(), "* " + ev.action()));
        } else {
          postTo(pm, true, d -> ui.appendActionAt(d, ev.at(), ev.from(), ev.action(), false, ev.messageId(), ev.ircv3Tags()));
        }
      }
      case IrcEvent.Notice ev -> {
        boolean isCtcp = parseCtcp(ev.text()) != null;
        InboundIgnorePolicy.Decision d = decideInbound(sid, ev.from(), isCtcp);
        boolean spoiler = d == InboundIgnorePolicy.Decision.SOFT_SPOILER;
        boolean suppress = d == InboundIgnorePolicy.Decision.HARD_DROP;

        TargetRef dest = null;
        String t = ev.target();
        if (t != null && !t.isBlank()) {
          TargetRef noticeTarget = new TargetRef(sid, t);
          if (noticeTarget.isChannel()) {
            dest = noticeTarget;
          }
        }
        if (dest == null) {
          dest = activeTargetForServerOrStatus(sid, status);
        }

        handleNoticeOrSpoiler(
            sid,
            dest,
            ev.at(),
            ev.from(),
            ev.text(),
            spoiler,
            suppress,
            ev.messageId(),
            ev.ircv3Tags());
      }
case IrcEvent.ServerTimeNotNegotiated ev -> {
        ui.appendStatus(status, "(ircv3)", ev.message());
      }
      case cafe.woden.ircclient.irc.IrcEvent.ServerResponseLine ev -> {
        ensureTargetExists(status);
        String msg = ev.message();
        if (msg == null) msg = "";
        String label = Objects.toString(ev.ircv3Tags().get("label"), "").trim();
        if (label != null && !label.isBlank()) {
          msg = msg + " {label=" + label + "}";
        }
        String rendered = "[" + ev.code() + "] " + msg;
        ui.appendStatusAt(status, ev.at(), "(server)", rendered, ev.messageId(), ev.ircv3Tags());
      }
      case IrcEvent.ChatHistoryBatchReceived ev -> {
        mediatorHistoryIngestOrchestrator.onChatHistoryBatchReceived(sid, ev);
      }

      case IrcEvent.ZncPlaybackBatchReceived ev -> {
        mediatorHistoryIngestOrchestrator.onZncPlaybackBatchReceived(sid, ev);
      }
      case IrcEvent.CtcpRequestReceived ev -> {
        InboundIgnorePolicy.Decision decision = decideInbound(sid, ev.from(), true);
        if (decision == InboundIgnorePolicy.Decision.HARD_DROP) return;

        TargetRef dest;
        if (uiSettingsBus.get().ctcpRequestsInActiveTargetEnabled()) {
          dest = resolveActiveOrStatus(sid, status);
        } else {
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
          postTo(dest, true, d -> ui.appendSpoilerChatAt(d, ev.at(), "(ctcp)", rendered));
        } else {
          postTo(dest, true, d -> ui.appendStatusAt(d, ev.at(), "(ctcp)", rendered));
        }
      }
      case IrcEvent.AwayStatusChanged ev -> {
        awayRoutingState.setAway(sid, ev.away());
        if (!ev.away()) awayRoutingState.setLastReason(sid, null);
        TargetRef dest = null;
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

      case IrcEvent.InvitedToChannel ev -> {
        TargetRef dest = resolveActiveOrStatus(sid, status);
        String from = Objects.toString(ev.from(), "").trim();
        if (from.isEmpty()) from = "server";
        String rendered = from + " invited you to " + ev.channel();
        postTo(dest, true, d -> ui.appendStatusAt(d, ev.at(), "(invite)", rendered));
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

      case IrcEvent.LeftChannel ev -> {
        TargetRef st = new TargetRef(sid, "status");
        String rendered = "You left " + ev.channel();
        String reason = Objects.toString(ev.reason(), "").trim();
        if (!reason.isEmpty()) rendered = rendered + " (" + reason + ")";

        ensureTargetExists(st);
        ui.appendStatusAt(st, ev.at(), "(part)", rendered);
        inboundModeEventHandler.onLeftChannel(sid, ev.channel());
        targetCoordinator.closeChannelLocally(sid, ev.channel());
      }

      case IrcEvent.UserKickedFromChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        TargetRef active = targetCoordinator.getActiveTarget();
        String rendered = renderOtherKick(ev.nick(), ev.by(), ev.reason());
        postTo(chan, active, true, d -> ui.appendStatusAt(d, ev.at(), "(kick)", rendered));
      }

      case IrcEvent.KickedFromChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        TargetRef st = new TargetRef(sid, "status");
        String rendered = renderSelfKick(ev.channel(), ev.by(), ev.reason());

        ensureTargetExists(chan);
        ui.appendErrorAt(chan, ev.at(), "(kick)", rendered);
        ensureTargetExists(st);
        ui.appendErrorAt(st, ev.at(), "(kick)", rendered);

        inboundModeEventHandler.onLeftChannel(sid, ev.channel());
        targetCoordinator.closeChannelLocally(sid, ev.channel());
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
        joinRoutingState.clear(sid, ev.channel());
        inboundModeEventHandler.onJoinedChannel(sid, ev.channel());
        userInfoEnrichmentService.enqueueWhoChannelPrioritized(sid, ev.channel());

        ensureTargetExists(chan);
        ui.appendStatus(chan, "(join)", "Joined " + ev.channel());
        ui.selectTarget(chan);
      }

      case IrcEvent.JoinFailed ev -> {
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

      case IrcEvent.UserHostChanged ev -> {
        TargetRef dest = resolveActiveOrStatus(sid, status);
        String nick = Objects.toString(ev.nick(), "").trim();
        String user = Objects.toString(ev.user(), "").trim();
        String host = Objects.toString(ev.host(), "").trim();
        if (nick.isEmpty()) nick = "(unknown)";
        String renderedText = nick + " changed host";
        if (!user.isEmpty() || !host.isEmpty()) {
          String uh = user + (host.isEmpty() ? "" : ("@" + host));
          renderedText = renderedText + " to " + uh;
        }
        String rendered = renderedText;
        postTo(dest, false, d -> ui.appendStatusAt(d, ev.at(), "(chghost)", rendered));
      }

      case IrcEvent.UserAwayStateObserved ev -> {
        targetCoordinator.onUserAwayStateObserved(sid, ev);
      }

      case IrcEvent.UserAccountStateObserved ev -> {
        targetCoordinator.onUserAccountStateObserved(sid, ev);
      }

      case IrcEvent.UserSetNameObserved ev -> {
        TargetRef dest = resolveActiveOrStatus(sid, status);
        String nick = Objects.toString(ev.nick(), "").trim();
        String realName = Objects.toString(ev.realName(), "").trim();
        if (nick.isEmpty()) nick = "(unknown)";
        if (realName.isEmpty()) realName = "(empty)";
        String rendered = nick + " set name to: " + realName;
        postTo(dest, false, d -> ui.appendStatusAt(d, ev.at(), "(setname)", rendered));
      }

      case IrcEvent.UserTypingObserved ev -> {
        if (isFromSelf(sid, ev.from())) return;
        TargetRef dest = resolveIrcv3Target(sid, ev.target(), ev.from(), status);
        String from = Objects.toString(ev.from(), "").trim();
        if (from.isEmpty()) from = "Someone";
        String state = Objects.toString(ev.state(), "").trim().toLowerCase(Locale.ROOT);
        if (state.isEmpty()) state = "active";
        ui.showTypingIndicator(dest, from, state);
      }

      case IrcEvent.ReadMarkerObserved ev -> {
        if (!irc.isReadMarkerAvailable(sid)) return;
        if (!shouldApplyReadMarkerEvent(sid, ev.from())) return;
        TargetRef dest = resolveReadMarkerTarget(sid, ev.target(), status);
        long markerEpochMs = parseReadMarkerEpochMs(ev.marker(), ev.at());
        ui.setReadMarker(dest, markerEpochMs);
      }

      case IrcEvent.MessageReplyObserved ev -> {
        // Reply context is rendered inline from IRCv3 tags on the message line itself.
      }

      case IrcEvent.MessageReactObserved ev -> {
        if (!irc.isDraftReactAvailable(sid)) return;
        TargetRef dest = resolveIrcv3Target(sid, ev.target(), ev.from(), status);
        String from = Objects.toString(ev.from(), "").trim();
        if (from.isEmpty()) return;
        String reaction = Objects.toString(ev.reaction(), "").trim();
        String targetMsgId = Objects.toString(ev.messageId(), "").trim();
        if (reaction.isEmpty() || targetMsgId.isEmpty()) return;
        ui.applyMessageReaction(dest, ev.at(), from, targetMsgId, reaction);
      }

      case IrcEvent.Ircv3CapabilityChanged ev -> {
        ensureTargetExists(status);
        String sub = Objects.toString(ev.subcommand(), "").trim().toUpperCase(Locale.ROOT);
        String cap = Objects.toString(ev.capability(), "").trim();
        if (cap.isEmpty()) cap = "(unknown)";
        String rendered;
        if ("NEW".equals(sub)) {
          rendered = "CAP NEW: " + cap + " (available)";
        } else if ("DEL".equals(sub)) {
          rendered = "CAP DEL: " + cap + " (removed)";
        } else if ("ACK".equals(sub)) {
          rendered = "CAP ACK: " + cap + (ev.enabled() ? " (enabled)" : " (disabled)");
        } else {
          rendered = "CAP " + sub + ": " + cap + (ev.enabled() ? " (enabled)" : "");
        }
        ui.appendStatusAt(status, ev.at(), "(ircv3)", rendered);
      }

      case IrcEvent.Error ev -> {
          ui.appendError(status, "(error)", ev.message());
      }

      default -> {
      }
    }
  }

  private void maybeRecordRuleMatch(String serverId, TargetRef chan, TargetRef active, String from, String text) {
    if (chan == null || text == null || text.isBlank()) return;
    if (active != null && chan.equals(active)) return;
    if (isFromSelf(serverId, from)) return;

    List<NotificationRuleMatch> matches;
    try {
      matches = notificationRuleMatcher.matchAll(text);
    } catch (Exception ignored) {
      return;
    }

    if (matches == null || matches.isEmpty()) return;
    // Record the first match only to avoid spamming notifications.
    NotificationRuleMatch m = matches.get(0);

    ui.markHighlight(chan);

    ui.recordRuleMatch(chan, from, m.ruleLabel(), snippetAround(text, m.start(), m.end()));
  }

  private boolean tryResolvePendingEchoChannelMessage(
      String sid,
      TargetRef chan,
      TargetRef active,
      IrcEvent.ChannelMessage ev
  ) {
    if (!isFromSelf(sid, ev.from())) return false;
    var pending = pendingEchoMessageState.consumeByTargetAndText(chan, ev.from(), ev.text());
    if (pending.isEmpty()) return false;

    var entry = pending.get();
    postTo(
        chan,
        active,
        true,
        d -> {
          boolean replaced = ui.resolvePendingOutgoingChat(
              d,
              entry.pendingId(),
              ev.at(),
              ev.from(),
              ev.text(),
              ev.messageId(),
              ev.ircv3Tags());
          if (!replaced) {
            ui.appendChatAt(d, ev.at(), ev.from(), ev.text(), true, ev.messageId(), ev.ircv3Tags());
          }
        });
    return true;
  }

  private boolean tryResolvePendingEchoPrivateMessage(
      String sid,
      TargetRef fallbackPm,
      IrcEvent.PrivateMessage ev
  ) {
    if (!isFromSelf(sid, ev.from())) return false;

    var pending = pendingEchoMessageState.consumeByTargetAndText(fallbackPm, ev.from(), ev.text());
    if (pending.isEmpty()) {
      pending = pendingEchoMessageState.consumePrivateFallback(sid, ev.from(), ev.text());
    }
    if (pending.isEmpty()) return false;

    var entry = pending.get();
    TargetRef dest = entry.target() != null ? entry.target() : fallbackPm;
    postTo(
        dest,
        true,
        d -> {
          boolean replaced = ui.resolvePendingOutgoingChat(
              d,
              entry.pendingId(),
              ev.at(),
              ev.from(),
              ev.text(),
              ev.messageId(),
              ev.ircv3Tags());
          if (!replaced) {
            ui.appendChatAt(d, ev.at(), ev.from(), ev.text(), true, ev.messageId(), ev.ircv3Tags());
          }
        });
    return true;
  }

  private void failPendingEchoesForServer(String sid, String reason) {
    if (sid == null || sid.isBlank()) return;
    Instant now = Instant.now();
    for (PendingEchoMessageState.PendingOutboundChat pending : pendingEchoMessageState.drainServer(sid)) {
      TargetRef target = pending.target();
      if (target == null) continue;
      ui.failPendingOutgoingChat(
          target,
          pending.pendingId(),
          now,
          pending.fromNick(),
          pending.text(),
          reason);
    }
  }

  private boolean isFromSelf(String serverId, String from) {
    if (serverId == null || from == null) return false;
    String me = irc.currentNick(serverId).orElse(null);
    if (me == null || me.isBlank()) return false;
    String fromNorm = normalizeNickForCompare(from);
    return fromNorm != null && fromNorm.equalsIgnoreCase(me);
  }

  private static String snippetAround(String message, int start, int end) {
    if (message == null) return "";
    int len = message.length();
    if (len == 0) return "";

    int s = Math.max(0, Math.min(start, len));
    int e = Math.max(0, Math.min(end, len));
    if (e < s) {
      int tmp = s;
      s = e;
      e = tmp;
    }

    int ctx = 70;
    int from = Math.max(0, s - ctx);
    int to = Math.min(len, e + ctx);

    String snip = message.substring(from, to).trim();
    snip = snip.replaceAll("\\s+", " ");

    if (from > 0) snip = "…" + snip;
    if (to < len) snip = snip + "…";

    int max = 200;
    if (snip.length() > max) {
      snip = snip.substring(0, max - 1) + "…";
    }
    return snip;
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
    if (s.startsWith("(") && s.endsWith(")") && s.length() > 2) {
      s = s.substring(1, s.length() - 1).trim();
    }
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

  private TargetRef resolveIrcv3Target(String sid, String target, String from, TargetRef status) {
    String t = Objects.toString(target, "").trim();
    if (!t.isEmpty() && (t.startsWith("#") || t.startsWith("&"))) {
      return new TargetRef(sid, t);
    }
    String f = Objects.toString(from, "").trim();
    if (!f.isEmpty() && !"server".equalsIgnoreCase(f)) {
      return new TargetRef(sid, f);
    }
    return resolveActiveOrStatus(sid, status);
  }

  private boolean shouldApplyReadMarkerEvent(String sid, String from) {
    String f = Objects.toString(from, "").trim();
    if (f.isEmpty() || "server".equalsIgnoreCase(f)) return true;
    return isFromSelf(sid, f);
  }

  private TargetRef resolveReadMarkerTarget(String sid, String target, TargetRef status) {
    String t = Objects.toString(target, "").trim();
    if (!t.isEmpty()) {
      String me = irc.currentNick(sid).orElse("");
      if (me.isBlank() || !t.equalsIgnoreCase(me)) {
        return new TargetRef(sid, t);
      }
    }
    return resolveActiveOrStatus(sid, status);
  }

  private static long parseReadMarkerEpochMs(String marker, Instant fallbackAt) {
    Instant fallback = (fallbackAt != null) ? fallbackAt : Instant.now();
    String raw = Objects.toString(marker, "").trim();
    if (raw.isEmpty()) return fallback.toEpochMilli();

    try {
      return Instant.parse(raw).toEpochMilli();
    } catch (Exception ignored) {
    }

    try {
      long parsed = Long.parseLong(raw);
      if (parsed <= 0) return fallback.toEpochMilli();
      if (raw.length() <= 10) {
        return Math.multiplyExact(parsed, 1000L);
      }
      return parsed;
    } catch (Exception ignored) {
      return fallback.toEpochMilli();
    }
  }

  private static boolean isNickChar(char ch) {
    if (ch >= '0' && ch <= '9') return true;
    if (ch >= 'A' && ch <= 'Z') return true;
    if (ch >= 'a' && ch <= 'z') return true;
    return ch == '[' || ch == ']' || ch == '\\' || ch == '`' || ch == '_' || ch == '^' || ch == '{' || ch == '|' || ch == '}' || ch == '-';
  }

  private static String renderOtherKick(String nick, String by, String reason) {
    String n = Objects.toString(nick, "").trim();
    String k = Objects.toString(by, "").trim();
    String r = Objects.toString(reason, "").trim();
    if (n.isEmpty()) n = "(unknown)";
    if (k.isEmpty()) k = "server";
    String base = n + " was kicked by " + k;
    return r.isEmpty() ? base : base + " (" + r + ")";
  }

  private static String renderSelfKick(String channel, String by, String reason) {
    String ch = Objects.toString(channel, "").trim();
    String k = Objects.toString(by, "").trim();
    String r = Objects.toString(reason, "").trim();
    if (ch.isEmpty()) ch = "(unknown channel)";
    if (k.isEmpty()) k = "server";
    String base = "You were kicked from " + ch + " by " + k;
    return r.isEmpty() ? base : base + " (" + r + ")";
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
