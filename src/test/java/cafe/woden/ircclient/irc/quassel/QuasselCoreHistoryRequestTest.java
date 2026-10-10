package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verifyNoInteractions;

import cafe.woden.ircclient.config.servers.ServerCatalog;
import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import io.reactivex.rxjava3.core.Completable;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreHistoryRequestTest {
  @AfterEach
  void disposeSchedulers() {
    RxVirtualSchedulers.shutdown();
  }

  @ParameterizedTest
  @ValueSource(strings = {"before-time", "before", "latest", "between", "around"})
  void runtimePlanningIsDeferredAndRepeatedForEachSubscription(String mode) {
    IllegalStateException failure = new IllegalStateException("runtime provider unavailable");
    QuasselIrcv3RuntimeSupport runtime =
        mock(
            QuasselIrcv3RuntimeSupport.class,
            invocation -> {
              throw failure;
            });
    QuasselCoreIrcClientService service =
        new QuasselCoreIrcClientService(
            mock(ServerCatalog.class), mock(QuasselCoreSocketConnector.class),
            mock(QuasselCoreProtocolProbe.class), mock(QuasselCoreAuthHandshake.class),
            mock(QuasselCoreDatastreamCodec.class), runtime);
    try {
      Completable request = request(service, mode, "quassel", "#room", "msgid=20");
      verifyNoInteractions(runtime);
      request.test().awaitDone(5, TimeUnit.SECONDS).assertError(failure).assertNotComplete();
      request.test().awaitDone(5, TimeUnit.SECONDS).assertError(failure).assertNotComplete();
      assertEquals(2, mockingDetails(runtime).getInvocations().size());
    } finally {
      service.shutdownNow();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"before-time", "before", "latest", "between", "around"})
  void runtimeTargetValidationStillPrecedesBlankServerValidation(String mode) {
    QuasselCoreIrcClientService service = disconnectedService();
    try {
      request(service, mode, "", "", "msgid=20")
          .test()
          .awaitDone(5, TimeUnit.SECONDS)
          .assertError(
              error ->
                  error instanceof IllegalArgumentException
                      && "target is blank".equals(error.getMessage()));
    } finally {
      service.shutdownNow();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"before", "latest", "between", "around"})
  void missingSessionStillPrecedesNativeNumericSelectorValidation(String mode) {
    QuasselCoreIrcClientService service = disconnectedService();
    try {
      request(service, mode, " quassel ", "#room", "msgid=opaque")
          .test()
          .awaitDone(5, TimeUnit.SECONDS)
          .assertError(
              error ->
                  error instanceof BackendNotAvailableException
                      && error.getMessage().contains("quassel")
                      && error.getMessage().contains("Quassel Core backend is not connected"));
    } finally {
      service.shutdownNow();
    }
  }

  private static QuasselCoreIrcClientService disconnectedService() {
    return QuasselRuntimeTestFixtures.service(
        mock(ServerCatalog.class),
        mock(QuasselCoreSocketConnector.class),
        mock(QuasselCoreProtocolProbe.class),
        mock(QuasselCoreAuthHandshake.class),
        mock(QuasselCoreDatastreamCodec.class));
  }

  private static Completable request(
      QuasselCoreIrcClientService service,
      String mode,
      String serverId,
      String target,
      String selector) {
    return switch (mode) {
      case "before-time" ->
          service.requestChatHistoryBefore(serverId, target, Instant.ofEpochSecond(2), 25);
      case "before" -> service.requestChatHistoryBefore(serverId, target, selector, 25);
      case "latest" -> service.requestChatHistoryLatest(serverId, target, selector, 25);
      case "between" ->
          service.requestChatHistoryBetween(serverId, target, selector, "msgid=30", 25);
      case "around" -> service.requestChatHistoryAround(serverId, target, selector, 25);
      default -> throw new IllegalArgumentException("Unknown test mode: " + mode);
    };
  }
}
