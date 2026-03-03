package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BackendRoutingIrcClientServiceTest {

  @Test
  void routesCallsByConfiguredBackend() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);
    IrcBackendClientService quasselBackend = mock(IrcBackendClientService.class);

    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(quasselBackend.backend()).thenReturn(IrcProperties.Server.Backend.QUASSEL_CORE);
    when(ircBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(quasselBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(serverCatalog.find("irc"))
        .thenReturn(Optional.of(server("irc", IrcProperties.Server.Backend.IRC)));
    when(serverCatalog.find("quassel"))
        .thenReturn(Optional.of(server("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(ircBackend.connect("irc")).thenReturn(Completable.complete());
    when(quasselBackend.connect("quassel")).thenReturn(Completable.complete());

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend, quasselBackend));

    service.connect("irc").blockingAwait();
    service.connect("quassel").blockingAwait();

    verify(ircBackend).connect("irc");
    verify(quasselBackend).connect("quassel");
  }

  @Test
  void fallsBackToIrcBackendWhenServerIsUnknown() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);
    IrcBackendClientService quasselBackend = mock(IrcBackendClientService.class);

    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(quasselBackend.backend()).thenReturn(IrcProperties.Server.Backend.QUASSEL_CORE);
    when(ircBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(quasselBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(serverCatalog.find("missing")).thenReturn(Optional.empty());
    when(ircBackend.sendRaw("missing", "PING")).thenReturn(Completable.complete());

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend, quasselBackend));

    service.sendRaw("missing", "PING").blockingAwait();

    verify(ircBackend).sendRaw("missing", "PING");
  }

  @Test
  void throwsWhenConfiguredBackendIsMissing() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);

    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(ircBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(serverCatalog.find("quassel"))
        .thenReturn(Optional.of(server("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend));

    assertThrows(IllegalStateException.class, () -> service.connect("quassel"));
  }

  @Test
  void resolvesBackendAvailabilityReasonFromConfiguredBackend() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);
    IrcBackendClientService quasselBackend = mock(IrcBackendClientService.class);

    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(quasselBackend.backend()).thenReturn(IrcProperties.Server.Backend.QUASSEL_CORE);
    when(ircBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(quasselBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(serverCatalog.find("quassel"))
        .thenReturn(Optional.of(server("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(quasselBackend.backendAvailabilityReason("quassel"))
        .thenReturn("Quassel Core backend is not implemented yet");

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend, quasselBackend));

    assertEquals(
        "Quassel Core backend is not implemented yet",
        service.backendAvailabilityReason("quassel"));
  }

  @Test
  void mergesEventsFromAllBackends() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);
    IrcBackendClientService quasselBackend = mock(IrcBackendClientService.class);
    PublishProcessor<ServerIrcEvent> ircEvents = PublishProcessor.create();
    PublishProcessor<ServerIrcEvent> quasselEvents = PublishProcessor.create();

    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(quasselBackend.backend()).thenReturn(IrcProperties.Server.Backend.QUASSEL_CORE);
    when(ircBackend.events()).thenReturn(ircEvents.onBackpressureBuffer());
    when(quasselBackend.events()).thenReturn(quasselEvents.onBackpressureBuffer());

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend, quasselBackend));

    var subscriber = service.events().test();
    ircEvents.onNext(new ServerIrcEvent("irc", new IrcEvent.ConnectionReady(Instant.now())));
    quasselEvents.onNext(
        new ServerIrcEvent("quassel", new IrcEvent.ConnectionReady(Instant.now())));

    subscriber.awaitCount(2).assertValueCount(2);
  }

  @Test
  void forwardsHeartbeatRescheduleToAllBackends() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);
    IrcBackendClientService quasselBackend = mock(IrcBackendClientService.class);

    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(quasselBackend.backend()).thenReturn(IrcProperties.Server.Backend.QUASSEL_CORE);
    when(ircBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(quasselBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend, quasselBackend));

    service.rescheduleActiveHeartbeats();

    verify(ircBackend, times(1)).rescheduleActiveHeartbeats();
    verify(quasselBackend, times(1)).rescheduleActiveHeartbeats();
  }

  @Test
  void rejectsDuplicateBackendRegistrations() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService one = mock(IrcBackendClientService.class);
    IrcBackendClientService two = mock(IrcBackendClientService.class);

    when(one.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(two.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(one.events()).thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(two.events()).thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());

    assertThrows(
        IllegalStateException.class,
        () -> new BackendRoutingIrcClientService(serverCatalog, List.of(one, two)));
  }

  @Test
  void disconnectUsesActiveBackendOwnershipWhenConfigurationBackendChanges() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);
    IrcBackendClientService quasselBackend = mock(IrcBackendClientService.class);
    PublishProcessor<ServerIrcEvent> ircEvents = PublishProcessor.create();
    PublishProcessor<ServerIrcEvent> quasselEvents = PublishProcessor.create();

    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(quasselBackend.backend()).thenReturn(IrcProperties.Server.Backend.QUASSEL_CORE);
    when(ircBackend.events()).thenReturn(ircEvents.onBackpressureBuffer());
    when(quasselBackend.events()).thenReturn(quasselEvents.onBackpressureBuffer());
    when(serverCatalog.find("hybrid"))
        .thenReturn(Optional.of(server("hybrid", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(ircBackend.disconnect("hybrid")).thenReturn(Completable.complete());
    when(quasselBackend.disconnect("hybrid")).thenReturn(Completable.complete());

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend, quasselBackend));

    var sub = service.events().test();
    ircEvents.onNext(
        new ServerIrcEvent(
            "hybrid", new IrcEvent.Connected(Instant.now(), "irc.example.net", 6697, "tester")));
    sub.awaitCount(1).assertValueCount(1);

    service.disconnect("hybrid").blockingAwait();

    verify(ircBackend).disconnect("hybrid");
    verify(quasselBackend, never()).disconnect("hybrid");
    sub.cancel();
  }

  private static IrcProperties.Server server(String id, IrcProperties.Server.Backend backend) {
    return new IrcProperties.Server(
        id,
        "irc.example.net",
        6697,
        true,
        "",
        "tester",
        "tester",
        "IRCafe Test",
        null,
        null,
        List.of(),
        List.of(),
        null,
        backend);
  }
}
