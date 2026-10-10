package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreSignalProxyDispatcherTest {
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final OutputStream output = mock(OutputStream.class);
  private final Socket socket = mock(Socket.class);
  private final List<Socket> writes = new ArrayList<>();
  private final List<IrcEvent> events = new ArrayList<>();
  private final List<String> order = new ArrayList<>();
  private IOException writeFailure;
  private final QuasselCoreSession session =
      new QuasselCoreSession(
          "core",
          "alice",
          "host",
          4242,
          (captured, operation) -> {
            writes.add(captured);
            if (writeFailure != null) throw writeFailure;
            operation.write(codec, output);
          },
          id -> {},
          event -> {
            order.add(event.getClass().getSimpleName());
            events.add(event);
          });
  private final List<SignalProxyMessage> rpc = new ArrayList<>();
  private final List<SignalProxyMessage> sync = new ArrayList<>();
  private final QuasselCoreSignalProxyDispatcher dispatcher =
      new QuasselCoreSignalProxyDispatcher(session, rpc::add, sync::add);

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, SIGNAL_PROXY_INIT_REQUEST, 7, Integer.MAX_VALUE})
  void unsupportedTypesAndNullMessagesHaveNoEffects(int type) throws Exception {
    establish();
    dispatcher.dispatch(null);
    dispatcher.dispatch(message(type, null));
    assertTrue(rpc.isEmpty());
    assertTrue(sync.isEmpty());
    assertTrue(writes.isEmpty());
    assertTrue(events.isEmpty());
    assertFalse(session.readiness.syncObserved.get());
  }

  @Test
  void rpcPreservesTheEnvelopeAndRunsInlineWithoutReadiness() throws Exception {
    establish();
    var envelope = message(SIGNAL_PROXY_RPC_CALL, Arrays.asList("network", null));
    Thread caller = Thread.currentThread();
    var router =
        new QuasselCoreSignalProxyDispatcher(
            session,
            received -> {
              assertSame(caller, Thread.currentThread());
              assertSame(envelope, received);
              assertSame(envelope.params(), received.params());
              rpc.add(received);
            },
            sync::add);
    router.dispatch(envelope);
    assertEquals(List.of(envelope), rpc);
    assertTrue(sync.isEmpty());
    assertTrue(events.isEmpty());
    assertFalse(session.readiness.syncObserved.get());
  }

  @ParameterizedTest
  @ValueSource(ints = {SIGNAL_PROXY_SYNC, SIGNAL_PROXY_INIT_DATA})
  void syncStateIsAppliedInlineBeforeReadyAndFeatureEvents(int type) throws Exception {
    establish();
    var envelope = message(type, null);
    Thread caller = Thread.currentThread();
    var router =
        new QuasselCoreSignalProxyDispatcher(
            session,
            rpc::add,
            received -> {
              assertSame(caller, Thread.currentThread());
              assertSame(envelope, received);
              assertNull(received.params());
              assertTrue(session.readiness.syncObserved.get());
              assertTrue(events.isEmpty());
              session.networks.observe(7, "Applied");
              order.add("state");
              sync.add(received);
            });
    router.dispatch(envelope);
    assertEquals(List.of("state", "ConnectionReady", "ConnectionFeaturesUpdated"), order);
    assertEquals("Applied", session.networks.displayNames().get(7));
    assertEquals(List.of(envelope), sync);
    assertTrue(rpc.isEmpty());
    var feature = assertInstanceOf(IrcEvent.ConnectionFeaturesUpdated.class, events.get(1));
    assertEquals("quassel-phase=sync-ready;detail=quassel-sync", feature.source());
  }

  @Test
  void repeatedSyncAndInitStillApplyEveryEnvelopeWhileReadyIsEmittedOnce() throws Exception {
    establish();
    var first = message(SIGNAL_PROXY_INIT_DATA, List.of());
    var second = message(SIGNAL_PROXY_SYNC, List.of("state"));
    dispatcher.dispatch(first);
    dispatcher.dispatch(second);
    assertEquals(List.of(first, second), sync);
    assertEquals(2, events.size());
    assertEquals(1, events.stream().filter(IrcEvent.ConnectionReady.class::isInstance).count());
  }

  @ParameterizedTest
  @ValueSource(ints = {SIGNAL_PROXY_SYNC, SIGNAL_PROXY_INIT_DATA})
  void syncFailurePropagatesBeforeReadyAndLaterSuccessfulSyncCanPublish(int type) throws Exception {
    establish();
    IllegalStateException failure = new IllegalStateException("state failed");
    var router =
        new QuasselCoreSignalProxyDispatcher(
            session,
            rpc::add,
            received -> {
              throw failure;
            });
    assertSame(
        failure,
        assertThrows(IllegalStateException.class, () -> router.dispatch(message(type, List.of()))));
    assertTrue(session.readiness.syncObserved.get());
    assertFalse(session.readiness.connectionReadyEmitted.get());
    assertTrue(events.isEmpty());
    dispatcher.dispatch(message(type, List.of()));
    assertInstanceOf(IrcEvent.ConnectionReady.class, events.getFirst());
  }

  @Test
  void rpcFailurePropagatesWithoutMarkingSyncObserved() {
    establish();
    IllegalStateException failure = new IllegalStateException("rpc failed");
    var router =
        new QuasselCoreSignalProxyDispatcher(
            session,
            received -> {
              throw failure;
            },
            sync::add);
    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class,
            () -> router.dispatch(message(SIGNAL_PROXY_RPC_CALL, List.of()))));
    assertFalse(session.readiness.syncObserved.get());
    assertTrue(events.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = {SIGNAL_PROXY_SYNC, SIGNAL_PROXY_INIT_DATA})
  void disconnectDuringSyncSuppressesReadiness(int type) throws Exception {
    establish();
    var router =
        new QuasselCoreSignalProxyDispatcher(
            session,
            rpc::add,
            received -> {
              sync.add(received);
              session.closeRequested.set(true);
              session.readiness.close();
            });
    router.dispatch(message(type, List.of()));
    assertEquals(1, sync.size());
    assertTrue(events.isEmpty());
    assertFalse(session.readiness.connectionReadyEmitted.get());
  }

  @ParameterizedTest
  @ValueSource(ints = {SIGNAL_PROXY_SYNC, SIGNAL_PROXY_INIT_DATA})
  void syncBeforeEstablishmentIsObservedAndAppliedWithoutPublishingReady(int type)
      throws Exception {
    session.socketRef.set(socket);
    session.phase.set(QuasselSessionPhase.AUTHENTICATING);
    dispatcher.dispatch(message(type, List.of()));
    assertTrue(session.readiness.syncObserved.get());
    assertEquals(1, sync.size());
    assertTrue(events.isEmpty());
  }

  @Test
  void heartbeatRepliesWithFirstTimestampThroughTheSessionWriter() throws Exception {
    establish();
    var timestamp = utcDateTimeFromEpochMs(1_700_000_000_000L);
    dispatcher.dispatch(message(SIGNAL_PROXY_HEARTBEAT, List.of(timestamp, "extra")));
    assertEquals(List.of(socket), writes);
    verify(codec).writeSignalProxyHeartBeatReply(output, timestamp);
    assertNoDataOrReadiness();
  }

  @ParameterizedTest
  @MethodSource("invalidHeartbeatParams")
  void malformedHeartbeatIsIgnored(List<Object> params) throws Exception {
    establish();
    dispatcher.dispatch(message(SIGNAL_PROXY_HEARTBEAT, params));
    assertTrue(writes.isEmpty());
    verifyNoInteractions(codec);
    assertNoDataOrReadiness();
  }

  @Test
  void heartbeatAfterSocketClosureIsIgnored() throws Exception {
    dispatcher.dispatch(
        message(SIGNAL_PROXY_HEARTBEAT, List.of(utcDateTimeFromEpochMs(1_700_000_000_000L))));
    assertTrue(writes.isEmpty());
    verifyNoInteractions(codec);
    assertNoDataOrReadiness();
  }

  @Test
  void matchingHeartbeatReplyUpdatesLagWithoutSendingOrPublishingReady() throws Exception {
    establish();
    var token = session.lag.beginProbe();
    dispatcher.dispatch(message(SIGNAL_PROXY_HEARTBEAT_REPLY, List.of(token)));
    assertTrue(session.lag.lastMeasuredLagMs().isPresent());
    assertTrue(writes.isEmpty());
    verifyNoInteractions(codec);
    assertNoDataOrReadiness();
  }

  @Test
  void mismatchedHeartbeatReplyLeavesProbePendingForItsMatchingReply() throws Exception {
    establish();
    var token = session.lag.beginProbe();
    dispatcher.dispatch(
        message(SIGNAL_PROXY_HEARTBEAT_REPLY, List.of(utcDateTimeFromEpochMs(1_700_000_000_000L))));
    assertTrue(session.lag.lastMeasuredLagMs().isEmpty());
    dispatcher.dispatch(message(SIGNAL_PROXY_HEARTBEAT_REPLY, List.of(token)));
    assertTrue(session.lag.lastMeasuredLagMs().isPresent());
    assertTrue(writes.isEmpty());
    assertNoDataOrReadiness();
  }

  @ParameterizedTest
  @MethodSource("heartbeatWriteFailures")
  void heartbeatWriteFailureReachesReadLoopAsAnErrorIncludingEofAndTimeout(IOException failure)
      throws Exception {
    establish();
    writeFailure = failure;
    when(socket.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
    when(codec.readSignalProxyMessage(any()))
        .thenReturn(
            message(SIGNAL_PROXY_HEARTBEAT, List.of(utcDateTimeFromEpochMs(1_700_000_000_000L))));
    List<QuasselCoreReadLoop.Failure> failures = new ArrayList<>();
    new QuasselCoreReadLoop(codec)
        .read("core", socket, () -> false, dispatcher::dispatch, failures::add);
    assertEquals(
        List.of(
            new QuasselCoreReadLoop.Failure("Connection error: " + failure.getMessage(), failure)),
        failures);
    assertEquals(List.of(socket), writes);
    assertNoDataOrReadiness();
  }

  private void establish() {
    session.socketRef.set(socket);
    session.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
  }

  private void assertNoDataOrReadiness() {
    assertTrue(rpc.isEmpty());
    assertTrue(sync.isEmpty());
    assertTrue(events.isEmpty());
    assertFalse(session.readiness.syncObserved.get());
  }

  private static SignalProxyMessage message(int type, List<Object> params) {
    return new SignalProxyMessage(type, "Network", "7", "slot", params);
  }

  private static Stream<List<Object>> invalidHeartbeatParams() {
    return Stream.of(
        null,
        List.of(),
        Arrays.asList((Object) null),
        List.of("not a datetime"),
        List.of("invalid first", utcDateTimeFromEpochMs(1_700_000_000_000L)),
        List.of(new UserTypeValue("QDateTime", utcDateTimeFromEpochMs(1_700_000_000_000L))));
  }

  private static Stream<IOException> heartbeatWriteFailures() {
    return Stream.of(
        new IOException("broken"),
        new EOFException("ended during write"),
        new SocketTimeoutException("write timed out"));
  }
}
