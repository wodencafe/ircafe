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
  private final QuasselCoreSetupCoordinator setups = mock(QuasselCoreSetupCoordinator.class);
  private final QuasselCoreReconnectCoordinator reconnects =
      mock(QuasselCoreReconnectCoordinator.class);
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();
  private final List<String> trace = new ArrayList<>();
  private final AtomicReference<Consumer<String>> availabilityCleared =
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
            trace.add("availability");
            availabilityCleared.get().accept(serverId);
          },
          setups,
          reconnects);

  @BeforeEach
  void configureSessionAndCollaborators() throws Exception {
    session.phase.set(QuasselSessionPhase.AUTHENTICATING);
    session.socketRef.set(mock(Socket.class));
    doAnswer(
            invocation -> {
              assertEquals(QuasselSessionPhase.SESSION_ESTABLISHED, session.phase.get());
              assertEquals("availability", trace.getLast());
              verifyNoInteractions(reconnects, codec, networks, reader);
              trace.add("forget setup");
              return null;
            })
        .when(setups)
        .forget("core");
    doAnswer(
            invocation -> {
              assertEquals("forget setup", trace.getLast());
              trace.add("reset reconnect");
              return null;
            })
        .when(reconnects)
        .reset("core");
    doAnswer(
            invocation -> {
              assertEquals("reset reconnect", trace.getLast());
              trace.add("cancel reconnect");
              return null;
            })
        .when(reconnects)
        .cancel("core", false);
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
              assertEquals("cancel reconnect", trace.getLast());
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
    availabilityCleared.set(
        serverId -> {
          assertSame(auth, session.authResult.get());
          assertNull(session.buffers.get(99));
          assertSame(buffer, session.buffers.get(30));
          assertFalse(session.features.hasObservedCapabilities());
          verifyNoInteractions(setups, reconnects, codec, networks, reader);
        });
    bootstrap.complete(session, auth);
    assertEquals(
        List.of(
            "observe:2",
            "observe:1",
            "observe:2",
            "observe:3",
            "buffer:#buffer-only:3",
            "availability",
            "forget setup",
            "reset reconnect",
            "cancel reconnect",
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
    verify(setups).forget("core");
    verify(reconnects).reset("core");
    verify(reconnects).cancel("core", false);
    verifyNoMoreInteractions(setups, reconnects);
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
  void networkInitSelectionUsesLiveStateAfterEstablishedBookkeeping() throws Exception {
    availabilityCleared.set(serverId -> session.networks.observe(7, "discovered"));
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
    assertFalse(trace.contains("availability"));
    verifyNoInteractions(setups, reconnects, codec, networks, reader);
  }

  @Test
  void bookkeepingFailureKeepsEstablishedPhaseAndStopsBeforeInitRequests() {
    var failure = new IllegalStateException("bookkeeping failed");
    availabilityCleared.set(
        serverId -> {
          throw failure;
        });
    assertSame(
        failure,
        assertThrows(IllegalStateException.class, () -> bootstrap.complete(session, auth())));
    assertEquals(QuasselSessionPhase.SESSION_ESTABLISHED, session.phase.get());
    verifyNoInteractions(setups, reconnects, codec, networks, reader);
  }

  @ParameterizedTest
  @ValueSource(strings = {"forget setup", "reset reconnect", "cancel reconnect"})
  void cleanupFailureStopsLaterBookkeepingAndInitWithoutRollingBackEstablishedPhase(String stage) {
    var failure = new IllegalStateException("cleanup failed");
    switch (stage) {
      case "forget setup" -> doThrow(failure).when(setups).forget("core");
      case "reset reconnect" -> doThrow(failure).when(reconnects).reset("core");
      case "cancel reconnect" -> doThrow(failure).when(reconnects).cancel("core", false);
      default -> throw new AssertionError("Unknown stage");
    }
    assertSame(
        failure,
        assertThrows(IllegalStateException.class, () -> bootstrap.complete(session, auth())));
    assertEquals(QuasselSessionPhase.SESSION_ESTABLISHED, session.phase.get());
    var expected = new ArrayList<>(List.of("observe:2", "observe:1", "availability"));
    if (!"forget setup".equals(stage)) expected.add("forget setup");
    if ("cancel reconnect".equals(stage)) expected.add("reset reconnect");
    assertEquals(expected, trace);
    verify(setups).forget("core");
    verify(reconnects, times("forget setup".equals(stage) ? 0 : 1)).reset("core");
    verify(reconnects, times("cancel reconnect".equals(stage) ? 1 : 0)).cancel("core", false);
    verifyNoMoreInteractions(setups, reconnects);
    verifyNoInteractions(codec, networks, reader);
  }

  @Test
  void bufferInitWriteFailurePreventsNetworkInitAndReaderStartupWithoutRollingBackPhase()
      throws Exception {
    var failure = new IOException("buffer init failed");
    doThrow(failure).when(codec).writeSignalProxyInitRequest(output, "BufferSyncer", "", List.of());
    assertSame(failure, assertThrows(IOException.class, () -> bootstrap.complete(session, auth())));
    assertEquals(QuasselSessionPhase.SESSION_ESTABLISHED, session.phase.get());
    assertTrue(trace.contains("cancel reconnect"));
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
            "availability",
            "forget setup",
            "reset reconnect",
            "cancel reconnect",
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
