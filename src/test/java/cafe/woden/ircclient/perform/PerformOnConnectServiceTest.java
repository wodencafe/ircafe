package cafe.woden.ircclient.perform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.api.AvailableBackendIdsPort;
import cafe.woden.ircclient.app.api.BackendAvailabilityReasonFormatter;
import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.commands.CommandParser;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.DisconnectRequestSource;
import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.ServerIrcEvent;
import cafe.woden.ircclient.irc.backend.IrcBackendAvailabilityPort;
import cafe.woden.ircclient.irc.port.IrcConnectionLifecyclePort;
import cafe.woden.ircclient.irc.port.IrcIdentityPort;
import cafe.woden.ircclient.irc.port.IrcMediatorInteractionPort;
import cafe.woden.ircclient.irc.port.IrcMessagingPort;
import cafe.woden.ircclient.irc.port.IrcTargetMembershipPort;
import cafe.woden.ircclient.model.TargetRef;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.TestScheduler;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PerformOnConnectServiceTest {

  private IrcMediatorInteractionPort irc;
  private IrcTargetMembershipPort membership;
  private IrcConnectionLifecyclePort lifecycle;
  private IrcIdentityPort identity;
  private IrcMessagingPort messaging;
  private IrcBackendAvailabilityPort backendAvailability;
  private ServerCatalog serverCatalog;
  private CommandParser commandParser;
  private UiPort ui;
  private PublishProcessor<ServerIrcEvent> events;
  private PerformOnConnectService service;

  @BeforeEach
  void setUp() {
    irc = Mockito.mock(IrcMediatorInteractionPort.class);
    membership = Mockito.mock(IrcTargetMembershipPort.class);
    lifecycle = Mockito.mock(IrcConnectionLifecyclePort.class);
    identity = Mockito.mock(IrcIdentityPort.class);
    messaging = Mockito.mock(IrcMessagingPort.class);
    backendAvailability = Mockito.mock(IrcBackendAvailabilityPort.class);
    serverCatalog = Mockito.mock(ServerCatalog.class);
    commandParser = Mockito.mock(CommandParser.class);
    ui = Mockito.mock(UiPort.class);
    events = PublishProcessor.create();
    when(irc.events()).thenReturn(events);
    when(irc.currentNick("libera")).thenReturn(Optional.of("me"));
    service =
        new PerformOnConnectService(
            irc,
            membership,
            lifecycle,
            identity,
            messaging,
            backendAvailability,
            BackendAvailabilityReasonFormatter.builtInsBackendMetadata(),
            serverCatalog,
            commandParser,
            ui);
  }

  @AfterEach
  void tearDown() {
    service.shutdown();
    RxJavaPlugins.reset();
  }

  @Test
  void unknownCommandIsReportedAndLaterLinesStillRun() {
    doReturn(Optional.of(serverWithPerform("libera", List.of("/weird command", "RAW NEXT"))))
        .when(serverCatalog)
        .find("libera");
    doReturn(new ParsedInput.Unknown("/weird command")).when(commandParser).parse("/weird command");
    doReturn(Completable.complete()).when(irc).sendRaw("libera", "RAW NEXT");

    fireReady();

    TargetRef status = new TargetRef("libera", "status");
    verify(ui, timeout(1_000))
        .appendStatus(status, "(perform)", "Unknown perform command: /weird command");
    verify(irc, timeout(2_000)).sendRaw("libera", "RAW NEXT");
  }

  @Test
  void perLineFailureIsSurfacedAndRunContinues() {
    doReturn(Optional.of(serverWithPerform("libera", List.of("/join #ircafe", "RAW NEXT"))))
        .when(serverCatalog)
        .find("libera");
    doReturn(new ParsedInput.Join("#ircafe")).when(commandParser).parse("/join #ircafe");
    doReturn(Completable.error(new IllegalStateException("join failed")))
        .when(membership)
        .joinChannel("libera", "#ircafe");
    doReturn(Completable.complete()).when(irc).sendRaw("libera", "RAW NEXT");

    fireReady();

    TargetRef status = new TargetRef("libera", "status");
    verify(ui, timeout(1_500))
        .appendError(eq(status), eq("(perform)"), contains("Error running: /join #ircafe"));
    verify(irc, timeout(2_000)).sendRaw("libera", "RAW NEXT");
  }

  @Test
  void reconnectStormCancelsPriorRunAndPreventsDuplicates() throws Exception {
    TestScheduler scheduler = installTestScheduler();
    doReturn(Optional.of(serverWithPerform("libera", List.of("/wait 700", "RAW ONCE"))))
        .when(serverCatalog)
        .find("libera");

    AtomicInteger rawCalls = new AtomicInteger();
    doAnswer(
            invocation -> {
              rawCalls.incrementAndGet();
              return Completable.complete();
            })
        .when(irc)
        .sendRaw("libera", "RAW ONCE");

    fireReady();
    scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
    fireReady();

    scheduler.advanceTimeBy(1_000, TimeUnit.MILLISECONDS);
    assertEquals(1, rawCalls.get(), "reconnect should keep only the latest perform run active");
  }

  @Test
  void skipsPerformWhenBackendIsUnavailable() {
    doReturn(Optional.of(serverWithPerform("libera", List.of("RAW NEXT"))))
        .when(serverCatalog)
        .find("libera");
    when(backendAvailability.backendAvailabilityReason("libera"))
        .thenReturn("Quassel Core backend is not implemented yet");

    fireReady();

    TargetRef status = new TargetRef("libera", "status");
    verify(ui, timeout(1_000))
        .appendStatus(
            status,
            "(perform)",
            "Skipping perform list: backend unavailable (Quassel Core backend is not implemented yet)");
    verify(irc, never()).sendRaw("libera", "RAW NEXT");
  }

  @Test
  void skipsPerformWhenGenericPluginBackendReasonUsesDisplayName() {
    service.shutdown();

    AvailableBackendIdsPort backendMetadata = Mockito.mock(AvailableBackendIdsPort.class);
    when(backendMetadata.backendDisplayName("plugin-backend")).thenReturn("Fancy Plugin");
    service =
        new PerformOnConnectService(
            irc,
            membership,
            lifecycle,
            identity,
            messaging,
            backendAvailability,
            backendMetadata,
            serverCatalog,
            commandParser,
            ui);

    doReturn(Optional.of(serverWithPerform("libera", List.of("RAW NEXT"), "plugin-backend")))
        .when(serverCatalog)
        .find("libera");
    when(backendAvailability.backendAvailabilityReason("libera")).thenReturn("not connected");

    fireReady();

    TargetRef status = new TargetRef("libera", "status");
    verify(ui, timeout(1_000))
        .appendStatus(
            status,
            "(perform)",
            "Skipping perform list: backend unavailable (Fancy Plugin backend: not connected)");
    verify(irc, never()).sendRaw("libera", "RAW NEXT");
  }

  @Test
  void commandsUseNarrowPortsInOrderAndQuitRetainsAutomationSource() {
    TestScheduler scheduler = installTestScheduler();
    List<String> lines =
        List.of(
            "/nick next",
            "/away",
            "/part #ircafe",
            "/names #ircafe",
            "/notice alice hi",
            "/msg bob hello",
            "/whois alice",
            "/whowas bob 2",
            "/quit bye");
    when(serverCatalog.find("libera")).thenReturn(Optional.of(serverWithPerform("libera", lines)));
    when(commandParser.parse(lines.get(0))).thenReturn(new ParsedInput.Nick("next"));
    when(commandParser.parse(lines.get(1))).thenReturn(new ParsedInput.Away(""));
    when(commandParser.parse(lines.get(2))).thenReturn(new ParsedInput.Part("#ircafe", ""));
    when(commandParser.parse(lines.get(3))).thenReturn(new ParsedInput.Names("#ircafe"));
    when(commandParser.parse(lines.get(4))).thenReturn(new ParsedInput.Notice("alice", "hi"));
    when(commandParser.parse(lines.get(5))).thenReturn(new ParsedInput.Msg("bob", "hello"));
    when(commandParser.parse(lines.get(6))).thenReturn(new ParsedInput.Whois("alice"));
    when(commandParser.parse(lines.get(7))).thenReturn(new ParsedInput.Whowas("bob", 2));
    when(commandParser.parse(lines.get(8))).thenReturn(new ParsedInput.Quit(" bye "));
    when(identity.changeNick("libera", "next")).thenReturn(Completable.complete());
    when(identity.setAway("libera", null)).thenReturn(Completable.complete());
    when(membership.partChannel("libera", "#ircafe", null)).thenReturn(Completable.complete());
    when(membership.requestNames("libera", "#ircafe")).thenReturn(Completable.complete());
    when(messaging.sendNotice("libera", "alice", "hi")).thenReturn(Completable.complete());
    when(irc.sendPrivateMessage("libera", "bob", "hello")).thenReturn(Completable.complete());
    when(irc.whois("libera", "alice")).thenReturn(Completable.complete());
    when(irc.whowas("libera", "bob", 2)).thenReturn(Completable.complete());
    when(lifecycle.disconnect("libera", "bye", DisconnectRequestSource.AUTOMATION))
        .thenReturn(Completable.complete());

    fireReady();
    scheduler.advanceTimeBy(3, TimeUnit.SECONDS);

    var order = inOrder(identity, membership, messaging, irc, lifecycle);
    order.verify(identity).changeNick("libera", "next");
    order.verify(identity).setAway("libera", null);
    order.verify(membership).partChannel("libera", "#ircafe", null);
    order.verify(membership).requestNames("libera", "#ircafe");
    order.verify(messaging).sendNotice("libera", "alice", "hi");
    order.verify(irc).sendPrivateMessage("libera", "bob", "hello");
    order.verify(irc).whois("libera", "alice");
    order.verify(irc).whowas("libera", "bob", 2);
    order.verify(lifecycle).disconnect("libera", "bye", DisconnectRequestSource.AUTOMATION);
  }

  @Test
  void rawLinesAndKeyedJoinsKeepVariableSubstitution() {
    TestScheduler scheduler = installTestScheduler();
    when(serverCatalog.find("libera"))
        .thenReturn(
            Optional.of(
                serverWithPerform(
                    "libera", List.of("PRIVMSG $me :$nick on $server", "/join #ircafe secret"))));
    when(commandParser.parse("/join #ircafe secret"))
        .thenReturn(new ParsedInput.Join("#ircafe", "secret"));
    when(irc.sendRaw("libera", "PRIVMSG me :me on libera")).thenReturn(Completable.complete());
    when(irc.sendRaw("libera", "JOIN #ircafe secret")).thenReturn(Completable.complete());

    fireReady();
    scheduler.advanceTimeBy(1, TimeUnit.SECONDS);

    var order = inOrder(irc);
    order.verify(irc).sendRaw("libera", "PRIVMSG me :me on libera");
    order.verify(irc).sendRaw("libera", "JOIN #ircafe secret");
    verify(membership, never()).joinChannel("libera", "#ircafe");
  }

  @Test
  void shutdownDisposesEventsAndPendingCommandAndPreventsLaterLines() {
    TestScheduler scheduler = installTestScheduler();
    CompletableSubject pending = CompletableSubject.create();
    when(serverCatalog.find("libera"))
        .thenReturn(Optional.of(serverWithPerform("libera", List.of("RAW FIRST", "RAW SECOND"))));
    when(irc.sendRaw("libera", "RAW FIRST")).thenReturn(pending);

    fireReady();
    assertTrue(events.hasSubscribers());
    assertTrue(pending.hasObservers());

    service.shutdown();
    assertFalse(events.hasSubscribers());
    assertFalse(pending.hasObservers());
    pending.onComplete();
    scheduler.advanceTimeBy(1, TimeUnit.SECONDS);

    verify(irc, never()).sendRaw("libera", "RAW SECOND");
  }

  private void fireReady() {
    events.onNext(new ServerIrcEvent("libera", new IrcEvent.ConnectionReady(Instant.now())));
  }

  private static TestScheduler installTestScheduler() {
    TestScheduler scheduler = new TestScheduler();
    RxJavaPlugins.setComputationSchedulerHandler(ignored -> scheduler);
    return scheduler;
  }

  private static IrcProperties.Server serverWithPerform(String id, List<String> perform) {
    return serverWithPerform(id, perform, "irc");
  }

  private static IrcProperties.Server serverWithPerform(
      String id, List<String> perform, String backendId) {
    return IrcPropertiesTestFixtures.serverBuilder(id)
        .nick("tester")
        .login("tester")
        .realName("Tester")
        .perform(perform)
        .backendId(backendId)
        .build();
  }
}
