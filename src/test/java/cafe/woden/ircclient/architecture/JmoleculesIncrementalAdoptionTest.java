package cafe.woden.ircclient.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import cafe.woden.ircclient.app.api.ActiveTargetPort;
import cafe.woden.ircclient.app.api.ChatHistoryBatchEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestEventsPort;
import cafe.woden.ircclient.app.api.ChatHistoryIngestionPort;
import cafe.woden.ircclient.app.api.ChatTranscriptHistoryPort;
import cafe.woden.ircclient.app.api.InterceptorIngestPort;
import cafe.woden.ircclient.app.api.IrcEventNotifierPort;
import cafe.woden.ircclient.app.api.MediatorControlPort;
import cafe.woden.ircclient.app.api.MonitorFallbackPort;
import cafe.woden.ircclient.app.api.MonitorRosterPort;
import cafe.woden.ircclient.app.api.NotificationRuleMatch;
import cafe.woden.ircclient.app.api.NotificationRuleMatcherPort;
import cafe.woden.ircclient.app.api.PresenceEvent;
import cafe.woden.ircclient.app.api.PrivateMessageRequest;
import cafe.woden.ircclient.app.api.TargetChatHistoryPort;
import cafe.woden.ircclient.app.api.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.TrayNotificationsPort;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.api.UiSettingsPort;
import cafe.woden.ircclient.app.api.UserActionRequest;
import cafe.woden.ircclient.app.api.ZncPlaybackEventsPort;
import cafe.woden.ircclient.app.core.ConnectionCoordinator;
import cafe.woden.ircclient.app.core.IrcMediator;
import cafe.woden.ircclient.app.core.MediatorConnectionSubscriptionBinder;
import cafe.woden.ircclient.app.core.MediatorHistoryIngestOrchestrator;
import cafe.woden.ircclient.app.core.MediatorUiSubscriptionBinder;
import cafe.woden.ircclient.app.core.TargetCoordinator;
import cafe.woden.ircclient.app.outbound.LocalFilterCommandHandler;
import cafe.woden.ircclient.app.state.AwayRoutingState;
import cafe.woden.ircclient.app.state.ChannelFlagModeState;
import cafe.woden.ircclient.app.state.ChatHistoryRequestRoutingState;
import cafe.woden.ircclient.app.state.CtcpRoutingState;
import cafe.woden.ircclient.app.state.JoinRoutingState;
import cafe.woden.ircclient.app.state.LabeledResponseRoutingState;
import cafe.woden.ircclient.app.state.ModeRoutingState;
import cafe.woden.ircclient.app.state.PendingEchoMessageState;
import cafe.woden.ircclient.app.state.PendingInviteState;
import cafe.woden.ircclient.app.state.RecentStatusModeState;
import cafe.woden.ircclient.app.state.WhoisRoutingState;
import cafe.woden.ircclient.dcc.DccTransferStore;
import cafe.woden.ircclient.diagnostics.ApplicationDiagnosticsService;
import cafe.woden.ircclient.diagnostics.AssertjSwingDiagnosticsService;
import cafe.woden.ircclient.diagnostics.JfrRuntimeEventsService;
import cafe.woden.ircclient.diagnostics.JfrSnapshotSummarizer;
import cafe.woden.ircclient.diagnostics.JhiccupDiagnosticsService;
import cafe.woden.ircclient.diagnostics.RuntimeDiagnosticEvent;
import cafe.woden.ircclient.diagnostics.RuntimeJfrService;
import cafe.woden.ircclient.diagnostics.SpringRuntimeEventsService;
import cafe.woden.ircclient.ignore.IgnoreListService;
import cafe.woden.ircclient.ignore.IgnoreStatusService;
import cafe.woden.ircclient.ignore.InboundIgnorePolicy;
import cafe.woden.ircclient.ignore.api.IgnoreListCommandPort;
import cafe.woden.ircclient.ignore.api.IgnoreListQueryPort;
import cafe.woden.ircclient.ignore.api.InboundIgnorePolicyPort;
import cafe.woden.ircclient.interceptors.InterceptorHit;
import cafe.woden.ircclient.interceptors.InterceptorStore;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.PircbotxIrcClientService;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.InterceptorRule;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.LogLine;
import cafe.woden.ircclient.model.UserCommandAlias;
import cafe.woden.ircclient.monitor.MonitorIsonFallbackService;
import cafe.woden.ircclient.monitor.MonitorListService;
import cafe.woden.ircclient.monitor.MonitorSyncService;
import cafe.woden.ircclient.notifications.IrcEventNotificationRulesBus;
import cafe.woden.ircclient.notifications.IrcEventNotificationService;
import cafe.woden.ircclient.notifications.NotificationRuleMatcher;
import cafe.woden.ircclient.notifications.NotificationStore;
import cafe.woden.ircclient.perform.PerformOnConnectService;
import cafe.woden.ircclient.ui.SwingUiPort;
import java.lang.annotation.Annotation;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.jmolecules.ddd.annotation.ValueObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

class JmoleculesIncrementalAdoptionTest {

  @Test
  void layeredMarkersArePresentOnInitialBoundaryTypes() {
    assertAnnotated(UiPort.class, ApplicationLayer.class);
    assertAnnotated(IrcClientService.class, ApplicationLayer.class);
    assertAnnotated(IrcMediator.class, ApplicationLayer.class);
    assertAnnotated(ConnectionCoordinator.class, ApplicationLayer.class);
    assertAnnotated(TargetCoordinator.class, ApplicationLayer.class);
    assertAnnotated(MediatorConnectionSubscriptionBinder.class, ApplicationLayer.class);
    assertAnnotated(MediatorUiSubscriptionBinder.class, ApplicationLayer.class);
    assertAnnotated(MediatorHistoryIngestOrchestrator.class, ApplicationLayer.class);
    assertAnnotated(NotificationStore.class, ApplicationLayer.class);
    assertAnnotated(WhoisRoutingState.class, ApplicationLayer.class);
    assertAnnotated(CtcpRoutingState.class, ApplicationLayer.class);
    assertAnnotated(ModeRoutingState.class, ApplicationLayer.class);
    assertAnnotated(AwayRoutingState.class, ApplicationLayer.class);
    assertAnnotated(ChatHistoryRequestRoutingState.class, ApplicationLayer.class);
    assertAnnotated(JoinRoutingState.class, ApplicationLayer.class);
    assertAnnotated(LabeledResponseRoutingState.class, ApplicationLayer.class);
    assertAnnotated(PendingEchoMessageState.class, ApplicationLayer.class);
    assertAnnotated(PendingInviteState.class, ApplicationLayer.class);
    assertAnnotated(ChannelFlagModeState.class, ApplicationLayer.class);
    assertAnnotated(RecentStatusModeState.class, ApplicationLayer.class);
    assertAnnotated(ApplicationDiagnosticsService.class, ApplicationLayer.class);
    assertAnnotated(AssertjSwingDiagnosticsService.class, ApplicationLayer.class);
    assertAnnotated(JhiccupDiagnosticsService.class, ApplicationLayer.class);
    assertAnnotated(RuntimeJfrService.class, ApplicationLayer.class);
    assertAnnotated(JfrRuntimeEventsService.class, ApplicationLayer.class);
    assertAnnotated(SpringRuntimeEventsService.class, ApplicationLayer.class);
    assertAnnotated(MediatorControlPort.class, ApplicationLayer.class);
    assertAnnotated(ActiveTargetPort.class, ApplicationLayer.class);
    assertAnnotated(TrayNotificationsPort.class, ApplicationLayer.class);
    assertAnnotated(UiSettingsPort.class, ApplicationLayer.class);
    assertAnnotated(InboundIgnorePolicyPort.class, ApplicationLayer.class);
    assertAnnotated(IgnoreListQueryPort.class, ApplicationLayer.class);
    assertAnnotated(IgnoreListCommandPort.class, ApplicationLayer.class);
    assertAnnotated(ChatHistoryIngestionPort.class, ApplicationLayer.class);
    assertAnnotated(ChatHistoryIngestEventsPort.class, ApplicationLayer.class);
    assertAnnotated(ChatHistoryBatchEventsPort.class, ApplicationLayer.class);
    assertAnnotated(ZncPlaybackEventsPort.class, ApplicationLayer.class);
    assertAnnotated(TargetChatHistoryPort.class, ApplicationLayer.class);
    assertAnnotated(TargetLogMaintenancePort.class, ApplicationLayer.class);
    assertAnnotated(ChatTranscriptHistoryPort.class, ApplicationLayer.class);
    assertAnnotated(InterceptorIngestPort.class, ApplicationLayer.class);
    assertAnnotated(IrcEventNotifierPort.class, ApplicationLayer.class);
    assertAnnotated(NotificationRuleMatcherPort.class, ApplicationLayer.class);
    assertAnnotated(MonitorFallbackPort.class, ApplicationLayer.class);
    assertAnnotated(MonitorRosterPort.class, ApplicationLayer.class);
    assertAnnotated(LocalFilterCommandHandler.class, ApplicationLayer.class);
    assertAnnotated(PerformOnConnectService.class, ApplicationLayer.class);
    assertAnnotated(JfrSnapshotSummarizer.class, ApplicationLayer.class);
    assertAnnotated(MonitorListService.class, ApplicationLayer.class);
    assertAnnotated(MonitorIsonFallbackService.class, ApplicationLayer.class);
    assertAnnotated(MonitorSyncService.class, ApplicationLayer.class);
    assertAnnotated(InterceptorStore.class, ApplicationLayer.class);
    assertAnnotated(IrcEventNotificationService.class, ApplicationLayer.class);
    assertAnnotated(NotificationRuleMatcher.class, ApplicationLayer.class);
    assertAnnotated(IrcEventNotificationRulesBus.class, ApplicationLayer.class);
    assertAnnotated(DccTransferStore.class, ApplicationLayer.class);
    assertAnnotated(IgnoreListService.class, ApplicationLayer.class);
    assertAnnotated(IgnoreStatusService.class, ApplicationLayer.class);
    assertAnnotated(InboundIgnorePolicy.class, ApplicationLayer.class);

    assertAnnotated(SwingUiPort.class, InterfaceLayer.class);
    assertAnnotated(PircbotxIrcClientService.class, InfrastructureLayer.class);

    assertTrue(UiPort.class.isInterface(), "UiPort should remain an interface");
    assertTrue(
        MediatorControlPort.class.isInterface(), "MediatorControlPort should remain an interface");
    assertTrue(ActiveTargetPort.class.isInterface(), "ActiveTargetPort should remain an interface");
    assertTrue(IrcClientService.class.isInterface(), "IrcClientService should remain an interface");
    assertTrue(
        TrayNotificationsPort.class.isInterface(),
        "TrayNotificationsPort should remain an interface");
    assertTrue(UiSettingsPort.class.isInterface(), "UiSettingsPort should remain an interface");
    assertTrue(
        MonitorFallbackPort.class.isInterface(), "MonitorFallbackPort should remain an interface");
    assertTrue(
        MonitorRosterPort.class.isInterface(), "MonitorRosterPort should remain an interface");
    assertTrue(
        ChatHistoryIngestionPort.class.isInterface(),
        "ChatHistoryIngestionPort should remain an interface");
    assertTrue(
        LocalFilterCommandHandler.class.isInterface(),
        "LocalFilterCommandHandler should remain an interface");
    assertTrue(
        InterceptorIngestPort.class.isInterface(),
        "InterceptorIngestPort should remain an interface");
    assertTrue(
        IrcEventNotifierPort.class.isInterface(),
        "IrcEventNotifierPort should remain an interface");
    assertTrue(
        NotificationRuleMatcherPort.class.isInterface(),
        "NotificationRuleMatcherPort should remain an interface");
    assertTrue(
        InboundIgnorePolicyPort.class.isInterface(),
        "InboundIgnorePolicyPort should remain an interface");
    assertTrue(
        IgnoreListQueryPort.class.isInterface(), "IgnoreListQueryPort should remain an interface");
    assertTrue(
        IgnoreListCommandPort.class.isInterface(),
        "IgnoreListCommandPort should remain an interface");
  }

  @Test
  void valueObjectMarkersArePresentOnSharedMessageTypes() {
    assertAnnotated(TargetRef.class, ValueObject.class);
    assertAnnotated(ChatHistoryEntry.class, ValueObject.class);
    assertAnnotated(ServerIrcEvent.class, ValueObject.class);
    assertAnnotated(LogLine.class, ValueObject.class);
    assertAnnotated(RuntimeDiagnosticEvent.class, ValueObject.class);
    assertAnnotated(PresenceEvent.class, ValueObject.class);
    assertAnnotated(PrivateMessageRequest.class, ValueObject.class);
    assertAnnotated(UserActionRequest.class, ValueObject.class);
    assertAnnotated(IrcEventNotificationRule.class, ValueObject.class);
    assertAnnotated(NotificationRuleMatch.class, ValueObject.class);
    assertAnnotated(UserCommandAlias.class, ValueObject.class);
    assertAnnotated(InterceptorDefinition.class, ValueObject.class);
    assertAnnotated(InterceptorRule.class, ValueObject.class);
    assertAnnotated(InterceptorHit.class, ValueObject.class);
  }

  @Test
  void componentEntryPointsInExtractedModulesRemainApplicationLayerAnnotated() {
    assertComponentPackageAnnotated("cafe.woden.ircclient.perform", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.diagnostics", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.monitor", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.interceptors", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.notifications", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.dcc", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.ignore", ApplicationLayer.class);
    assertComponentPackageAnnotated("cafe.woden.ircclient.app.state", ApplicationLayer.class);
  }

  private static void assertComponentPackageAnnotated(
      String basePackage, Class<? extends Annotation> marker) {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
    var candidates = scanner.findCandidateComponents(basePackage);
    assertTrue(!candidates.isEmpty(), "Expected at least one @Component in " + basePackage);
    for (BeanDefinition candidate : candidates) {
      String className = candidate.getBeanClassName();
      if (className == null || className.isBlank()) {
        fail("Missing bean class name for candidate in " + basePackage);
      }
      try {
        Class<?> type = Class.forName(className);
        assertAnnotated(type, marker);
      } catch (ClassNotFoundException e) {
        fail("Could not load component class " + className, e);
      }
    }
  }

  private static void assertAnnotated(Class<?> type, Class<? extends Annotation> marker) {
    assertTrue(
        type.isAnnotationPresent(marker),
        () -> type.getName() + " must be annotated with @" + marker.getSimpleName());
  }
}
