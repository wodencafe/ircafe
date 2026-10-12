package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreIrcClientServiceOperationTest {
  private final ServerCatalog catalog = mock(ServerCatalog.class);
  private final QuasselCoreSocketConnector connector = mock(QuasselCoreSocketConnector.class);
  private final QuasselCoreProtocolProbe probe = mock(QuasselCoreProtocolProbe.class);
  private final QuasselCoreAuthHandshake auth = mock(QuasselCoreAuthHandshake.class);
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final QuasselCoreIrcClientService service =
      spy(QuasselRuntimeTestFixtures.service(catalog, connector, probe, auth, codec));

  @AfterEach
  void shutdown() {
    service.shutdownNow();
  }

  @ParameterizedTest
  @EnumSource(Operation.class)
  void blankServerValidationIsDeferredAndPrecedesOperationSpecificValidation(Operation operation) {
    Completable request = assertDoesNotThrow(() -> operation.request(service, "   "));
    verify(service, never()).backendAvailabilityReason(anyString());
    request
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof IllegalArgumentException
                    && "server id is blank".equals(error.getMessage()));
    verify(service, never()).backendAvailabilityReason(anyString());
    verifyNoInteractions(catalog, connector, probe, auth, codec);
  }

  @ParameterizedTest
  @EnumSource(Operation.class)
  void sessionLookupIsDeferredToIoAndPreservesNormalizedBackendErrorContext(Operation operation) {
    var worker = new AtomicReference<Thread>();
    doAnswer(
            invocation -> {
              worker.set(Thread.currentThread());
              return "offline";
            })
        .when(service)
        .backendAvailabilityReason("core");
    Completable request = assertDoesNotThrow(() -> operation.request(service, " core "));
    assertNull(worker.get());
    Thread caller = Thread.currentThread();
    request
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof BackendNotAvailableException unavailable
                    && "quassel-core".equals(unavailable.backendId())
                    && "core".equals(unavailable.serverId())
                    && operation.description.equals(unavailable.operation())
                    && "offline".equals(unavailable.detail()));
    assertNotNull(worker.get());
    assertNotSame(caller, worker.get());
    verify(service).backendAvailabilityReason("core");
    verifyNoInteractions(catalog, connector, probe, auth, codec);
  }

  @ParameterizedTest
  @ValueSource(strings = {"planned", "raw"})
  void invalidInputStillTakesPrecedenceOverAnUnavailableSession(String kind) {
    Completable request =
        assertDoesNotThrow(
            () ->
                "planned".equals(kind)
                    ? service.sendToChannel("core", "", "hello")
                    : service.sendRaw("core", "PRIVMSG #room :hello\r\nQUIT"));
    request
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof IllegalArgumentException
                    && ("planned".equals(kind) ? "channel is blank" : "raw line contains CR/LF")
                        .equals(error.getMessage()));
    verify(service, never()).backendAvailabilityReason(anyString());
    verifyNoInteractions(catalog, connector, probe, auth, codec);
  }

  private enum Operation {
    CONNECT_NETWORK("quassel connect network"),
    DISCONNECT_NETWORK("quassel disconnect network"),
    CREATE_NETWORK("quassel create network"),
    UPDATE_NETWORK("quassel update network"),
    REMOVE_NETWORK("quassel remove network"),
    TYPING("send typing"),
    READ_MARKER("send read marker"),
    CHANNEL_MESSAGE("send message to channel");

    private final String description;

    Operation(String description) {
      this.description = description;
    }

    Completable request(QuasselCoreIrcClientService service, String serverId) {
      return switch (this) {
        case CONNECT_NETWORK -> service.quasselCoreConnectNetwork(serverId, "");
        case DISCONNECT_NETWORK -> service.quasselCoreDisconnectNetwork(serverId, "");
        case CREATE_NETWORK -> service.quasselCoreCreateNetwork(serverId, null);
        case UPDATE_NETWORK -> service.quasselCoreUpdateNetwork(serverId, "", null);
        case REMOVE_NETWORK -> service.quasselCoreRemoveNetwork(serverId, "");
        case TYPING -> service.sendTyping(serverId, "", "active");
        case READ_MARKER -> service.sendReadMarker(serverId, "", Instant.ofEpochSecond(10));
        case CHANNEL_MESSAGE -> service.sendToChannel(serverId, "#room", "hello");
      };
    }
  }
}
