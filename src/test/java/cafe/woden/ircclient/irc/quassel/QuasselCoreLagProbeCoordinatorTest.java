package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.QtDateTimeValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import io.reactivex.rxjava3.core.Completable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreLagProbeCoordinatorTest {
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final OutputStream output = mock(OutputStream.class);
  private final List<String> requests = new CopyOnWriteArrayList<>();
  private final List<String> measurements = new CopyOnWriteArrayList<>();
  private final List<Socket> writes = new CopyOnWriteArrayList<>();
  private final AtomicReference<Thread> worker = new AtomicReference<>();
  private final AtomicReference<Runnable> beforeWrite = new AtomicReference<>(() -> {});
  private final QuasselCoreSession original = session();
  private final AtomicReference<QuasselCoreSession> current = new AtomicReference<>(original);
  private final QuasselCoreLagProbeCoordinator coordinator =
      new QuasselCoreLagProbeCoordinator(
          (serverId, operation) -> {
            requests.add(serverId + ":" + operation);
            worker.set(Thread.currentThread());
            var session = current.get();
            if (session == null) {
              throw new BackendNotAvailableException(
                  IrcProperties.Server.Backend.QUASSEL_CORE, operation, serverId, "offline");
            }
            return session;
          },
          serverId -> {
            measurements.add(serverId);
            return current.get();
          });

  @Test
  void probeIsDeferredAndWritesAMatchingNativeHeartbeatOnAnIoWorker() throws Exception {
    Completable request = coordinator.request(" core ");
    assertTrue(requests.isEmpty());
    assertTrue(writes.isEmpty());
    verifyNoInteractions(codec);
    Thread caller = Thread.currentThread();
    complete(request);
    assertNotSame(caller, worker.get());
    assertEquals(List.of("core:request lag probe"), requests);
    assertEquals(List.of(original.socketRef.get()), writes);
    assertEquals(OptionalLong.empty(), coordinator.lastMeasuredLagMs("core"));
    var token = org.mockito.ArgumentCaptor.forClass(QtDateTimeValue.class);
    verify(codec).writeSignalProxyHeartBeat(eq(output), token.capture());
    verifyNoMoreInteractions(codec);
    original.lag.observeReply(List.of(token.getValue()));
    assertTrue(coordinator.lastMeasuredLagMs(" core ").isPresent());
    assertEquals(List.of("core", "core"), measurements);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void blankServerIsRejectedOnlyWhenSubscribedBeforeSessionLookup(String serverId) {
    Completable request = coordinator.request(serverId);
    assertTrue(requests.isEmpty());
    request
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof IllegalArgumentException
                    && "server id is blank".equals(error.getMessage()));
    assertTrue(requests.isEmpty());
    assertTrue(writes.isEmpty());
    verifyNoInteractions(codec);
  }

  @Test
  void unavailableSessionPreservesTheBackendErrorContextWithoutSending() {
    current.set(null);
    coordinator
        .request(" core ")
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof BackendNotAvailableException unavailable
                    && "quassel-core".equals(unavailable.backendId())
                    && "core".equals(unavailable.serverId())
                    && "request lag probe".equals(unavailable.operation())
                    && "offline".equals(unavailable.detail()));
    assertTrue(writes.isEmpty());
    verifyNoInteractions(codec);
  }

  @Test
  void socketLostAfterSessionValidationFailsBeforeStartingOrCancelingAProbe() {
    var pending = original.lag.beginProbe();
    original.socketRef.set(null);
    coordinator
        .request("core")
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof IllegalStateException
                    && "Quassel socket is closed".equals(error.getMessage()));
    assertTrue(writes.isEmpty());
    verifyNoInteractions(codec);
    original.lag.observeReply(List.of(pending));
    assertTrue(original.lag.lastMeasuredLagMs().isPresent());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedWriteCancelsPendingProbeAndPreservesThePreviousMeasurement(boolean runtimeFailure)
      throws Exception {
    recordSample(original);
    OptionalLong previous = original.lag.lastMeasuredLagMs();
    assertTrue(previous.isPresent());
    Exception error =
        runtimeFailure
            ? new IllegalStateException("heartbeat codec failed")
            : new IOException("heartbeat write failed");
    doThrow(error).when(codec).writeSignalProxyHeartBeat(eq(output), any());
    coordinator.request("core").test().awaitDone(5, TimeUnit.SECONDS).assertError(error);
    var token = org.mockito.ArgumentCaptor.forClass(QtDateTimeValue.class);
    verify(codec).writeSignalProxyHeartBeat(eq(output), token.capture());
    original.lag.observeReply(List.of(token.getValue()));
    assertEquals(previous, coordinator.lastMeasuredLagMs("core"));
    assertEquals(1, writes.size());
    verifyNoMoreInteractions(codec);
  }

  @Test
  void failedProbeReplyCannotCreateTheFirstMeasurement() throws Exception {
    var error = new IOException("heartbeat write failed");
    doThrow(error).when(codec).writeSignalProxyHeartBeat(eq(output), any());
    coordinator.request("core").test().awaitDone(5, TimeUnit.SECONDS).assertError(error);
    var token = org.mockito.ArgumentCaptor.forClass(QtDateTimeValue.class);
    verify(codec).writeSignalProxyHeartBeat(eq(output), token.capture());
    original.lag.observeReply(List.of(token.getValue()));
    assertEquals(OptionalLong.empty(), coordinator.lastMeasuredLagMs("core"));
    assertEquals(1, writes.size());
    verifyNoMoreInteractions(codec);
  }

  @Test
  void senderFailureBeforeEncodingPropagatesWithoutWriting() {
    var error = new IllegalStateException("sender failed before codec");
    beforeWrite.set(
        () -> {
          throw error;
        });
    coordinator.request("core").test().awaitDone(5, TimeUnit.SECONDS).assertError(error);
    assertEquals(OptionalLong.empty(), coordinator.lastMeasuredLagMs("core"));
    assertTrue(writes.isEmpty());
    verifyNoInteractions(codec);
  }

  @Test
  void repeatedSubscriptionsResolveTheCurrentSessionWithoutReusingTheOldSocket() throws Exception {
    Completable request = coordinator.request("core");
    complete(request);
    var replacement = session();
    current.set(replacement);
    complete(request);
    assertEquals(List.of(original.socketRef.get(), replacement.socketRef.get()), writes);
    assertEquals(List.of("core:request lag probe", "core:request lag probe"), requests);
    verify(codec, times(2)).writeSignalProxyHeartBeat(eq(output), any());
  }

  @Test
  void heartbeatUsesTheCapturedSocketWhenTheReferenceChangesDuringTheWrite() throws Exception {
    var captured = original.socketRef.get();
    var replacement = new Socket();
    beforeWrite.set(() -> original.socketRef.set(replacement));
    complete(coordinator.request("core"));
    assertEquals(List.of(captured), writes);
    assertSame(replacement, original.socketRef.get());
    verify(codec).writeSignalProxyHeartBeat(eq(output), any());
  }

  @Test
  void measurementsFollowSessionReplacementAndClearWithObservedState() {
    recordSample(original);
    assertTrue(coordinator.lastMeasuredLagMs("core").isPresent());
    var replacement = session();
    current.set(replacement);
    assertEquals(OptionalLong.empty(), coordinator.lastMeasuredLagMs("core"));
    recordSample(replacement);
    assertTrue(coordinator.lastMeasuredLagMs("core").isPresent());
    replacement.clearObservedState();
    assertEquals(OptionalLong.empty(), coordinator.lastMeasuredLagMs("core"));
    assertTrue(original.lag.lastMeasuredLagMs().isPresent());
  }

  @Test
  void missingOrDisconnectedSessionHidesMeasurementsWithoutChangingTheTracker() {
    recordSample(original);
    OptionalLong recorded = original.lag.lastMeasuredLagMs();
    var socket = original.socketRef.getAndSet(null);
    assertEquals(OptionalLong.empty(), coordinator.lastMeasuredLagMs("core"));
    assertEquals(recorded, original.lag.lastMeasuredLagMs());
    current.set(null);
    assertEquals(OptionalLong.empty(), coordinator.lastMeasuredLagMs("core"));
    current.set(original);
    original.socketRef.set(socket);
    assertEquals(recorded, coordinator.lastMeasuredLagMs("core"));
  }

  @Test
  void measurementLookupRetainsItsSocketOnlyGateWithoutRequiringEstablishedPhase() {
    recordSample(original);
    assertEquals(QuasselSessionPhase.TRANSPORT_CONNECTING, original.phase.get());
    assertTrue(coordinator.lastMeasuredLagMs("core").isPresent());
    assertTrue(requests.isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void blankMeasurementLookupUsesTheNormalizedKeyAndDoesNotValidateTheServer(String serverId) {
    current.set(null);
    assertEquals(OptionalLong.empty(), coordinator.lastMeasuredLagMs(serverId));
    assertEquals(List.of(""), measurements);
    assertTrue(requests.isEmpty());
  }

  private QuasselCoreSession session() {
    var session =
        new QuasselCoreSession(
            "core",
            "alice",
            "core.example",
            4242,
            (socket, operation) -> {
              beforeWrite.get().run();
              writes.add(socket);
              operation.write(codec, output);
            },
            ignored -> {},
            ignored -> {});
    session.socketRef.set(new Socket());
    return session;
  }

  private static void recordSample(QuasselCoreSession session) {
    session.lag.observeReply(List.of(session.lag.beginProbe()));
  }

  private static void complete(Completable request) {
    request.test().awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors();
  }
}
