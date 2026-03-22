package cafe.woden.ircclient.app.core;

import cafe.woden.ircclient.app.AppSchedulers;
import cafe.woden.ircclient.app.api.InterceptorEventType;
import cafe.woden.ircclient.app.api.InterceptorIngestPort;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.Ircv3CapabilityToggleRequest;
import cafe.woden.ircclient.app.api.MediatorControlPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.state.api.PendingEchoMessagePort;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jmolecules.architecture.hexagonal.Application;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** App mediator. */
@Component
@Lazy
@Application
@ApplicationLayer
public class IrcMediator implements MediatorControlPort {

  private static final Scheduler IRC_EVENT_PREPARE_SCHEDULER = Schedulers.computation();

  @Qualifier("ircMediatorInteractionPort")
  private final IrcMediatorInteractionPort irc;

  private final UiPort ui;
  private final ServerRegistry serverRegistry;
  private final ConnectionCoordinator connectionCoordinator;
  private final MediatorConnectivityLifecycleOrchestrator mediatorConnectivityLifecycleOrchestrator;
  private final MediatorServerStatusEventHandler mediatorServerStatusEventHandler;
  private final MediatorInviteEventHandler mediatorInviteEventHandler;
  private final MediatorChannelMembershipEventHandler mediatorChannelMembershipEventHandler;
  private final MediatorRosterStatusEventHandler mediatorRosterStatusEventHandler;
  private final MediatorIrcv3PresenceEventHandler mediatorIrcv3PresenceEventHandler;
  private final MediatorIrcv3EventHandler mediatorIrcv3EventHandler;
  private final MediatorAlertNotificationHandler mediatorAlertNotificationHandler;
  private final MediatorChannelStateEventHandler mediatorChannelStateEventHandler;
  private final MediatorOutboundUiActionHandler mediatorOutboundUiActionHandler;
  private final MediatorTargetUiSupport mediatorTargetUiSupport;
  private final MediatorConnectionSubscriptionBinder mediatorConnectionSubscriptionBinder;
  private final MediatorUiSubscriptionBinder mediatorUiSubscriptionBinder;

  private final TargetCoordinator targetCoordinator;

  private final MediatorInboundEventPreparationService eventPreparationService;
  private final MediatorInboundTextEventHandler mediatorInboundTextEventHandler;

  private final UserListStore userListStore;
  private final PendingEchoMessagePort pendingEchoMessageState;
  private final IrcEventNotifierPort ircEventNotifierPort;
  private final InterceptorIngestPort interceptorIngestPort;

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
  private final MediatorChannelStateEventHandler.Callbacks channelStateEventCallbacks =
      new ChannelStateEventCallbacks();
  private final MediatorRosterStatusEventHandler.Callbacks rosterStatusEventCallbacks =
      new RosterStatusEventCallbacks();
  private final MediatorIrcv3PresenceEventHandler.Callbacks ircv3PresenceEventCallbacks =
      new Ircv3PresenceEventCallbacks();
  private final MediatorIrcv3EventHandler.Callbacks ircv3EventCallbacks = new Ircv3EventCallbacks();
  private final MediatorAlertNotificationHandler.Callbacks alertNotificationCallbacks =
      new AlertNotificationCallbacks();
  private final MediatorInboundTextEventHandler.Callbacks inboundTextEventCallbacks =
      new InboundTextEventCallbacks();

  public record InboundMessageDedupDiagnostics(
      String serverId,
      String target,
      String eventType,
      long suppressedCount,
      long suppressedTotal,
      String messageIdSample) {}

  public IrcMediator(
      @Qualifier("ircMediatorInteractionPort") IrcMediatorInteractionPort irc,
      UiPort ui,
      ServerRegistry serverRegistry,
      ConnectionCoordinator connectionCoordinator,
      MediatorConnectivityLifecycleOrchestrator mediatorConnectivityLifecycleOrchestrator,
      MediatorServerStatusEventHandler mediatorServerStatusEventHandler,
      MediatorInviteEventHandler mediatorInviteEventHandler,
      MediatorChannelMembershipEventHandler mediatorChannelMembershipEventHandler,
      MediatorRosterStatusEventHandler mediatorRosterStatusEventHandler,
      MediatorIrcv3PresenceEventHandler mediatorIrcv3PresenceEventHandler,
      MediatorIrcv3EventHandler mediatorIrcv3EventHandler,
      MediatorAlertNotificationHandler mediatorAlertNotificationHandler,
      MediatorChannelStateEventHandler mediatorChannelStateEventHandler,
      MediatorOutboundUiActionHandler mediatorOutboundUiActionHandler,
      MediatorTargetUiSupport mediatorTargetUiSupport,
      MediatorConnectionSubscriptionBinder mediatorConnectionSubscriptionBinder,
      MediatorUiSubscriptionBinder mediatorUiSubscriptionBinder,
      TargetCoordinator targetCoordinator,
      MediatorInboundEventPreparationService eventPreparationService,
      MediatorInboundTextEventHandler mediatorInboundTextEventHandler,
      UserListStore userListStore,
      PendingEchoMessagePort pendingEchoMessageState,
      IrcEventNotifierPort ircEventNotifierPort,
      InterceptorIngestPort interceptorIngestPort) {
    this.irc = irc;
    this.ui = ui;
    this.serverRegistry = serverRegistry;
    this.connectionCoordinator = connectionCoordinator;
    this.mediatorConnectivityLifecycleOrchestrator = mediatorConnectivityLifecycleOrchestrator;
    this.mediatorServerStatusEventHandler = mediatorServerStatusEventHandler;
    this.mediatorInviteEventHandler = mediatorInviteEventHandler;
    this.mediatorChannelMembershipEventHandler = mediatorChannelMembershipEventHandler;
    this.mediatorRosterStatusEventHandler = mediatorRosterStatusEventHandler;
    this.mediatorIrcv3PresenceEventHandler = mediatorIrcv3PresenceEventHandler;
    this.mediatorIrcv3EventHandler = mediatorIrcv3EventHandler;
    this.mediatorAlertNotificationHandler = mediatorAlertNotificationHandler;
    this.mediatorChannelStateEventHandler = mediatorChannelStateEventHandler;
    this.mediatorOutboundUiActionHandler = mediatorOutboundUiActionHandler;
    this.mediatorTargetUiSupport = mediatorTargetUiSupport;
    this.mediatorConnectionSubscriptionBinder = mediatorConnectionSubscriptionBinder;
    this.mediatorUiSubscriptionBinder = mediatorUiSubscriptionBinder;
    this.targetCoordinator = targetCoordinator;
    this.eventPreparationService = eventPreparationService;
    this.mediatorInboundTextEventHandler = mediatorInboundTextEventHandler;
    this.userListStore = userListStore;
    this.pendingEchoMessageState = pendingEchoMessageState;
    this.ircEventNotifierPort = ircEventNotifierPort;
    this.interceptorIngestPort = interceptorIngestPort;
  }

  private final class ConnectivityLifecycleCallbacks
      implements MediatorConnectivityLifecycleOrchestrator.Callbacks {
    @Override
    public void failPendingEchoesForServer(String serverId, String reason) {
      IrcMediator.this.failPendingEchoesForServer(serverId, reason);
    }

    @Override
    public void clearNetsplitDebounceForServer(String serverId) {
      mediatorAlertNotificationHandler.clearNetsplitDebounceForServer(serverId);
    }
  }

  private final class ServerStatusEventCallbacks
      implements MediatorServerStatusEventHandler.Callbacks {
    @Override
    public TargetRef safeStatusTarget() {
      return mediatorTargetUiSupport.safeStatusTarget();
    }

    @Override
    public void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
      mediatorTargetUiSupport.postTo(dest, markUnreadIfNotActive, write);
    }

    @Override
    public TargetRef resolveActiveOrStatus(String sid, TargetRef status) {
      return mediatorTargetUiSupport.resolveActiveOrStatus(sid, status);
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
      mediatorTargetUiSupport.postTo(dest, markUnreadIfNotActive, write);
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
      return mediatorTargetUiSupport.isMutedChannel(serverId, channel);
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
      mediatorTargetUiSupport.postTo(dest, markUnreadIfNotActive, write);
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
      return mediatorTargetUiSupport.resolveActiveOrStatus(sid, status);
    }

    @Override
    public TargetRef safeStatusTarget() {
      return mediatorTargetUiSupport.safeStatusTarget();
    }

    @Override
    public void markPrivateMessagePeerOffline(String serverId, String nick) {
      mediatorTargetUiSupport.markPrivateMessagePeerOffline(serverId, nick);
    }

    @Override
    public void maybeNotifyUserKlineFromQuit(String serverId, IrcEvent.UserQuitChannel event) {
      mediatorAlertNotificationHandler.maybeNotifyUserKlineFromQuit(
          alertNotificationCallbacks, serverId, event);
    }

    @Override
    public void maybeNotifyNetsplitDetected(String serverId, IrcEvent.UserQuitChannel event) {
      mediatorAlertNotificationHandler.maybeNotifyNetsplitDetected(
          alertNotificationCallbacks, serverId, event);
    }
  }

  private final class ChannelStateEventCallbacks
      implements MediatorChannelStateEventHandler.Callbacks {
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
    public String learnedHostmaskForNick(String serverId, String nick) {
      return IrcMediator.this.learnedHostmaskForNick(serverId, nick);
    }
  }

  private final class RosterStatusEventCallbacks
      implements MediatorRosterStatusEventHandler.Callbacks {
    @Override
    public void markPrivateMessagePeerOnline(String serverId, String nick) {
      mediatorTargetUiSupport.markPrivateMessagePeerOnline(serverId, nick);
    }

    @Override
    public void markPrivateMessagePeerOffline(String serverId, String nick) {
      mediatorTargetUiSupport.markPrivateMessagePeerOffline(serverId, nick);
    }
  }

  private final class Ircv3PresenceEventCallbacks
      implements MediatorIrcv3PresenceEventHandler.Callbacks {
    @Override
    public boolean isFromSelf(String serverId, String from) {
      return mediatorTargetUiSupport.isFromSelf(serverId, from);
    }

    @Override
    public void markPrivateMessagePeerOnline(String serverId, String nick) {
      mediatorTargetUiSupport.markPrivateMessagePeerOnline(serverId, nick);
    }

    @Override
    public TargetRef resolveIrcv3Target(String sid, String target, String from, TargetRef status) {
      return mediatorTargetUiSupport.resolveIrcv3Target(sid, target, from, status);
    }

    @Override
    public TargetRef resolveActiveOrStatus(String sid, TargetRef status) {
      return mediatorTargetUiSupport.resolveActiveOrStatus(sid, status);
    }
  }

  private final class Ircv3EventCallbacks implements MediatorIrcv3EventHandler.Callbacks {
    @Override
    public TargetRef resolveIrcv3Target(String sid, String target, String from, TargetRef status) {
      return mediatorTargetUiSupport.resolveIrcv3Target(sid, target, from, status);
    }
  }

  private final class AlertNotificationCallbacks
      implements MediatorAlertNotificationHandler.Callbacks {
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

  private final class InboundTextEventCallbacks
      implements MediatorInboundTextEventHandler.Callbacks {
    @Override
    public void observeChannelActivity(String serverId, String channel) {
      IrcMediator.this.observeChannelActivity(serverId, channel);
    }

    @Override
    public void postTo(
        TargetRef dest,
        TargetRef active,
        boolean markUnreadIfNotActive,
        Consumer<TargetRef> write) {
      mediatorTargetUiSupport.postTo(dest, active, markUnreadIfNotActive, write);
    }

    @Override
    public void postTo(TargetRef dest, boolean markUnreadIfNotActive, Consumer<TargetRef> write) {
      mediatorTargetUiSupport.postTo(dest, markUnreadIfNotActive, write);
    }

    @Override
    public boolean isFromSelf(String serverId, String from) {
      return mediatorTargetUiSupport.isFromSelf(serverId, from);
    }

    @Override
    public void markPrivateMessagePeerOnline(String serverId, String nick) {
      mediatorTargetUiSupport.markPrivateMessagePeerOnline(serverId, nick);
    }

    @Override
    public void recordInterceptorEvent(
        String serverId,
        String target,
        String actorNick,
        String text,
        InterceptorEventType eventType) {
      IrcMediator.this.recordInterceptorEvent(
          serverId,
          target,
          actorNick,
          IrcMediator.this.learnedHostmaskForNick(serverId, actorNick),
          text,
          eventType);
    }

    @Override
    public boolean notifyIrcEvent(
        IrcEventNotificationRule.EventType eventType,
        String serverId,
        String channel,
        String sourceNick,
        String title,
        String body,
        String ctcpCommand,
        String ctcpValue) {
      return IrcMediator.this.notifyIrcEvent(
          eventType, serverId, channel, sourceNick, title, body, ctcpCommand, ctcpValue);
    }

    @Override
    public TargetRef safeStatusTarget() {
      return mediatorTargetUiSupport.safeStatusTarget();
    }

    @Override
    public TargetRef resolveActiveOrStatus(String sid, TargetRef status) {
      return mediatorTargetUiSupport.resolveActiveOrStatus(sid, status);
    }

    @Override
    public boolean isMutedChannel(TargetRef target) {
      return mediatorTargetUiSupport.isMutedChannel(target);
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
                  mediatorServerStatusEventHandler.handleLabeledRequestTimeouts(
                      serverStatusEventCallbacks);
                  mediatorServerStatusEventHandler.handlePendingEchoTimeouts();
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
    mediatorOutboundUiActionHandler.handleUserActionRequest(disposables, req);
  }

  private void handleIrcv3CapabilityToggleRequest(Ircv3CapabilityToggleRequest req) {
    mediatorOutboundUiActionHandler.handleIrcv3CapabilityToggleRequest(disposables, req);
  }

  private void observeChannelActivity(String serverId, String channel) {
    mediatorChannelStateEventHandler.observeChannelActivity(serverId, channel);
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
    mediatorOutboundUiActionHandler.handleOutgoingLine(disposables, raw);
  }

  private void handleBackendNamedCommandRequest(ParsedInput.BackendNamed command) {
    mediatorOutboundUiActionHandler.handleBackendNamedCommandRequest(disposables, command);
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
        mediatorInboundTextEventHandler.handleChannelMessage(
            inboundTextEventCallbacks, sid, prepared, ev);
      }
      case IrcEvent.ChannelAction ev -> {
        mediatorInboundTextEventHandler.handleChannelAction(
            inboundTextEventCallbacks, sid, prepared, ev);
      }
      case IrcEvent.ChannelModeObserved ev -> {
        handleChannelModeObserved(sid, ev);
      }

      case IrcEvent.ChannelTopicUpdated ev -> {
        handleChannelTopicUpdated(sid, ev);
      }

      case IrcEvent.PrivateMessage ev -> {
        mediatorInboundTextEventHandler.handlePrivateMessage(
            inboundTextEventCallbacks, sid, prepared, ev);
      }

      case IrcEvent.PrivateAction ev -> {
        mediatorInboundTextEventHandler.handlePrivateAction(
            inboundTextEventCallbacks, sid, prepared, ev);
      }
      case IrcEvent.Notice ev -> {
        mediatorInboundTextEventHandler.handleNotice(
            inboundTextEventCallbacks, sid, status, prepared, ev);
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
        mediatorInboundTextEventHandler.handleCtcpRequest(
            inboundTextEventCallbacks, sid, status, prepared, ev);
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
        mediatorServerStatusEventHandler.handleError(serverStatusEventCallbacks, sid, status, ev);
      }

      default -> {}
    }
  }

  private void handleChannelModeObserved(String sid, IrcEvent.ChannelModeObserved ev) {
    mediatorChannelStateEventHandler.handleChannelModeObserved(channelStateEventCallbacks, sid, ev);
  }

  private void handleChannelTopicUpdated(String sid, IrcEvent.ChannelTopicUpdated ev) {
    mediatorChannelStateEventHandler.handleChannelTopicUpdated(channelStateEventCallbacks, sid, ev);
  }

  private void handleChatHistoryBatchReceived(String sid, IrcEvent.ChatHistoryBatchReceived ev) {
    mediatorChannelStateEventHandler.handleChatHistoryBatchReceived(sid, ev);
  }

  private void handleZncPlaybackBatchReceived(String sid, IrcEvent.ZncPlaybackBatchReceived ev) {
    mediatorChannelStateEventHandler.handleZncPlaybackBatchReceived(sid, ev);
  }

  private void handleNickListUpdated(String sid, IrcEvent.NickListUpdated ev) {
    mediatorChannelStateEventHandler.handleNickListUpdated(sid, ev);
  }

  private void handleUserHostmaskObserved(String sid, IrcEvent.UserHostmaskObserved ev) {
    mediatorChannelStateEventHandler.handleUserHostmaskObserved(sid, ev);
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
    if (mediatorTargetUiSupport.isMutedChannel(sid, channel)) return false;

    String src = Objects.toString(sourceNick, "").trim();
    Boolean sourceIsSelf = src.isEmpty() ? null : mediatorTargetUiSupport.isFromSelf(sid, src);
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
    if (mediatorTargetUiSupport.isFromSelf(sid, from)) return;
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
}
