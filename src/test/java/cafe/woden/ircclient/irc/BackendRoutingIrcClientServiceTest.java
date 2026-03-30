package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.config.api.BackendMetadataPort;
import cafe.woden.ircclient.irc.backend.BackendRoutingIrcClientService;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
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
  void routesCallsByConfiguredMatrixBackend() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);
    IrcBackendClientService matrixBackend = mock(IrcBackendClientService.class);

    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(matrixBackend.backend()).thenReturn(IrcProperties.Server.Backend.MATRIX);
    when(ircBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(matrixBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(server("matrix", IrcProperties.Server.Backend.MATRIX)));
    when(matrixBackend.connect("matrix")).thenReturn(Completable.complete());

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend, matrixBackend));

    service.connect("matrix").blockingAwait();

    verify(matrixBackend).connect("matrix");
    verify(ircBackend, never()).connect("matrix");
  }

  @Test
  void routesCallsByConfiguredCustomBackendId() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);
    IrcBackendClientService pluginBackend = mock(IrcBackendClientService.class);

    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(pluginBackend.backend()).thenReturn(null);
    when(pluginBackend.backendId()).thenReturn("plugin-backend");
    when(ircBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(pluginBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(serverCatalog.find("plugin")).thenReturn(Optional.of(server("plugin", "plugin-backend")));
    when(pluginBackend.connect("plugin")).thenReturn(Completable.complete());

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend, pluginBackend));

    assertEquals("plugin-backend", service.backendIdForServer("plugin"));

    service.connect("plugin").blockingAwait();

    verify(pluginBackend).connect("plugin");
    verify(ircBackend, never()).connect("plugin");
  }

  @Test
  void reportsMatrixBackendServerFromConfiguration() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);
    IrcBackendClientService matrixBackend = mock(IrcBackendClientService.class);

    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(matrixBackend.backend()).thenReturn(IrcProperties.Server.Backend.MATRIX);
    when(ircBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(matrixBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(server("matrix", IrcProperties.Server.Backend.MATRIX)));
    when(serverCatalog.find("irc"))
        .thenReturn(Optional.of(server("irc", IrcProperties.Server.Backend.IRC)));

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend, matrixBackend));

    assertEquals("matrix", service.backendIdForServer("matrix"));
    assertEquals("irc", service.backendIdForServer("irc"));
  }

  @Test
  void routesMatrixRoomMessagesToChannelPath() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);
    IrcBackendClientService matrixBackend = mock(IrcBackendClientService.class);

    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(matrixBackend.backend()).thenReturn(IrcProperties.Server.Backend.MATRIX);
    when(ircBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(matrixBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(server("matrix", IrcProperties.Server.Backend.MATRIX)));
    when(matrixBackend.sendToChannel("matrix", "!room:matrix.example.org", "hello"))
        .thenReturn(Completable.complete());

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend, matrixBackend));

    service.sendMessage("matrix", "!room:matrix.example.org", "hello").blockingAwait();

    verify(matrixBackend).sendToChannel("matrix", "!room:matrix.example.org", "hello");
    verify(matrixBackend, never())
        .sendPrivateMessage("matrix", "!room:matrix.example.org", "hello");
  }

  @Test
  void routesRegularIrcOperationsToQuasselBackendWhenServerUsesQuassel() {
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
    when(quasselBackend.joinChannel("quassel", "#ircafe")).thenReturn(Completable.complete());
    when(quasselBackend.sendToChannel("quassel", "#ircafe", "hello"))
        .thenReturn(Completable.complete());

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend, quasselBackend));

    service.joinChannel("quassel", "#ircafe").blockingAwait();
    service.sendMessage("quassel", "#ircafe", "hello").blockingAwait();

    verify(quasselBackend).joinChannel("quassel", "#ircafe");
    verify(quasselBackend).sendToChannel("quassel", "#ircafe", "hello");
    verify(ircBackend, never()).joinChannel("quassel", "#ircafe");
    verify(ircBackend, never()).sendToChannel("quassel", "#ircafe", "hello");
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
  void delegatesLagProbeStrategyToConfiguredBackend() {
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
    when(ircBackend.shouldRequestLagProbe("irc")).thenReturn(false);
    when(quasselBackend.shouldRequestLagProbe("quassel")).thenReturn(true);
    when(ircBackend.isLagProbeReady("irc")).thenReturn(true);
    when(quasselBackend.isLagProbeReady("quassel")).thenReturn(false);
    when(ircBackend.lastMeasuredLagMs("irc")).thenReturn(OptionalLong.of(111L));
    when(quasselBackend.lastMeasuredLagMs("quassel")).thenReturn(OptionalLong.of(222L));

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend, quasselBackend));

    assertFalse(service.shouldRequestLagProbe("irc"));
    assertTrue(service.shouldRequestLagProbe("quassel"));
    assertTrue(service.isLagProbeReady("irc"));
    assertFalse(service.isLagProbeReady("quassel"));
    assertEquals(111L, service.lastMeasuredLagMs("irc").orElseThrow());
    assertEquals(222L, service.lastMeasuredLagMs("quassel").orElseThrow());
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

    IllegalStateException err =
        assertThrows(IllegalStateException.class, () -> service.connect("quassel"));

    assertTrue(err.getMessage().contains("Quassel Core"));
    assertTrue(err.getMessage().contains("quassel-core"));
  }

  @Test
  void throwsWhenConfiguredPluginBackendIsMissingUsingPluginDisplayName() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    BackendMetadataPort backendMetadata = mock(BackendMetadataPort.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);

    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(ircBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(serverCatalog.find("plugin")).thenReturn(Optional.of(server("plugin", "plugin-backend")));
    when(backendMetadata.backendDisplayName("plugin-backend")).thenReturn("Fancy Plugin");

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, backendMetadata, List.of(ircBackend));

    IllegalStateException err =
        assertThrows(IllegalStateException.class, () -> service.connect("plugin"));

    assertTrue(err.getMessage().contains("Fancy Plugin"));
    assertTrue(err.getMessage().contains("plugin-backend"));
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
  void routesQuasselSetupOperationsToConfiguredBackend() {
    ServerCatalog serverCatalog = mock(ServerCatalog.class);
    IrcBackendClientService ircBackend = mock(IrcBackendClientService.class);
    IrcBackendClientService quasselBackend = mock(IrcBackendClientService.class);
    QuasselCoreControlPort.QuasselCoreSetupPrompt prompt =
        new QuasselCoreControlPort.QuasselCoreSetupPrompt(
            "quassel", "setup required", List.of("SQLite"), List.of("Database"), Map.of());
    QuasselCoreControlPort.QuasselCoreSetupRequest request =
        new QuasselCoreControlPort.QuasselCoreSetupRequest(
            "admin", "secret", "SQLite", "Database", Map.of(), Map.of());
    QuasselCoreControlPort.QuasselCoreNetworkSummary network =
        new QuasselCoreControlPort.QuasselCoreNetworkSummary(
            1, "libera", true, true, 1, "irc.libera.chat", 6697, true, Map.of());
    QuasselCoreControlPort.QuasselCoreNetworkCreateRequest createRequest =
        new QuasselCoreControlPort.QuasselCoreNetworkCreateRequest(
            "libera", "irc.libera.chat", 6697, true, "", true, 1, List.of());
    QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest updateRequest =
        new QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest(
            "", "irc2.libera.chat", 6667, false, "", true, null, null);

    when(ircBackend.backend()).thenReturn(IrcProperties.Server.Backend.IRC);
    when(quasselBackend.backend()).thenReturn(IrcProperties.Server.Backend.QUASSEL_CORE);
    when(ircBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(quasselBackend.events())
        .thenReturn(PublishProcessor.<ServerIrcEvent>create().onBackpressureBuffer());
    when(serverCatalog.find("quassel"))
        .thenReturn(Optional.of(server("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(quasselBackend.isQuasselCoreSetupPending("quassel")).thenReturn(true);
    when(quasselBackend.quasselCoreSetupPrompt("quassel")).thenReturn(Optional.of(prompt));
    when(quasselBackend.submitQuasselCoreSetup("quassel", request))
        .thenReturn(Completable.complete());
    when(quasselBackend.quasselCoreNetworks("quassel")).thenReturn(List.of(network));
    when(quasselBackend.quasselCoreConnectNetwork("quassel", "libera"))
        .thenReturn(Completable.complete());
    when(quasselBackend.quasselCoreDisconnectNetwork("quassel", "libera"))
        .thenReturn(Completable.complete());
    when(quasselBackend.quasselCoreCreateNetwork("quassel", createRequest))
        .thenReturn(Completable.complete());
    when(quasselBackend.quasselCoreUpdateNetwork("quassel", "libera", updateRequest))
        .thenReturn(Completable.complete());
    when(quasselBackend.quasselCoreRemoveNetwork("quassel", "libera"))
        .thenReturn(Completable.complete());

    BackendRoutingIrcClientService service =
        new BackendRoutingIrcClientService(serverCatalog, List.of(ircBackend, quasselBackend));

    assertEquals(true, service.isQuasselCoreSetupPending("quassel"));
    assertEquals(Optional.of(prompt), service.quasselCoreSetupPrompt("quassel"));
    service.submitQuasselCoreSetup("quassel", request).blockingAwait();
    assertEquals(List.of(network), service.quasselCoreNetworks("quassel"));
    service.quasselCoreConnectNetwork("quassel", "libera").blockingAwait();
    service.quasselCoreDisconnectNetwork("quassel", "libera").blockingAwait();
    service.quasselCoreCreateNetwork("quassel", createRequest).blockingAwait();
    service.quasselCoreUpdateNetwork("quassel", "libera", updateRequest).blockingAwait();
    service.quasselCoreRemoveNetwork("quassel", "libera").blockingAwait();

    verify(quasselBackend).submitQuasselCoreSetup("quassel", request);
    verify(quasselBackend).quasselCoreNetworks("quassel");
    verify(quasselBackend).quasselCoreConnectNetwork("quassel", "libera");
    verify(quasselBackend).quasselCoreDisconnectNetwork("quassel", "libera");
    verify(quasselBackend).quasselCoreCreateNetwork("quassel", createRequest);
    verify(quasselBackend).quasselCoreUpdateNetwork("quassel", "libera", updateRequest);
    verify(quasselBackend).quasselCoreRemoveNetwork("quassel", "libera");
    verify(ircBackend, never()).submitQuasselCoreSetup("quassel", request);
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

  private static IrcProperties.Server server(String id, String backendId) {
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
        backendId);
  }
}
