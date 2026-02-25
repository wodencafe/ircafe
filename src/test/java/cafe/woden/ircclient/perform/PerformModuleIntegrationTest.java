package cafe.woden.ircclient.perform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.IrcClientService;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.modulith.AbstractApplicationModuleIntegrationTest;
import io.reactivex.rxjava3.core.Completable;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class PerformModuleIntegrationTest extends AbstractApplicationModuleIntegrationTest {

  @MockitoBean CommandParser commandParser;
  @MockitoBean ServerCatalog serverCatalog;

  private final ApplicationContext applicationContext;
  private final PerformOnConnectService performOnConnectService;
  private final IrcClientService ircClientService;
  private final UiPort uiPort;

  PerformModuleIntegrationTest(
      ApplicationContext applicationContext,
      PerformOnConnectService performOnConnectService,
      IrcClientService ircClientService,
      @Qualifier("swingUiPort") UiPort uiPort) {
    this.applicationContext = applicationContext;
    this.performOnConnectService = performOnConnectService;
    this.ircClientService = ircClientService;
    this.uiPort = uiPort;
  }

  @Test
  void exposesSinglePerformOnConnectServiceBean() {
    assertEquals(1, applicationContext.getBeansOfType(PerformOnConnectService.class).size());
    assertNotNull(performOnConnectService);
  }

  @Test
  void connectedEventRunsPerformLinesInConfiguredOrderAndSurfacesStepErrors() throws Exception {
    doReturn(
            Optional.of(
                serverWithPerform("libera", List.of("/join #ircafe", "PRIVMSG #ircafe :hello"))))
        .when(serverCatalog)
        .find("libera");
    doReturn(new ParsedInput.Join("#ircafe")).when(commandParser).parse("/join #ircafe");
    doReturn(Completable.error(new IllegalStateException("join failed")))
        .when(ircClientService)
        .joinChannel("libera", "#ircafe");
    doReturn(Completable.complete())
        .when(ircClientService)
        .sendRaw("libera", "PRIVMSG #ircafe :hello");

    fireEvent(new IrcEvent.Connected(Instant.now(), "irc.libera.chat", 6697, "tester"));

    TargetRef status = new TargetRef("libera", "status");
    verify(uiPort).ensureTargetExists(status);
    verify(uiPort).appendStatus(status, "(perform)", "Running perform list (2 lines)");
    verify(uiPort, timeout(2_000))
        .appendError(eq(status), eq("(perform)"), contains("Error running: /join #ircafe"));
    verify(ircClientService, timeout(2_000)).joinChannel("libera", "#ircafe");
    verify(ircClientService, timeout(2_000)).sendRaw("libera", "PRIVMSG #ircafe :hello");

    InOrder inOrder = inOrder(ircClientService);
    inOrder.verify(ircClientService).joinChannel("libera", "#ircafe");
    inOrder.verify(ircClientService).sendRaw("libera", "PRIVMSG #ircafe :hello");
  }

  @Test
  void disconnectedEventCancelsInFlightPerformRun() throws Exception {
    doReturn(Optional.of(serverWithPerform("libera", List.of("/wait 2000", "RAW SECOND"))))
        .when(serverCatalog)
        .find("libera");

    AtomicInteger secondLineCalls = new AtomicInteger();
    doAnswer(
            invocation -> {
              secondLineCalls.incrementAndGet();
              return Completable.complete();
            })
        .when(ircClientService)
        .sendRaw("libera", "RAW SECOND");

    fireEvent(new IrcEvent.Connected(Instant.now(), "irc.libera.chat", 6697, "tester"));

    TargetRef status = new TargetRef("libera", "status");
    verify(uiPort, timeout(1_000)).appendStatus(status, "(perform)", "Waiting 2000ms");

    fireEvent(new IrcEvent.Disconnected(Instant.now(), "network split"));
    Thread.sleep(2_600);
    assertEquals(0, secondLineCalls.get(), "disconnect should cancel queued perform lines");
  }

  @Test
  void reconnectCancelsPriorPerformRunAndOnlyLatestRunContinues() throws Exception {
    doReturn(Optional.of(serverWithPerform("libera", List.of("/wait 800", "RAW SECOND"))))
        .when(serverCatalog)
        .find("libera");

    AtomicInteger rawCalls = new AtomicInteger();
    doAnswer(
            invocation -> {
              rawCalls.incrementAndGet();
              return Completable.complete();
            })
        .when(ircClientService)
        .sendRaw("libera", "RAW SECOND");

    fireEvent(new IrcEvent.Connected(Instant.now(), "irc.libera.chat", 6697, "tester"));
    Thread.sleep(120);
    fireEvent(new IrcEvent.Connected(Instant.now(), "irc.libera.chat", 6697, "tester"));

    TargetRef status = new TargetRef("libera", "status");
    verify(uiPort, timeout(1_000).atLeast(2))
        .appendStatus(status, "(perform)", "Running perform list (2 lines)");

    Thread.sleep(1_300);
    assertEquals(1, rawCalls.get(), "reconnect should cancel overlapping perform runs");
  }

  @Test
  void waitAndUnsupportedCommandsAreReportedAndRunContinues() throws Exception {
    doReturn(
            Optional.of(serverWithPerform("libera", List.of("/wait 120", "/help topic", "RAW OK"))))
        .when(serverCatalog)
        .find("libera");
    doReturn(new ParsedInput.Help("topic")).when(commandParser).parse("/help topic");
    doReturn(Completable.complete()).when(ircClientService).sendRaw("libera", "RAW OK");

    fireEvent(new IrcEvent.Connected(Instant.now(), "irc.libera.chat", 6697, "tester"));

    TargetRef status = new TargetRef("libera", "status");
    verify(uiPort, timeout(1_000)).appendStatus(status, "(perform)", "Waiting 120ms");
    verify(uiPort, timeout(1_000))
        .appendStatus(
            status, "(perform)", "Unsupported in perform: /help topic (use /quote or raw IRC)");
    verify(ircClientService, timeout(2_000)).sendRaw("libera", "RAW OK");
  }

  private void fireEvent(IrcEvent event) throws Exception {
    PerformOnConnectService target = AopTestUtils.getTargetObject(performOnConnectService);
    Method onEvent =
        PerformOnConnectService.class.getDeclaredMethod("onEvent", ServerIrcEvent.class);
    onEvent.setAccessible(true);
    onEvent.invoke(target, new ServerIrcEvent("libera", event));
  }

  private static IrcProperties.Server serverWithPerform(String id, List<String> perform) {
    return new IrcProperties.Server(
        id,
        "irc.example.net",
        6697,
        true,
        "",
        "tester",
        "tester",
        "Tester",
        null,
        List.of(),
        perform,
        null);
  }
}
