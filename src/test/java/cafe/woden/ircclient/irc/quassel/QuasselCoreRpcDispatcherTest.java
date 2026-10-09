package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.*;
import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreRpcDispatcherTest {
  private final List<String> trace = new ArrayList<>();
  private final List<IrcEvent> events = new ArrayList<>();
  private final List<MessageValue> messages = new ArrayList<>();
  private final List<BufferInfoValue> observations = new ArrayList<>();
  private RuntimeException observerFailure;
  private RuntimeException networkFailure;
  private final QuasselCoreSession session =
      new QuasselCoreSession(
          "core",
          "alice",
          "host",
          4242,
          (socket, write) -> fail("RPC routing must not write"),
          sid -> trace.add("identity"),
          events::add);
  private final QuasselCoreNetworkLifecycleTranslator networks =
      new QuasselCoreNetworkLifecycleTranslator(
          "core",
          session.networks::claimCreatedName,
          (id, name) -> {
            trace.add("network:" + id + ":" + name);
            if (networkFailure != null) throw networkFailure;
            session.networks.observe(id, name);
          },
          id -> {
            trace.add("remove:" + id);
            session.networks.forget(id);
          },
          (id, state) -> trace.add("state:" + id));
  private final QuasselCoreRpcDispatcher dispatcher =
      new QuasselCoreRpcDispatcher(
          session,
          networks,
          new QuasselCoreRpcDispatcher.SessionPort() {
            @Override
            public void displayMessage(MessageValue message) {
              messages.add(message);
            }

            @Override
            public void observeBuffer(BufferInfoValue merged) {
              assertSame(merged, session.buffers.get(merged.bufferId()));
              if (observerFailure != null) throw observerFailure;
              observations.add(merged);
            }

            @Override
            public void emit(IrcEvent event) {
              events.add(event);
            }
          });

  @Test
  void displayMessageKeepsFirstNativeMessageAndDoesNotInspectLifecyclePayloads() {
    var first = textMessage();
    dispatcher.dispatch(rpc(" 2displayMsg(Message) ", List.of(first, Map.of("identityId", 9))));
    assertSame(first, messages.getFirst());
    assertTrue(trace.isEmpty());
    assertTrue(events.isEmpty());
    assertFalse(session.identities.isKnown(9));
  }

  @ParameterizedTest
  @MethodSource("invalidMessageParams")
  void displayMessageRejectsMalformedFirstParamsWithoutLookingAtLaterParams(List<Object> params) {
    dispatcher.dispatch(rpc("2displayMsg(Message)", params));
    assertTrue(messages.isEmpty());
    assertTrue(trace.isEmpty());
    assertTrue(events.isEmpty());
  }

  @ParameterizedTest
  @MethodSource("statusCases")
  void statusMessagesKeepNumericRenderingRawLinesAndNetworkFallback(
      List<Object> params, int code, String display, String raw) {
    dispatcher.dispatch(rpc(" 2displayStatusMsg(QString,QString) ", params));
    var event = assertInstanceOf(IrcEvent.ServerResponseLine.class, events.getFirst());
    assertEquals(code, event.code());
    assertEquals(display, event.message());
    assertEquals(raw, event.rawLine());
    assertEquals("", event.messageId());
    assertEquals(Map.of(), event.ircv3Tags());
    assertNotNull(event.at());
    assertEquals(1, events.size());
    assertTrue(trace.isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  void missingStatusParamsDoNotEmit(List<Object> params) {
    dispatcher.dispatch(rpc("2displayStatusMsg(QString,QString)", params));
    assertTrue(events.isEmpty());
  }

  @Test
  void blankNetworkAndStatusDoNotEmit() {
    dispatcher.dispatch(rpc("2displayStatusMsg(QString,QString)", List.of(" ", " \t ")));
    assertTrue(events.isEmpty());
  }

  @Test
  void bufferUpdateMergesPartialMetadataBeforeNotifyingAndIgnoresExtraParams() {
    var known = new BufferInfoValue(11, 7, 2, 9, "#room");
    session.buffers.merge(known);
    dispatcher.dispatch(
        rpc(
            "2bufferInfoUpdated(BufferInfo)",
            List.of(
                new BufferInfoValue(11, 0, 0, -1, " "),
                new BufferInfoValue(22, 8, 2, 4, "#ignored"))));
    assertEquals(List.of(known), observations);
    assertSame(observations.getFirst(), session.buffers.get(11));
    assertNull(session.buffers.get(22));
    assertTrue(trace.isEmpty());
  }

  @Test
  void bufferObserverFailurePropagatesAfterMergeWithoutRollingBackMetadata() {
    observerFailure = new IllegalStateException("observer failed");
    var update = new BufferInfoValue(11, 7, 2, 9, "#room");
    assertSame(
        observerFailure,
        assertThrows(
            IllegalStateException.class,
            () -> dispatcher.dispatch(rpc("2bufferInfoUpdated(BufferInfo)", List.of(update)))));
    assertSame(update, session.buffers.get(11));
    assertTrue(observations.isEmpty());
    assertTrue(trace.isEmpty());
  }

  @Test
  void removalDropsOnlyFirstBufferAndItsPendingMarkers() {
    var removed = new BufferInfoValue(11, 7, 2, 9, "#room");
    var retained = new BufferInfoValue(22, 8, 2, 4, "#retained");
    session.buffers.merge(removed);
    session.buffers.merge(retained);
    session.pendingReadMarkers.defer(11, 101);
    session.pendingReadMarkers.defer(11, 102);
    session.pendingReadMarkers.defer(22, 201);
    dispatcher.dispatch(rpc("2bufferInfoRemoved(BufferInfo)", List.of(removed, retained)));
    assertNull(session.buffers.get(11));
    assertSame(retained, session.buffers.get(22));
    assertNull(session.pendingReadMarkers.takeBufferForMessage(101));
    assertNull(session.pendingReadMarkers.takeBufferForMessage(102));
    assertEquals(22, session.pendingReadMarkers.takeBufferForMessage(201));
    assertTrue(observations.isEmpty());
    assertTrue(events.isEmpty());
  }

  @ParameterizedTest
  @MethodSource("invalidBufferParams")
  void malformedBufferUpdatesAndRemovalsKeepExistingBufferAndPendingMarker(List<Object> params) {
    var known = new BufferInfoValue(11, 7, 2, 9, "#room");
    session.buffers.merge(known);
    session.pendingReadMarkers.defer(11, 101);
    dispatcher.dispatch(rpc("2bufferInfoUpdated(BufferInfo)", params));
    dispatcher.dispatch(rpc("2bufferInfoRemoved(BufferInfo)", params));
    assertSame(known, session.buffers.get(11));
    assertEquals(11, session.pendingReadMarkers.takeBufferForMessage(101));
    assertTrue(observations.isEmpty());
    assertTrue(events.isEmpty());
  }

  @Test
  void networkCreatedClaimsOnePendingNameThroughExistingLifecycleTranslator() {
    session.networks.rememberCreatedName("Work");
    session.networks.rememberCreatedName("Next");
    dispatcher.dispatch(
        rpc(" 2networkCreated(NetworkId) ", List.of(new UserTypeValue("NetworkId", 7))));
    assertEquals(List.of("network:7:Work"), trace);
    assertEquals("Work", session.networks.displayNames().get(7));
    assertEquals("Next", session.networks.claimCreatedName());
  }

  @Test
  void mixedLifecycleSlotObservesNetworkAndStateBeforeIdentity() {
    Map<String, Object> state =
        Map.of("networkId", 7, "networkName", "Work", "identityId", 9, "identityName", "Alice");
    dispatcher.dispatch(rpc("2networkIdentityAdded(QVariantMap)", List.of(state)));
    assertEquals(List.of("network:7:Work", "state:7", "identity"), trace);
    assertTrue(session.identities.isKnown(9));
    assertEquals(state, session.identities.state(9));
  }

  @Test
  void identityOnlyLifecycleDoesNotConsumePendingNetworkName() {
    session.networks.rememberCreatedName("Work");
    dispatcher.dispatch(
        rpc(
            "2identityCreated(Identity)",
            List.of(
                new UserTypeValue("Identity", Map.of("identityId", 9, "identityName", "Alice")))));
    assertEquals(List.of("identity"), trace);
    assertTrue(session.identities.isKnown(9));
    assertEquals("Work", session.networks.claimCreatedName());
  }

  @Test
  void mixedRemovalSlotForgetsNetworkAndIdentityThroughTheirOwners() {
    session.networks.observe(7, "Work");
    session.identities.observe(9, "Alice");
    trace.clear();
    dispatcher.dispatch(
        rpc(
            "2networkIdentityRemoved(QVariantMap)",
            List.of(Map.of("networkId", 7, "identityId", 9))));
    assertEquals(List.of("remove:7"), trace);
    assertFalse(session.networks.displayNames().containsKey(7));
    assertFalse(session.identities.isKnown(9));
  }

  @Test
  void networkObserverFailureStopsLaterIdentityObservation() {
    networkFailure = new IllegalStateException("network observer failed");
    var frame =
        rpc("2networkIdentityAdded(QVariantMap)", List.of(Map.of("networkId", 7, "identityId", 9)));
    assertSame(
        networkFailure,
        assertThrows(IllegalStateException.class, () -> dispatcher.dispatch(frame)));
    assertEquals(List.of("network:7:"), trace);
    assertFalse(session.identities.isKnown(9));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        " ",
        "unknown",
        "2DISPLAYMSG(Message)",
        "2bufferinfoupdated(BufferInfo)",
        "displayMsg(Message)"
      })
  void blankUnknownAndCaseVariantSlotsDoNotApplyKnownOperations(String slot) {
    dispatcher.dispatch(
        rpc(slot, List.of(textMessage(), new BufferInfoValue(11, 7, 2, 9, "#room"))));
    assertTrue(messages.isEmpty());
    assertTrue(observations.isEmpty());
    assertTrue(events.isEmpty());
    assertTrue(trace.isEmpty());
    assertNull(session.buffers.get(11));
  }

  private static SignalProxyMessage rpc(String slot, List<Object> params) {
    return new SignalProxyMessage(SIGNAL_PROXY_RPC_CALL, "", "", slot, params);
  }

  private static MessageValue textMessage() {
    return new MessageValue(
        101, 1_700_000_000L, 1, 0, new BufferInfoValue(11, 7, 2, 9, "#room"), "bob!u@h", "hello");
  }

  private static Stream<List<Object>> invalidMessageParams() {
    return Stream.of(
        null,
        List.of(),
        Arrays.asList(null, textMessage()),
        List.of("wrong", textMessage()),
        List.of(new UserTypeValue("Message", textMessage())),
        List.of(Map.of("content", "hello")));
  }

  private static Stream<List<Object>> invalidBufferParams() {
    return Stream.of(
        null,
        List.of(),
        Arrays.asList((Object) null),
        List.of("wrong"),
        List.of(new BufferInfoValue(-1, 7, 2, 9, "#room")));
  }

  private static Stream<Arguments> statusCases() {
    return Stream.of(
        Arguments.of(List.of(" network ", " Connected "), 0, "network: Connected", "Connected"),
        Arguments.of(List.of(" network "), 0, "network", "network"),
        Arguments.of(Arrays.asList(null, "text"), 0, "text", "text"),
        Arguments.of(
            List.of("network", ":srv 001 alice :Welcome"), 1, "Welcome", ":srv 001 alice :Welcome"),
        Arguments.of(
            List.of("network", "@tag=x :srv 372 alice :MOTD"),
            372,
            "MOTD",
            "@tag=x :srv 372 alice :MOTD"),
        Arguments.of(List.of(42, true, "ignored"), 0, "42: true", "true"));
  }
}
