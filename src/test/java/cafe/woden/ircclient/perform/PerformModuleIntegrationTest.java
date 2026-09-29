package cafe.woden.ircclient.perform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.backend.IrcBackendAvailabilityPort;
import cafe.woden.ircclient.irc.port.IrcConnectionLifecyclePort;
import cafe.woden.ircclient.irc.port.IrcIdentityPort;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.irc.port.IrcMessagingPort;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.modulith.AbstractApplicationModuleIntegrationTest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class PerformModuleIntegrationTest extends AbstractApplicationModuleIntegrationTest {

  @MockitoBean(name = "ircIdentityPort")
  IrcIdentityPort ircIdentityPort;

  @MockitoBean(name = "ircMessagingPort")
  IrcMessagingPort ircMessagingPort;

  @MockitoBean(name = "ircTargetMembershipPort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcTargetMembershipPort ircTargetMembershipPort;

  @MockitoBean(name = "ircMediatorInteractionPort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcMediatorInteractionPort ircMediatorInteractionPort;

  @MockitoBean(name = "ircConnectionLifecyclePort", answers = Answers.RETURNS_DEEP_STUBS)
  IrcConnectionLifecyclePort ircConnectionLifecyclePort;

  @MockitoBean(name = "swingUiPort", answers = Answers.RETURNS_DEEP_STUBS)
  UiPort swingUiPort;

  @MockitoBean(name = "ircClientService")
  IrcBackendAvailabilityPort backendAvailabilityDouble;

  @BeforeEach
  void resetBackendAvailability() {
    when(backendAvailabilityDouble.backendAvailabilityReason(
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn("");
  }

  @MockitoBean CommandParser commandParser;
  @MockitoBean ServerCatalog serverCatalog;

  private final ApplicationContext applicationContext;
  private final PerformOnConnectService performOnConnectService;
  private final IrcMediatorInteractionPort irc;
  private final IrcTargetMembershipPort membership;
  private final IrcBackendAvailabilityPort backendAvailability;
  private final UiPort uiPort;

  PerformModuleIntegrationTest(
      ApplicationContext applicationContext,
      PerformOnConnectService performOnConnectService,
      IrcMediatorInteractionPort irc,
      IrcTargetMembershipPort membership,
      @Qualifier("ircClientService") IrcBackendAvailabilityPort backendAvailability,
      @Qualifier("swingUiPort") UiPort uiPort) {
    this.applicationContext = applicationContext;
    this.performOnConnectService = performOnConnectService;
    this.irc = irc;
    this.membership = membership;
    this.backendAvailability = backendAvailability;
    this.uiPort = uiPort;
  }

  @AfterEach
  void resetRxJavaPlugins() {
    RxJavaPlugins.reset();
  }

  @Test
  void exposesSinglePerformOnConnectServiceBean() {
    assertEquals(1, applicationContext.getBeansOfType(PerformOnConnectService.class).size());
    assertNotNull(performOnConnectService);
  }

  @Test
  void connectionReadyEventRunsPerformLinesInConfiguredOrderAndSurfacesStepErrors()
      throws Exception {
    doReturn(
            Optional.of(
                serverWithPerform("libera", List.of("/join #ircafe", "PRIVMSG #ircafe :hello"))))
        .when(serverCatalog)
        .find("libera");
    doReturn(new ParsedInput.Join("#ircafe")).when(commandParser).parse("/join #ircafe");
    doReturn(Completable.error(new IllegalStateException("join failed")))
        .when(membership)
        .joinChannel("libera", "#ircafe");
    doReturn(Completable.complete()).when(irc).sendRaw("libera", "PRIVMSG #ircafe :hello");

    fireEvent(new IrcEvent.ConnectionReady(Instant.now()));

    TargetRef status = new TargetRef("libera", "status");
    verify(uiPort).ensureTargetExists(status);
    verify(uiPort).appendStatus(status, "(perform)", "Running perform list (2 lines)");
    verify(uiPort, timeout(2_000))
        .appendError(eq(status), eq("(perform)"), contains("Error running: /join #ircafe"));
    verify(membership, timeout(2_000)).joinChannel("libera", "#ircafe");
    verify(irc, timeout(2_000)).sendRaw("libera", "PRIVMSG #ircafe :hello");

    InOrder inOrder = inOrder(membership, irc);
    inOrder.verify(membership).joinChannel("libera", "#ircafe");
    inOrder.verify(irc).sendRaw("libera", "PRIVMSG #ircafe :hello");
  }

  @Test
  void disconnectedEventCancelsInFlightPerformRun() throws Exception {
    TestScheduler scheduler = installTestScheduler();
    doReturn(Optional.of(serverWithPerform("libera", List.of("/wait 2000", "RAW SECOND"))))
        .when(serverCatalog)
        .find("libera");

    AtomicInteger secondLineCalls = new AtomicInteger();
    doAnswer(
            invocation -> {
              secondLineCalls.incrementAndGet();
              return Completable.complete();
            })
        .when(irc)
        .sendRaw("libera", "RAW SECOND");

    fireEvent(new IrcEvent.ConnectionReady(Instant.now()));

    TargetRef status = new TargetRef("libera", "status");
    verify(uiPort, timeout(1_000)).appendStatus(status, "(perform)", "Waiting 2000ms");

    fireEvent(new IrcEvent.Disconnected(Instant.now(), "network split"));
    scheduler.advanceTimeBy(3_000, TimeUnit.MILLISECONDS);
    assertEquals(0, secondLineCalls.get(), "disconnect should cancel queued perform lines");
  }

  @Test
  void reconnectCancelsPriorPerformRunAndOnlyLatestRunContinues() throws Exception {
    TestScheduler scheduler = installTestScheduler();
    doReturn(Optional.of(serverWithPerform("libera", List.of("/wait 800", "RAW SECOND"))))
        .when(serverCatalog)
        .find("libera");

    AtomicInteger rawCalls = new AtomicInteger();
    doAnswer(
            invocation -> {
              rawCalls.incrementAndGet();
              return Completable.complete();
            })
        .when(irc)
        .sendRaw("libera", "RAW SECOND");

    fireEvent(new IrcEvent.ConnectionReady(Instant.now()));
    scheduler.advanceTimeBy(120, TimeUnit.MILLISECONDS);
    fireEvent(new IrcEvent.ConnectionReady(Instant.now()));

    TargetRef status = new TargetRef("libera", "status");
    verify(uiPort, timeout(1_000).atLeast(2))
        .appendStatus(status, "(perform)", "Running perform list (2 lines)");

    scheduler.advanceTimeBy(1_100, TimeUnit.MILLISECONDS);
    assertEquals(1, rawCalls.get(), "reconnect should cancel overlapping perform runs");
  }

  @Test
  void waitAndUnsupportedCommandsAreReportedAndRunContinues() throws Exception {
    TestScheduler scheduler = installTestScheduler();
    doReturn(
            Optional.of(serverWithPerform("libera", List.of("/wait 120", "/help topic", "RAW OK"))))
        .when(serverCatalog)
        .find("libera");
    doReturn(new ParsedInput.Help("topic")).when(commandParser).parse("/help topic");
    doReturn(Completable.complete()).when(irc).sendRaw("libera", "RAW OK");

    fireEvent(new IrcEvent.ConnectionReady(Instant.now()));

    TargetRef status = new TargetRef("libera", "status");
    verify(uiPort).appendStatus(status, "(perform)", "Waiting 120ms");
    scheduler.advanceTimeBy(600, TimeUnit.MILLISECONDS);
    verify(uiPort)
        .appendStatus(
            status, "(perform)", "Unsupported in perform: /help topic (use /quote or raw IRC)");
    verify(irc).sendRaw("libera", "RAW OK");
  }

  @Test
  void connectionReadyEventSkipsPerformWhenBackendIsUnavailable() throws Exception {
    doReturn(Optional.of(serverWithPerform("libera", List.of("PRIVMSG #ircafe :hello"))))
        .when(serverCatalog)
        .find("libera");
    doReturn("Quassel Core backend is not implemented yet")
        .when(backendAvailability)
        .backendAvailabilityReason("libera");

    fireEvent(new IrcEvent.ConnectionReady(Instant.now()));

    TargetRef status = new TargetRef("libera", "status");
    verify(uiPort, timeout(2_000))
        .appendStatus(
            status,
            "(perform)",
            "Skipping perform list: backend unavailable (Quassel Core backend is not implemented yet)");
    verify(irc, never()).sendRaw("libera", "PRIVMSG #ircafe :hello");
  }

  private void fireEvent(IrcEvent event) throws Exception {
    PerformOnConnectService target = AopTestUtils.getTargetObject(performOnConnectService);
    Method onEvent =
        PerformOnConnectService.class.getDeclaredMethod("onEvent", ServerIrcEvent.class);
    onEvent.setAccessible(true);
    onEvent.invoke(target, new ServerIrcEvent("libera", event));
  }

  private static TestScheduler installTestScheduler() {
    TestScheduler scheduler = new TestScheduler();
    RxJavaPlugins.setComputationSchedulerHandler(ignored -> scheduler);
    return scheduler;
  }

  private static IrcProperties.Server serverWithPerform(String id, List<String> perform) {
    return IrcPropertiesTestFixtures.serverBuilder(id)
        .nick("tester")
        .login("tester")
        .realName("Tester")
        .perform(perform)
        .build();
  }
}
