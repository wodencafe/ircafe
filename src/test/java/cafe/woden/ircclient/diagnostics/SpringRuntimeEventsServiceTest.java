package cafe.woden.ircclient.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.InstalledPluginProblem;
import cafe.woden.ircclient.util.InstalledPluginDescriptor;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;

class SpringRuntimeEventsServiceTest {

  @Test
  void onStartAddsInitializationEvent() {
    SpringRuntimeEventsService service = new SpringRuntimeEventsService();

    service.onStart();

    List<RuntimeDiagnosticEvent> events = service.recentEvents(10);
    assertEquals(1, events.size());
    RuntimeDiagnosticEvent event = events.getFirst();
    assertEquals("INFO", event.level());
    assertEquals("SpringRuntimeEventsService", event.type());
    assertEquals("Spring runtime event capture initialized.", event.summary());
    assertTrue(event.details().contains("Listening for ApplicationEvent"));
  }

  @Test
  void payloadThrowableIsClassifiedAsErrorAndIncludesStack() {
    SpringRuntimeEventsService service = new SpringRuntimeEventsService();

    IllegalStateException boom = new IllegalStateException("boom");
    service.onApplicationEvent(new PayloadApplicationEvent<>(this, boom));

    RuntimeDiagnosticEvent event = service.recentEvents(1).getFirst();
    assertEquals("ERROR", event.level());
    assertTrue(event.summary().contains("Payload event: IllegalStateException"));
    assertTrue(event.details().contains("payloadType=java.lang.IllegalStateException"));
    assertTrue(event.details().contains("IllegalStateException: boom"));
  }

  @Test
  void warningLikeEventNameIsClassifiedAsWarn() {
    SpringRuntimeEventsService service = new SpringRuntimeEventsService();

    service.onApplicationEvent(new WarnSignalEvent(this));

    RuntimeDiagnosticEvent event = service.recentEvents(1).getFirst();
    assertEquals("WARN", event.level());
    assertEquals("WarnSignalEvent", event.summary());
  }

  @Test
  void recentEventsReturnsNewestFirstAndHonorsLimit() {
    SpringRuntimeEventsService service = new SpringRuntimeEventsService();

    service.onApplicationEvent(new AlphaEvent(this));
    service.onApplicationEvent(new BetaEvent(this));
    service.onApplicationEvent(new GammaEvent(this));

    List<RuntimeDiagnosticEvent> events = service.recentEvents(2);
    assertEquals(2, events.size());
    assertEquals("GammaEvent", events.get(0).summary());
    assertEquals("BetaEvent", events.get(1).summary());
  }

  @Test
  void clearEventsRemovesAllBufferedRows() {
    SpringRuntimeEventsService service = new SpringRuntimeEventsService();
    service.onApplicationEvent(new AlphaEvent(this));
    service.onApplicationEvent(new BetaEvent(this));
    assertTrue(!service.recentEvents(10).isEmpty());

    service.clearEvents();

    assertTrue(service.recentEvents(10).isEmpty());
  }

  @Test
  void changeStreamEmitsOnAppendAndClear() {
    SpringRuntimeEventsService service = new SpringRuntimeEventsService();
    TestSubscriber<Long> ts = service.changeStream().test();

    service.onApplicationEvent(new AlphaEvent(this));
    service.onApplicationEvent(new BetaEvent(this));
    service.clearEvents();

    ts.assertValueCount(3);
  }

  @Test
  void onStartAddsInstalledPluginSummaryWhenPluginsAreDeclared() {
    SpringRuntimeEventsService service =
        new SpringRuntimeEventsService(
            Path.of("/tmp/ircafe/plugins"),
            List.of(
                new InstalledPluginDescriptor(
                    "sample-plugin", "1.2.0", 1, Path.of("/tmp/ircafe/plugins/sample.jar"))));

    service.onStart();

    List<RuntimeDiagnosticEvent> events = service.recentEvents(10);
    assertEquals(2, events.size());
    RuntimeDiagnosticEvent pluginEvent = events.getFirst();
    assertEquals("INFO", pluginEvent.level());
    assertEquals("InstalledPlugins", pluginEvent.type());
    assertEquals("Discovered 1 declared plugin(s).", pluginEvent.summary());
    assertTrue(pluginEvent.details().contains("pluginDirectory=/tmp/ircafe/plugins"));
    assertTrue(pluginEvent.details().contains("pluginId=sample-plugin"));
    assertTrue(pluginEvent.details().contains("pluginVersion=1.2.0"));
    assertTrue(pluginEvent.details().contains("sourceJar=/tmp/ircafe/plugins/sample.jar"));
  }

  @Test
  void onStartAddsPluginProblemsWhenPluginIssuesArePresent() {
    SpringRuntimeEventsService service =
        new SpringRuntimeEventsService(
            Path.of("/tmp/ircafe/plugins"),
            List.of(),
            List.of(
                new InstalledPluginProblem(
                    "ERROR",
                    "Failed to load plugin providers for backend commands",
                    "Provider class could not be loaded")));

    service.onStart();

    List<RuntimeDiagnosticEvent> events = service.recentEvents(10);
    assertEquals(2, events.size());
    RuntimeDiagnosticEvent pluginProblemEvent = events.getFirst();
    assertEquals("ERROR", pluginProblemEvent.level());
    assertEquals("PluginProblems", pluginProblemEvent.type());
    assertEquals("Detected 1 plugin problem(s).", pluginProblemEvent.summary());
    assertTrue(pluginProblemEvent.details().contains("summary=Failed to load plugin providers"));
    assertTrue(pluginProblemEvent.details().contains("Provider class could not be loaded"));
  }

  private static final class WarnSignalEvent extends ApplicationEvent {
    private WarnSignalEvent(Object source) {
      super(source);
    }
  }

  private static final class AlphaEvent extends ApplicationEvent {
    private AlphaEvent(Object source) {
      super(source);
    }
  }

  private static final class BetaEvent extends ApplicationEvent {
    private BetaEvent(Object source) {
      super(source);
    }
  }

  private static final class GammaEvent extends ApplicationEvent {
    private GammaEvent(Object source) {
      super(source);
    }
  }
}
