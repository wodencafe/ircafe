package cafe.woden.ircclient.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.ConnectionCoordinator;
import cafe.woden.ircclient.app.IrcMediator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.app.UiPort;
import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.PircbotxIrcClientService;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.logging.model.LogLine;
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

    assertAnnotated(SwingUiPort.class, InterfaceLayer.class);
    assertAnnotated(PircbotxIrcClientService.class, InfrastructureLayer.class);

    assertTrue(UiPort.class.isInterface(), "UiPort should remain an interface");
    assertTrue(IrcClientService.class.isInterface(), "IrcClientService should remain an interface");
  }

  @Test
  void valueObjectMarkersArePresentOnSharedMessageTypes() {
    assertAnnotated(TargetRef.class, ValueObject.class);
    assertAnnotated(ChatHistoryEntry.class, ValueObject.class);
    assertAnnotated(ServerIrcEvent.class, ValueObject.class);
    assertAnnotated(LogLine.class, ValueObject.class);
  }

  private static void assertAnnotated(Class<?> type, Class<? extends Annotation> marker) {
    assertTrue(
        type.isAnnotationPresent(marker),
        () -> type.getName() + " must be annotated with @" + marker.getSimpleName());
  }
}
