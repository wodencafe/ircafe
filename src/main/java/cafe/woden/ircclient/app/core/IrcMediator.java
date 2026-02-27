package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.AppSchedulers;
import cafe.woden.ircclient.app.InboundModeEventHandler;
import cafe.woden.ircclient.app.api.InterceptorEventType;
import cafe.woden.ircclient.app.api.InterceptorIngestPort;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.MediatorControlPort;
import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.NotificationRuleMatch;
import cafe.woden.ircclient.app.api.NotificationRuleMatcherPort;
import cafe.woden.ircclient.app.api.PresenceEvent;
import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.UserCommandAliasEngine;
import cafe.woden.ircclient.app.outbound.OutboundCommandDispatcher;
import cafe.woden.ircclient.app.outbound.OutboundDccCommandService;
import cafe.woden.ircclient.app.state.AwayRoutingState;
import cafe.woden.ircclient.app.state.ChatHistoryRequestRoutingState;
import cafe.woden.ircclient.app.state.CtcpRoutingState;
import cafe.woden.ircclient.app.state.CtcpRoutingState.PendingCtcp;
import cafe.woden.ircclient.app.state.JoinRoutingState;
import cafe.woden.ircclient.app.state.LabeledResponseRoutingState;
import cafe.woden.ircclient.app.state.ModeRoutingState;
import cafe.woden.ircclient.app.state.PendingEchoMessageState;
import cafe.woden.ircclient.app.state.PendingInviteState;
import cafe.woden.ircclient.app.state.WhoisRoutingState;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.UserListStore;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import io.reactivex.rxjava3.core.Flowable;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** App mediator. */
@Component
@Lazy
@ApplicationLayer
public class IrcMediator implements MediatorControlPort {
  private static final Logger log = LoggerFactory.getLogger(IrcMediator.class);
  private static final Duration LABELED_RESPONSE_CORRELATION_WINDOW = Duration.ofMinutes(2);
  private static final Duration LABELED_RESPONSE_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration PENDING_ECHO_TIMEOUT = Duration.ofSeconds(45);
  private static final int PENDING_ECHO_TIMEOUT_BATCH_MAX = 64;
  private static final long NETSPLIT_NOTIFY_DEBOUNCE_MS = 20_000L;
  private static final int NETSPLIT_NOTIFY_MAX_KEYS = 512;

  private final IrcClientService irc;
  private final UiPort ui;
  private final CommandParser commandParser;
  private final UserCommandAliasEngine userCommandAliasEngine;
  private final ServerRegistry serverRegistry;
  private final RuntimeConfigStore runtimeConfig;
  private final ConnectionCoordinator connectionCoordinator;
  private final MediatorConnectionSubscriptionBinder mediatorConnectionSubscriptionBinder;
  private final MediatorUiSubscriptionBinder mediatorUiSubscriptionBinder;
  private final MediatorHistoryIngestOrchestrator mediatorHistoryIngestOrchestrator;
  private final OutboundCommandDispatcher outboundCommandDispatcher;
  private final OutboundDccCommandService outboundDccCommandService;
  private final TargetCoordinator targetCoordinator;
  private final UiSettingsPort uiSettingsPort;
  private final TrayNotificationsPort trayNotificationService;
  private final UserInfoEnrichmentService userInfoEnrichmentService;
  private final UserListStore userListStore;
  private final InboundIgnorePolicyPort inboundIgnorePolicy;
  private final CompositeDisposable disposables = new CompositeDisposable();
  private final WhoisRoutingState whoisRoutingState;
  private final CtcpRoutingState ctcpRoutingState;
  private final ModeRoutingState modeRoutingState;
  private final AwayRoutingState awayRoutingState;
  private final ChatHistoryRequestRoutingState chatHistoryRequestRoutingState;
  private final JoinRoutingState joinRoutingState;
  private final LabeledResponseRoutingState labeledResponseRoutingState;
  private final PendingEchoMessageState pendingEchoMessageState;
  private final PendingInviteState pendingInviteState;
  private final InboundModeEventHandler inboundModeEventHandler;
  private final IrcEventNotifierPort ircEventNotifierPort;
  private final InterceptorIngestPort interceptorIngestPort;
  private final MonitorFallbackPort monitorFallbackPort;

  private final NotificationRuleMatcherPort notificationRuleMatcherPort;

  private final java.util.concurrent.atomic.AtomicBoolean started =
      new java.util.concurrent.atomic.AtomicBoolean(false);

  // Dedup cache
  private final Map<String, TypingLogState> lastTypingByKey = new ConcurrentHashMap<>();
  private final Map<String, Long> lastNetsplitNotifyAtMs = new ConcurrentHashMap<>();
  private static final long TYPING_LOG_DEDUP_MS = 5_000;
  private static final int TYPING_LOG_MAX_KEYS = 512;

  private record TypingLogState(String state, long atMs) {}

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
      UserCommandAliasEngine userCommandAliasEngine,
      ServerRegistry serverRegistry,
      RuntimeConfigStore runtimeConfig,
      ConnectionCoordinator connectionCoordinator,
      MediatorConnectionSubscriptionBinder mediatorConnectionSubscriptionBinder,
      MediatorUiSubscriptionBinder mediatorUiSubscriptionBinder,
      MediatorHistoryIngestOrchestrator mediatorHistoryIngestOrchestrator,
      OutboundCommandDispatcher outboundCommandDispatcher,
      OutboundDccCommandService outboundDccCommandService,
      TargetCoordinator targetCoordinator,
      UiSettingsPort uiSettingsPort,
      TrayNotificationsPort trayNotificationService,
      NotificationRuleMatcherPort notificationRuleMatcherPort,
      UserInfoEnrichmentService userInfoEnrichmentService,
      UserListStore userListStore,
      WhoisRoutingState whoisRoutingState,
      CtcpRoutingState ctcpRoutingState,
      ModeRoutingState modeRoutingState,
      AwayRoutingState awayRoutingState,
      ChatHistoryRequestRoutingState chatHistoryRequestRoutingState,
      JoinRoutingState joinRoutingState,
      LabeledResponseRoutingState labeledResponseRoutingState,
      PendingEchoMessageState pendingEchoMessageState,
      PendingInviteState pendingInviteState,
      InboundModeEventHandler inboundModeEventHandler,
      IrcEventNotifierPort ircEventNotifierPort,
      InterceptorIngestPort interceptorIngestPort,
      InboundIgnorePolicyPort inboundIgnorePolicy,
      MonitorFallbackPort monitorFallbackPort) {

    this.irc = irc;
    this.ui = ui;
    this.commandParser = commandParser;
    this.userCommandAliasEngine = userCommandAliasEngine;
    this.serverRegistry = serverRegistry;
    this.runtimeConfig = runtimeConfig;
    this.connectionCoordinator = connectionCoordinator;
    this.mediatorConnectionSubscriptionBinder = mediatorConnectionSubscriptionBinder;
    this.mediatorUiSubscriptionBinder = mediatorUiSubscriptionBinder;
    this.mediatorHistoryIngestOrchestrator = mediatorHistoryIngestOrchestrator;
    this.outboundCommandDispatcher = outboundCommandDispatcher;
    this.outboundDccCommandService = outboundDccCommandService;
    this.targetCoordinator = targetCoordinator;
    this.uiSettingsPort = uiSettingsPort;
    this.trayNotificationService = trayNotificationService;
    this.notificationRuleMatcherPort = notificationRuleMatcherPort;
    this.userInfoEnrichmentService = userInfoEnrichmentService;
    this.userListStore = userListStore;
    this.whoisRoutingState = whoisRoutingState;
    this.ctcpRoutingState = ctcpRoutingState;
    this.modeRoutingState = modeRoutingState;
    this.awayRoutingState = awayRoutingState;
    this.chatHistoryRequestRoutingState = chatHistoryRequestRoutingState;
    this.joinRoutingState = joinRoutingState;
    this.labeledResponseRoutingState = labeledResponseRoutingState;
    this.pendingEchoMessageState = pendingEchoMessageState;
    this.pendingInviteState = pendingInviteState;
    this.inboundModeEventHandler = inboundModeEventHandler;
    this.ircEventNotifierPort = ircEventNotifierPort;
    this.interceptorIngestPort = interceptorIngestPort;
    this.inboundIgnorePolicy = inboundIgnorePolicy;
    this.monitorFallbackPort = monitorFallbackPort;
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
        this::handleOutgoingLine);
    bindIrcEventSubscriptions();
    bindLabeledResponseTimeoutTicker();
    bindIrcv3CapabilityToggleSubscriptions();
    mediatorConnectionSubscriptionBinder.bind(
        ui, connectionCoordinator, targetCoordinator, serverRegistry, disposables);
  }

  private void bindIrcEventSubscriptions() {
    disposables.add(
        irc.events()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                this::onServerIrcEvent,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(irc-error)", err.toString())));
  }

  private void bindLabeledResponseTimeoutTicker() {
    disposables.add(
        Flowable.interval(5, TimeUnit.SECONDS)
            .observeOn(AppSchedulers.edt())
            .subscribe(
                tick -> {
                  handleLabeledRequestTimeouts();
                  handlePendingEchoTimeouts();
                },
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(),
                        "(label-timeout)",
                        String.valueOf(err))));
  }

  private void bindIrcv3CapabilityToggleSubscriptions() {
    disposables.add(
        ui.ircv3CapabilityToggleRequests()
            .observeOn(AppSchedulers.edt())
            .subscribe(
                this::handleIrcv3CapabilityToggleRequest,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(ui-error)", String.valueOf(err))));
  }

  private void handleUserActionRequest(UserActionRequest req) {
    if (req == null) return;
    TargetRef ctx =
        req.contextTarget() != null ? req.contextTarget() : targetCoordinator.getActiveTarget();
    if (ctx == null) ctx = targetCoordinator.safeStatusTarget();
    final var fCtx = ctx;
    String sid = ctx.serverId();
    String nick = req.nick() == null ? "" : req.nick().trim();
    if (sid == null || sid.isBlank() || nick.isBlank()) return;

    switch (req.action()) {
      case OPEN_QUERY ->
          targetCoordinator.openPrivateConversation(new PrivateMessageRequest(sid, nick));

      case WHOIS -> {
        whoisRoutingState.put(sid, nick, ctx);
        ui.appendStatus(ctx, "(whois)", "Requesting WHOIS for " + nick + "...");
        Disposable d =
            irc.whois(sid, nick)
                .subscribe(() -> {}, err -> ui.appendError(fCtx, "(whois)", err.toString()));
        disposables.add(d);
      }

      case CTCP_VERSION -> {
        ctcpRoutingState.put(sid, nick, "VERSION", null, ctx);
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " VERSION");
        disposables.add(
            irc.sendPrivateMessage(sid, nick, "\u0001VERSION\u0001")
                .subscribe(() -> {}, err -> ui.appendError(fCtx, "(ctcp)", err.toString())));
      }

      case CTCP_PING -> {
        String token = Long.toString(System.currentTimeMillis());
        ctcpRoutingState.put(sid, nick, "PING", token, ctx);
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " PING");
        disposables.add(
            irc.sendPrivateMessage(sid, nick, "\u0001PING " + token + "\u0001")
                .subscribe(() -> {}, err -> ui.appendError(fCtx, "(ctcp)", err.toString())));
      }

      case CTCP_TIME -> {
        ctcpRoutingState.put(sid, nick, "TIME", null, ctx);
        ui.appendStatus(ctx, "(ctcp)", "\u2192 " + nick + " TIME");
        disposables.add(
            irc.sendPrivateMessage(sid, nick, "\u0001TIME\u0001")
                .subscribe(() -> {}, err -> ui.appendError(fCtx, "(ctcp)", err.toString())));
      }

      case OP -> handleNickModeUserAction(ctx, nick, "+o");

      case DEOP -> handleNickModeUserAction(ctx, nick, "-o");

      case VOICE -> handleNickModeUserAction(ctx, nick, "+v");

      case DEVOICE -> handleNickModeUserAction(ctx, nick, "-v");

      case KICK -> handleKickUserAction(ctx, nick);

      case BAN -> handleBanUserAction(ctx, nick);
    }
  }

  private void handleNickModeUserAction(TargetRef ctx, String nick, String mode) {
    if (ctx == null || !ctx.isChannel()) return;
    String line = "MODE " + ctx.target() + " " + mode + " " + nick;
    sendUserActionRaw(ctx, "(mode)", "(mode-error)", line);
  }

  private void handleKickUserAction(TargetRef ctx, String nick) {
    if (ctx == null || !ctx.isChannel()) return;
    String line = "KICK " + ctx.target() + " " + nick;
    sendUserActionRaw(ctx, "(kick)", "(kick-error)", line);
  }

  private void handleBanUserAction(TargetRef ctx, String nick) {
    if (ctx == null || !ctx.isChannel()) return;
    String mask = looksLikeMask(nick) ? nick : (nick + "!*@*");
    String line = "MODE " + ctx.target() + " +b " + mask;
    sendUserActionRaw(ctx, "(mode)", "(mode-error)", line);
  }

  private void sendUserActionRaw(TargetRef out, String statusTag, String errorTag, String line) {
    if (out == null) return;
    String sid = Objects.toString(out.serverId(), "").trim();
    String sendLine = Objects.toString(line, "").trim();
    if (sid.isBlank() || sendLine.isBlank()) return;

    TargetRef status = new TargetRef(sid, "status");
    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(status, "(conn)", "Not connected");
      return;
    }
    if (containsCrlf(sendLine)) {
      ui.appendStatus(status, statusTag, "Refusing to send multi-line input.");
      return;
    }

    ensureTargetExists(out);
    ui.appendStatus(out, statusTag, "\u2192 " + sendLine);
    disposables.add(
        irc.sendRaw(sid, sendLine)
            .subscribe(() -> {}, err -> ui.appendError(status, errorTag, String.valueOf(err))));
  }

  private static boolean containsCrlf(String s) {
    if (s == null || s.isEmpty()) return false;
    return s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
  }

  private static boolean looksLikeMask(String s) {
    if (s == null) return false;
    return s.indexOf('!') >= 0 || s.indexOf('@') >= 0 || s.indexOf('*') >= 0 || s.indexOf('?') >= 0;
  }

  private void handleIrcv3CapabilityToggleRequest(Ircv3CapabilityToggleRequest req) {
    if (req == null) return;

    String sid = Objects.toString(req.serverId(), "").trim();
    String cap = normalizeIrcv3CapabilityKey(req.capability());
    if (sid.isEmpty() || cap.isEmpty()) return;

    boolean enabled = req.enabled();
    runtimeConfig.rememberIrcv3CapabilityEnabled(cap, enabled);
    TargetRef status = new TargetRef(sid, "status");

    if (!connectionCoordinator.isConnected(sid)) {
      ui.appendStatus(
          status,
          "(cap)",
          "Saved "
              + cap
              + " as "
              + (enabled ? "enabled" : "disabled")
              + "; connect/reconnect to apply.");
      return;
    }

    ui.appendStatus(
        status,
        "(cap)",
        "Requesting CAP " + (enabled ? "enable" : "disable") + " for " + cap + "...");
    disposables.add(
        irc.setIrcv3CapabilityEnabled(sid, cap, enabled)
            .subscribe(() -> {}, err -> ui.appendError(status, "(cap)", String.valueOf(err))));
  }

  private static String normalizeIrcv3CapabilityKey(String capability) {
    return Objects.toString(capability, "").trim().toLowerCase(Locale.ROOT);
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

  private InboundIgnorePolicyPort.Decision decideInbound(
      String sid,
      String from,
      boolean isCtcp,
      String inboundChannel,
      String inboundText,
      String... levels) {
    if (inboundIgnorePolicy == null) return InboundIgnorePolicyPort.Decision.ALLOW;
    String f = Objects.toString(from, "").trim();
    if (f.isEmpty()) return InboundIgnorePolicyPort.Decision.ALLOW;
    if ("server".equalsIgnoreCase(f)) return InboundIgnorePolicyPort.Decision.ALLOW;
    String ch = Objects.toString(inboundChannel, "").trim();
    String text = Objects.toString(inboundText, "");
    List<String> levelList = (levels == null || levels.length == 0) ? List.of() : List.of(levels);
    return inboundIgnorePolicy.decide(sid, f, null, isCtcp, levelList, ch, text);
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
      Map<String, String> ircv3Tags) {
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
      ui.appendSpoilerChatAt(status, at, "(notice) " + from, text, messageId, ircv3Tags);
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

  @Override
  public void connectAll() {
    connectionCoordinator.connectAll();
  }

  public void disconnectAll() {
    connectionCoordinator.disconnectAll();
  }

  private void handleOutgoingLine(String raw) {
    TargetRef at = targetCoordinator.getActiveTarget();
    TargetRef ctx = (at != null) ? at : targetCoordinator.safeStatusTarget();

    UserCommandAliasEngine.ExpansionResult expanded = userCommandAliasEngine.expand(raw, ctx);

    for (String warning : expanded.warnings()) {
      if (warning == null || warning.isBlank()) continue;
      TargetRef out =
          (ctx != null)
              ? new TargetRef(ctx.serverId(), "status")
              : targetCoordinator.safeStatusTarget();
      ui.appendStatus(out, "(alias)", warning);
    }

    for (String line : expanded.lines()) {
      if (line == null || line.isBlank()) continue;
      outboundCommandDispatcher.dispatch(disposables, commandParser.parse(line));
    }
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
      if (e instanceof IrcEvent.Connected ev) {
        ui.setServerConnectedIdentity(sid, ev.serverHost(), ev.serverPort(), ev.nick(), ev.at());
      }
      connectionCoordinator.handleConnectivityEvent(sid, e, targetCoordinator.getActiveTarget());
      if (e instanceof IrcEvent.Disconnected) {
        failPendingEchoesForServer(sid, "disconnected before echo");
        ui.clearPrivateMessageOnlineStates(sid);
        targetCoordinator.onServerDisconnected(sid);
        whoisRoutingState.clearServer(sid);
        ctcpRoutingState.clearServer(sid);
        modeRoutingState.clearServer(sid);
        awayRoutingState.clearServer(sid);
        chatHistoryRequestRoutingState.clearServer(sid);
        joinRoutingState.clearServer(sid);
        labeledResponseRoutingState.clearServer(sid);
        pendingInviteState.clearServer(sid);
        inboundModeEventHandler.clearServer(sid);
        clearNetsplitDebounceForServer(sid);
      }
      targetCoordinator.refreshInputEnabledForActiveTarget();
      return;
    }

    switch (e) {
      case IrcEvent.NickChanged ev -> {
        irc.currentNick(sid)
            .ifPresent(
                currentNick -> {
                  if (!Objects.equals(currentNick, ev.oldNick())
                      && !Objects.equals(currentNick, ev.newNick())) {
                    ui.appendNotice(
                        status, "(nick)", ev.oldNick() + " is now known as " + ev.newNick());
                  } else {
                    ui.appendStatus(status, "(nick)", "Now known as " + ev.newNick());
                    ui.setChatCurrentNick(sid, ev.newNick());
                  }
                });
      }
      case IrcEvent.ChannelMessage ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        TargetRef active = targetCoordinator.getActiveTarget();
        NotificationRuleMatch ruleMatch = firstRuleMatchForChannel(sid, chan, ev.from(), ev.text());

        userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());

        InboundIgnorePolicyPort.Decision decision =
            decideInbound(sid, ev.from(), false, ev.channel(), ev.text(), "MSGS", "PUBLIC");
        if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) return;

        if (tryResolvePendingEchoChannelMessage(sid, chan, active, ev)) {
          return;
        }

        if (maybeApplyMessageEditFromTaggedLine(
            sid, chan, ev.at(), ev.from(), ev.text(), ev.messageId(), ev.ircv3Tags())) {
          return;
        }

        if (decision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER) {
          postTo(
              chan,
              active,
              true,
              d ->
                  ui.appendSpoilerChatAt(
                      d, ev.at(), ev.from(), ev.text(), ev.messageId(), ev.ircv3Tags()));
        } else {
          postTo(
              chan,
              active,
              true,
              d ->
                  ui.appendChatAt(
                      d,
                      ev.at(),
                      ev.from(),
                      ev.text(),
                      false,
                      ev.messageId(),
                      ev.ircv3Tags(),
                      ruleMatch != null ? ruleMatch.highlightColor() : null));
        }

        recordRuleMatchIfPresent(chan, active, ev.from(), ev.text(), ruleMatch);
        recordInterceptorEvent(
            sid,
            ev.channel(),
            ev.from(),
            learnedHostmaskForNick(sid, ev.from()),
            ev.text(),
            InterceptorEventType.MESSAGE);

        boolean mention = containsSelfMention(sid, ev.from(), ev.text());
        if (mention) {
          recordInterceptorEvent(
              sid,
              ev.channel(),
              ev.from(),
              learnedHostmaskForNick(sid, ev.from()),
              ev.text(),
              InterceptorEventType.HIGHLIGHT);
          recordMentionHighlight(chan, active, ev.from(), ev.text());

          try {
            trayNotificationService.notifyHighlight(sid, ev.channel(), ev.from(), ev.text());
          } catch (Exception ignored) {
          }
        }
      }
      case IrcEvent.ChannelAction ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        TargetRef active = targetCoordinator.getActiveTarget();
        NotificationRuleMatch ruleMatch =
            firstRuleMatchForChannel(sid, chan, ev.from(), ev.action());

        userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());

        InboundIgnorePolicyPort.Decision decision =
            decideInbound(sid, ev.from(), true, ev.channel(), ev.action(), "ACTIONS", "CTCPS");
        if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) return;

        if (decision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER) {
          postTo(
              chan,
              active,
              true,
              d ->
                  ui.appendSpoilerChatAt(
                      d, ev.at(), ev.from(), "* " + ev.action(), ev.messageId(), ev.ircv3Tags()));
        } else {
          postTo(
              chan,
              active,
              true,
              d ->
                  ui.appendActionAt(
                      d,
                      ev.at(),
                      ev.from(),
                      ev.action(),
                      false,
                      ev.messageId(),
                      ev.ircv3Tags(),
                      ruleMatch != null ? ruleMatch.highlightColor() : null));
        }

        recordRuleMatchIfPresent(chan, active, ev.from(), ev.action(), ruleMatch);
        recordInterceptorEvent(
            sid,
            ev.channel(),
            ev.from(),
            learnedHostmaskForNick(sid, ev.from()),
            ev.action(),
            InterceptorEventType.ACTION);

        boolean mention = containsSelfMention(sid, ev.from(), ev.action());
        if (mention) {
          recordInterceptorEvent(
              sid,
              ev.channel(),
              ev.from(),
              learnedHostmaskForNick(sid, ev.from()),
              ev.action(),
              InterceptorEventType.HIGHLIGHT);
          recordMentionHighlight(chan, active, ev.from(), "* " + ev.action());

          try {
            trayNotificationService.notifyHighlight(
                sid, ev.channel(), ev.from(), "* " + ev.action());
          } catch (Exception ignored) {
          }
        }
      }
      case IrcEvent.ChannelModeChanged ev -> {
        inboundModeEventHandler.handleChannelModeChanged(sid, ev);
        maybeNotifyModeEvents(sid, ev);
        recordInterceptorEvent(
            sid,
            ev.channel(),
            ev.by(),
            learnedHostmaskForNick(sid, ev.by()),
            ev.details(),
            InterceptorEventType.MODE);
      }

      case IrcEvent.ChannelModesListed ev -> {
        inboundModeEventHandler.handleChannelModesListed(sid, ev);
      }

      case IrcEvent.ChannelTopicUpdated ev -> {
        inboundModeEventHandler.onChannelTopicUpdated(sid, ev.channel());
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.setChannelTopic(chan, ev.topic());
        String channel = Objects.toString(ev.channel(), "").trim();
        String topic = Objects.toString(ev.topic(), "").trim();
        String body;
        if (topic.isEmpty()) {
          body = "Topic cleared in " + channel;
        } else {
          body = "Topic changed in " + channel + ": " + topic;
        }
        notifyIrcEvent(
            IrcEventNotificationRule.EventType.TOPIC_CHANGED,
            sid,
            channel,
            null,
            "Topic changed" + (channel.isEmpty() ? "" : " in " + channel),
            body);
        recordInterceptorEvent(
            sid,
            channel,
            "server",
            "",
            topic.isEmpty() ? "(topic cleared)" : topic,
            InterceptorEventType.TOPIC);
      }

      case IrcEvent.PrivateMessage ev -> {
        boolean fromSelf = isFromSelf(sid, ev.from());
        String peer = ev.from();
        if (fromSelf) {
          String dest = ev.ircv3Tags().get("ircafe/pm-target");
          if (dest != null && !dest.isBlank()) {
            peer = dest;
          }
        }
        TargetRef pm = new TargetRef(sid, peer);
        boolean allowAutoOpen = targetCoordinator.allowPrivateAutoOpenFromInbound(pm, fromSelf);

        // Suppress our own internal ZNC playback control lines if they get echoed back.
        if (fromSelf
            && "*playback".equalsIgnoreCase(peer)
            && ev.text() != null
            && ev.text().toLowerCase(java.util.Locale.ROOT).startsWith("play ")) {
          return;
        }

        if (!fromSelf) {
          userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());
          markPrivateMessagePeerOnline(sid, ev.from());
        }

        ParsedCtcp ctcp = parseCtcp(ev.text());
        if (!fromSelf && ctcp != null && "DCC".equals(ctcp.commandUpper())) {
          InboundIgnorePolicyPort.Decision dccDecision =
              decideInbound(sid, ev.from(), true, "", ctcp.arg(), "DCC", "CTCPS");
          if (dccDecision == InboundIgnorePolicyPort.Decision.HARD_DROP) return;

          boolean dccHandled =
              outboundDccCommandService.handleInboundDccOffer(
                  ev.at(),
                  sid,
                  ev.from(),
                  ctcp.arg(),
                  dccDecision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER);
          if (dccHandled) return;
        }

        InboundIgnorePolicyPort.Decision decision =
            fromSelf
                ? InboundIgnorePolicyPort.Decision.ALLOW
                : decideInbound(sid, ev.from(), false, "", ev.text(), "MSGS");
        if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) return;

        if (tryResolvePendingEchoPrivateMessage(sid, pm, ev, allowAutoOpen)) {
          return;
        }

        if (maybeApplyMessageEditFromTaggedLine(
            sid, pm, ev.at(), ev.from(), ev.text(), ev.messageId(), ev.ircv3Tags())) {
          return;
        }

        if (decision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER) {
          if (allowAutoOpen) {
            postTo(
                pm,
                true,
                d ->
                    ui.appendSpoilerChatAt(
                        d, ev.at(), ev.from(), ev.text(), ev.messageId(), ev.ircv3Tags()));
          } else {
            ui.appendSpoilerChatAt(
                pm, ev.at(), ev.from(), ev.text(), ev.messageId(), ev.ircv3Tags());
          }
        } else {
          if (allowAutoOpen) {
            postTo(
                pm,
                true,
                d ->
                    ui.appendChatAt(
                        d,
                        ev.at(),
                        ev.from(),
                        ev.text(),
                        fromSelf,
                        ev.messageId(),
                        ev.ircv3Tags()));
          } else {
            ui.appendChatAt(
                pm, ev.at(), ev.from(), ev.text(), fromSelf, ev.messageId(), ev.ircv3Tags());
          }
        }

        recordInterceptorEvent(
            sid,
            "pm:" + Objects.toString(peer, "").trim(),
            ev.from(),
            learnedHostmaskForNick(sid, ev.from()),
            ev.text(),
            InterceptorEventType.PRIVATE_MESSAGE);

        if (!fromSelf) {
          String fromNick = Objects.toString(ev.from(), "").trim();
          String title =
              fromNick.isEmpty() ? "Private message" : ("Private message from " + fromNick);
          String body = Objects.toString(ev.text(), "").trim();
          boolean customPmNotified =
              notifyIrcEvent(
                  IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED,
                  sid,
                  null,
                  fromNick,
                  title,
                  body);
          boolean pmRulesEnabled =
              ircEventNotifierPort != null
                  && ircEventNotifierPort.hasEnabledRuleFor(
                      IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED);
          if (!customPmNotified && !pmRulesEnabled) {
            try {
              trayNotificationService.notifyPrivateMessage(sid, ev.from(), ev.text());
            } catch (Exception ignored) {
            }
          }
        }
      }

      case IrcEvent.PrivateAction ev -> {
        boolean fromSelf = isFromSelf(sid, ev.from());
        String peer = ev.from();
        if (fromSelf) {
          String dest = ev.ircv3Tags().get("ircafe/pm-target");
          if (dest != null && !dest.isBlank()) {
            peer = dest;
          }
        }
        TargetRef pm = new TargetRef(sid, peer);
        boolean allowAutoOpen = targetCoordinator.allowPrivateAutoOpenFromInbound(pm, fromSelf);

        if (!fromSelf) {
          userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());
          markPrivateMessagePeerOnline(sid, ev.from());
        }

        InboundIgnorePolicyPort.Decision decision =
            fromSelf
                ? InboundIgnorePolicyPort.Decision.ALLOW
                : decideInbound(sid, ev.from(), true, "", ev.action(), "ACTIONS", "CTCPS");
        if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) return;

        if (decision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER) {
          if (allowAutoOpen) {
            postTo(
                pm,
                true,
                d ->
                    ui.appendSpoilerChatAt(
                        d, ev.at(), ev.from(), "* " + ev.action(), ev.messageId(), ev.ircv3Tags()));
          } else {
            ui.appendSpoilerChatAt(
                pm, ev.at(), ev.from(), "* " + ev.action(), ev.messageId(), ev.ircv3Tags());
          }
        } else {
          if (allowAutoOpen) {
            postTo(
                pm,
                true,
                d ->
                    ui.appendActionAt(
                        d,
                        ev.at(),
                        ev.from(),
                        ev.action(),
                        fromSelf,
                        ev.messageId(),
                        ev.ircv3Tags()));
          } else {
            ui.appendActionAt(
                pm, ev.at(), ev.from(), ev.action(), fromSelf, ev.messageId(), ev.ircv3Tags());
          }
        }

        recordInterceptorEvent(
            sid,
            "pm:" + Objects.toString(peer, "").trim(),
            ev.from(),
            learnedHostmaskForNick(sid, ev.from()),
            ev.action(),
            InterceptorEventType.PRIVATE_ACTION);

        if (!fromSelf) {
          String fromNick = Objects.toString(ev.from(), "").trim();
          String title =
              fromNick.isEmpty() ? "Private action" : ("Private action from " + fromNick);
          String body = "* " + Objects.toString(ev.action(), "").trim();
          boolean customPmNotified =
              notifyIrcEvent(
                  IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED,
                  sid,
                  null,
                  fromNick,
                  title,
                  body);
          boolean pmRulesEnabled =
              ircEventNotifierPort != null
                  && ircEventNotifierPort.hasEnabledRuleFor(
                      IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED);
          if (!customPmNotified && !pmRulesEnabled) {
            try {
              trayNotificationService.notifyPrivateMessage(sid, ev.from(), "* " + ev.action());
            } catch (Exception ignored) {
            }
          }
        }
      }
      case IrcEvent.Notice ev -> {
        boolean fromSelf = isFromSelf(sid, ev.from());
        markPrivateMessagePeerOnline(sid, ev.from());
        boolean isCtcp = parseCtcp(ev.text()) != null;
        String noticeChannel = "";
        String rawNoticeTargetForIgnore = Objects.toString(ev.target(), "").trim();
        if (!rawNoticeTargetForIgnore.isEmpty()) {
          TargetRef targetRef = new TargetRef(sid, rawNoticeTargetForIgnore);
          if (targetRef.isChannel()) {
            noticeChannel = targetRef.target();
          }
        }
        InboundIgnorePolicyPort.Decision d =
            isCtcp
                ? decideInbound(sid, ev.from(), true, noticeChannel, ev.text(), "NOTICES", "CTCPS")
                : decideInbound(sid, ev.from(), false, noticeChannel, ev.text(), "NOTICES");
        boolean spoiler = d == InboundIgnorePolicyPort.Decision.SOFT_SPOILER;
        boolean suppress = d == InboundIgnorePolicyPort.Decision.HARD_DROP;

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

        if (maybeApplyMessageEditFromTaggedLine(
            sid, dest, ev.at(), ev.from(), ev.text(), ev.messageId(), ev.ircv3Tags())) {
          return;
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

        String noticeChannelForInterceptor = "status";
        String rawNoticeTarget = Objects.toString(ev.target(), "").trim();
        if (!rawNoticeTarget.isEmpty()) {
          TargetRef targetRef = new TargetRef(sid, rawNoticeTarget);
          if (targetRef.isChannel()) {
            noticeChannelForInterceptor = targetRef.target();
          }
        }
        recordInterceptorEvent(
            sid,
            noticeChannelForInterceptor,
            ev.from(),
            learnedHostmaskForNick(sid, ev.from()),
            ev.text(),
            InterceptorEventType.NOTICE);

        if (!fromSelf && !suppress) {
          String fromNick = Objects.toString(ev.from(), "").trim();
          String title = fromNick.isEmpty() ? "Notice" : ("Notice from " + fromNick);
          String body = Objects.toString(ev.text(), "").trim();
          String channel = null;
          String rawTarget = Objects.toString(ev.target(), "").trim();
          if (!rawTarget.isEmpty()) {
            TargetRef targetRef = new TargetRef(sid, rawTarget);
            if (targetRef.isChannel()) {
              channel = targetRef.target();
            }
          }
          notifyIrcEvent(
              IrcEventNotificationRule.EventType.NOTICE_RECEIVED,
              sid,
              channel,
              fromNick,
              title,
              body);
        }
      }
      case IrcEvent.WallopsReceived ev -> {
        TargetRef dest = status != null ? status : safeStatusTarget();
        String from = Objects.toString(ev.from(), "").trim();
        if (from.isEmpty()) from = "server";
        String body = Objects.toString(ev.text(), "").trim();
        if (body.isEmpty()) body = "(empty WALLOPS)";

        String fromFinal = from;
        String rendered = fromFinal + ": " + body;
        postTo(dest, true, d -> ui.appendStatusAt(d, ev.at(), "(wallops)", rendered));
        recordInterceptorEvent(
            sid,
            "status",
            fromFinal,
            learnedHostmaskForNick(sid, fromFinal),
            body,
            InterceptorEventType.SERVER);

        notifyIrcEvent(
            IrcEventNotificationRule.EventType.WALLOPS_RECEIVED,
            sid,
            null,
            fromFinal,
            fromFinal.equalsIgnoreCase("server") ? "WALLOPS" : ("WALLOPS from " + fromFinal),
            body);
      }
      case IrcEvent.ServerTimeNotNegotiated ev -> {
        ui.appendStatus(status, "(ircv3)", ev.message());
        recordInterceptorEvent(
            sid, "status", "server", "", ev.message(), InterceptorEventType.SERVER);
      }
      case IrcEvent.StandardReply ev -> {
        handleStandardReply(sid, status, ev);
        recordInterceptorEvent(
            sid,
            "status",
            "server",
            "",
            Objects.toString(ev.description(), "").trim(),
            InterceptorEventType.SERVER);
      }
      case IrcEvent.ChannelListStarted ev -> {
        ui.beginChannelList(sid, ev.banner());
      }
      case IrcEvent.ChannelListEntry ev -> {
        ui.appendChannelListEntry(sid, ev.channel(), ev.visibleUsers(), ev.topic());
      }
      case IrcEvent.ChannelListEnded ev -> {
        ui.endChannelList(sid, ev.summary());
      }
      case IrcEvent.ChannelBanListStarted ev -> {
        ui.beginChannelBanList(sid, ev.channel());
      }
      case IrcEvent.ChannelBanListEntry ev -> {
        ui.appendChannelBanListEntry(
            sid, ev.channel(), ev.mask(), ev.setBy(), ev.setAtEpochSeconds());
      }
      case IrcEvent.ChannelBanListEnded ev -> {
        ui.endChannelBanList(sid, ev.channel(), ev.summary());
      }
      case cafe.woden.ircclient.irc.IrcEvent.ServerResponseLine ev -> {
        handleServerResponseLine(sid, status, ev);
        String rawLine = Objects.toString(ev.rawLine(), "").trim();
        if (rawLine.isEmpty()) rawLine = Objects.toString(ev.message(), "").trim();
        recordInterceptorEvent(sid, "status", "server", "", rawLine, InterceptorEventType.SERVER);
      }
      case IrcEvent.ChatHistoryBatchReceived ev -> {
        mediatorHistoryIngestOrchestrator.onChatHistoryBatchReceived(sid, ev);
      }

      case IrcEvent.ZncPlaybackBatchReceived ev -> {
        mediatorHistoryIngestOrchestrator.onZncPlaybackBatchReceived(sid, ev);
      }
      case IrcEvent.CtcpRequestReceived ev -> {
        markPrivateMessagePeerOnline(sid, ev.from());
        String ctcpText =
            Objects.toString(ev.command(), "").trim()
                + (Objects.toString(ev.argument(), "").isBlank()
                    ? ""
                    : (" " + Objects.toString(ev.argument(), "").trim()));
        InboundIgnorePolicyPort.Decision decision =
            decideInbound(sid, ev.from(), true, ev.channel(), ctcpText, "CTCPS");
        if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) return;

        TargetRef dest;
        if (uiSettingsPort.get().ctcpRequestsInActiveTargetEnabled()) {
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

        StringBuilder sb =
            new StringBuilder()
                .append("\u2190 ")
                .append(ev.from())
                .append(" CTCP ")
                .append(ev.command());
        if (ev.argument() != null && !ev.argument().isBlank()) sb.append(' ').append(ev.argument());
        if (ev.channel() != null && !ev.channel().isBlank()) sb.append(" in ").append(ev.channel());
        final String rendered = sb.toString();

        if (decision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER) {
          postTo(dest, true, d -> ui.appendSpoilerChatAt(d, ev.at(), "(ctcp)", rendered));
        } else {
          postTo(dest, true, d -> ui.appendStatusAt(d, ev.at(), "(ctcp)", rendered));
        }
        recordInterceptorEvent(
            sid,
            Objects.toString(ev.channel(), "").trim().isEmpty() ? "status" : ev.channel(),
            ev.from(),
            learnedHostmaskForNick(sid, ev.from()),
            rendered,
            InterceptorEventType.CTCP);

        String fromNick = Objects.toString(ev.from(), "").trim();
        String channel = Objects.toString(ev.channel(), "").trim();
        if (channel.isBlank()) channel = null;
        String command = Objects.toString(ev.command(), "").trim();
        String argument = Objects.toString(ev.argument(), "").trim();
        String title =
            fromNick.isEmpty()
                ? "CTCP request received"
                : ("CTCP from " + fromNick + (channel == null ? "" : (" in " + channel)));
        String body = command.isEmpty() ? "CTCP request" : command;
        if (!argument.isEmpty()) body = body + " " + argument;

        notifyIrcEvent(
            IrcEventNotificationRule.EventType.CTCP_RECEIVED, sid, channel, fromNick, title, body);
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
        postTo(
            dest,
            true,
            d -> {
              ui.appendStatus(d, "(whois)", "WHOIS for " + ev.nick());
              for (String line : ev.lines()) ui.appendStatus(d, "(whois)", line);
            });
      }

      case IrcEvent.InvitedToChannel ev -> {
        TargetRef dest = status;
        String inviter = Objects.toString(ev.from(), "").trim();
        if (inviter.isEmpty()) inviter = "server";
        String channel = Objects.toString(ev.channel(), "").trim();
        if (channel.isEmpty()) {
          String invalidLine = inviter + " sent an invalid invite.";
          postTo(dest, true, d -> ui.appendStatusAt(d, ev.at(), "(invite)", invalidLine));
          recordInterceptorEvent(
              sid,
              "status",
              inviter,
              learnedHostmaskForNick(sid, inviter),
              invalidLine,
              InterceptorEventType.INVITE);
        } else {
          String invitee = Objects.toString(ev.invitee(), "").trim();
          String selfNick = irc.currentNick(sid).orElse("");
          boolean isSelfInvite =
              invitee.isBlank() || (!selfNick.isBlank() && invitee.equalsIgnoreCase(selfNick));

          if (!isSelfInvite) {
            String rendered = inviter + " invited " + invitee + " to " + channel;
            postTo(dest, true, d -> ui.appendStatusAt(d, ev.at(), "(invite-notify)", rendered));
            recordInterceptorEvent(
                sid,
                channel,
                inviter,
                learnedHostmaskForNick(sid, inviter),
                rendered,
                InterceptorEventType.INVITE);
          } else {
            PendingInviteState.RecordResult recorded =
                pendingInviteState.record(
                    ev.at(), sid, channel, inviter, invitee, ev.reason(), ev.inviteNotify());
            PendingInviteState.PendingInvite invite = recorded.invite();
            TargetRef inviteStatus = new TargetRef(sid, "status");

            if (recorded.collapsed()) {
              String rendered =
                  inviter
                      + " invited you to "
                      + channel
                      + " (repeated x"
                      + invite.repeatCount()
                      + ")";
              postTo(dest, true, d -> ui.appendStatusAt(d, ev.at(), "(invite)", rendered));
            } else {
              String reason = Objects.toString(invite.reason(), "").trim();
              String rendered = inviter + " invited you to " + channel + " on " + sid;
              if (!reason.isEmpty()) rendered = rendered + " (" + reason + ")";
              String finalRendered = rendered;
              String actions =
                  "Actions: /invjoin "
                      + invite.id()
                      + " | /join -i"
                      + " | /invignore "
                      + invite.id()
                      + " | /invwhois "
                      + invite.id()
                      + " | /invblock "
                      + invite.id()
                      + " | /invites";

              postTo(
                  dest,
                  true,
                  d -> {
                    ui.appendStatusAt(d, ev.at(), "(invite)", finalRendered);
                    ui.appendStatus(d, "(invite)", actions);
                  });
              recordInterceptorEvent(
                  sid,
                  channel,
                  inviter,
                  learnedHostmaskForNick(sid, inviter),
                  finalRendered,
                  InterceptorEventType.INVITE);

              boolean customInviteNotified =
                  notifyIrcEvent(
                      IrcEventNotificationRule.EventType.INVITE_RECEIVED,
                      sid,
                      channel,
                      inviter,
                      "Invite" + (channel.isBlank() ? "" : " to " + channel),
                      finalRendered);
              boolean inviteRulesEnabled =
                  ircEventNotifierPort != null
                      && ircEventNotifierPort.hasEnabledRuleFor(
                          IrcEventNotificationRule.EventType.INVITE_RECEIVED);
              if (!customInviteNotified && !inviteRulesEnabled) {
                try {
                  trayNotificationService.notifyInvite(sid, channel, inviter, reason);
                } catch (Exception ignored) {
                }
              }

              if (pendingInviteState.inviteAutoJoinEnabled()) {
                if (!connectionCoordinator.isConnected(sid)) {
                  ui.appendStatus(
                      inviteStatus, "(invite)", "Auto-join is enabled, but you are not connected.");
                } else if (containsCrlf(channel)) {
                  ui.appendStatus(
                      inviteStatus, "(invite)", "Refusing to auto-join malformed invite channel.");
                } else {
                  ui.appendStatus(
                      inviteStatus, "(invite)", "Auto-join enabled, joining " + channel + "...");
                  disposables.add(
                      irc.joinChannel(sid, channel)
                          .subscribe(
                              () -> pendingInviteState.remove(invite.id()),
                              err ->
                                  ui.appendError(
                                      inviteStatus, "(invite-error)", String.valueOf(err))));
                }
              }
            }
          }
        }
      }

      case IrcEvent.UserJoinedChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendPresence(chan, PresenceEvent.join(ev.nick()));
        String joinedNick = Objects.toString(ev.nick(), "").trim();
        String body = (joinedNick.isEmpty() ? "Someone" : joinedNick) + " joined " + ev.channel();
        notifyIrcEvent(
            IrcEventNotificationRule.EventType.USER_JOINED,
            sid,
            ev.channel(),
            joinedNick,
            "Join in " + ev.channel(),
            body);
        recordInterceptorEvent(
            sid,
            ev.channel(),
            joinedNick,
            learnedHostmaskForNick(sid, joinedNick),
            body,
            InterceptorEventType.JOIN);
      }

      case IrcEvent.UserPartedChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendPresence(chan, PresenceEvent.part(ev.nick(), ev.reason()));
        String channel = Objects.toString(ev.channel(), "").trim();
        String nick = Objects.toString(ev.nick(), "").trim();
        String reason = Objects.toString(ev.reason(), "").trim();
        String body = (nick.isEmpty() ? "Someone" : nick) + " parted " + channel;
        if (!reason.isEmpty()) body = body + " (" + reason + ")";
        notifyIrcEvent(
            IrcEventNotificationRule.EventType.USER_PARTED,
            sid,
            channel,
            nick,
            "Part in " + channel,
            body);
        recordInterceptorEvent(
            sid, channel, nick, learnedHostmaskForNick(sid, nick), body, InterceptorEventType.PART);
      }

      case IrcEvent.LeftChannel ev -> {
        TargetRef st = new TargetRef(sid, "status");
        String rendered = "You left " + ev.channel();
        String reason = Objects.toString(ev.reason(), "").trim();
        if (!reason.isEmpty()) rendered = rendered + " (" + reason + ")";
        String detachedWarning = reason.isEmpty() ? "Removed from channel by server." : reason;

        ensureTargetExists(st);
        ui.appendStatusAt(st, ev.at(), "(part)", rendered);
        inboundModeEventHandler.onLeftChannel(sid, ev.channel());
        targetCoordinator.onChannelMembershipLost(sid, ev.channel(), true, detachedWarning);
      }

      case IrcEvent.UserKickedFromChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        TargetRef active = targetCoordinator.getActiveTarget();
        String rendered = renderOtherKick(ev.nick(), ev.by(), ev.reason());
        postTo(chan, active, true, d -> ui.appendStatusAt(d, ev.at(), "(kick)", rendered));
        notifyIrcEvent(
            IrcEventNotificationRule.EventType.KICKED,
            sid,
            ev.channel(),
            ev.by(),
            "Kick in " + ev.channel(),
            rendered);
        recordInterceptorEvent(
            sid,
            ev.channel(),
            ev.by(),
            learnedHostmaskForNick(sid, ev.by()),
            rendered,
            InterceptorEventType.KICK);
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
        String by = Objects.toString(ev.by(), "").trim();
        String reason = Objects.toString(ev.reason(), "").trim();
        String detachedWarning = "Kicked" + (by.isEmpty() ? "" : (" by " + by));
        if (!reason.isEmpty()) detachedWarning = detachedWarning + " (" + reason + ")";
        targetCoordinator.onChannelMembershipLost(sid, ev.channel(), true, detachedWarning);
        notifyIrcEvent(
            IrcEventNotificationRule.EventType.YOU_KICKED,
            sid,
            ev.channel(),
            ev.by(),
            "You were kicked from " + ev.channel(),
            rendered);
        recordInterceptorEvent(
            sid,
            ev.channel(),
            ev.by(),
            learnedHostmaskForNick(sid, ev.by()),
            rendered,
            InterceptorEventType.KICK);
      }

      case IrcEvent.UserQuitChannel ev -> {
        markPrivateMessagePeerOffline(sid, ev.nick());
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendPresence(chan, PresenceEvent.quit(ev.nick(), ev.reason()));
        String channel = Objects.toString(ev.channel(), "").trim();
        String nick = Objects.toString(ev.nick(), "").trim();
        String reason = Objects.toString(ev.reason(), "").trim();
        String body = (nick.isEmpty() ? "Someone" : nick) + " quit";
        if (!reason.isEmpty()) body = body + " (" + reason + ")";
        if (!channel.isEmpty()) body = body + " while in " + channel;
        notifyIrcEvent(
            IrcEventNotificationRule.EventType.USER_QUIT,
            sid,
            channel,
            nick,
            "Quit" + (channel.isEmpty() ? "" : " in " + channel),
            body);
        recordInterceptorEvent(
            sid, channel, nick, learnedHostmaskForNick(sid, nick), body, InterceptorEventType.QUIT);
        maybeNotifyUserKlineFromQuit(sid, ev);
        maybeNotifyNetsplitDetected(sid, ev);
      }

      case IrcEvent.UserNickChangedChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        ensureTargetExists(chan);
        ui.appendPresence(chan, PresenceEvent.nick(ev.oldNick(), ev.newNick()));
        String channel = Objects.toString(ev.channel(), "").trim();
        String oldNick = Objects.toString(ev.oldNick(), "").trim();
        String newNick = Objects.toString(ev.newNick(), "").trim();
        String body =
            (oldNick.isEmpty() ? "(unknown)" : oldNick)
                + " is now known as "
                + (newNick.isEmpty() ? "(unknown)" : newNick)
                + (channel.isEmpty() ? "" : " in " + channel);
        notifyIrcEvent(
            IrcEventNotificationRule.EventType.USER_NICK_CHANGED,
            sid,
            channel,
            oldNick,
            "Nick changed" + (channel.isEmpty() ? "" : " in " + channel),
            body);
        recordInterceptorEvent(
            sid,
            channel,
            oldNick,
            learnedHostmaskForNick(sid, oldNick),
            body,
            InterceptorEventType.NICK);
      }

      case IrcEvent.JoinedChannel ev -> {
        TargetRef chan = new TargetRef(sid, ev.channel());
        TargetRef joinOrigin =
            joinRoutingState.recentOriginIfFresh(sid, ev.channel(), Duration.ofSeconds(15));
        joinRoutingState.clear(sid, ev.channel());

        if (!targetCoordinator.onJoinedChannel(sid, ev.channel())) {
          TargetRef st = new TargetRef(sid, "status");
          ensureTargetExists(st);
          ui.appendStatusAt(
              st,
              ev.at(),
              "(join)",
              "Stayed detached from " + ev.channel() + " (right-click channel and choose Join).");
          break;
        }

        runtimeConfig.rememberJoinedChannel(sid, ev.channel());
        inboundModeEventHandler.onJoinedChannel(sid, ev.channel());
        userInfoEnrichmentService.enqueueWhoChannelPrioritized(sid, ev.channel());

        ensureTargetExists(chan);
        ui.appendStatus(chan, "(join)", "Joined " + ev.channel());
        // Auto-joins should not steal focus; only switch when this join came from an explicit user
        // /join.
        if (joinOrigin != null) {
          ui.selectTarget(chan);
        }
      }

      case IrcEvent.JoinFailed ev -> {
        TargetRef origin =
            joinRoutingState.recentOriginIfFresh(sid, ev.channel(), Duration.ofSeconds(15));
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
        targetCoordinator.onUserHostChanged(sid, ev);

        String nick = Objects.toString(ev.nick(), "").trim();
        List<TargetRef> sharedChannels = targetCoordinator.sharedChannelTargetsForNick(sid, nick);
        String user = Objects.toString(ev.user(), "").trim();
        String host = Objects.toString(ev.host(), "").trim();
        if (nick.isEmpty()) nick = "(unknown)";
        String renderedText = nick + " changed host";
        if (!user.isEmpty() || !host.isEmpty()) {
          String uh = user + (host.isEmpty() ? "" : ("@" + host));
          renderedText = renderedText + " to " + uh;
        }
        String rendered = renderedText;
        if (!sharedChannels.isEmpty()) {
          for (TargetRef dest : sharedChannels) {
            postTo(dest, false, d -> ui.appendStatusAt(d, ev.at(), "(chghost)", rendered));
          }
          return;
        }

        TargetRef dest = (status != null) ? status : safeStatusTarget();
        postTo(dest, false, d -> ui.appendStatusAt(d, ev.at(), "(chghost)", rendered));
      }

      case IrcEvent.UserAwayStateObserved ev -> {
        if (ev.awayState() == IrcEvent.AwayState.AWAY
            || ev.awayState() == IrcEvent.AwayState.HERE) {
          markPrivateMessagePeerOnline(sid, ev.nick());
        }
        targetCoordinator.onUserAwayStateObserved(sid, ev);
      }

      case IrcEvent.UserAccountStateObserved ev -> {
        if (ev.accountState() == IrcEvent.AccountState.LOGGED_IN
            || ev.accountState() == IrcEvent.AccountState.LOGGED_OUT) {
          markPrivateMessagePeerOnline(sid, ev.nick());
        }
        targetCoordinator.onUserAccountStateObserved(sid, ev);
      }

      case IrcEvent.MonitorOnlineObserved ev -> {
        TargetRef monitor = TargetRef.monitorGroup(sid);
        ensureTargetExists(monitor);
        List<String> nicks = ev.nicks();
        if (!nicks.isEmpty()) {
          for (String nick : nicks) {
            markPrivateMessagePeerOnline(sid, nick);
          }
          ui.appendStatusAt(monitor, ev.at(), "(monitor)", "Online: " + String.join(", ", nicks));
        }
      }

      case IrcEvent.MonitorOfflineObserved ev -> {
        TargetRef monitor = TargetRef.monitorGroup(sid);
        ensureTargetExists(monitor);
        List<String> nicks = ev.nicks();
        if (!nicks.isEmpty()) {
          for (String nick : nicks) {
            markPrivateMessagePeerOffline(sid, nick);
          }
          ui.appendStatusAt(monitor, ev.at(), "(monitor)", "Offline: " + String.join(", ", nicks));
        }
      }

      case IrcEvent.MonitorListObserved ev -> {
        TargetRef monitor = TargetRef.monitorGroup(sid);
        ensureTargetExists(monitor);
        List<String> nicks = ev.nicks();
        String rendered =
            nicks.isEmpty()
                ? "Monitor list: (empty)"
                : ("Monitor list: " + String.join(", ", nicks));
        ui.appendStatusAt(monitor, ev.at(), "(monitor)", rendered);
      }

      case IrcEvent.MonitorListEnded ev -> {
        TargetRef monitor = TargetRef.monitorGroup(sid);
        ensureTargetExists(monitor);
        ui.appendStatusAt(monitor, ev.at(), "(monitor)", "End of monitor list.");
      }

      case IrcEvent.MonitorListFull ev -> {
        TargetRef monitor = TargetRef.monitorGroup(sid);
        ensureTargetExists(monitor);

        String msg = Objects.toString(ev.message(), "").trim();
        if (msg.isEmpty()) msg = "Monitor list is full.";
        if (ev.limit() > 0) msg = msg + " (limit=" + ev.limit() + ")";
        if (ev.nicks() != null && !ev.nicks().isEmpty()) {
          msg = msg + " nicks=" + String.join(", ", ev.nicks());
        }
        ui.appendErrorAt(monitor, ev.at(), "(monitor)", msg);
      }

      case IrcEvent.UserSetNameObserved ev -> {
        targetCoordinator.onUserSetNameObserved(sid, ev);

        String nick = Objects.toString(ev.nick(), "").trim();
        List<TargetRef> sharedChannels = targetCoordinator.sharedChannelTargetsForNick(sid, nick);
        String realName = Objects.toString(ev.realName(), "").trim();
        if (nick.isEmpty()) nick = "(unknown)";
        if (realName.isEmpty()) realName = "(empty)";
        String rendered = nick + " set name to: " + realName;
        if (!sharedChannels.isEmpty()) {
          for (TargetRef dest : sharedChannels) {
            postTo(dest, false, d -> ui.appendStatusAt(d, ev.at(), "(setname)", rendered));
          }
          return;
        }

        TargetRef dest = (status != null) ? status : safeStatusTarget();
        postTo(dest, false, d -> ui.appendStatusAt(d, ev.at(), "(setname)", rendered));
      }

      case IrcEvent.UserTypingObserved ev -> {
        if (isFromSelf(sid, ev.from())) return;
        markPrivateMessagePeerOnline(sid, ev.from());
        TargetRef dest = resolveIrcv3Target(sid, ev.target(), ev.from(), status);
        String from = Objects.toString(ev.from(), "").trim();
        if (from.isEmpty()) from = "Someone";
        String state = Objects.toString(ev.state(), "").trim().toLowerCase(Locale.ROOT);
        if (state.isEmpty()) state = "active";

        boolean prefEnabled = false;
        try {
          prefEnabled = uiSettingsPort.get().typingIndicatorsReceiveEnabled();
        } catch (Exception ignored) {
        }
        boolean typingAvailable = false;
        try {
          typingAvailable = irc != null && irc.isTypingAvailable(sid);
        } catch (Exception ignored) {
        }
        maybeLogTypingObserved(
            sid, Objects.toString(ev.target(), ""), from, state, prefEnabled, typingAvailable);

        ui.showTypingIndicator(dest, from, state);
        if (prefEnabled) {
          ui.showTypingActivity(dest, state);
          ui.showUsersTypingIndicator(dest, from, state);
        }
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

      case IrcEvent.MessageUnreactObserved ev -> {
        if (!irc.isDraftUnreactAvailable(sid)) return;
        TargetRef dest = resolveIrcv3Target(sid, ev.target(), ev.from(), status);
        String from = Objects.toString(ev.from(), "").trim();
        if (from.isEmpty()) return;
        String reaction = Objects.toString(ev.reaction(), "").trim();
        String targetMsgId = Objects.toString(ev.messageId(), "").trim();
        if (reaction.isEmpty() || targetMsgId.isEmpty()) return;
        ui.removeMessageReaction(dest, ev.at(), from, targetMsgId, reaction);
      }

      case IrcEvent.MessageRedactionObserved ev -> {
        if (!irc.isMessageRedactionAvailable(sid)) return;
        TargetRef dest = resolveIrcv3Target(sid, ev.target(), ev.from(), status);
        String from = Objects.toString(ev.from(), "").trim();
        String targetMsgId = Objects.toString(ev.messageId(), "").trim();
        if (targetMsgId.isEmpty()) return;
        ui.applyMessageRedaction(
            dest, ev.at(), from, targetMsgId, "", Map.of("draft/delete", targetMsgId));
      }

      case IrcEvent.Ircv3CapabilityChanged ev -> {
        ensureTargetExists(status);
        String sub = Objects.toString(ev.subcommand(), "").trim().toUpperCase(Locale.ROOT);
        String cap = Objects.toString(ev.capability(), "").trim();
        ui.setServerIrcv3Capability(sid, cap, sub, ev.enabled());
        if (!ev.enabled() && ("ACK".equals(sub) || "DEL".equals(sub))) {
          ui.normalizeIrcv3CapabilityUiState(sid, cap);
        }
        if (cap.isEmpty()) cap = "(unknown)";
        String rendered;
        if ("NEW".equals(sub)) {
          rendered = "CAP NEW: " + cap + " (available)";
        } else if ("LS".equals(sub)) {
          rendered = "CAP LS: " + cap + " (available)";
        } else if ("NAK".equals(sub)) {
          rendered = "CAP NAK: " + cap + " (rejected)";
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
        connectionCoordinator.noteConnectionError(sid, ev.message());
        ui.appendError(status, "(error)", ev.message());
        recordInterceptorEvent(
            sid, "status", "server", "", ev.message(), InterceptorEventType.ERROR);
        maybeNotifyKline(sid, ev.message(), "Server restriction");
      }

      default -> {}
    }
  }

  private NotificationRuleMatch firstRuleMatchForChannel(
      String serverId, TargetRef chan, String from, String text) {
    if (chan == null || text == null || text.isBlank()) return null;
    if (isFromSelf(serverId, from)) return null;

    List<NotificationRuleMatch> matches;
    try {
      matches = notificationRuleMatcherPort.matchAll(text);
    } catch (Exception ignored) {
      return null;
    }
    if (matches == null || matches.isEmpty()) return null;
    // Keep only the first match to avoid over-highlighting and duplicate events.
    return matches.get(0);
  }

  private boolean notifyIrcEvent(
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      String title,
      String body) {
    if (eventType == null || ircEventNotifierPort == null) return false;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;

    String src = Objects.toString(sourceNick, "").trim();
    Boolean sourceIsSelf = src.isEmpty() ? null : isFromSelf(sid, src);
    TargetRef active = targetCoordinator != null ? targetCoordinator.getActiveTarget() : null;
    String activeSid = active != null ? active.serverId() : null;
    String activeTgt = active != null ? active.target() : null;
    try {
      return ircEventNotifierPort.notifyConfigured(
          eventType, sid, channel, src, sourceIsSelf, title, body, activeSid, activeTgt);
    } catch (Exception ignored) {
      return false;
    }
  }

  private void maybeNotifyKline(String serverId, String message, String title) {
    String msg = Objects.toString(message, "").trim();
    if (msg.isEmpty()) return;
    if (!looksLikeKlineMessage(msg)) return;
    notifyIrcEvent(IrcEventNotificationRule.EventType.YOU_KLINED, serverId, null, null, title, msg);
  }

  private void maybeNotifyUserKlineFromQuit(String serverId, IrcEvent.UserQuitChannel ev) {
    if (ev == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    String nick = Objects.toString(ev.nick(), "").trim();
    if (nick.isEmpty()) return;
    if (isFromSelf(sid, nick)) return;

    String reason = Objects.toString(ev.reason(), "").trim();
    if (!looksLikeKlineMessage(reason)) return;

    String channel = Objects.toString(ev.channel(), "").trim();
    String body = nick + " appears to be restricted";
    if (!reason.isEmpty()) body = body + " (" + reason + ")";
    if (!channel.isEmpty()) body = body + " in " + channel;

    notifyIrcEvent(
        IrcEventNotificationRule.EventType.KLINED,
        sid,
        channel,
        nick,
        "User restricted" + (channel.isEmpty() ? "" : " in " + channel),
        body);
  }

  private void maybeNotifyNetsplitDetected(String serverId, IrcEvent.UserQuitChannel ev) {
    if (ev == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    NetsplitServers split = parseNetsplitServers(ev.reason());
    if (split == null) return;

    long now = System.currentTimeMillis();
    String key = (sid + "|" + split.left() + "|" + split.right()).toLowerCase(Locale.ROOT);
    Long previous = lastNetsplitNotifyAtMs.put(key, now);
    if (previous != null && (now - previous) < NETSPLIT_NOTIFY_DEBOUNCE_MS) {
      return;
    }

    if (lastNetsplitNotifyAtMs.size() > NETSPLIT_NOTIFY_MAX_KEYS) {
      long cutoff = now - (NETSPLIT_NOTIFY_DEBOUNCE_MS * 3L);
      lastNetsplitNotifyAtMs
          .entrySet()
          .removeIf(e -> e.getValue() == null || e.getValue() < cutoff);
    }

    String channel = Objects.toString(ev.channel(), "").trim();
    String nick = Objects.toString(ev.nick(), "").trim();
    String body = "Possible netsplit detected (" + split.left() + " ↔ " + split.right() + ")";
    if (!channel.isEmpty()) body = body + " in " + channel;
    if (!nick.isEmpty()) body = body + " after " + nick + " quit";

    notifyIrcEvent(
        IrcEventNotificationRule.EventType.NETSPLIT_DETECTED,
        sid,
        channel,
        nick,
        "Netsplit detected",
        body);
  }

  private void clearNetsplitDebounceForServer(String serverId) {
    String sid = Objects.toString(serverId, "").trim().toLowerCase(Locale.ROOT);
    if (sid.isEmpty()) return;
    String prefix = sid + "|";
    lastNetsplitNotifyAtMs.keySet().removeIf(k -> k != null && k.startsWith(prefix));
  }

  private static NetsplitServers parseNetsplitServers(String reason) {
    String r = Objects.toString(reason, "").trim();
    if (r.isEmpty()) return null;
    String[] parts = r.split("\\s+");
    if (parts.length != 2) return null;
    String left = parts[0].trim();
    String right = parts[1].trim();
    if (!looksLikeIrcServerToken(left) || !looksLikeIrcServerToken(right)) return null;
    return new NetsplitServers(left, right);
  }

  private static boolean looksLikeIrcServerToken(String token) {
    String s = Objects.toString(token, "").trim();
    if (s.length() < 3 || s.length() > 255) return false;
    if (!s.contains(".")) return false;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean ok = Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == ':';
      if (!ok) return false;
    }
    return true;
  }

  private record NetsplitServers(String left, String right) {}

  private static boolean looksLikeKlineMessage(String message) {
    String m = Objects.toString(message, "").trim().toLowerCase(Locale.ROOT);
    if (m.isEmpty()) return false;
    return m.contains("k-line")
        || m.contains("klined")
        || m.contains("kline")
        || m.contains("g-line")
        || m.contains("gline")
        || m.contains("z-line")
        || m.contains("zline")
        || m.contains("akill")
        || m.contains("autokill")
        || m.contains("banned from this server");
  }

  private void maybeNotifyModeEvents(String serverId, IrcEvent.ChannelModeChanged ev) {
    if (serverId == null || ev == null) return;

    String channel = Objects.toString(ev.channel(), "").trim();
    if (channel.isEmpty()) return;

    String actor = Objects.toString(ev.by(), "").trim();
    String by = actor.isEmpty() ? "Someone" : actor;

    for (ModeChangeToken ch : parseModeChanges(ev.details())) {
      if (ch == null) continue;

      IrcEventNotificationRule.EventType type = null;
      switch (ch.mode()) {
        case 'o' ->
            type =
                ch.add()
                    ? IrcEventNotificationRule.EventType.OPPED
                    : IrcEventNotificationRule.EventType.DEOPPED;
        case 'v' ->
            type =
                ch.add()
                    ? IrcEventNotificationRule.EventType.VOICED
                    : IrcEventNotificationRule.EventType.DEVOICED;
        case 'h' ->
            type =
                ch.add()
                    ? IrcEventNotificationRule.EventType.HALF_OPPED
                    : IrcEventNotificationRule.EventType.DEHALF_OPPED;
        case 'b' -> {
          if (ch.add()) type = IrcEventNotificationRule.EventType.BANNED;
        }
        default -> {}
      }
      if (type == null) continue;

      String arg = Objects.toString(ch.arg(), "").trim();
      String body =
          switch (type) {
            case OPPED ->
                by + " gave operator to " + (arg.isEmpty() ? "(unknown)" : arg) + " in " + channel;
            case DEOPPED ->
                by
                    + " removed operator from "
                    + (arg.isEmpty() ? "(unknown)" : arg)
                    + " in "
                    + channel;
            case VOICED ->
                by + " gave voice to " + (arg.isEmpty() ? "(unknown)" : arg) + " in " + channel;
            case DEVOICED ->
                by
                    + " removed voice from "
                    + (arg.isEmpty() ? "(unknown)" : arg)
                    + " in "
                    + channel;
            case HALF_OPPED ->
                by + " gave half-op to " + (arg.isEmpty() ? "(unknown)" : arg) + " in " + channel;
            case DEHALF_OPPED ->
                by
                    + " removed half-op from "
                    + (arg.isEmpty() ? "(unknown)" : arg)
                    + " in "
                    + channel;
            case BANNED ->
                by + " set ban " + (arg.isEmpty() ? "(unknown)" : arg) + " in " + channel;
            default -> by + " changed mode in " + channel;
          };

      notifyIrcEvent(type, serverId, channel, actor, "Mode change in " + channel, body);

      IrcEventNotificationRule.EventType selfType = selfTargetedModeEventType(type);
      if (selfType != null && isSelfModeTargetForEvent(serverId, type, arg)) {
        String selfTitle = selfTargetedModeTitle(selfType, channel);
        String selfBody = selfTargetedModeBody(selfType, by, channel);
        notifyIrcEvent(selfType, serverId, channel, actor, selfTitle, selfBody);
      }
    }
  }

  private boolean isSelfModeTargetForEvent(
      String serverId, IrcEventNotificationRule.EventType baseType, String rawTarget) {
    if (baseType == IrcEventNotificationRule.EventType.BANNED) {
      return isSelfBanTarget(serverId, rawTarget);
    }
    return isSelfModeTarget(serverId, rawTarget);
  }

  private boolean isSelfModeTarget(String serverId, String rawTargetNick) {
    String target = normalizeNickForCompare(rawTargetNick);
    if (target == null || target.isBlank()) return false;
    return isFromSelf(serverId, target);
  }

  private boolean isSelfBanTarget(String serverId, String rawBanTarget) {
    String target = Objects.toString(rawBanTarget, "").trim();
    if (target.isEmpty()) return false;

    if (isSelfModeTarget(serverId, target)) return true;

    String me = irc.currentNick(serverId).orElse("");
    if (me.isBlank()) return false;
    return target.toLowerCase(Locale.ROOT).contains(me.toLowerCase(Locale.ROOT));
  }

  private static IrcEventNotificationRule.EventType selfTargetedModeEventType(
      IrcEventNotificationRule.EventType baseType) {
    if (baseType == null) return null;
    return switch (baseType) {
      case OPPED -> IrcEventNotificationRule.EventType.YOU_OPPED;
      case DEOPPED -> IrcEventNotificationRule.EventType.YOU_DEOPPED;
      case VOICED -> IrcEventNotificationRule.EventType.YOU_VOICED;
      case DEVOICED -> IrcEventNotificationRule.EventType.YOU_DEVOICED;
      case HALF_OPPED -> IrcEventNotificationRule.EventType.YOU_HALF_OPPED;
      case DEHALF_OPPED -> IrcEventNotificationRule.EventType.YOU_DEHALF_OPPED;
      case BANNED -> IrcEventNotificationRule.EventType.YOU_BANNED;
      default -> null;
    };
  }

  private static String selfTargetedModeTitle(
      IrcEventNotificationRule.EventType selfType, String channel) {
    if (selfType == null) return "Mode change in " + channel;
    return switch (selfType) {
      case YOU_OPPED -> "You were opped in " + channel;
      case YOU_DEOPPED -> "You were de-opped in " + channel;
      case YOU_VOICED -> "You were voiced in " + channel;
      case YOU_DEVOICED -> "You were de-voiced in " + channel;
      case YOU_HALF_OPPED -> "You were half-opped in " + channel;
      case YOU_DEHALF_OPPED -> "You were de-half-opped in " + channel;
      case YOU_BANNED -> "You were banned in " + channel;
      default -> "Mode change in " + channel;
    };
  }

  private static String selfTargetedModeBody(
      IrcEventNotificationRule.EventType selfType, String by, String channel) {
    if (selfType == null) return by + " changed your mode in " + channel;
    return switch (selfType) {
      case YOU_OPPED -> by + " gave you operator in " + channel;
      case YOU_DEOPPED -> by + " removed your operator in " + channel;
      case YOU_VOICED -> by + " gave you voice in " + channel;
      case YOU_DEVOICED -> by + " removed your voice in " + channel;
      case YOU_HALF_OPPED -> by + " gave you half-op in " + channel;
      case YOU_DEHALF_OPPED -> by + " removed your half-op in " + channel;
      case YOU_BANNED -> by + " set a ban matching you in " + channel;
      default -> by + " changed your mode in " + channel;
    };
  }

  private List<ModeChangeToken> parseModeChanges(String details) {
    String d = Objects.toString(details, "").trim();
    if (d.isEmpty()) return List.of();

    String[] parts = d.split("\\s+");
    if (parts.length == 0) return List.of();

    int modeIdx = -1;
    for (int i = 0; i < parts.length; i++) {
      String token = parts[i];
      if (token.indexOf('+') >= 0 || token.indexOf('-') >= 0) {
        modeIdx = i;
        break;
      }
    }
    if (modeIdx < 0) return List.of();

    String modeSeq = parts[modeIdx];
    List<String> args = new java.util.ArrayList<>();
    for (int i = modeIdx + 1; i < parts.length; i++) {
      args.add(parts[i]);
    }

    boolean add = true;
    int argIdx = 0;
    List<ModeChangeToken> out = new java.util.ArrayList<>();
    for (int i = 0; i < modeSeq.length(); i++) {
      char c = modeSeq.charAt(i);
      if (c == '+') {
        add = true;
        continue;
      }
      if (c == '-') {
        add = false;
        continue;
      }

      String arg = null;
      if (modeTakesArg(c, add) && argIdx < args.size()) {
        arg = args.get(argIdx++);
      }
      out.add(new ModeChangeToken(add, c, arg));
    }
    return out;
  }

  private static boolean modeTakesArg(char mode, boolean adding) {
    return switch (mode) {
      case 'o', 'v', 'h', 'a', 'q', 'y', 'b', 'e', 'I', 'k', 'f', 'j' -> true;
      case 'l' -> adding;
      default -> false;
    };
  }

  private record ModeChangeToken(boolean add, char mode, String arg) {}

  private void recordRuleMatchIfPresent(
      TargetRef chan, TargetRef active, String from, String text, NotificationRuleMatch match) {
    if (chan == null || match == null) return;
    if (active == null || !chan.equals(active)) {
      ui.markHighlight(chan);
    }
    ui.recordRuleMatch(
        chan, from, match.ruleLabel(), snippetAround(text, match.start(), match.end()));
  }

  private void recordMentionHighlight(
      TargetRef chan, TargetRef active, String fromNick, String snippet) {
    if (chan == null) return;
    if (active == null || !chan.equals(active)) {
      ui.markHighlight(chan);
    }
    ui.recordHighlight(chan, fromNick, snippet);
  }

  private void recordInterceptorEvent(
      String serverId,
      String channel,
      String fromNick,
      String fromHostmask,
      String text,
      InterceptorEventType eventType) {
    if (interceptorIngestPort == null) return;
    String sid = Objects.toString(serverId, "").trim();
    String from = Objects.toString(fromNick, "").trim();
    if (sid.isEmpty() || from.isEmpty()) return;
    if (isFromSelf(sid, from)) return;
    interceptorIngestPort.ingestEvent(
        sid,
        channel,
        from,
        Objects.toString(fromHostmask, "").trim(),
        Objects.toString(text, "").trim(),
        eventType);
  }

  private String learnedHostmaskForNick(String serverId, String nick) {
    if (userListStore == null) return "";
    String sid = Objects.toString(serverId, "").trim();
    String n = Objects.toString(nick, "").trim();
    if (sid.isEmpty() || n.isEmpty()) return "";
    String hostmask = userListStore.getLearnedHostmask(sid, n);
    return Objects.toString(hostmask, "").trim();
  }

  private boolean tryResolvePendingEchoChannelMessage(
      String sid, TargetRef chan, TargetRef active, IrcEvent.ChannelMessage ev) {
    if (!isFromSelf(sid, ev.from())) return false;
    var pending = pendingEchoMessageState.consumeByTargetAndText(chan, ev.from(), ev.text());
    if (pending.isEmpty()) return false;

    var entry = pending.get();
    postTo(
        chan,
        active,
        true,
        d -> {
          boolean replaced =
              ui.resolvePendingOutgoingChat(
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
      String sid, TargetRef fallbackPm, IrcEvent.PrivateMessage ev, boolean allowAutoOpen) {
    if (!isFromSelf(sid, ev.from())) return false;

    var pending = pendingEchoMessageState.consumeByTargetAndText(fallbackPm, ev.from(), ev.text());
    if (pending.isEmpty()) {
      pending = pendingEchoMessageState.consumePrivateFallback(sid, ev.from(), ev.text());
    }
    if (pending.isEmpty()) return false;

    var entry = pending.get();
    TargetRef dest = entry.target() != null ? entry.target() : fallbackPm;
    if (allowAutoOpen) {
      postTo(
          dest,
          true,
          d -> {
            boolean replaced =
                ui.resolvePendingOutgoingChat(
                    d,
                    entry.pendingId(),
                    ev.at(),
                    ev.from(),
                    ev.text(),
                    ev.messageId(),
                    ev.ircv3Tags());
            if (!replaced) {
              ui.appendChatAt(
                  d, ev.at(), ev.from(), ev.text(), true, ev.messageId(), ev.ircv3Tags());
            }
          });
    } else {
      boolean replaced =
          ui.resolvePendingOutgoingChat(
              dest,
              entry.pendingId(),
              ev.at(),
              ev.from(),
              ev.text(),
              ev.messageId(),
              ev.ircv3Tags());
      if (!replaced) {
        ui.appendChatAt(dest, ev.at(), ev.from(), ev.text(), true, ev.messageId(), ev.ircv3Tags());
      }
    }
    return true;
  }

  private boolean maybeApplyMessageEditFromTaggedLine(
      String sid,
      TargetRef target,
      Instant at,
      String from,
      String text,
      String messageId,
      Map<String, String> ircv3Tags) {
    if (sid == null || sid.isBlank()) return false;
    if (target == null || target.isUiOnly()) return false;
    if (!irc.isMessageEditAvailable(sid)) return false;

    String targetMsgId = firstIrcv3TagValue(ircv3Tags, "draft/edit", "+draft/edit");
    if (targetMsgId.isBlank()) return false;

    return ui.applyMessageEdit(
        target,
        at,
        Objects.toString(from, "").trim(),
        targetMsgId,
        Objects.toString(text, ""),
        messageId,
        ircv3Tags);
  }

  private void failPendingEchoesForServer(String sid, String reason) {
    if (sid == null || sid.isBlank()) return;
    Instant now = Instant.now();
    for (PendingEchoMessageState.PendingOutboundChat pending :
        pendingEchoMessageState.drainServer(sid)) {
      TargetRef target = pending.target();
      if (target == null) continue;
      ui.failPendingOutgoingChat(
          target, pending.pendingId(), now, pending.fromNick(), pending.text(), reason);
    }
  }

  private void updateServerMetadataFromServerResponseLine(
      String serverId, IrcEvent.ServerResponseLine ev) {
    if (ev == null) return;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return;

    ParsedIrcLine pl = parseIrcLineForMetadata(ev.rawLine());
    if (pl == null) return;

    String cmd = Objects.toString(pl.command(), "").trim();
    if (cmd.isEmpty()) return;

    if ("004".equals(cmd) || ev.code() == 4) {
      List<String> params = pl.params();
      String serverName = params.size() >= 2 ? params.get(1) : "";
      String version = params.size() >= 3 ? params.get(2) : "";
      String userModes = params.size() >= 4 ? params.get(3) : "";
      String channelModes = params.size() >= 5 ? params.get(4) : "";
      ui.setServerVersionDetails(sid, serverName, version, userModes, channelModes);
      return;
    }

    if ("351".equals(cmd) || ev.code() == 351) {
      List<String> params = pl.params();
      String version = params.size() >= 2 ? params.get(1) : "";
      String serverName = params.size() >= 3 ? params.get(2) : "";
      ui.setServerVersionDetails(sid, serverName, version, "", "");
      return;
    }

    if ("005".equals(cmd) || ev.code() == 5) {
      List<String> params = pl.params();
      int start = params.size() >= 1 ? 1 : 0; // skip nick/target
      for (int i = start; i < params.size(); i++) {
        String tok = Objects.toString(params.get(i), "").trim();
        if (tok.isEmpty()) continue;

        if (tok.startsWith("-") && tok.length() > 1) {
          ui.setServerIsupportToken(sid, tok.substring(1), null);
          continue;
        }

        int eq = tok.indexOf('=');
        if (eq >= 0) {
          String key = tok.substring(0, eq).trim();
          String value = tok.substring(eq + 1).trim();
          if (!key.isEmpty()) {
            ui.setServerIsupportToken(sid, key, value);
          }
          continue;
        }

        // Tokens without "=" still represent support (for example WHOX).
        ui.setServerIsupportToken(sid, tok, "");
      }
    }
  }

  private record ParsedIrcLine(
      String prefix, String command, List<String> params, String trailing) {}

  private static ParsedIrcLine parseIrcLineForMetadata(String rawLine) {
    String s = Objects.toString(rawLine, "").trim();
    if (s.isEmpty()) return null;

    // Strip IRCv3 tags (@aaa=bbb;ccc ...).
    if (s.startsWith("@")) {
      int sp = s.indexOf(' ');
      if (sp <= 0 || sp >= s.length() - 1) return null;
      s = s.substring(sp + 1).trim();
    }

    String prefix = "";
    if (s.startsWith(":")) {
      int sp = s.indexOf(' ');
      if (sp <= 1 || sp >= s.length() - 1) return null;
      prefix = s.substring(1, sp).trim();
      s = s.substring(sp + 1).trim();
    }

    String trailing = "";
    int trailStart = s.indexOf(" :");
    if (trailStart >= 0) {
      trailing = s.substring(trailStart + 2).trim();
      s = s.substring(0, trailStart).trim();
    }

    if (s.isEmpty()) return null;
    String[] toks = s.split("\\s+");
    if (toks.length == 0) return null;

    String command = toks[0].trim();
    if (command.isEmpty()) return null;

    List<String> params = new java.util.ArrayList<>();
    for (int i = 1; i < toks.length; i++) {
      String tok = Objects.toString(toks[i], "").trim();
      if (!tok.isEmpty()) params.add(tok);
    }

    return new ParsedIrcLine(prefix, command, List.copyOf(params), trailing);
  }

  private void handleServerResponseLine(
      String sid, TargetRef status, IrcEvent.ServerResponseLine ev) {
    ensureTargetExists(status);
    String msg = Objects.toString(ev.message(), "");
    String rendered = "[" + ev.code() + "] " + msg;
    updateServerMetadataFromServerResponseLine(sid, ev);
    boolean suppressStatusLine =
        (ev.code() == 322); // /LIST entry rows are shown in the dedicated channel-list panel.
    if (ev.code() == 303 && monitorFallbackPort.shouldSuppressIsonServerResponse(sid)) {
      suppressStatusLine = true;
    }
    if (ev.code() == 321) {
      rendered =
          "[321] "
              + (msg.isBlank()
                  ? "Channel list follows (see Channel List)."
                  : (msg + " (see Channel List)."));
    } else if (ev.code() == 323 && !msg.isBlank()) {
      rendered = "[323] " + msg + " (see Channel List).";
    }
    String label = Objects.toString(ev.ircv3Tags().get("label"), "").trim();
    if (!label.isBlank()) {
      LabeledResponseRoutingState.PendingLabeledRequest pending =
          labeledResponseRoutingState.findIfFresh(sid, label, LABELED_RESPONSE_CORRELATION_WINDOW);
      if (pending != null && pending.originTarget() != null) {
        TargetRef dest = normalizeLabeledDestination(sid, status, pending.originTarget());
        LabeledResponseRoutingState.PendingLabeledRequest transitioned =
            labeledResponseRoutingState.markOutcomeIfPending(
                sid, label, LabeledResponseRoutingState.Outcome.SUCCESS, ev.at());
        if (transitioned != null) {
          appendLabeledOutcome(
              dest,
              ev.at(),
              label,
              transitioned.requestPreview(),
              transitioned.outcome(),
              "response received");
        }

        String preview = Objects.toString(pending.requestPreview(), "").trim();
        String correlated = preview.isBlank() ? rendered : (rendered + " \u2190 " + preview);
        if (!suppressStatusLine) {
          postTo(
              dest,
              true,
              d ->
                  ui.appendStatusAt(
                      d, ev.at(), "(server)", correlated, ev.messageId(), ev.ircv3Tags()));
        }
        return;
      }
      rendered = rendered + " {label=" + label + "}";
    }
    if (!suppressStatusLine) {
      ui.appendStatusAt(status, ev.at(), "(server)", rendered, ev.messageId(), ev.ircv3Tags());
    }

    if (ev.code() == 465 || ev.code() == 466 || ev.code() == 463 || ev.code() == 464) {
      String msgTrim = Objects.toString(ev.message(), "").trim();
      String body =
          msgTrim.isBlank()
              ? ("Server response [" + ev.code() + "]")
              : ("[" + ev.code() + "] " + msgTrim);
      notifyIrcEvent(
          IrcEventNotificationRule.EventType.YOU_KLINED,
          sid,
          null,
          null,
          "Server restriction",
          body);
    } else {
      maybeNotifyKline(sid, ev.message(), "Server restriction");
    }
  }

  private void handleStandardReply(String sid, TargetRef status, IrcEvent.StandardReply ev) {
    ensureTargetExists(status);
    String rendered = renderStandardReply(ev);
    String label = Objects.toString(ev.ircv3Tags().get("label"), "").trim();
    if (!label.isBlank()) {
      LabeledResponseRoutingState.PendingLabeledRequest pending =
          labeledResponseRoutingState.findIfFresh(sid, label, LABELED_RESPONSE_CORRELATION_WINDOW);
      if (pending != null && pending.originTarget() != null) {
        TargetRef dest = normalizeLabeledDestination(sid, status, pending.originTarget());
        LabeledResponseRoutingState.Outcome outcome =
            (ev.kind() == IrcEvent.StandardReplyKind.FAIL)
                ? LabeledResponseRoutingState.Outcome.FAILURE
                : LabeledResponseRoutingState.Outcome.SUCCESS;
        LabeledResponseRoutingState.PendingLabeledRequest transitioned =
            labeledResponseRoutingState.markOutcomeIfPending(sid, label, outcome, ev.at());
        if (transitioned != null) {
          appendLabeledOutcome(
              dest,
              ev.at(),
              label,
              transitioned.requestPreview(),
              transitioned.outcome(),
              ev.description());
        }

        String preview = Objects.toString(pending.requestPreview(), "").trim();
        String correlated = preview.isBlank() ? rendered : (rendered + " \u2190 " + preview);
        postTo(
            dest,
            true,
            d ->
                ui.appendStatusAt(
                    d, ev.at(), "(standard-reply)", correlated, ev.messageId(), ev.ircv3Tags()));
        return;
      }
      rendered = rendered + " {label=" + label + "}";
    }
    ui.appendStatusAt(
        status, ev.at(), "(standard-reply)", rendered, ev.messageId(), ev.ircv3Tags());
    maybeNotifyKline(sid, ev.description(), "Server restriction");
  }

  private void handleLabeledRequestTimeouts() {
    List<LabeledResponseRoutingState.TimedOutLabeledRequest> timedOut =
        labeledResponseRoutingState.collectTimedOut(LABELED_RESPONSE_TIMEOUT, 32);
    if (timedOut == null || timedOut.isEmpty()) return;
    for (LabeledResponseRoutingState.TimedOutLabeledRequest timeout : timedOut) {
      if (timeout == null || timeout.request() == null) continue;
      TargetRef status = new TargetRef(timeout.serverId(), "status");
      TargetRef dest =
          normalizeLabeledDestination(timeout.serverId(), status, timeout.request().originTarget());
      appendLabeledOutcome(
          dest,
          timeout.timedOutAt(),
          timeout.label(),
          timeout.request().requestPreview(),
          LabeledResponseRoutingState.Outcome.TIMEOUT,
          "no reply within " + LABELED_RESPONSE_TIMEOUT.toSeconds() + "s");
    }
  }

  private void handlePendingEchoTimeouts() {
    Instant now = Instant.now();
    List<PendingEchoMessageState.PendingOutboundChat> timedOut =
        pendingEchoMessageState.collectTimedOut(
            PENDING_ECHO_TIMEOUT, PENDING_ECHO_TIMEOUT_BATCH_MAX, now);
    if (timedOut == null || timedOut.isEmpty()) return;

    String reason =
        "Timed out waiting for server echo after " + PENDING_ECHO_TIMEOUT.toSeconds() + "s";
    for (PendingEchoMessageState.PendingOutboundChat pending : timedOut) {
      if (pending == null || pending.target() == null) continue;
      ui.failPendingOutgoingChat(
          pending.target(), pending.pendingId(), now, pending.fromNick(), pending.text(), reason);
    }
  }

  private static String renderStandardReply(IrcEvent.StandardReply ev) {
    if (ev == null) return "";
    StringBuilder out = new StringBuilder();
    out.append(ev.kind().name());
    String cmd = Objects.toString(ev.command(), "").trim();
    if (!cmd.isBlank()) out.append(' ').append(cmd);
    String code = Objects.toString(ev.code(), "").trim();
    if (!code.isBlank()) out.append(' ').append(code);
    String context = Objects.toString(ev.context(), "").trim();
    if (!context.isBlank()) out.append(" [").append(context).append(']');
    String desc = Objects.toString(ev.description(), "").trim();
    if (!desc.isBlank()) out.append(": ").append(desc);
    return out.toString();
  }

  private void appendLabeledOutcome(
      TargetRef dest,
      Instant at,
      String label,
      String requestPreview,
      LabeledResponseRoutingState.Outcome outcome,
      String detail) {
    String lbl = Objects.toString(label, "").trim();
    if (lbl.isEmpty()) return;
    String preview = Objects.toString(requestPreview, "").trim();
    String d = Objects.toString(detail, "").trim();
    String state =
        switch (outcome) {
          case FAILURE -> "failed";
          case TIMEOUT -> "timed out";
          case SUCCESS -> "completed";
          case PENDING -> "pending";
        };

    StringBuilder text =
        new StringBuilder("Request ").append(state).append(" {label=").append(lbl).append('}');
    if (!preview.isBlank()) text.append(": ").append(preview);
    if (!d.isBlank()) text.append(" (").append(d).append(')');
    String from =
        switch (outcome) {
          case FAILURE -> "(label-fail)";
          case TIMEOUT -> "(label-timeout)";
          case SUCCESS -> "(label-ok)";
          case PENDING -> "(label)";
        };
    ui.appendStatusAt(dest, at == null ? Instant.now() : at, from, text.toString());
  }

  private TargetRef normalizeLabeledDestination(String sid, TargetRef status, TargetRef origin) {
    if (origin == null) return status;
    TargetRef dest = origin;
    if (!Objects.equals(dest.serverId(), sid)) {
      dest = new TargetRef(sid, dest.target());
    }
    if (dest.isUiOnly()) {
      return status;
    }
    return dest;
  }

  private boolean isFromSelf(String serverId, String from) {
    if (serverId == null || from == null) return false;
    String me = irc.currentNick(serverId).orElse(null);
    if (me == null || me.isBlank()) return false;
    String fromNorm = normalizeNickForCompare(from);
    return fromNorm != null && fromNorm.equalsIgnoreCase(me);
  }

  private void markPrivateMessagePeerOnline(String serverId, String rawNick) {
    String nick = normalizePrivateMessagePeer(rawNick);
    if (nick.isEmpty()) return;
    if (isFromSelf(serverId, nick)) return;
    ui.setPrivateMessageOnlineState(serverId, nick, true);
  }

  private void markPrivateMessagePeerOffline(String serverId, String rawNick) {
    String nick = normalizePrivateMessagePeer(rawNick);
    if (nick.isEmpty()) return;
    if (isFromSelf(serverId, nick)) return;
    ui.setPrivateMessageOnlineState(serverId, nick, false);
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

  private static String normalizePrivateMessagePeer(String raw) {
    String n = normalizeNickForCompare(raw);
    n = Objects.toString(n, "").trim();
    if (n.isEmpty()) return "";
    if ("server".equalsIgnoreCase(n)) return "";
    if (n.startsWith("*")) return "";
    return n;
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

  private static String firstIrcv3TagValue(Map<String, String> tags, String... keys) {
    if (tags == null || tags.isEmpty() || keys == null) return "";
    for (String key : keys) {
      String want = normalizeIrcv3TagKey(key);
      if (want.isEmpty()) continue;
      for (Map.Entry<String, String> e : tags.entrySet()) {
        String got = normalizeIrcv3TagKey(e.getKey());
        if (!want.equals(got)) continue;
        String raw = Objects.toString(e.getValue(), "").trim();
        if (raw.isEmpty()) continue;
        return raw;
      }
    }
    return "";
  }

  private static String normalizeIrcv3TagKey(String rawKey) {
    String k = Objects.toString(rawKey, "").trim();
    if (k.startsWith("@")) k = k.substring(1).trim();
    if (k.startsWith("+")) k = k.substring(1).trim();
    if (k.isEmpty()) return "";
    return k.toLowerCase(Locale.ROOT);
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
    return ch == '['
        || ch == ']'
        || ch == '\\'
        || ch == '`'
        || ch == '_'
        || ch == '^'
        || ch == '{'
        || ch == '|'
        || ch == '}'
        || ch == '-';
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

  private void maybeLogTypingObserved(
      String serverId,
      String rawTarget,
      String from,
      String state,
      boolean prefEnabled,
      boolean typingAvailable) {
    if (!log.isInfoEnabled()) return;

    String sid = Objects.toString(serverId, "").trim();
    String tgt = Objects.toString(rawTarget, "").trim();
    String nick = Objects.toString(from, "").trim();
    String st = Objects.toString(state, "").trim().toLowerCase(Locale.ROOT);
    if (sid.isEmpty() || nick.isEmpty() || st.isEmpty()) return;

    String key = sid + "|" + tgt + "|" + nick;
    long now = System.currentTimeMillis();

    TypingLogState prev = lastTypingByKey.get(key);
    boolean stateChanged = prev == null || !Objects.equals(prev.state(), st);
    boolean stale = prev == null || (now - prev.atMs()) >= TYPING_LOG_DEDUP_MS;

    if (lastTypingByKey.size() > TYPING_LOG_MAX_KEYS) {
      lastTypingByKey.clear();
    }

    if (stateChanged || stale || "done".equals(st)) {
      lastTypingByKey.put(key, new TypingLogState(st, now));
      log.info(
          "[{}] typing observed: from={} target={} state={} (prefsEnabled={} typingAvailable={})",
          sid,
          nick,
          tgt.isEmpty() ? "(unknown)" : tgt,
          st,
          prefEnabled,
          typingAvailable);
    } else if (log.isDebugEnabled()) {
      log.debug(
          "[{}] typing observed (repeat): from={} target={} state={}",
          sid,
          nick,
          tgt.isEmpty() ? "(unknown)" : tgt,
          st);
    }
  }

  private void postTo(
      TargetRef dest, TargetRef active, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
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
