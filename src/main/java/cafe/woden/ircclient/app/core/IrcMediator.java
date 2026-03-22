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
import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.app.commands.UserCommandAliasEngine;
import cafe.woden.ircclient.app.outbound.OutboundCommandDispatcher;
import cafe.woden.ircclient.app.outbound.OutboundDccCommandService;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.config.api.IrcSessionRuntimeConfigPort;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.enrichment.UserInfoEnrichmentService;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.CtcpRoutingPort;
import cafe.woden.ircclient.state.api.CtcpRoutingPort.PendingCtcp;
import cafe.woden.ircclient.state.api.LabeledResponseRoutingPort;
import cafe.woden.ircclient.state.api.ModeVocabulary;
import cafe.woden.ircclient.state.api.NegotiatedModeSemantics;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import cafe.woden.ircclient.state.api.ServerIsupportStatePort;
import cafe.woden.ircclient.state.api.WhoisRoutingPort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.Application;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** App mediator. */
@Component
@Lazy
@Application
@ApplicationLayer
@RequiredArgsConstructor
public class IrcMediator implements MediatorControlPort {

  private static final Scheduler IRC_EVENT_PREPARE_SCHEDULER = Schedulers.computation();
  private static final Duration LABELED_RESPONSE_CORRELATION_WINDOW = Duration.ofMinutes(2);
  private static final Duration LABELED_RESPONSE_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration PENDING_ECHO_TIMEOUT = Duration.ofSeconds(45);
  private static final int PENDING_ECHO_TIMEOUT_BATCH_MAX = 64;
  private static final long NETSPLIT_NOTIFY_DEBOUNCE_MS = 20_000L;
  private static final int NETSPLIT_NOTIFY_MAX_KEYS = 512;
  private static final int INBOUND_MSGID_DEDUP_MAX_KEYS = 50_000;
  private static final Duration INBOUND_MSGID_DEDUP_TTL = Duration.ofMinutes(30);
  private static final int INBOUND_MSGID_DEDUP_COUNTER_MAX_KEYS = 4_096;
  private static final Duration INBOUND_MSGID_DEDUP_COUNTER_TTL = Duration.ofHours(6);
  private static final long INBOUND_MSGID_DEDUP_DIAG_MIN_EMIT_MS = 10_000L;

  @Qualifier("ircMediatorInteractionPort")
  private final IrcMediatorInteractionPort irc;

  @NonNull private final IrcNegotiatedFeaturePort negotiatedFeaturePort;
  private final UiPort ui;
  private final CommandParser commandParser;
  private final UserCommandAliasEngine userCommandAliasEngine;
  private final ServerRegistry serverRegistry;
  private final IrcSessionRuntimeConfigPort runtimeConfig;
  private final ConnectionCoordinator connectionCoordinator;
  private final MediatorConnectivityLifecycleOrchestrator mediatorConnectivityLifecycleOrchestrator;
  private final MediatorServerStatusEventHandler mediatorServerStatusEventHandler;
  private final MediatorInviteEventHandler mediatorInviteEventHandler;
  private final MediatorChannelMembershipEventHandler mediatorChannelMembershipEventHandler;
  private final MediatorRosterStatusEventHandler mediatorRosterStatusEventHandler;
  private final MediatorIrcv3PresenceEventHandler mediatorIrcv3PresenceEventHandler;
  private final MediatorIrcv3EventHandler mediatorIrcv3EventHandler;
  private final MediatorConnectionSubscriptionBinder mediatorConnectionSubscriptionBinder;
  private final MediatorUiSubscriptionBinder mediatorUiSubscriptionBinder;
  private final MediatorHistoryIngestOrchestrator mediatorHistoryIngestOrchestrator;
  private final OutboundCommandDispatcher outboundCommandDispatcher;
  private final OutboundDccCommandService outboundDccCommandService;
  private final TargetCoordinator targetCoordinator;
  private final UiSettingsPort uiSettingsPort;
  private final TrayNotificationsPort trayNotificationService;
  private final MediatorInboundEventPreparationService eventPreparationService;
  private final UserInfoEnrichmentService userInfoEnrichmentService;
  private final UserListStore userListStore;
  private final WhoisRoutingPort whoisRoutingState;
  private final CtcpRoutingPort ctcpRoutingState;

  private final LabeledResponseRoutingPort labeledResponseRoutingState;
  private final PendingEchoMessagePort pendingEchoMessageState;

  private final ServerIsupportStatePort serverIsupportState;
  private final InboundModeEventHandler inboundModeEventHandler;
  private final IrcEventNotifierPort ircEventNotifierPort;
  private final InterceptorIngestPort interceptorIngestPort;
  private final MonitorFallbackPort monitorFallbackPort;
  private final ApplicationEventPublisher applicationEventPublisher;

  private final CompositeDisposable disposables = new CompositeDisposable();

  private final java.util.concurrent.atomic.AtomicBoolean started =
      new java.util.concurrent.atomic.AtomicBoolean(false);
  private final MediatorConnectivityLifecycleOrchestrator.Callbacks connectivityLifecycleCallbacks =
      new ConnectivityLifecycleCallbacks();
  private final MediatorServerStatusEventHandler.Callbacks serverStatusEventCallbacks =
      new ServerStatusEventCallbacks();
  private final MediatorInviteEventHandler.Callbacks inviteEventCallbacks =
      new InviteEventCallbacks();
  private final MediatorChannelMembershipEventHandler.Callbacks channelMembershipEventCallbacks =
      new ChannelMembershipEventCallbacks();
  private final MediatorRosterStatusEventHandler.Callbacks rosterStatusEventCallbacks =
      new RosterStatusEventCallbacks();
  private final MediatorIrcv3PresenceEventHandler.Callbacks ircv3PresenceEventCallbacks =
      new Ircv3PresenceEventCallbacks();
  private final MediatorIrcv3EventHandler.Callbacks ircv3EventCallbacks = new Ircv3EventCallbacks();

  // Dedup cache
  private final Map<String, Long> lastNetsplitNotifyAtMs = new ConcurrentHashMap<>();
  private final Cache<InboundMessageDedupKey, Boolean> inboundMessageIdDedup =
      Caffeine.newBuilder()
          .maximumSize(INBOUND_MSGID_DEDUP_MAX_KEYS)
          .expireAfterAccess(INBOUND_MSGID_DEDUP_TTL)
          .build();
  private final Cache<InboundMessageDedupCounterKey, Long>
      inboundMessageIdDedupSuppressedCountByKey =
          Caffeine.newBuilder()
              .maximumSize(INBOUND_MSGID_DEDUP_COUNTER_MAX_KEYS)
              .expireAfterAccess(INBOUND_MSGID_DEDUP_COUNTER_TTL)
              .build();
  private final Cache<InboundMessageDedupCounterKey, Long>
      inboundMessageIdDedupDiagLastEmitMsByKey =
          Caffeine.newBuilder()
              .maximumSize(INBOUND_MSGID_DEDUP_COUNTER_MAX_KEYS)
              .expireAfterAccess(INBOUND_MSGID_DEDUP_COUNTER_TTL)
              .build();
  private final AtomicLong inboundMessageIdDedupSuppressedTotal = new AtomicLong();

  private record InboundMessageDedupKey(
      String serverId, String target, String eventType, String msgId) {}

  private record InboundMessageDedupCounterKey(String serverId, String target, String eventType) {}

  public record InboundMessageDedupDiagnostics(
      String serverId,
      String target,
      String eventType,
      long suppressedCount,
      long suppressedTotal,
      String messageIdSample) {}

  private final class ConnectivityLifecycleCallbacks
      implements MediatorConnectivityLifecycleOrchestrator.Callbacks {
    @Override
    public void failPendingEchoesForServer(String serverId, String reason) {
      IrcMediator.this.failPendingEchoesForServer(serverId, reason);
    }

    @Override
    public void clearNetsplitDebounceForServer(String serverId) {
      IrcMediator.this.clearNetsplitDebounceForServer(serverId);
    }
  }

  private final class ServerStatusEventCallbacks
      implements MediatorServerStatusEventHandler.Callbacks {
    @Override
    public TargetRef safeStatusTarget() {
      return IrcMediator.this.safeStatusTarget();
    }

    @Override
    public void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
      IrcMediator.this.postTo(dest, markUnreadIfNotActive, write);
    }

    @Override
    public void handleStandardReply(String sid, TargetRef status, IrcEvent.StandardReply event) {
      IrcMediator.this.handleStandardReply(sid, status, event);
    }

    @Override
    public void handleServerResponseLine(
        String sid, TargetRef status, IrcEvent.ServerResponseLine event) {
      IrcMediator.this.handleServerResponseLine(sid, status, event);
    }

    @Override
    public TargetRef resolveActiveOrStatus(String sid, TargetRef status) {
      return IrcMediator.this.resolveActiveOrStatus(sid, status);
    }

    @Override
    public void recordInterceptorEvent(
        String serverId,
        String target,
        String actorNick,
        String hostmask,
        String text,
        InterceptorEventType eventType) {
      IrcMediator.this.recordInterceptorEvent(
          serverId, target, actorNick, hostmask, text, eventType);
    }

    @Override
    public String learnedHostmaskForNick(String sid, String nick) {
      return IrcMediator.this.learnedHostmaskForNick(sid, nick);
    }

    @Override
    public boolean notifyIrcEvent(
        IrcEventNotificationRule.EventType eventType,
        String serverId,
        String channel,
        String sourceNick,
        String title,
        String body) {
      return IrcMediator.this.notifyIrcEvent(eventType, serverId, channel, sourceNick, title, body);
    }
  }

  private final class InviteEventCallbacks implements MediatorInviteEventHandler.Callbacks {
    @Override
    public void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
      IrcMediator.this.postTo(dest, markUnreadIfNotActive, write);
    }

    @Override
    public void recordInterceptorEvent(
        String serverId,
        String target,
        String actorNick,
        String hostmask,
        String text,
        InterceptorEventType eventType) {
      IrcMediator.this.recordInterceptorEvent(
          serverId, target, actorNick, hostmask, text, eventType);
    }

    @Override
    public String learnedHostmaskForNick(String sid, String nick) {
      return IrcMediator.this.learnedHostmaskForNick(sid, nick);
    }

    @Override
    public boolean notifyIrcEvent(
        IrcEventNotificationRule.EventType eventType,
        String serverId,
        String channel,
        String sourceNick,
        String title,
        String body) {
      return IrcMediator.this.notifyIrcEvent(eventType, serverId, channel, sourceNick, title, body);
    }

    @Override
    public boolean isMutedChannel(String serverId, String channel) {
      return IrcMediator.this.isMutedChannel(serverId, channel);
    }

    @Override
    public void addDisposable(Disposable disposable) {
      disposables.add(disposable);
    }
  }

  private final class ChannelMembershipEventCallbacks
      implements MediatorChannelMembershipEventHandler.Callbacks {
    @Override
    public void observeChannelActivity(String serverId, String channel) {
      IrcMediator.this.observeChannelActivity(serverId, channel);
    }

    @Override
    public void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
      IrcMediator.this.postTo(dest, markUnreadIfNotActive, write);
    }

    @Override
    public boolean notifyIrcEvent(
        IrcEventNotificationRule.EventType eventType,
        String serverId,
        String channel,
        String sourceNick,
        String title,
        String body) {
      return IrcMediator.this.notifyIrcEvent(eventType, serverId, channel, sourceNick, title, body);
    }

    @Override
    public void recordInterceptorEvent(
        String serverId,
        String target,
        String actorNick,
        String hostmask,
        String text,
        InterceptorEventType eventType) {
      IrcMediator.this.recordInterceptorEvent(
          serverId, target, actorNick, hostmask, text, eventType);
    }

    @Override
    public String learnedHostmaskForNick(String sid, String nick) {
      return IrcMediator.this.learnedHostmaskForNick(sid, nick);
    }

    @Override
    public TargetRef resolveActiveOrStatus(String sid, TargetRef status) {
      return IrcMediator.this.resolveActiveOrStatus(sid, status);
    }

    @Override
    public TargetRef safeStatusTarget() {
      return IrcMediator.this.safeStatusTarget();
    }

    @Override
    public void markPrivateMessagePeerOffline(String serverId, String nick) {
      IrcMediator.this.markPrivateMessagePeerOffline(serverId, nick);
    }

    @Override
    public void maybeNotifyUserKlineFromQuit(String serverId, IrcEvent.UserQuitChannel event) {
      IrcMediator.this.maybeNotifyUserKlineFromQuit(serverId, event);
    }

    @Override
    public void maybeNotifyNetsplitDetected(String serverId, IrcEvent.UserQuitChannel event) {
      IrcMediator.this.maybeNotifyNetsplitDetected(serverId, event);
    }
  }

  private final class RosterStatusEventCallbacks
      implements MediatorRosterStatusEventHandler.Callbacks {
    @Override
    public void markPrivateMessagePeerOnline(String serverId, String nick) {
      IrcMediator.this.markPrivateMessagePeerOnline(serverId, nick);
    }

    @Override
    public void markPrivateMessagePeerOffline(String serverId, String nick) {
      IrcMediator.this.markPrivateMessagePeerOffline(serverId, nick);
    }
  }

  private final class Ircv3PresenceEventCallbacks
      implements MediatorIrcv3PresenceEventHandler.Callbacks {
    @Override
    public boolean isFromSelf(String serverId, String from) {
      return IrcMediator.this.isFromSelf(serverId, from);
    }

    @Override
    public void markPrivateMessagePeerOnline(String serverId, String nick) {
      IrcMediator.this.markPrivateMessagePeerOnline(serverId, nick);
    }

    @Override
    public TargetRef resolveIrcv3Target(String sid, String target, String from, TargetRef status) {
      return IrcMediator.this.resolveIrcv3Target(sid, target, from, status);
    }

    @Override
    public TargetRef resolveActiveOrStatus(String sid, TargetRef status) {
      return IrcMediator.this.resolveActiveOrStatus(sid, status);
    }
  }

  private final class Ircv3EventCallbacks implements MediatorIrcv3EventHandler.Callbacks {
    @Override
    public TargetRef resolveIrcv3Target(String sid, String target, String from, TargetRef status) {
      return IrcMediator.this.resolveIrcv3Target(sid, target, from, status);
    }
  }

  @PostConstruct
  void init() {
    start();
  }

  @PreDestroy
  void shutdown() {
    stop();
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
        this::handleOutgoingLine,
        this::handleBackendNamedCommandRequest);
    bindIrcEventSubscriptions();
    bindLabeledResponseTimeoutTicker();
    bindIrcv3CapabilityToggleSubscriptions();
    mediatorConnectionSubscriptionBinder.bind(
        ui, connectionCoordinator, targetCoordinator, serverRegistry, disposables);
  }

  private void bindIrcEventSubscriptions() {
    disposables.add(
        offloadSelectedEventProcessing(
                irc.events(),
                eventPreparationService::shouldPrepareOffEdt,
                eventPreparationService::prepare,
                IRC_EVENT_PREPARE_SCHEDULER,
                AppSchedulers.edt())
            .subscribe(
                this::onServerIrcEvent,
                err ->
                    ui.appendError(
                        targetCoordinator.safeStatusTarget(), "(irc-error)", err.toString())));
  }

  static <T, R> Flowable<R> offloadSelectedEventProcessing(
      Flowable<T> events,
      Predicate<T> shouldOffload,
      Function<T, R> mapper,
      Scheduler offloadScheduler,
      Scheduler observeScheduler) {
    Objects.requireNonNull(events, "events");
    Objects.requireNonNull(shouldOffload, "shouldOffload");
    Objects.requireNonNull(mapper, "mapper");
    Objects.requireNonNull(offloadScheduler, "offloadScheduler");
    Objects.requireNonNull(observeScheduler, "observeScheduler");
    return events
        .concatMap(
            event ->
                shouldOffload.test(event)
                    ? Single.fromCallable(
                            () -> Objects.requireNonNull(mapper.apply(event), "mapped"))
                        .subscribeOn(offloadScheduler)
                        .toFlowable()
                    : Flowable.fromCallable(
                        () -> Objects.requireNonNull(mapper.apply(event), "mapped")))
        .observeOn(observeScheduler);
  }

  private void bindLabeledResponseTimeoutTicker() {
    disposables.add(
        timeoutMaintenanceTicks(Schedulers.computation(), AppSchedulers.edt())
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

  static Flowable<Long> timeoutMaintenanceTicks(
      Scheduler intervalScheduler, Scheduler observeScheduler) {
    Objects.requireNonNull(intervalScheduler, "intervalScheduler");
    Objects.requireNonNull(observeScheduler, "observeScheduler");
    return Flowable.interval(5, 5, TimeUnit.SECONDS, intervalScheduler)
        .onBackpressureLatest()
        .observeOn(observeScheduler);
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

  private ParsedCtcp parseCtcp(String text) {
    return eventPreparationService.parseCtcp(text);
  }

  private void observeChannelActivity(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return;
    try {
      TargetRef target = new TargetRef(sid, ch);
      if (!target.isChannel()) return;
    } catch (IllegalArgumentException ignored) {
      return;
    }
    targetCoordinator.onChannelActivityObserved(sid, ch);
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
        if (!dest.equals(targetCoordinator.getActiveTarget()) && !isMutedChannel(dest)) {
          ui.markUnread(dest);
        }
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

  @Override
  public void connectAutoConnectOnStartServers() {
    connectionCoordinator.connectAutoConnectOnStartServers();
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

  private void handleBackendNamedCommandRequest(ParsedInput.BackendNamed command) {
    if (command == null) return;
    outboundCommandDispatcher.dispatch(disposables, command);
  }

  private TargetRef activeTargetForServerOrStatus(String sid, TargetRef status) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active != null && Objects.equals(active.serverId(), sid)) {
      return active;
    }
    return status;
  }

  private void onServerIrcEvent(ServerIrcEvent se) {
    onServerIrcEvent(eventPreparationService.prepare(se));
  }

  private void onServerIrcEvent(PreparedServerIrcEvent prepared) {
    if (prepared == null) return;
    ServerIrcEvent se = prepared.event();
    if (se == null) return;

    String sid = se.serverId();
    IrcEvent e = se.event();

    TargetRef status = new TargetRef(sid, "status");
    if (mediatorConnectivityLifecycleOrchestrator.isConnectivityLifecycleEvent(e)) {
      mediatorConnectivityLifecycleOrchestrator.handleConnectivityLifecycleEvent(
          connectivityLifecycleCallbacks, sid, e);
      return;
    }

    switch (e) {
      case IrcEvent.NickChanged ev -> {
        mediatorServerStatusEventHandler.handleNickChanged(sid, status, ev);
      }
      case IrcEvent.ChannelMessage ev -> {
        handleChannelMessage(sid, prepared, ev);
      }
      case IrcEvent.ChannelAction ev -> {
        handleChannelAction(sid, prepared, ev);
      }
      case IrcEvent.ChannelModeObserved ev -> {
        handleChannelModeObserved(sid, ev);
      }

      case IrcEvent.ChannelTopicUpdated ev -> {
        handleChannelTopicUpdated(sid, ev);
      }

      case IrcEvent.PrivateMessage ev -> {
        handlePrivateMessage(sid, prepared, ev);
      }

      case IrcEvent.PrivateAction ev -> {
        handlePrivateAction(sid, prepared, ev);
      }
      case IrcEvent.Notice ev -> {
        handleNotice(sid, status, prepared, ev);
      }
      case IrcEvent.WallopsReceived ev -> {
        mediatorServerStatusEventHandler.handleWallopsReceived(
            serverStatusEventCallbacks, sid, status, ev);
      }
      case IrcEvent.ServerTimeNotNegotiated ev -> {
        mediatorServerStatusEventHandler.handleServerTimeNotNegotiated(
            serverStatusEventCallbacks, sid, status, ev);
      }
      case IrcEvent.StandardReply ev -> {
        mediatorServerStatusEventHandler.handleStandardReplyEvent(
            serverStatusEventCallbacks, sid, status, ev);
      }
      case IrcEvent.ChannelListStarted ev -> {
        mediatorServerStatusEventHandler.handleChannelListStarted(sid, ev);
      }
      case IrcEvent.ChannelListEntry ev -> {
        mediatorServerStatusEventHandler.handleChannelListEntry(sid, ev);
      }
      case IrcEvent.ChannelListEnded ev -> {
        mediatorServerStatusEventHandler.handleChannelListEnded(sid, ev);
      }
      case IrcEvent.ChannelBanListStarted ev -> {
        mediatorServerStatusEventHandler.handleChannelBanListStarted(sid, ev);
      }
      case IrcEvent.ChannelBanListEntry ev -> {
        mediatorServerStatusEventHandler.handleChannelBanListEntry(sid, ev);
      }
      case IrcEvent.ChannelBanListEnded ev -> {
        mediatorServerStatusEventHandler.handleChannelBanListEnded(sid, ev);
      }
      case cafe.woden.ircclient.irc.IrcEvent.ServerResponseLine ev -> {
        mediatorServerStatusEventHandler.handleServerResponseLineEvent(
            serverStatusEventCallbacks, sid, status, ev);
      }
      case IrcEvent.ChatHistoryBatchReceived ev -> {
        handleChatHistoryBatchReceived(sid, ev);
      }

      case IrcEvent.ZncPlaybackBatchReceived ev -> {
        handleZncPlaybackBatchReceived(sid, ev);
      }
      case IrcEvent.CtcpRequestReceived ev -> {
        handleCtcpRequest(sid, status, prepared, ev);
      }
      case IrcEvent.AwayStatusChanged ev -> {
        mediatorServerStatusEventHandler.handleAwayStatusChanged(
            serverStatusEventCallbacks, sid, status, ev);
      }
      case IrcEvent.WhoisResult ev -> {
        mediatorServerStatusEventHandler.handleWhoisResult(
            serverStatusEventCallbacks, sid, status, ev);
      }

      case IrcEvent.InvitedToChannel ev -> {
        mediatorInviteEventHandler.handleInvitedToChannel(inviteEventCallbacks, sid, status, ev);
      }

      case IrcEvent.UserJoinedChannel ev -> {
        mediatorChannelMembershipEventHandler.handleUserJoinedChannel(
            channelMembershipEventCallbacks, sid, ev);
      }

      case IrcEvent.UserPartedChannel ev -> {
        mediatorChannelMembershipEventHandler.handleUserPartedChannel(
            channelMembershipEventCallbacks, sid, ev);
      }

      case IrcEvent.LeftChannel ev -> {
        mediatorChannelMembershipEventHandler.handleLeftChannel(sid, ev);
      }

      case IrcEvent.UserKickedFromChannel ev -> {
        mediatorChannelMembershipEventHandler.handleUserKickedFromChannel(
            channelMembershipEventCallbacks, sid, ev);
      }

      case IrcEvent.KickedFromChannel ev -> {
        mediatorChannelMembershipEventHandler.handleKickedFromChannel(
            channelMembershipEventCallbacks, sid, ev);
      }

      case IrcEvent.UserQuitChannel ev -> {
        mediatorChannelMembershipEventHandler.handleUserQuitChannel(
            channelMembershipEventCallbacks, sid, ev);
      }

      case IrcEvent.UserNickChangedChannel ev -> {
        mediatorChannelMembershipEventHandler.handleUserNickChangedChannel(
            channelMembershipEventCallbacks, sid, ev);
      }

      case IrcEvent.ChannelRedirected ev -> {
        mediatorChannelMembershipEventHandler.handleChannelRedirected(sid, ev);
      }

      case IrcEvent.JoinedChannel ev -> {
        mediatorChannelMembershipEventHandler.handleJoinedChannel(sid, ev);
      }

      case IrcEvent.JoinFailed ev -> {
        mediatorChannelMembershipEventHandler.handleJoinFailed(
            channelMembershipEventCallbacks, sid, status, ev);
      }

      case IrcEvent.NickListUpdated ev -> {
        handleNickListUpdated(sid, ev);
      }

      case IrcEvent.UserHostmaskObserved ev -> {
        handleUserHostmaskObserved(sid, ev);
      }

      case IrcEvent.UserHostChanged ev -> {
        mediatorRosterStatusEventHandler.handleUserHostChanged(sid, status, ev);
      }

      case IrcEvent.UserAwayStateObserved ev -> {
        mediatorRosterStatusEventHandler.handleUserAwayStateObserved(
            rosterStatusEventCallbacks, sid, ev);
      }

      case IrcEvent.UserAccountStateObserved ev -> {
        mediatorRosterStatusEventHandler.handleUserAccountStateObserved(
            rosterStatusEventCallbacks, sid, ev);
      }

      case IrcEvent.MonitorOnlineObserved ev -> {
        mediatorRosterStatusEventHandler.handleMonitorOnlineObserved(
            rosterStatusEventCallbacks, sid, ev);
      }

      case IrcEvent.MonitorOfflineObserved ev -> {
        mediatorRosterStatusEventHandler.handleMonitorOfflineObserved(
            rosterStatusEventCallbacks, sid, ev);
      }

      case IrcEvent.MonitorListObserved ev -> {
        mediatorRosterStatusEventHandler.handleMonitorListObserved(sid, ev);
      }

      case IrcEvent.MonitorListEnded ev -> {
        mediatorRosterStatusEventHandler.handleMonitorListEnded(sid, ev);
      }

      case IrcEvent.MonitorListFull ev -> {
        mediatorRosterStatusEventHandler.handleMonitorListFull(sid, ev);
      }

      case IrcEvent.UserSetNameObserved ev -> {
        mediatorRosterStatusEventHandler.handleUserSetNameObserved(sid, status, ev);
      }

      case IrcEvent.UserTypingObserved ev -> {
        mediatorIrcv3PresenceEventHandler.handleUserTypingObserved(
            ircv3PresenceEventCallbacks, sid, status, ev);
      }

      case IrcEvent.ReadMarkerObserved ev -> {
        mediatorIrcv3PresenceEventHandler.handleReadMarkerObserved(
            ircv3PresenceEventCallbacks, sid, status, ev);
      }

      case IrcEvent.MessageReplyObserved ev -> {
        // Reply context is rendered inline from IRCv3 tags on the message line itself.
      }

      case IrcEvent.MessageReactObserved ev -> {
        mediatorIrcv3EventHandler.handleMessageReactObserved(ircv3EventCallbacks, sid, status, ev);
      }

      case IrcEvent.MessageUnreactObserved ev -> {
        mediatorIrcv3EventHandler.handleMessageUnreactObserved(
            ircv3EventCallbacks, sid, status, ev);
      }

      case IrcEvent.MessageRedactionObserved ev -> {
        mediatorIrcv3EventHandler.handleMessageRedactionObserved(
            ircv3EventCallbacks, sid, status, ev);
      }

      case IrcEvent.Ircv3CapabilityChanged ev -> {
        mediatorIrcv3EventHandler.handleIrcv3CapabilityChanged(sid, status, ev);
      }

      case IrcEvent.Error ev -> {
        handleError(sid, status, ev);
      }

      default -> {}
    }
  }

  private void handleChannelModeObserved(String sid, IrcEvent.ChannelModeObserved ev) {
    observeChannelActivity(sid, ev.channel());
    inboundModeEventHandler.handleChannelModeObserved(sid, ev);
    if (ev.kind() == IrcEvent.ChannelModeKind.DELTA) {
      maybeNotifyModeEvents(sid, ev);
      recordInterceptorEvent(
          sid,
          ev.channel(),
          ev.by(),
          learnedHostmaskForNick(sid, ev.by()),
          ev.details(),
          InterceptorEventType.MODE);
    }
  }

  private void handleChannelTopicUpdated(String sid, IrcEvent.ChannelTopicUpdated ev) {
    observeChannelActivity(sid, ev.channel());
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

  private void handleChatHistoryBatchReceived(String sid, IrcEvent.ChatHistoryBatchReceived ev) {
    observeChannelActivity(sid, ev.target());
    mediatorHistoryIngestOrchestrator.onChatHistoryBatchReceived(sid, ev);
  }

  private void handleZncPlaybackBatchReceived(String sid, IrcEvent.ZncPlaybackBatchReceived ev) {
    mediatorHistoryIngestOrchestrator.onZncPlaybackBatchReceived(sid, ev);
  }

  private void handleNickListUpdated(String sid, IrcEvent.NickListUpdated ev) {
    observeChannelActivity(sid, ev.channel());
    inboundModeEventHandler.onNickListUpdated(sid, ev.channel());
    targetCoordinator.onNickListUpdated(sid, ev);
  }

  private void handleUserHostmaskObserved(String sid, IrcEvent.UserHostmaskObserved ev) {
    targetCoordinator.onUserHostmaskObserved(sid, ev);
  }

  private void handleChannelMessage(
      String sid, PreparedServerIrcEvent prepared, IrcEvent.ChannelMessage ev) {
    observeChannelActivity(sid, ev.channel());
    TargetRef chan = new TargetRef(sid, ev.channel());
    TargetRef active = targetCoordinator.getActiveTarget();
    PreparedChannelText channelText = prepared.channelText();
    NotificationRuleMatch ruleMatch = channelText.ruleMatch();

    userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());

    InboundIgnorePolicyPort.Decision decision = channelText.decision();
    if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) return;

    if (tryResolvePendingEchoChannelMessage(sid, chan, active, ev)) {
      return;
    }

    if (maybeApplyMessageEditFromTaggedLine(
        sid, chan, ev.at(), ev.from(), ev.text(), ev.messageId(), ev.ircv3Tags())) {
      return;
    }
    if (shouldSuppressInboundDuplicateByMsgId(
        sid, chan, "channel-message", ev.messageId(), ev.ircv3Tags())) {
      return;
    }

    clearRemoteTypingIndicatorsForSender(chan, ev.from());

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

    if (channelText.mention()) {
      recordInterceptorEvent(
          sid,
          ev.channel(),
          ev.from(),
          learnedHostmaskForNick(sid, ev.from()),
          ev.text(),
          InterceptorEventType.HIGHLIGHT);
      recordMentionHighlight(chan, active, ev.from(), ev.text());

      if (!isMutedChannel(chan)) {
        try {
          trayNotificationService.notifyHighlight(sid, ev.channel(), ev.from(), ev.text());
        } catch (Exception ignored) {
        }
      }
    }
  }

  private void handleChannelAction(
      String sid, PreparedServerIrcEvent prepared, IrcEvent.ChannelAction ev) {
    observeChannelActivity(sid, ev.channel());
    TargetRef chan = new TargetRef(sid, ev.channel());
    TargetRef active = targetCoordinator.getActiveTarget();
    PreparedChannelText channelText = prepared.channelText();
    NotificationRuleMatch ruleMatch = channelText.ruleMatch();

    userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());

    InboundIgnorePolicyPort.Decision decision = channelText.decision();
    if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) return;
    if (shouldSuppressInboundDuplicateByMsgId(
        sid, chan, "channel-action", ev.messageId(), ev.ircv3Tags())) {
      return;
    }

    clearRemoteTypingIndicatorsForSender(chan, ev.from());

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

    if (channelText.mention()) {
      recordInterceptorEvent(
          sid,
          ev.channel(),
          ev.from(),
          learnedHostmaskForNick(sid, ev.from()),
          ev.action(),
          InterceptorEventType.HIGHLIGHT);
      recordMentionHighlight(chan, active, ev.from(), "* " + ev.action());

      if (!isMutedChannel(chan)) {
        try {
          trayNotificationService.notifyHighlight(sid, ev.channel(), ev.from(), "* " + ev.action());
        } catch (Exception ignored) {
        }
      }
    }
  }

  private void handlePrivateMessage(
      String sid, PreparedServerIrcEvent prepared, IrcEvent.PrivateMessage ev) {
    PreparedPrivateMessage privateMessage = prepared.privateMessage();
    boolean fromSelf = privateMessage.fromSelf();
    String peer = privateMessage.peer();
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

    ParsedCtcp ctcp = privateMessage.ctcp();
    if (!fromSelf && ctcp != null && "DCC".equals(ctcp.commandUpper())) {
      InboundIgnorePolicyPort.Decision dccDecision = privateMessage.dccDecision();
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

    InboundIgnorePolicyPort.Decision decision = privateMessage.decision();
    if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) return;

    if (tryResolvePendingEchoPrivateMessage(sid, pm, ev, allowAutoOpen)) {
      return;
    }

    if (maybeApplyMessageEditFromTaggedLine(
        sid, pm, ev.at(), ev.from(), ev.text(), ev.messageId(), ev.ircv3Tags())) {
      return;
    }
    if (shouldSuppressInboundDuplicateByMsgId(
        sid, pm, "private-message", ev.messageId(), ev.ircv3Tags())) {
      return;
    }

    if (!fromSelf) {
      clearRemoteTypingIndicatorsForSender(pm, ev.from());
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
        ui.appendSpoilerChatAt(pm, ev.at(), ev.from(), ev.text(), ev.messageId(), ev.ircv3Tags());
      }
    } else {
      if (allowAutoOpen) {
        postTo(
            pm,
            true,
            d ->
                ui.appendChatAt(
                    d, ev.at(), ev.from(), ev.text(), fromSelf, ev.messageId(), ev.ircv3Tags()));
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
      String title = fromNick.isEmpty() ? "Private message" : ("Private message from " + fromNick);
      maybeNotifyInboundPrivateConversation(
          sid,
          fromNick,
          title,
          Objects.toString(ev.text(), "").trim(),
          Objects.toString(ev.text(), ""));
    }
  }

  private void handlePrivateAction(
      String sid, PreparedServerIrcEvent prepared, IrcEvent.PrivateAction ev) {
    PreparedPrivateAction privateAction = prepared.privateAction();
    boolean fromSelf = privateAction.fromSelf();
    String peer = privateAction.peer();
    TargetRef pm = new TargetRef(sid, peer);
    boolean allowAutoOpen = targetCoordinator.allowPrivateAutoOpenFromInbound(pm, fromSelf);

    if (!fromSelf) {
      userInfoEnrichmentService.noteUserActivity(sid, ev.from(), ev.at());
      markPrivateMessagePeerOnline(sid, ev.from());
    }

    InboundIgnorePolicyPort.Decision decision = privateAction.decision();
    if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) return;
    if (shouldSuppressInboundDuplicateByMsgId(
        sid, pm, "private-action", ev.messageId(), ev.ircv3Tags())) {
      return;
    }

    if (!fromSelf) {
      clearRemoteTypingIndicatorsForSender(pm, ev.from());
    }

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
                    d, ev.at(), ev.from(), ev.action(), fromSelf, ev.messageId(), ev.ircv3Tags()));
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
      String title = fromNick.isEmpty() ? "Private action" : ("Private action from " + fromNick);
      String body = "* " + Objects.toString(ev.action(), "").trim();
      maybeNotifyInboundPrivateConversation(sid, fromNick, title, body, "* " + ev.action());
    }
  }

  private void handleNotice(
      String sid, TargetRef status, PreparedServerIrcEvent prepared, IrcEvent.Notice ev) {
    PreparedNotice notice = prepared.notice();
    boolean fromSelf = notice.fromSelf();
    markPrivateMessagePeerOnline(sid, ev.from());
    InboundIgnorePolicyPort.Decision decision = notice.decision();
    boolean spoiler = decision == InboundIgnorePolicyPort.Decision.SOFT_SPOILER;
    boolean suppress = decision == InboundIgnorePolicyPort.Decision.HARD_DROP;
    TargetRef dest = resolveNoticeDestination(sid, status, ev);

    if (maybeApplyMessageEditFromTaggedLine(
        sid, dest, ev.at(), ev.from(), ev.text(), ev.messageId(), ev.ircv3Tags())) {
      return;
    }
    if (shouldSuppressInboundDuplicateByMsgId(
        sid, dest, "notice", ev.messageId(), ev.ircv3Tags())) {
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

    String noticeChannel = notice.noticeChannel();
    recordInterceptorEvent(
        sid,
        noticeChannel.isBlank() ? "status" : noticeChannel,
        ev.from(),
        learnedHostmaskForNick(sid, ev.from()),
        ev.text(),
        InterceptorEventType.NOTICE);

    if (!fromSelf && !suppress) {
      String fromNick = Objects.toString(ev.from(), "").trim();
      String title = fromNick.isEmpty() ? "Notice" : ("Notice from " + fromNick);
      String channel = noticeChannel.isBlank() ? null : noticeChannel;
      notifyIrcEvent(
          IrcEventNotificationRule.EventType.NOTICE_RECEIVED,
          sid,
          channel,
          fromNick,
          title,
          Objects.toString(ev.text(), "").trim());
    }
  }

  private void handleCtcpRequest(
      String sid,
      TargetRef status,
      PreparedServerIrcEvent prepared,
      IrcEvent.CtcpRequestReceived ev) {
    PreparedCtcpRequest ctcpRequest = prepared.ctcpRequest();
    String command = ctcpRequest.command();
    String argument = ctcpRequest.argument();
    String ctcpText = ctcpRequest.normalizedText();
    InboundIgnorePolicyPort.Decision decision = ctcpRequest.decision();
    if (decision == InboundIgnorePolicyPort.Decision.HARD_DROP) return;

    TargetRef dest = resolveCtcpRequestDestination(sid, status, ev);
    maybeMarkPrivateMessagePeerOnlineForCtcp(sid, ev, dest);

    StringBuilder sb =
        new StringBuilder()
            .append("\u2190 ")
            .append(ev.from())
            .append(" CTCP ")
            .append(command.isBlank() ? Objects.toString(ev.command(), "").trim() : command);
    if (!argument.isBlank()) sb.append(' ').append(argument);
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
        ctcpText.isBlank() ? "CTCP" : ctcpText,
        InterceptorEventType.CTCP);

    String fromNick = Objects.toString(ev.from(), "").trim();
    String channel = Objects.toString(ev.channel(), "").trim();
    if (channel.isBlank()) channel = null;
    String title =
        fromNick.isEmpty()
            ? "CTCP request received"
            : ("CTCP from " + fromNick + (channel == null ? "" : (" in " + channel)));
    String body = command.isEmpty() ? "CTCP request" : command;
    if (!argument.isEmpty()) body = body + " " + argument;

    notifyIrcEvent(
        IrcEventNotificationRule.EventType.CTCP_RECEIVED,
        sid,
        channel,
        fromNick,
        title,
        body,
        command,
        argument);
  }

  private void maybeNotifyInboundPrivateConversation(
      String sid, String fromNick, String title, String notifyBody, String trayBody) {
    boolean customPmNotified =
        notifyIrcEvent(
            IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED,
            sid,
            null,
            fromNick,
            title,
            notifyBody);
    boolean pmRulesEnabled =
        ircEventNotifierPort != null
            && ircEventNotifierPort.hasEnabledRuleFor(
                IrcEventNotificationRule.EventType.PRIVATE_MESSAGE_RECEIVED);
    if (!customPmNotified && !pmRulesEnabled) {
      try {
        trayNotificationService.notifyPrivateMessage(sid, fromNick, trayBody);
      } catch (Exception ignored) {
      }
    }
  }

  private TargetRef resolveNoticeDestination(String sid, TargetRef status, IrcEvent.Notice ev) {
    TargetRef dest = null;
    String target = ev.target();
    String from = Objects.toString(ev.from(), "").trim();
    boolean serverNotice = from.isEmpty() || "server".equalsIgnoreCase(from);
    if (serverNotice) {
      if (target != null && !target.isBlank()) {
        TargetRef noticeTarget = new TargetRef(sid, target);
        if (noticeTarget.isChannel()) {
          dest = noticeTarget;
        }
      }
      if (dest == null) {
        dest = status != null ? status : safeStatusTarget();
      }
    } else if (target != null && !target.isBlank()) {
      TargetRef noticeTarget = new TargetRef(sid, target);
      if (noticeTarget.isChannel()) {
        dest = noticeTarget;
      }
    }
    if (dest == null) {
      dest = activeTargetForServerOrStatus(sid, status);
    }
    return dest;
  }

  private TargetRef resolveCtcpRequestDestination(
      String sid, TargetRef status, IrcEvent.CtcpRequestReceived ev) {
    if (uiSettingsPort.get().ctcpRequestsInActiveTargetEnabled()) {
      return resolveActiveOrStatus(sid, status);
    }
    if (ev.channel() != null && !ev.channel().isBlank()) {
      return new TargetRef(sid, ev.channel());
    }
    if (ev.from() != null && !ev.from().isBlank()) {
      return new TargetRef(sid, ev.from());
    }
    return status != null ? status : safeStatusTarget();
  }

  private void handleError(String sid, TargetRef status, IrcEvent.Error ev) {
    TargetRef dest = status != null ? status : safeStatusTarget();
    connectionCoordinator.noteConnectionError(sid, ev.message());
    ui.appendError(dest, "(error)", ev.message());
    recordInterceptorEvent(sid, "status", "server", "", ev.message(), InterceptorEventType.ERROR);
    maybeNotifyKline(sid, ev.message(), "Server restriction");
  }

  private boolean notifyIrcEvent(
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      String title,
      String body) {
    return notifyIrcEvent(eventType, serverId, channel, sourceNick, title, body, null, null);
  }

  private boolean notifyIrcEvent(
      IrcEventNotificationRule.EventType eventType,
      String serverId,
      String channel,
      String sourceNick,
      String title,
      String body,
      String ctcpCommand,
      String ctcpValue) {
    if (eventType == null || ircEventNotifierPort == null) return false;
    String sid = Objects.toString(serverId, "").trim();
    if (sid.isEmpty()) return false;
    if (isMutedChannel(sid, channel)) return false;

    String src = Objects.toString(sourceNick, "").trim();
    Boolean sourceIsSelf = src.isEmpty() ? null : isFromSelf(sid, src);
    TargetRef active = targetCoordinator != null ? targetCoordinator.getActiveTarget() : null;
    String activeSid = active != null ? active.serverId() : null;
    String activeTgt = active != null ? active.target() : null;
    try {
      return ircEventNotifierPort.notifyConfigured(
          eventType,
          sid,
          channel,
          src,
          sourceIsSelf,
          title,
          body,
          activeSid,
          activeTgt,
          ctcpCommand,
          ctcpValue);
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

  private void maybeNotifyModeEvents(String serverId, IrcEvent.ChannelModeObserved ev) {
    if (serverId == null || ev == null) return;

    String channel = Objects.toString(ev.channel(), "").trim();
    if (channel.isEmpty()) return;

    String actor = Objects.toString(ev.by(), "").trim();
    String by = actor.isEmpty() ? "Someone" : actor;

    for (ModeChangeToken ch : parseModeChanges(serverId, ev.details())) {
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

  private List<ModeChangeToken> parseModeChanges(String serverId, String details) {
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
    ModeVocabulary vocabulary = serverIsupportState.vocabularyForServer(serverId);
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
      if (NegotiatedModeSemantics.takesArgument(vocabulary, c, add) && argIdx < args.size()) {
        arg = args.get(argIdx++);
      }
      out.add(new ModeChangeToken(add, c, arg));
    }
    return out;
  }

  private record ModeChangeToken(boolean add, char mode, String arg) {}

  private void recordRuleMatchIfPresent(
      TargetRef chan, TargetRef active, String from, String text, NotificationRuleMatch match) {
    if (chan == null || match == null) return;
    if (isMutedChannel(chan)) return;
    if (active == null || !chan.equals(active)) {
      ui.markHighlight(chan);
    }
    ui.recordRuleMatch(
        chan, from, match.ruleLabel(), snippetAround(text, match.start(), match.end()));
  }

  private void recordMentionHighlight(
      TargetRef chan, TargetRef active, String fromNick, String snippet) {
    if (chan == null) return;
    if (isMutedChannel(chan)) return;
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
    if (!negotiatedFeaturePort.isMessageEditAvailable(sid)) return false;

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
    for (PendingEchoMessagePort.PendingOutboundChat pending :
        pendingEchoMessageState.drainServer(sid)) {
      TargetRef target = pending.target();
      if (target == null) continue;
      ui.failPendingOutgoingChat(
          target, pending.pendingId(), now, pending.fromNick(), pending.text(), reason);
    }
  }

  private void maybeHandlePendingPrivateMessageDeliveryError(
      String sid, IrcEvent.ServerResponseLine ev) {
    if (sid == null || sid.isBlank() || ev == null) return;
    if (ev.code() != 401) return; // ERR_NOSUCHNICK

    ParsedIrcLine pl = parseIrcLineForMetadata(ev.rawLine());
    if (pl == null) return;
    if (pl.params() == null || pl.params().size() < 2) return;

    String targetToken = Objects.toString(pl.params().get(1), "").trim();
    if (targetToken.isEmpty()) return;

    final TargetRef pmTarget;
    try {
      pmTarget = new TargetRef(sid, targetToken);
    } catch (IllegalArgumentException ignored) {
      return;
    }
    if (pmTarget.isChannel() || pmTarget.isUiOnly() || pmTarget.isStatus()) return;

    PendingEchoMessagePort.PendingOutboundChat pending =
        pendingEchoMessageState.consumeOldestByTarget(pmTarget).orElse(null);
    if (pending == null) return;

    String reason = Objects.toString(pl.trailing(), "").trim();
    if (reason.isEmpty()) {
      reason = Objects.toString(ev.message(), "").trim();
    }
    if (reason.isEmpty()) {
      reason = "No such nick/channel";
    }

    String pendingReason = "[" + ev.code() + "] " + reason;
    ui.failPendingOutgoingChat(
        pmTarget, pending.pendingId(), ev.at(), pending.fromNick(), pending.text(), pendingReason);

    ensureTargetExists(pmTarget);
    ui.appendErrorAt(
        pmTarget,
        ev.at(),
        "(send)",
        "Cannot deliver to " + pmTarget.target() + " [" + ev.code() + "]: " + reason);
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
          serverIsupportState.applyIsupportToken(sid, tok.substring(1), null);
          continue;
        }

        int eq = tok.indexOf('=');
        if (eq >= 0) {
          String key = tok.substring(0, eq).trim();
          String value = tok.substring(eq + 1).trim();
          if (!key.isEmpty()) {
            ui.setServerIsupportToken(sid, key, value);
            serverIsupportState.applyIsupportToken(sid, key, value);
          }
          continue;
        }

        // Tokens without "=" still represent support (for example WHOX).
        ui.setServerIsupportToken(sid, tok, "");
        serverIsupportState.applyIsupportToken(sid, tok, "");
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
    maybeHandlePendingPrivateMessageDeliveryError(sid, ev);
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
      LabeledResponseRoutingPort.PendingLabeledRequest pending =
          labeledResponseRoutingState.findIfFresh(sid, label, LABELED_RESPONSE_CORRELATION_WINDOW);
      if (pending != null && pending.originTarget() != null) {
        TargetRef dest = normalizeLabeledDestination(sid, status, pending.originTarget());
        LabeledResponseRoutingPort.PendingLabeledRequest transitioned =
            labeledResponseRoutingState.markOutcomeIfPending(
                sid, label, LabeledResponseRoutingPort.Outcome.SUCCESS, ev.at());
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
      LabeledResponseRoutingPort.PendingLabeledRequest pending =
          labeledResponseRoutingState.findIfFresh(sid, label, LABELED_RESPONSE_CORRELATION_WINDOW);
      if (pending != null && pending.originTarget() != null) {
        TargetRef dest = normalizeLabeledDestination(sid, status, pending.originTarget());
        LabeledResponseRoutingPort.Outcome outcome =
            (ev.kind() == IrcEvent.StandardReplyKind.FAIL)
                ? LabeledResponseRoutingPort.Outcome.FAILURE
                : LabeledResponseRoutingPort.Outcome.SUCCESS;
        LabeledResponseRoutingPort.PendingLabeledRequest transitioned =
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
    List<LabeledResponseRoutingPort.TimedOutLabeledRequest> timedOut =
        labeledResponseRoutingState.collectTimedOut(LABELED_RESPONSE_TIMEOUT, 32);
    if (timedOut == null || timedOut.isEmpty()) return;
    for (LabeledResponseRoutingPort.TimedOutLabeledRequest timeout : timedOut) {
      if (timeout == null || timeout.request() == null) continue;
      TargetRef status = new TargetRef(timeout.serverId(), "status");
      TargetRef dest =
          normalizeLabeledDestination(timeout.serverId(), status, timeout.request().originTarget());
      appendLabeledOutcome(
          dest,
          timeout.timedOutAt(),
          timeout.label(),
          timeout.request().requestPreview(),
          LabeledResponseRoutingPort.Outcome.TIMEOUT,
          "no reply within " + LABELED_RESPONSE_TIMEOUT.toSeconds() + "s");
    }
  }

  private void handlePendingEchoTimeouts() {
    Instant now = Instant.now();
    List<PendingEchoMessagePort.PendingOutboundChat> timedOut =
        pendingEchoMessageState.collectTimedOut(
            PENDING_ECHO_TIMEOUT, PENDING_ECHO_TIMEOUT_BATCH_MAX, now);
    if (timedOut == null || timedOut.isEmpty()) return;

    String reason =
        "Timed out waiting for server echo after " + PENDING_ECHO_TIMEOUT.toSeconds() + "s";
    for (PendingEchoMessagePort.PendingOutboundChat pending : timedOut) {
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
      LabeledResponseRoutingPort.Outcome outcome,
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
    return eventPreparationService.isFromSelf(serverId, from);
  }

  private void markPrivateMessagePeerOnline(String serverId, String rawNick) {
    String nick = normalizePrivateMessagePeer(rawNick);
    if (nick.isEmpty()) return;
    if (isFromSelf(serverId, nick)) return;
    ui.setPrivateMessageOnlineState(serverId, nick, true);
  }

  private void maybeMarkPrivateMessagePeerOnlineForCtcp(
      String serverId, IrcEvent.CtcpRequestReceived event, TargetRef destination) {
    if (event == null || destination == null) return;
    if (event.channel() != null && !event.channel().isBlank()) return;

    String from = normalizePrivateMessagePeer(event.from());
    if (from.isEmpty()) return;

    String destinationPeer = normalizePrivateMessagePeer(destination.target());
    if (destinationPeer.isEmpty()) return;
    if (!from.equalsIgnoreCase(destinationPeer)) return;

    markPrivateMessagePeerOnline(serverId, from);
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

  private static String normalizeNickForCompare(String raw) {
    return MediatorInboundEventPreparationService.normalizeNickForCompare(raw);
  }

  private static String normalizePrivateMessagePeer(String raw) {
    String n = normalizeNickForCompare(raw);
    n = Objects.toString(n, "").trim();
    if (n.isEmpty()) return "";
    if ("server".equalsIgnoreCase(n)) return "";
    if (n.startsWith("*")) return "";
    return n;
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

  private boolean shouldSuppressInboundDuplicateByMsgId(
      String sid, TargetRef target, String eventType, String messageId, Map<String, String> tags) {
    if (hasMessageMutationTag(tags)) return false;

    String msgId = effectiveMessageIdForDedup(messageId, tags);
    if (msgId.isBlank()) return false;

    String sidKey = Objects.toString(sid, "").trim().toLowerCase(Locale.ROOT);
    String targetKey = normalizeDedupTarget(target);
    String eventKey = Objects.toString(eventType, "").trim().toLowerCase(Locale.ROOT);
    InboundMessageDedupKey key = new InboundMessageDedupKey(sidKey, targetKey, eventKey, msgId);
    boolean duplicate = inboundMessageIdDedup.asMap().putIfAbsent(key, Boolean.TRUE) != null;
    if (duplicate) {
      recordInboundMessageIdSuppression(sidKey, targetKey, eventKey, msgId);
    }
    return duplicate;
  }

  private static String normalizeDedupTarget(TargetRef target) {
    if (target == null) return "";
    return Objects.toString(target.target(), "").trim().toLowerCase(Locale.ROOT);
  }

  private void recordInboundMessageIdSuppression(
      String serverId, String target, String eventType, String messageId) {
    InboundMessageDedupCounterKey counterKey =
        new InboundMessageDedupCounterKey(serverId, target, eventType);
    long keyCount =
        inboundMessageIdDedupSuppressedCountByKey
            .asMap()
            .compute(counterKey, (k, prev) -> prev == null ? 1L : (prev + 1L));
    long total = inboundMessageIdDedupSuppressedTotal.incrementAndGet();
    maybePublishInboundMessageIdSuppression(counterKey, keyCount, total, messageId);
  }

  private void maybePublishInboundMessageIdSuppression(
      InboundMessageDedupCounterKey key, long keyCount, long total, String messageId) {
    if (applicationEventPublisher == null || key == null) return;
    if (keyCount > 1 && (keyCount % 25L) != 0L) return;

    long now = System.currentTimeMillis();
    Long last = inboundMessageIdDedupDiagLastEmitMsByKey.asMap().get(key);
    if (last != null && (now - last.longValue()) < INBOUND_MSGID_DEDUP_DIAG_MIN_EMIT_MS) return;
    inboundMessageIdDedupDiagLastEmitMsByKey.put(key, now);

    try {
      applicationEventPublisher.publishEvent(
          new InboundMessageDedupDiagnostics(
              key.serverId(),
              key.target(),
              key.eventType(),
              keyCount,
              total,
              Objects.toString(messageId, "")));
    } catch (Exception ignored) {
    }
  }

  private static String effectiveMessageIdForDedup(String messageId, Map<String, String> tags) {
    String direct = Objects.toString(messageId, "").trim();
    if (!direct.isBlank()) return direct;
    return firstIrcv3TagValue(
        tags, "msgid", "+msgid", "draft/msgid", "+draft/msgid", "znc.in/msgid", "+znc.in/msgid");
  }

  private static boolean hasMessageMutationTag(Map<String, String> tags) {
    return !firstIrcv3TagValue(tags, "draft/edit", "+draft/edit").isBlank()
        || !firstIrcv3TagValue(tags, "draft/react", "+draft/react").isBlank()
        || !firstIrcv3TagValue(tags, "draft/unreact", "+draft/unreact").isBlank()
        || !firstIrcv3TagValue(tags, "draft/delete", "+draft/delete").isBlank()
        || !firstIrcv3TagValue(tags, "draft/redact", "+draft/redact").isBlank();
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

  private void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
    postTo(dest, targetCoordinator.getActiveTarget(), markUnreadIfNotActive, write);
  }

  private void clearRemoteTypingIndicatorsForSender(TargetRef target, String fromNick) {
    if (target == null) return;
    String nick = Objects.toString(fromNick, "").trim();
    if (nick.isEmpty()) return;

    ui.showTypingIndicator(target, nick, "done");
    if (!target.isChannel()) return;

    ui.showTypingActivity(target, "done");
    ui.showUsersTypingIndicator(target, nick, "done");
  }

  /** Prefer the active target for {@code sid}, otherwise fall back to {@code status}. */
  private TargetRef resolveActiveOrStatus(String sid, TargetRef status) {
    TargetRef active = targetCoordinator.getActiveTarget();
    if (active != null && Objects.equals(active.serverId(), sid)) return active;
    return status != null ? status : safeStatusTarget();
  }

  private void postTo(
      TargetRef dest, TargetRef active, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
    if (dest == null) dest = safeStatusTarget();
    ensureTargetExists(dest);

    if (write != null) {
      write.accept(dest);
    }

    if (markUnreadIfNotActive && active != null && !dest.equals(active) && !isMutedChannel(dest)) {
      ui.markUnread(dest);
    }
  }

  private boolean isMutedChannel(TargetRef target) {
    if (target == null || !target.isChannel()) return false;
    try {
      return ui.isChannelMuted(target);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean isMutedChannel(String serverId, String channel) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return false;
    try {
      return isMutedChannel(new TargetRef(sid, ch));
    } catch (Exception ignored) {
      return false;
    }
  }

  private void ensureTargetExists(TargetRef target) {
    ui.ensureTargetExists(target);
  }

  private TargetRef safeStatusTarget() {
    return targetCoordinator.safeStatusTarget();
  }
}
