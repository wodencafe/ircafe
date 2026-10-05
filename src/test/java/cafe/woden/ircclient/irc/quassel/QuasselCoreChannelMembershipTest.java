package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.IrcEvent;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.UserTypeValue;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreChannelMembershipTest {
  private final List<IrcEvent> events = new ArrayList<>();
  private final QuasselCoreChannelMembership membership =
      new QuasselCoreChannelMembership(events::add);
  private final Instant at = Instant.parse("2026-01-01T00:00:00Z");
  private final BiFunction<String, Integer, String> qualify =
      (channel, id) -> channel + "{net:id-" + id + "}";

  @Test
  void numericNetworkIdKeepsDedupeStableAcrossTokenChangesAndCase() {
    membership.observeJoin(at, "#Chat{net:old}", 1);
    membership.observeJoin(at, " #chat{net:new} ", 1);
    membership.observeJoin(at, "#chat{net:new}", 2);
    assertEquals(
        List.of(
            new IrcEvent.JoinedChannel(at, "#Chat{net:old}"),
            new IrcEvent.JoinedChannel(at, "#chat{net:new}")),
        events);
    membership.leave("#CHAT{net:another}", 1);
    membership.observeJoin(at, "#chat{net:new}", 1);
    assertEquals(3, events.size());
  }

  @Test
  void forgettingNetworkKeepsOtherNetworksAndUnknownMemberships() {
    for (int id : List.of(1, 10, -1)) membership.observeJoin(at, "#chat", id);
    membership.observeJoin(at, "#chat{net:one}", -1);
    membership.forgetNetwork(-1);
    membership.forgetNetwork(1);
    for (int id : List.of(1, 10, -1)) membership.observeJoin(at, "#chat", id);
    membership.observeJoin(at, "#chat{net:one}", -1);
    assertEquals(5, events.size());
    membership.clear();
    membership.observeJoin(at, "#chat", 10);
    assertEquals(6, events.size());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankTargetsDoNotEmitJoins(String target) {
    membership.observeJoin(at, target, 1);
    membership.leave(target, 1);
    assertTrue(events.isEmpty());
  }

  static Stream<Arguments> lifecyclePayloads() {
    return Stream.of(
        Arguments.of("#chat"),
        Arguments.of(" #chat ".getBytes(StandardCharsets.UTF_8)),
        Arguments.of(new BufferInfoValue(11, 2, 0x02, -1, "#chat")),
        Arguments.of(new UserTypeValue("QString", List.of("#chat"))),
        Arguments.of(Map.of("NAME", " #chat ")),
        Arguments.of(Map.of("CHANNEL", "#chat")),
        Arguments.of(Map.of("bufferName", "#chat")));
  }

  @ParameterizedTest
  @MethodSource("lifecyclePayloads")
  void lifecyclePayloadsUseEnvelopeNetworkAndSilentRemoval(Object payload) {
    membership.observeNetworkLifecycle(
        "core/1", "addIrcChannel(QString)", List.of(payload), qualify);
    membership.observeNetworkLifecycle("1", "addIrcChannel", List.of("#CHAT"), qualify);
    assertEquals(1, events.size());
    assertEquals("#chat{net:id-1}", ((IrcEvent.JoinedChannel) events.getFirst()).channel());
    membership.observeNetworkLifecycle("1", "removeIrcChannel", List.of(payload), qualify);
    assertEquals(1, events.size());
    membership.observeNetworkLifecycle("1", "addIrcChannel", List.of(payload), qualify);
    assertEquals(2, events.size());
  }

  @Test
  void lifecycleRetainsAliasPriorityAndDoesNotTraverseMapValues() {
    membership.observeNetworkLifecycle(
        "1",
        "addIrcChannel",
        List.of(
            Map.of("name", "#first", "channel", "#second"),
            Map.of("nested", Map.of("channel", "#hidden")),
            "nick",
            "#FIRST"),
        qualify);
    assertEquals(1, events.size());
    assertEquals("#first{net:id-1}", ((IrcEvent.JoinedChannel) events.getFirst()).channel());
  }

  @Test
  void lifecycleFallsBackToObjectLeafOnlyWhenNoPayloadCandidatesExist() {
    membership.observeNetworkLifecycle("core/#fallback", "addIrcChannel", List.of("nick"), qualify);
    assertTrue(events.isEmpty());
    membership.observeNetworkLifecycle("core/#fallback", "addIrcChannel", null, qualify);
    assertEquals("#fallback{net:id--1}", ((IrcEvent.JoinedChannel) events.getFirst()).channel());
    membership.observeNetworkLifecycle("1", "unrelated", List.of("#unused"), qualify);
    assertEquals(1, events.size());
  }

  @Test
  void disconnectedNetworkRehydratesOnlyItsOwnBuffersOnReconnect() {
    List<BufferInfoValue> buffers = List.of(buffer(1, 0x02, "#chat"), buffer(2, 0x02, "#chat"));
    var hint = new ArrayList<String>();
    membership.reconcileNetwork(1, true, buffers, qualify, (channel, id) -> hint.add(channel));
    membership.reconcileNetwork(2, true, buffers, qualify, (channel, id) -> hint.add(channel));
    membership.reconcileNetwork(1, false, buffers, qualify, (channel, id) -> fail());
    assertEquals(2, events.size());
    membership.reconcileNetwork(2, true, buffers, qualify, (channel, id) -> hint.add(channel));
    membership.reconcileNetwork(1, true, buffers, qualify, (channel, id) -> hint.add(channel));
    assertEquals(
        List.of("#chat{net:id-1}", "#chat{net:id-2}", "#chat{net:id-1}"),
        events.stream()
            .map(IrcEvent.JoinedChannel.class::cast)
            .map(IrcEvent.JoinedChannel::channel)
            .toList());
    assertEquals(4, hint.size());
    membership.reconcileNetwork(
        -1,
        true,
        buffers,
        (channel, id) -> {
          fail();
          return "";
        },
        (channel, id) -> fail());
  }

  @Test
  void bufferHintsPrecedeEventsAndRefreshForDuplicateJoins() {
    List<String> order = new ArrayList<>();
    QuasselCoreChannelMembership observed =
        new QuasselCoreChannelMembership(event -> order.add("join"));
    for (int i = 0; i < 2; i++) {
      observed.observeBuffer(
          buffer(0, 0x02, "#zero"),
          at,
          qualify,
          (channel, id) -> {
            assertEquals(0, id);
            order.add("hint");
          });
    }
    assertEquals(List.of("hint", "join", "hint"), order);
  }

  static Stream<Arguments> bufferKinds() {
    return Stream.of(
        Arguments.of(0x02, "arbitrary", true),
        Arguments.of(0x04, "#chat", true),
        Arguments.of(0x01, "#chat", true),
        Arguments.of(0x04, "nick", false),
        Arguments.of(0x02, " ", false));
  }

  @ParameterizedTest
  @MethodSource("bufferKinds")
  void bufferEligibilityPreservesTypeAndNameFallback(int type, String name, boolean expected) {
    membership.observeBuffer(buffer(1, type, name), at, qualify, (channel, id) -> {});
    assertEquals(expected ? 1 : 0, events.size());
    if (expected) assertEquals(at, ((IrcEvent.JoinedChannel) events.getFirst()).at());
  }

  @Test
  void absentBufferAndEmptyQualificationDoNotPublishOrRefreshHints() {
    membership.observeBuffer(null, at, qualify, (channel, id) -> fail());
    membership.observeBuffer(
        buffer(1, 0x02, "#chat"), at, (channel, id) -> "", (channel, id) -> fail());
    assertTrue(events.isEmpty());
  }

  @Test
  void concurrentDuplicateJoinsPublishOnlyOnce() throws Exception {
    var emitted = new java.util.concurrent.ConcurrentLinkedQueue<IrcEvent>();
    var concurrent = new QuasselCoreChannelMembership(emitted::add);
    var ready = new CountDownLatch(16);
    var start = new CountDownLatch(1);
    List<Thread> workers = new ArrayList<>();
    try {
      for (int i = 0; i < 16; i++) {
        workers.add(
            Thread.ofVirtual()
                .start(
                    () -> {
                      ready.countDown();
                      try {
                        start.await();
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                      }
                      concurrent.observeJoin(at, "#chat", 1);
                    }));
      }
      assertTrue(ready.await(2, java.util.concurrent.TimeUnit.SECONDS));
    } finally {
      start.countDown();
    }
    for (Thread worker : workers) worker.join(2_000);
    assertEquals(1, emitted.size());
  }

  private static BufferInfoValue buffer(int networkId, int type, String name) {
    return new BufferInfoValue(11, networkId, type, -1, name);
  }
}
