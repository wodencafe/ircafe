package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeCatalogs;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.UserTypeValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreSession.QuasselSessionPhase;
import io.reactivex.rxjava3.core.Completable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreOutboundSignalCoordinatorTest {
  private static final String TARGET = "#room{net:work}";
  private static final Instant MARKER_AT = Instant.ofEpochSecond(11);
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final OutputStream output = mock(OutputStream.class);
  private final List<Socket> writes = new ArrayList<>();
  private final QuasselIrcv3RuntimeSupport runtime =
      spy(new QuasselIrcv3RuntimeSupport(Ircv3RuntimeCatalogs.applicationClasspath()));
  private final QuasselCoreSession session = session();
  private final QuasselCoreTargetResolver targets =
      new QuasselCoreTargetResolver(
          new QuasselCoreTargetResolver.SessionPort() {
            @Override
            public void observeNetwork(QuasselCoreSession observed, int networkId) {
              observations.observeNetwork(observed, networkId, "");
            }

            @Override
            public boolean isSelfNick(QuasselCoreSession observed, String nick, int networkId) {
              return observed.nicks.isSelf(nick, networkId);
            }
          });
  private final QuasselCoreStateObservationCoordinator observations =
      new QuasselCoreStateObservationCoordinator(targets, ignored -> {});
  private final QuasselCoreInputCoordinator inputs =
      new QuasselCoreInputCoordinator(
          targets,
          (observed, target, networkId) ->
              observations.observeTargetNetwork(observed, target, networkId, true));
  private final Map<String, QuasselCoreSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, String> reasons = new ConcurrentHashMap<>();
  private final QuasselCoreSessionQueries queries =
      new QuasselCoreSessionQueries(sessions::get, reasons::get);
  private final QuasselCoreSessionOperations operations =
      new QuasselCoreSessionOperations(queries, inputs);
  private final QuasselCoreOutboundSignalCoordinator coordinator =
      new QuasselCoreOutboundSignalCoordinator(
          runtime, targets, inputs, observations, queries, operations);

  @ParameterizedTest
  @ValueSource(strings = {"unobserved", "without-message-tags"})
  void typingAvailabilityFailurePrecedesUnsafeTargetValidationAndRendering(String capabilities) {
    establish(session);
    if ("without-message-tags".equals(capabilities)) {
      session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("read-marker")));
    }
    String reason =
        "unobserved".equals(capabilities)
            ? "typing support status is not yet available from Quassel backend state"
            : "message-tags not negotiated in Quassel backend network state";
    coordinator
        .sendTyping(" core ", "#room\r\nQUIT", "active")
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof IllegalStateException
                    && ("Typing indicators not available (requires message-tags and server allowing +typing) ("
                            + reason
                            + "): core")
                        .equals(error.getMessage()));
    assertEquals(reason, coordinator.typingReason(" core "));
    assertEquals(-1, session.targetNetworkHints.networkIdForTarget("#room"));
    assertTrue(writes.isEmpty());
    verifyNoInteractions(runtime, codec);
  }

  @Test
  void markerAvailabilityFailurePrecedesUnsafeTargetValidationAndObservation() {
    establish(session);
    coordinator
        .sendReadMarker(" core ", "#room\r\nQUIT", MARKER_AT)
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof IllegalStateException
                    && "read-marker capability not negotiated (requires read-marker or draft/read-marker): core"
                        .equals(error.getMessage()));
    assertEquals(-1, session.targetNetworkHints.networkIdForTarget("#room"));
    assertTrue(writes.isEmpty());
    verifyNoInteractions(runtime, codec);
  }

  @ParameterizedTest
  @CsvSource({
    "TRANSPORT_CONNECTING, false, remembered",
    "TRANSPORT_CONNECTED, false, 'Quassel transport connected, but protocol negotiation is incomplete'",
    "PROTOCOL_NEGOTIATED, false, 'Quassel protocol negotiated, but login/session handshake is not complete'",
    "AUTHENTICATING, false, 'Quassel protocol negotiated, but login/session handshake is not complete'",
    "SESSION_ESTABLISHED, true, ''"
  })
  void serverAvailabilityRequiresAnEstablishedSessionAndPreservesBackendReasons(
      QuasselSessionPhase phase, boolean available, String reason) {
    establish(session);
    reasons.put("core", " remembered ");
    session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("message-tags")));
    session.nativeReadMarkerSupportObserved.set(true);
    session.phase.set(phase);
    assertEquals(available, coordinator.typingAvailable(" core "));
    assertEquals(available, coordinator.readMarkerAvailable(" core "));
    assertEquals(reason, coordinator.typingReason(" core "));
    session.socketRef.set(null);
    assertFalse(coordinator.typingAvailable("core"));
    assertFalse(coordinator.readMarkerAvailable("core"));
    assertEquals(reason, coordinator.typingReason("core"));
    sessions.clear();
    assertEquals("remembered", coordinator.typingReason("core"));
    verifyNoInteractions(runtime, codec);
  }

  @Test
  void typingRequestResolvesCapabilitiesAndReplacementSessionsAtSubscriptionOnIo()
      throws Exception {
    var writer = new AtomicReference<Thread>();
    doAnswer(
            invocation -> {
              writer.set(Thread.currentThread());
              return null;
            })
        .when(codec)
        .writeSignalProxyRpcCall(eq(output), anyString(), anyList());
    Completable command = coordinator.sendTyping(" core ", TARGET, "active");
    assertTrue(writes.isEmpty());
    verifyNoInteractions(runtime, codec);
    establish(session);
    session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("message-tags")));
    complete(command);
    assertNotSame(Thread.currentThread(), writer.get());
    var replacement = session();
    establish(replacement);
    replacement.features.observeCapabilities(8, Map.of("capsEnabled", List.of("message-tags")));
    complete(command);
    assertEquals(List.of(session.socketRef.get(), replacement.socketRef.get()), writes);
    verify(runtime, times(2)).typingRawLines(TARGET, "active");
    verify(codec, times(2))
        .writeSignalProxyRpcCall(
            output,
            "2sendInput(BufferInfo,QString)",
            List.of(buffer(22, 8, 2, "#room"), "/QUOTE @+typing=active TAGMSG #room"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"native", "read-marker", "draft/read-marker"})
  void markerRequestUsesSupportObservedAfterCreationWithoutChangingNativeOrRawRouting(
      String support) throws Exception {
    Completable command = coordinator.sendReadMarker(" core ", TARGET, MARKER_AT);
    assertTrue(writes.isEmpty());
    verifyNoInteractions(runtime, codec);
    establish(session);
    if ("native".equals(support)) {
      session.nativeReadMarkerSupportObserved.set(true);
      session.history.observe(TARGET, 201, Instant.ofEpochSecond(10));
    } else {
      session.features.observeCapabilities(8, Map.of("capsEnabled", List.of(support)));
    }
    complete(command);
    assertEquals(List.of(session.socketRef.get()), writes);
    if ("native".equals(support)) {
      verify(codec)
          .writeSignalProxySync(
              output, "BufferSyncer", "", "requestSetMarkerLine", markerParams(22, 201));
      verify(codec)
          .writeSignalProxySync(
              output, "BufferSyncer", "", "requestSetLastSeenMsg", markerParams(22, 201));
      verifyNoInteractions(runtime);
    } else {
      verifyInput(
          buffer(22, 8, 2, "#room"), "/QUOTE MARKREAD #room timestamp=1970-01-01T00:00:11.000Z");
      verify(runtime).readMarkerRawLines(TARGET, MARKER_AT);
    }
    verifyNoMoreInteractions(codec);
  }

  @ParameterizedTest
  @ValueSource(strings = {" Socket closed ", ""})
  void disconnectDuringTypingValidationUsesTheLatestBackendReasonWithoutWriting(String reason) {
    establish(session);
    session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("message-tags")));
    var changingQueries = mock(QuasselCoreSessionQueries.class);
    when(changingQueries.findEstablished("core")).thenReturn(session, null, null);
    when(changingQueries.availabilityReason("core")).thenReturn(reason);
    var changing =
        new QuasselCoreOutboundSignalCoordinator(
            runtime,
            targets,
            inputs,
            observations,
            changingQueries,
            new QuasselCoreSessionOperations(changingQueries, inputs));
    String suffix = reason.isEmpty() ? "" : " (Socket closed)";
    changing
        .sendTyping(" core ", TARGET, "active")
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertError(
            error ->
                error instanceof IllegalStateException
                    && ("Typing indicators not available (requires message-tags and server allowing +typing)"
                            + suffix
                            + ": core")
                        .equals(error.getMessage()));
    verify(changingQueries, times(3)).findEstablished("core");
    verify(changingQueries).availabilityReason("core");
    verifyNoMoreInteractions(changingQueries);
    verifyNoInteractions(runtime, codec);
    assertTrue(writes.isEmpty());
  }

  @Test
  void nativeMarkerUsesLatestTimestampTieFromTheSelectedNetworkInOneWrite() throws Exception {
    session.history.observe("#room{net:home}", 900, Instant.ofEpochSecond(10));
    session.history.observe(TARGET, 200, Instant.ofEpochSecond(10));
    session.history.observe(TARGET, 201, Instant.ofEpochSecond(10));
    session.history.observe(TARGET, 300, Instant.ofEpochSecond(12));
    session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("read-marker")));
    coordinator.sendReadMarker(session, TARGET, MARKER_AT);
    var order = inOrder(codec);
    order
        .verify(codec)
        .writeSignalProxySync(
            output, "BufferSyncer", "", "requestSetMarkerLine", markerParams(22, 201));
    order
        .verify(codec)
        .writeSignalProxySync(
            output, "BufferSyncer", "", "requestSetLastSeenMsg", markerParams(22, 201));
    order.verifyNoMoreInteractions();
    assertEquals(List.of(session.socketRef.get()), writes);
    assertEquals(8, session.targetNetworkHints.networkIdForTarget("#room"));
    verifyNoInteractions(runtime);
  }

  @ParameterizedTest
  @ValueSource(strings = {"read-marker", "draft/read-marker"})
  void absentAnchorFallsBackToTheQualifiedBuffersRawMarker(String capability) throws Exception {
    session.features.observeCapabilities(8, Map.of("capsEnabled", List.of(capability)));
    coordinator.sendReadMarker(session, TARGET, MARKER_AT);
    verifyInput(
        buffer(22, 8, 2, "#room"), "/QUOTE MARKREAD #room timestamp=1970-01-01T00:00:11.000Z");
    verify(runtime).readMarkerRawLines(TARGET, MARKER_AT);
    verifyNoMoreInteractions(codec);
  }

  @Test
  void markerEarlierThanObservedHistoryFallsBackWithoutSendingAFutureAnchor() throws Exception {
    session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("read-marker")));
    session.history.observe(TARGET, 300, Instant.ofEpochSecond(12));
    coordinator.sendReadMarker(session, TARGET, MARKER_AT);
    verifyInput(
        buffer(22, 8, 2, "#room"), "/QUOTE MARKREAD #room timestamp=1970-01-01T00:00:11.000Z");
    verifyNoMoreInteractions(codec);
  }

  @Test
  void syntheticQueryBufferFallsBackEvenWithAKnownMessageAnchor() throws Exception {
    session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("read-marker")));
    session.history.observe("bob{net:work}", 200, Instant.ofEpochSecond(10));
    coordinator.sendReadMarker(session, "bob{net:work}", MARKER_AT);
    verifyInput(buffer(-1, 8, 4, "bob"), "/QUOTE MARKREAD bob timestamp=1970-01-01T00:00:11.000Z");
    assertEquals(8, session.targetNetworkHints.networkIdForTarget("bob"));
    verifyNoMoreInteractions(codec);
  }

  @Test
  void nativeSupportWithoutAnAnchorDoesNotAttemptAnIrcv3Fallback() {
    session.nativeReadMarkerSupportObserved.set(true);
    var error =
        assertThrows(
            IllegalStateException.class,
            () -> coordinator.sendReadMarker(session, TARGET, MARKER_AT));
    assertEquals(
        "Quassel read marker requires an observed message for " + TARGET, error.getMessage());
    assertEquals(8, session.targetNetworkHints.networkIdForTarget("#room"));
    assertTrue(writes.isEmpty());
    verifyNoInteractions(codec, runtime);
  }

  @ParameterizedTest
  @ValueSource(strings = {"requestSetMarkerLine", "requestSetLastSeenMsg"})
  void partialNativeWritePropagatesWithoutFallbackOrRetry(String failingSlot) throws Exception {
    session.history.observe(TARGET, 200, Instant.ofEpochSecond(10));
    session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("read-marker")));
    var error = new IOException("native marker write failed");
    doThrow(error)
        .when(codec)
        .writeSignalProxySync(eq(output), eq("BufferSyncer"), eq(""), eq(failingSlot), anyList());
    assertSame(
        error,
        assertThrows(
            IOException.class, () -> coordinator.sendReadMarker(session, TARGET, MARKER_AT)));
    verify(codec)
        .writeSignalProxySync(
            output, "BufferSyncer", "", "requestSetMarkerLine", markerParams(22, 200));
    verify(codec, times("requestSetLastSeenMsg".equals(failingSlot) ? 1 : 0))
        .writeSignalProxySync(
            output, "BufferSyncer", "", "requestSetLastSeenMsg", markerParams(22, 200));
    verifyNoMoreInteractions(codec);
    verifyNoInteractions(runtime);
    assertEquals(1, writes.size());
  }

  @Test
  void missingMarkerTimestampIsSuppliedToTheRuntimeProvider() throws Exception {
    session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("read-marker")));
    Instant before = Instant.now();
    coordinator.sendReadMarker(session, TARGET, null);
    Instant after = Instant.now();
    var timestamp = org.mockito.ArgumentCaptor.forClass(Instant.class);
    verify(runtime).readMarkerRawLines(eq(TARGET), timestamp.capture());
    assertFalse(timestamp.getValue().isBefore(before));
    assertFalse(timestamp.getValue().isAfter(after));
    assertEquals(1, writes.size());
  }

  @Test
  void emptyMarkerRenderingFailsAfterObservingTheTargetWithoutWriting() {
    session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("read-marker")));
    doReturn(List.of()).when(runtime).readMarkerRawLines(TARGET, MARKER_AT);
    var error =
        assertThrows(
            IllegalStateException.class,
            () -> coordinator.sendReadMarker(session, TARGET, MARKER_AT));
    assertEquals(
        "Read-marker runtime provider did not render a command for " + TARGET, error.getMessage());
    assertEquals(8, session.targetNetworkHints.networkIdForTarget("#room"));
    verifyNoInteractions(codec);
  }

  @ParameterizedTest
  @CsvSource({
    "active, active",
    "composing, active",
    "paused, paused",
    "done, done",
    "inactive, done"
  })
  void typingNormalizesStateAndUsesTheQualifiedBuffersRawRouting(String state, String rendered)
      throws Exception {
    coordinator.sendTyping(session, " #room{net:work} ", state);
    verifyInput(buffer(22, 8, 2, "#room"), "/QUOTE @+typing=" + rendered + " TAGMSG #room");
    verify(runtime).typingRawLines(TARGET, state);
    assertEquals(8, session.targetNetworkHints.networkIdForTarget("#room"));
    verifyNoMoreInteractions(codec);
  }

  @Test
  void unknownTypingStateProducesNoWriteOrTargetObservation() throws Exception {
    coordinator.sendTyping(session, TARGET, "unknown");
    verify(runtime).typingRawLines(TARGET, "unknown");
    assertEquals(-1, session.targetNetworkHints.networkIdForTarget("#room"));
    verifyNoInteractions(codec);
    assertTrue(writes.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"typing", "marker"})
  void allProviderCommandsAreSentInOrderWithTheirOwnQualifiedTargets(String kind) throws Exception {
    String first = "typing".equals(kind) ? "@+typing=active TAGMSG " : "MARKREAD ";
    String second = "typing".equals(kind) ? "@+typing=done TAGMSG " : "MARKREAD ";
    String suffix = "typing".equals(kind) ? "" : " timestamp=1970-01-01T00:00:11.000Z";
    var lines = List.of(first + TARGET + suffix, second + "#room{net:home}" + suffix);
    if ("typing".equals(kind)) {
      doReturn(lines).when(runtime).typingRawLines(TARGET, "active");
      coordinator.sendTyping(session, TARGET, "active");
    } else {
      session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("read-marker")));
      doReturn(lines).when(runtime).readMarkerRawLines(TARGET, MARKER_AT);
      coordinator.sendReadMarker(session, TARGET, MARKER_AT);
    }
    var order = inOrder(codec);
    order
        .verify(codec)
        .writeSignalProxyRpcCall(
            output,
            "2sendInput(BufferInfo,QString)",
            List.of(buffer(22, 8, 2, "#room"), "/QUOTE " + first + "#room" + suffix));
    order
        .verify(codec)
        .writeSignalProxyRpcCall(
            output,
            "2sendInput(BufferInfo,QString)",
            List.of(buffer(11, 7, 2, "#room"), "/QUOTE " + second + "#room" + suffix));
    order.verifyNoMoreInteractions();
    assertEquals(2, writes.size());
    assertEquals(7, session.targetNetworkHints.networkIdForTarget("#room"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"typing", "marker"})
  void rawWriteFailurePropagatesWithoutRetry(String kind) throws Exception {
    var error = new IOException("raw write failed");
    doThrow(error).when(codec).writeSignalProxyRpcCall(eq(output), anyString(), anyList());
    if ("typing".equals(kind)) {
      assertSame(
          error,
          assertThrows(IOException.class, () -> coordinator.sendTyping(session, TARGET, "active")));
    } else {
      session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("read-marker")));
      assertSame(
          error,
          assertThrows(
              IOException.class, () -> coordinator.sendReadMarker(session, TARGET, MARKER_AT)));
    }
    assertEquals(1, writes.size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "#room withspace", "#room\r\nQUIT"})
  void unsafeTargetsFailBeforeRenderingObservationOrWriting(String target) {
    assertThrows(
        IllegalArgumentException.class, () -> coordinator.sendTyping(session, target, "active"));
    assertThrows(
        IllegalArgumentException.class,
        () -> coordinator.sendReadMarker(session, target, MARKER_AT));
    assertEquals(-1, session.targetNetworkHints.networkIdForTarget("#room"));
    verifyNoInteractions(runtime, codec);
  }

  @ParameterizedTest
  @CsvSource({"read-marker, true", "draft/read-marker, true", "message-tags, false", "'', false"})
  void markerAvailabilityUsesNativeSupportOrEitherMarkerCapability(
      String capability, boolean available) {
    assertFalse(coordinator.isReadMarkerAvailable(null));
    session.features.observeCapabilities(8, Map.of("capsEnabled", List.of(capability)));
    assertEquals(available, coordinator.isReadMarkerAvailable(session));
    session.nativeReadMarkerSupportObserved.set(true);
    assertTrue(coordinator.isReadMarkerAvailable(session));
    session.clearObservedState();
    assertFalse(coordinator.isReadMarkerAvailable(session));
  }

  @Test
  void typingAvailabilityReasonsFollowObservedCapabilitiesAndClearWithSessionState() {
    assertFalse(coordinator.isTypingAvailable(null));
    assertFalse(coordinator.isTypingAvailable(session));
    assertEquals(
        "typing support status is not yet available from Quassel backend state",
        coordinator.typingAvailabilityReason(session));
    session.features.observeCapabilities(8, Map.of("capsEnabled", List.of("read-marker")));
    assertFalse(coordinator.isTypingAvailable(session));
    assertEquals(
        "message-tags not negotiated in Quassel backend network state",
        coordinator.typingAvailabilityReason(session));
    session.features.observeCapabilities(7, Map.of("capsEnabled", List.of("message-tags")));
    assertTrue(coordinator.isTypingAvailable(session));
    assertEquals("", coordinator.typingAvailabilityReason(session));
    session.clearObservedState();
    assertFalse(coordinator.isTypingAvailable(session));
  }

  @Test
  void sharedCoordinatorUsesEachSessionsOwnBufferAndHistory() throws Exception {
    session.history.observe(TARGET, 200, Instant.ofEpochSecond(10));
    var other = session();
    other.buffers.clear();
    other.buffers.merge(buffer(44, 8, 2, "#room"));
    other.history.observe(TARGET, 400, Instant.ofEpochSecond(10));
    coordinator.sendReadMarker(session, TARGET, MARKER_AT);
    coordinator.sendReadMarker(other, TARGET, MARKER_AT);
    verify(codec)
        .writeSignalProxySync(
            output, "BufferSyncer", "", "requestSetMarkerLine", markerParams(22, 200));
    verify(codec)
        .writeSignalProxySync(
            output, "BufferSyncer", "", "requestSetMarkerLine", markerParams(44, 400));
    assertEquals(List.of(session.socketRef.get(), other.socketRef.get()), writes);
  }

  private QuasselCoreSession session() {
    var session =
        new QuasselCoreSession(
            "core",
            "alice",
            "core.example",
            4242,
            (socket, operation) -> {
              writes.add(socket);
              operation.write(codec, output);
            },
            ignored -> {},
            ignored -> {});
    session.socketRef.set(new Socket());
    session.networks.observe(7, "Home");
    session.networks.observe(8, "Work");
    session.buffers.merge(buffer(11, 7, 2, "#room"));
    session.buffers.merge(buffer(22, 8, 2, "#room"));
    return session;
  }

  private void establish(QuasselCoreSession established) {
    established.phase.set(QuasselSessionPhase.SESSION_ESTABLISHED);
    sessions.put("core", established);
  }

  private void complete(Completable command) {
    command.test().awaitDone(5, TimeUnit.SECONDS).assertComplete().assertNoErrors();
  }

  private static BufferInfoValue buffer(int id, int network, int type, String name) {
    return new BufferInfoValue(id, network, type, -1, name);
  }

  private static List<Object> markerParams(int bufferId, int msgId) {
    return List.of(new UserTypeValue("BufferId", bufferId), new UserTypeValue("MsgId", msgId));
  }

  private void verifyInput(BufferInfoValue buffer, String input) throws IOException {
    verify(codec)
        .writeSignalProxyRpcCall(output, "2sendInput(BufferInfo,QString)", List.of(buffer, input));
  }
}
