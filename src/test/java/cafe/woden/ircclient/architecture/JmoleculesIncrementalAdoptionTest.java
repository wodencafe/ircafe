package cafe.woden.ircclient.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.ApplicationDiagnosticsService;
import cafe.woden.ircclient.app.AssertjSwingDiagnosticsService;
import cafe.woden.ircclient.app.ChatHistoryBatchEventsPort;
import cafe.woden.ircclient.app.ChatHistoryIngestEventsPort;
import cafe.woden.ircclient.app.ChatHistoryIngestionPort;
import cafe.woden.ircclient.app.ChatTranscriptHistoryPort;
import cafe.woden.ircclient.app.ConnectionCoordinator;
import cafe.woden.ircclient.app.IrcMediator;
import cafe.woden.ircclient.app.JfrRuntimeEventsService;
import cafe.woden.ircclient.app.JhiccupDiagnosticsService;
import cafe.woden.ircclient.app.MediatorConnectionSubscriptionBinder;
import cafe.woden.ircclient.app.MediatorHistoryIngestOrchestrator;
import cafe.woden.ircclient.app.MediatorUiSubscriptionBinder;
import cafe.woden.ircclient.app.NotificationStore;
import cafe.woden.ircclient.app.PresenceEvent;
import cafe.woden.ircclient.app.PrivateMessageRequest;
import cafe.woden.ircclient.app.RuntimeDiagnosticEvent;
import cafe.woden.ircclient.app.RuntimeJfrService;
import cafe.woden.ircclient.app.SpringRuntimeEventsService;
import cafe.woden.ircclient.app.TargetChatHistoryPort;
import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.TargetLogMaintenancePort;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.TrayNotificationsPort;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.app.UiSettingsPort;
import cafe.woden.ircclient.app.UserActionRequest;
import cafe.woden.ircclient.app.ZncPlaybackEventsPort;
import cafe.woden.ircclient.app.interceptors.InterceptorHit;
import cafe.woden.ircclient.app.notifications.NotificationRuleMatch;
import cafe.woden.ircclient.app.outbound.LocalFilterCommandHandler;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.PircbotxIrcClientService;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.InterceptorRule;
import cafe.woden.ircclient.model.IrcEventNotificationRule;
import cafe.woden.ircclient.model.LogLine;
import cafe.woden.ircclient.model.UserCommandAlias;
import cafe.woden.ircclient.ui.SwingUiPort;
import java.lang.annotation.Annotation;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.jmolecules.ddd.annotation.ValueObject;
import org.junit.jupiter.api.Test;

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
    assertAnnotated(ApplicationDiagnosticsService.class, ApplicationLayer.class);
    assertAnnotated(AssertjSwingDiagnosticsService.class, ApplicationLayer.class);
    assertAnnotated(JhiccupDiagnosticsService.class, ApplicationLayer.class);
    assertAnnotated(RuntimeJfrService.class, ApplicationLayer.class);
    assertAnnotated(JfrRuntimeEventsService.class, ApplicationLayer.class);
    assertAnnotated(SpringRuntimeEventsService.class, ApplicationLayer.class);
    assertAnnotated(TrayNotificationsPort.class, ApplicationLayer.class);
    assertAnnotated(UiSettingsPort.class, ApplicationLayer.class);
    assertAnnotated(ChatHistoryIngestionPort.class, ApplicationLayer.class);
    assertAnnotated(ChatHistoryIngestEventsPort.class, ApplicationLayer.class);
    assertAnnotated(ChatHistoryBatchEventsPort.class, ApplicationLayer.class);
    assertAnnotated(ZncPlaybackEventsPort.class, ApplicationLayer.class);
    assertAnnotated(TargetChatHistoryPort.class, ApplicationLayer.class);
    assertAnnotated(TargetLogMaintenancePort.class, ApplicationLayer.class);
    assertAnnotated(ChatTranscriptHistoryPort.class, ApplicationLayer.class);
    assertAnnotated(LocalFilterCommandHandler.class, ApplicationLayer.class);

    assertAnnotated(SwingUiPort.class, InterfaceLayer.class);
    assertAnnotated(PircbotxIrcClientService.class, InfrastructureLayer.class);

    assertTrue(UiPort.class.isInterface(), "UiPort should remain an interface");
    assertTrue(IrcClientService.class.isInterface(), "IrcClientService should remain an interface");
    assertTrue(
        TrayNotificationsPort.class.isInterface(),
        "TrayNotificationsPort should remain an interface");
    assertTrue(UiSettingsPort.class.isInterface(), "UiSettingsPort should remain an interface");
    assertTrue(
        ChatHistoryIngestionPort.class.isInterface(),
        "ChatHistoryIngestionPort should remain an interface");
    assertTrue(
        LocalFilterCommandHandler.class.isInterface(),
        "LocalFilterCommandHandler should remain an interface");
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

  private static void assertAnnotated(Class<?> type, Class<? extends Annotation> marker) {
    assertTrue(
        type.isAnnotationPresent(marker),
        () -> type.getName() + " must be annotated with @" + marker.getSimpleName());
  }
}
