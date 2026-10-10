package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreReadMarkerCoordinatorTest {
  private static final Instant MESSAGE_AT = Instant.parse("2023-11-14T22:13:20Z");
  private static final String TARGET = "#room{net:7}";
  private final List<IrcEvent.ReadMarkerObserved> events = new ArrayList<>();
  private final List<String> trace = new ArrayList<>();
  private String targetOverride;
  private String qualifiedOverride;
  private String targetFrom;
  private RuntimeException hintFailure;
  private RuntimeException emitFailure;
  private final Map<Integer, String> networkNicks = new HashMap<>();
  private final QuasselCoreSession session =
      new QuasselCoreSession(
          "core",
          "alice",
          "host",
          4242,
          (socket, write) -> fail("Markers must not write"),
          sid -> {},
          event -> {});
  private final QuasselCoreReadMarkerCoordinator coordinator =
      new QuasselCoreReadMarkerCoordinator(
          session,
          new QuasselCoreReadMarkerCoordinator.SessionPort() {
            @Override
            public String currentNick(int networkId) {
              return networkNicks.getOrDefault(networkId, "alice");
            }

            @Override
            public String historyTarget(BufferInfoValue buffer, String from) {
              targetFrom = from;
              return targetOverride == null
                  ? buffer.bufferName() + "{net:" + buffer.networkId() + "}"
                  : targetOverride;
            }

            @Override
            public String qualifyTarget(String target, int networkId) {
              return qualifiedOverride == null
                  ? target + "{net:" + networkId + "}"
                  : qualifiedOverride;
            }

            @Override
            public void observeTargetNetwork(String target, int networkId) {
              trace.add("hint:" + networkId + ":" + target);
              if (hintFailure != null) throw hintFailure;
              session.targetNetworkHints.observe(target, networkId);
            }

            @Override
            public void emit(IrcEvent.ReadMarkerObserved event) {
              trace.add("marker:" + event.target());
              if (emitFailure != null) throw emitFailure;
              events.add(event);
            }
          });

  @ParameterizedTest
  @ValueSource(ints = {0, 11})
  void markerWaitsForBothBufferMetadataAndMatchingHistory(int bufferId) {
    coordinator.observeSync("setMarkerLine", List.of(bufferId, 42));
    assertTrue(trace.isEmpty());
    coordinator.observeHistory(TARGET, 42, MESSAGE_AT);
    assertTrue(events.isEmpty());
    session.buffers.merge(buffer(bufferId, 7, "#room"));
    coordinator.observeHistory(TARGET, 42, MESSAGE_AT);
    assertEquals(List.of("hint:7:" + TARGET, "marker:" + TARGET), trace);
    assertMarker(events.getFirst(), TARGET, "alice", MESSAGE_AT);
    assertNull(session.pendingReadMarkers.takeBufferForMessage(42));
    coordinator.observeHistory(TARGET, 42, MESSAGE_AT);
    assertEquals(1, events.size(), "history replay must not repeat the consumed pending marker");
  }

  @Test
  void knownTimestampRendersMarkerImmediatelyWithSyncObservationTime() {
    session.buffers.merge(buffer(11, 7, "#room"));
    session.history.observe(TARGET, 42, MESSAGE_AT);
    Instant before = Instant.now();
    coordinator.observeSync("setLastSeenMsg", List.of(11, 42));
    Instant after = Instant.now();
    var event = events.getFirst();
    assertMarker(event, TARGET, "alice", MESSAGE_AT);
    assertFalse(event.at().isBefore(before));
    assertFalse(event.at().isAfter(after));
    assertEquals(List.of("hint:7:" + TARGET, "marker:" + TARGET), trace);
    assertEquals(7, session.targetNetworkHints.networkIdForTarget(TARGET));
  }

  @Test
  void adjacentHistorySamplesDoNotResolveNativeMarker() {
    session.buffers.merge(buffer(11, 7, "#room"));
    session.history.observe(TARGET, 41, MESSAGE_AT.minusSeconds(1));
    session.history.observe(TARGET, 43, MESSAGE_AT.plusSeconds(1));
    coordinator.observeSync("setMarkerLine", List.of(11, 42));
    coordinator.observeHistory(TARGET, 43, MESSAGE_AT.plusSeconds(1));
    assertTrue(events.isEmpty());
    coordinator.observeHistory(TARGET, 42, MESSAGE_AT);
    assertMarker(events.getFirst(), TARGET, "alice", MESSAGE_AT);
    assertEquals(MESSAGE_AT, events.getFirst().at());
  }

  @Test
  void newestMarkerForBufferSupersedesItsEarlierPendingMarker() {
    session.buffers.merge(buffer(11, 7, "#room"));
    coordinator.observeSync("setMarkerLine", List.of(11, 42));
    coordinator.observeSync("setLastSeenMsg", List.of(11, 43));
    coordinator.observeHistory(TARGET, 42, MESSAGE_AT);
    assertTrue(events.isEmpty());
    coordinator.observeHistory(TARGET, 43, MESSAGE_AT.plusSeconds(1));
    assertEquals(1, events.size());
    assertMarker(events.getFirst(), TARGET, "alice", MESSAGE_AT.plusSeconds(1));
    assertNull(session.pendingReadMarkers.takeBufferForMessage(42));
  }

  @Test
  void matchingMessageIdOnAnotherNetworkKeepsMarkerPending() {
    session.buffers.merge(buffer(11, 7, "#room"));
    coordinator.observeSync("setMarkerLine", List.of(11, 42));
    coordinator.observeHistory("#room{net:8}", 42, MESSAGE_AT.minusSeconds(10));
    assertTrue(events.isEmpty());
    coordinator.observeHistory(TARGET, 42, MESSAGE_AT);
    assertMarker(events.getFirst(), TARGET, "alice", MESSAGE_AT);
    assertNull(session.pendingReadMarkers.takeBufferForMessage(42));
  }

  @Test
  void markerUsesNetworkNickAndFallsBackToServerWhenBlank() {
    session.buffers.merge(buffer(11, 7, "#room"));
    networkNicks.put(7, "workNick");
    session.history.observe(TARGET, 42, MESSAGE_AT);
    coordinator.observeSync("setMarkerLine", List.of(11, 42));
    assertMarker(events.getFirst(), TARGET, "workNick", MESSAGE_AT);
    assertEquals("workNick", targetFrom);
    networkNicks.put(7, " ");
    coordinator.observeSync("setMarkerLine", List.of(11, 42));
    assertMarker(events.getLast(), TARGET, "server", MESSAGE_AT);
    assertEquals("server", targetFrom);
  }

  @Test
  void blankHistoryTargetFallsBackToTrimmedAndQualifiedBufferName() {
    session.buffers.merge(buffer(11, 7, " #room "));
    session.history.observe(TARGET, 42, MESSAGE_AT);
    targetOverride = " ";
    coordinator.observeSync("setMarkerLine", List.of(11, 42));
    assertMarker(events.getFirst(), TARGET, "alice", MESSAGE_AT);
  }

  @Test
  void unusableTargetDropsPreviousPendingMarkerWithoutPublishingOrDeferring() {
    session.buffers.merge(buffer(11, 7, ""));
    session.pendingReadMarkers.defer(11, 41);
    targetOverride = "";
    qualifiedOverride = "";
    coordinator.observeSync("setMarkerLine", List.of(11, 42));
    assertTrue(events.isEmpty());
    assertTrue(trace.isEmpty());
    assertNull(session.pendingReadMarkers.takeBufferForMessage(41));
    assertNull(session.pendingReadMarkers.takeBufferForMessage(42));
  }

  @Test
  void canonicalTargetFromPortTakesPrecedenceOverBufferName() {
    session.buffers.merge(buffer(11, 7, "ignored"));
    targetOverride = "status";
    session.history.observe("status", 42, MESSAGE_AT);
    coordinator.observeSync("setMarkerLine", List.of(11, 42));
    assertMarker(events.getFirst(), "status", "alice", MESSAGE_AT);
  }

  @Test
  void snapshotMarkersKeepParserOrderAndShareObservationTime() {
    session.buffers.merge(buffer(11, 7, "#room"));
    session.buffers.merge(buffer(22, 8, "#other"));
    session.history.observe(TARGET, 42, MESSAGE_AT);
    session.history.observe("#other{net:8}", 50, MESSAGE_AT.plusSeconds(1));
    coordinator.observeSync("", List.of("markerLines", List.of(22, 50, 11, 42)));
    assertEquals(
        List.of("#other{net:8}", TARGET),
        events.stream().map(IrcEvent.ReadMarkerObserved::target).toList());
    assertEquals(events.getFirst().at(), events.getLast().at());
    assertMarker(events.getFirst(), "#other{net:8}", "alice", MESSAGE_AT.plusSeconds(1));
    assertMarker(events.getLast(), TARGET, "alice", MESSAGE_AT);
  }

  @Test
  void nullHistoryTimeStoresTimestampBeforeReplayingPendingMarker() {
    session.buffers.merge(buffer(11, 7, "#room"));
    coordinator.observeSync("setMarkerLine", List.of(11, 42));
    Instant before = Instant.now();
    coordinator.observeHistory(TARGET, 42, null);
    Instant after = Instant.now();
    long exact = session.history.exactTimestampForMsgId(TARGET, 42);
    assertTrue(exact >= before.toEpochMilli() && exact <= after.toEpochMilli());
    assertMarker(events.getFirst(), TARGET, "alice", Instant.ofEpochMilli(exact));
    assertFalse(events.getFirst().at().isBefore(before));
    assertFalse(events.getFirst().at().isAfter(after));
  }

  @ParameterizedTest
  @MethodSource("invalidMarkerParams")
  void malformedMarkerUpdatesLeaveExistingPendingMarkerIntact(List<Object> params) {
    session.pendingReadMarkers.defer(11, 99);
    coordinator.observeSync("setMarkerLine", params);
    assertTrue(events.isEmpty());
    assertTrue(trace.isEmpty());
    assertEquals(11, session.pendingReadMarkers.takeBufferForMessage(99));
  }

  @ParameterizedTest
  @ValueSource(strings = {"buffer", "network", "session"})
  void existingStateCleanupPreventsLaterHistoryFromReplayingRemovedMarkers(String cleanup) {
    session.buffers.merge(buffer(11, 7, "#room"));
    coordinator.observeSync("setMarkerLine", List.of(11, 42));
    switch (cleanup) {
      case "buffer" -> {
        session.buffers.remove(11);
        session.pendingReadMarkers.forgetBuffer(11);
      }
      case "network" -> session.buffers.forgetNetwork(7, session.pendingReadMarkers::forgetBuffer);
      case "session" -> session.clearObservedState();
      default -> fail("Unexpected cleanup");
    }
    coordinator.observeHistory(TARGET, 42, MESSAGE_AT);
    assertTrue(events.isEmpty());
    assertNull(session.pendingReadMarkers.takeBufferForMessage(42));
  }

  @Test
  void hintFailurePropagatesBeforeDeferralAndAfterDiscardingOldPendingMarker() {
    session.buffers.merge(buffer(11, 7, "#room"));
    session.pendingReadMarkers.defer(11, 41);
    hintFailure = new IllegalStateException("hint failed");
    assertSame(
        hintFailure,
        assertThrows(
            IllegalStateException.class,
            () -> coordinator.observeSync("setMarkerLine", List.of(11, 42))));
    assertTrue(events.isEmpty());
    assertNull(session.pendingReadMarkers.takeBufferForMessage(41));
    assertNull(session.pendingReadMarkers.takeBufferForMessage(42));
  }

  @Test
  void observerFailurePropagatesAfterHistoryStorageAndPendingMarkerConsumption() {
    session.buffers.merge(buffer(11, 7, "#room"));
    coordinator.observeSync("setMarkerLine", List.of(11, 42));
    emitFailure = new IllegalStateException("observer failed");
    assertSame(
        emitFailure,
        assertThrows(
            IllegalStateException.class, () -> coordinator.observeHistory(TARGET, 42, MESSAGE_AT)));
    assertEquals(MESSAGE_AT.toEpochMilli(), session.history.exactTimestampForMsgId(TARGET, 42));
    assertNull(session.pendingReadMarkers.takeBufferForMessage(42));
    assertTrue(events.isEmpty());
  }

  private static BufferInfoValue buffer(int bufferId, int networkId, String name) {
    return new BufferInfoValue(bufferId, networkId, 2, -1, name);
  }

  private static void assertMarker(
      IrcEvent.ReadMarkerObserved event, String target, String from, Instant at) {
    assertEquals(target, event.target());
    assertEquals(from, event.from());
    assertTrue(event.marker().matches("timestamp=.+\\.\\d{3}Z"));
    assertEquals(at, Instant.parse(event.marker().substring("timestamp=".length())));
  }

  private static Stream<Arguments> invalidMarkerParams() {
    return Stream.of(
        Arguments.of((Object) null),
        Arguments.of(List.of()),
        Arguments.of(List.of(-1, 42)),
        Arguments.of(List.of(11, 0)),
        Arguments.of(List.of(11, -1)),
        Arguments.of(List.of(11)),
        Arguments.of(List.of("wrong", "invalid")),
        Arguments.of(List.of(Map.of("bufferId", 11))));
  }
}
