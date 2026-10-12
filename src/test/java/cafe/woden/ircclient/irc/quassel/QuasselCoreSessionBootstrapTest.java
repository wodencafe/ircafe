package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.irc.quassel.QuasselCoreAuthHandshake.AuthResult;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreSessionBootstrapTest {
  private final QuasselCoreStateObservationCoordinator observations =
      mock(QuasselCoreStateObservationCoordinator.class);
  private final QuasselCoreNetworkManagementCoordinator networks =
      mock(QuasselCoreNetworkManagementCoordinator.class);
  private final QuasselCoreSessionReader reader = mock(QuasselCoreSessionReader.class);
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();
  private final List<String> trace = new ArrayList<>();
  private final AtomicReference<Consumer<String>> established =
      new AtomicReference<>(ignored -> {});
  private final QuasselCoreSession session =
      new QuasselCoreSession(
          "core",
          "alice",
          "host",
          4242,
          (socket, operation) -> operation.write(codec, output),
          id -> {},
          event -> {});
  private final QuasselCoreSessionBootstrap bootstrap =
      new QuasselCoreSessionBootstrap(
          observations,
          networks,
          reader,
          serverId -> {
            assertEquals("core", serverId);
            assertEquals(QuasselSessionPhase.SESSION_ESTABLISHED, session.phase.get());
            trace.add("established");
            established.get().accept(serverId);
          });

  @BeforeEach
  void configureSessionAndCollaborators() throws Exception {
    session.phase.set(QuasselSessionPhase.AUTHENTICATING);
    session.socketRef.set(mock(Socket.class));
    doAnswer(
            invocation -> {
              int networkId = invocation.getArgument(1);
              assertEquals(QuasselSessionPhase.AUTHENTICATING, session.phase.get());
              assertNotNull(session.authResult.get());
              trace.add("observe:" + networkId);
              session.networks.observe(networkId, "");
              return null;
            })
        .when(observations)
        .observeNetwork(same(session), anyInt(), eq(""));
    doAnswer(
            invocation -> {
              assertEquals(QuasselSessionPhase.AUTHENTICATING, session.phase.get());
              assertEquals(false, invocation.getArgument(3));
              trace.add("buffer:" + invocation.getArgument(1) + ":" + invocation.getArgument(2));
              return null;
            })
        .when(observations)
        .observeTargetNetwork(same(session), anyString(), anyInt(), eq(false));
    doAnswer(
            invocation -> {
              assertEquals(QuasselSessionPhase.SESSION_ESTABLISHED, session.phase.get());
              assertTrue(trace.contains("established"));
              assertEquals("", invocation.getArgument(2));
              assertEquals(List.of(), invocation.getArgument(3));
              trace.add("init:" + invocation.getArgument(1));
              return null;
            })
        .when(codec)
        .writeSignalProxyInitRequest(eq(output), anyString(), anyString(), anyList());
    doAnswer(
            invocation -> {
              assertEquals(QuasselSessionPhase.SESSION_ESTABLISHED, session.phase.get());
              trace.add("network-init:" + invocation.getArgument(1));
              return null;
            })
        .when(networks)
        .requestNetworkInitState(same(session), anyInt());
    doAnswer(
            invocation -> {
              assertEquals(QuasselSessionPhase.SESSION_ESTABLISHED, session.phase.get());
              trace.add("read");
              return null;
            })
        .when(reader)
        .start(session);
  }

  @Test
  void bootstrapInitializesObservationsBeforeEstablishedBookkeepingAndOrderedInitRequests()
      throws Exception {
    var buffer = new BufferInfoValue(30, 3, 0x02, -1, "#buffer-only");
    var auth = new AuthResult("core", 2, List.of(2, 1, 2), Map.of(30, buffer));
    session.buffers.merge(new BufferInfoValue(99, 9, 0x02, -1, "#old"));
    session.networks.observe(9, "old");
    session.features.observeCapabilities(9, Map.of("capsEnabled", List.of("message-tags")));
    established.set(
        serverId -> {
          assertSame(auth, session.authResult.get());
          assertNull(session.buffers.get(99));
          assertSame(buffer, session.buffers.get(30));
          assertFalse(session.features.hasObservedCapabilities());
          verifyNoInteractions(codec, networks, reader);
        });
    bootstrap.complete(session, auth);
    assertEquals(
        List.of(
            "observe:2",
            "observe:1",
            "observe:2",
            "observe:3",
            "buffer:#buffer-only:3",
            "established",
            "init:BufferSyncer",
            "network-init:2",
            "network-init:1",
            "network-init:3",
            "read"),
        trace);
    verify(codec).writeSignalProxyInitRequest(output, "BufferSyncer", "", List.of());
    verify(networks).requestNetworkInitState(session, 2);
    verify(networks).requestNetworkInitState(session, 1);
    verify(networks).requestNetworkInitState(session, 3);
    verifyNoMoreInteractions(codec, networks);
    verify(reader).start(session);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void sessionWithoutAuthNetworkIdsStillRequestsBufferStateAndAnyBufferOnlyNetwork(
      boolean hasBuffer) throws Exception {
    var auth =
        new AuthResult(
            "core",
            -1,
            List.of(),
            hasBuffer ? Map.of(10, new BufferInfoValue(10, 4, 0x02, -1, "#room")) : Map.of());
    bootstrap.complete(session, auth);
    verify(codec).writeSignalProxyInitRequest(output, "BufferSyncer", "", List.of());
    if (hasBuffer) verify(networks).requestNetworkInitState(session, 4);
    else verifyNoInteractions(networks);
    verify(reader).start(session);
    assertEquals("read", trace.getLast());
  }

  @Test
  void networkInitSelectionUsesLiveStateAfterTheEstablishedCallback() throws Exception {
    established.set(serverId -> session.networks.observe(7, "discovered"));
    bootstrap.complete(session, auth());
    verify(networks).requestNetworkInitState(session, 2);
    verify(networks).requestNetworkInitState(session, 1);
    verify(networks).requestNetworkInitState(session, 7);
    assertEquals(
        List.of("network-init:2", "network-init:1", "network-init:7"),
        trace.stream().filter(item -> item.startsWith("network-init:")).toList());
  }

  @Test
  void observationFailureStopsBeforeEstablishedPhaseBookkeepingAndOutboundRequests() {
    var failure = new IllegalStateException("observation failed");
    doThrow(failure).when(observations).observeNetwork(session, 2, "");
    assertSame(
        failure,
        assertThrows(IllegalStateException.class, () -> bootstrap.complete(session, auth())));
    assertEquals(QuasselSessionPhase.AUTHENTICATING, session.phase.get());
    assertFalse(trace.contains("established"));
    verifyNoInteractions(codec, networks, reader);
  }

  @Test
  void bookkeepingFailureKeepsEstablishedPhaseAndStopsBeforeInitRequests() {
    var failure = new IllegalStateException("bookkeeping failed");
    established.set(
        serverId -> {
          throw failure;
        });
    assertSame(
        failure,
        assertThrows(IllegalStateException.class, () -> bootstrap.complete(session, auth())));
    assertEquals(QuasselSessionPhase.SESSION_ESTABLISHED, session.phase.get());
    verifyNoInteractions(codec, networks, reader);
  }

  @Test
  void bufferInitWriteFailurePreventsNetworkInitAndReaderStartupWithoutRollingBackPhase()
      throws Exception {
    var failure = new IOException("buffer init failed");
    doThrow(failure).when(codec).writeSignalProxyInitRequest(output, "BufferSyncer", "", List.of());
    assertSame(failure, assertThrows(IOException.class, () -> bootstrap.complete(session, auth())));
    assertEquals(QuasselSessionPhase.SESSION_ESTABLISHED, session.phase.get());
    assertTrue(trace.contains("established"));
    verifyNoInteractions(networks, reader);
  }

  @Test
  void networkInitFailureStopsRemainingRequestsAndReaderStartup() throws Exception {
    var failure = new IOException("network init failed");
    doThrow(failure).when(networks).requestNetworkInitState(session, 2);
    assertSame(failure, assertThrows(IOException.class, () -> bootstrap.complete(session, auth())));
    verify(codec).writeSignalProxyInitRequest(output, "BufferSyncer", "", List.of());
    verify(networks).requestNetworkInitState(session, 2);
    verifyNoMoreInteractions(networks);
    verifyNoInteractions(reader);
  }

  @Test
  void readerStartupFailurePropagatesAfterInitialRequestsWithoutRetrying() throws Exception {
    var failure = new IllegalStateException("scheduler unavailable");
    doThrow(failure).when(reader).start(session);
    assertSame(
        failure,
        assertThrows(IllegalStateException.class, () -> bootstrap.complete(session, auth())));
    assertEquals(
        List.of(
            "observe:2",
            "observe:1",
            "established",
            "init:BufferSyncer",
            "network-init:2",
            "network-init:1"),
        trace);
    verify(reader).start(session);
    verifyNoMoreInteractions(reader);
  }

  private static AuthResult auth() {
    return new AuthResult("core", 2, List.of(2, 1), Map.of());
  }
}
