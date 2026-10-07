package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.ChatHistoryEntry;
import cafe.woden.ircclient.irc.IrcEvent.ChatHistoryBatchReceived;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.MessageValue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreBacklogTranslatorTest {
  private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
  private static final BufferInfoValue CHANNEL = new BufferInfoValue(11, 1, 2, -1, "#ircafe");
  private static final BufferInfoValue OTHER = new BufferInfoValue(22, 2, 2, -1, "#other");

  private final QuasselCoreBacklogTranslator translator =
      new QuasselCoreBacklogTranslator(() -> NOW);
  private final Map<Integer, BufferInfoValue> known = new LinkedHashMap<>(Map.of(11, CHANNEL));
  private final List<BufferInfoValue> resolved = new ArrayList<>();
  private final List<String> trace = new ArrayList<>();
  private final List<MessageValue> observedMessages = new ArrayList<>();
  private final List<ChatHistoryEntry> observedEntries = new ArrayList<>();
  private UnaryOperator<BufferInfoValue> resolver = info -> info;
  private BiFunction<BufferInfoValue, String, String> targets =
      (info, from) -> info == null ? "" : info.bufferName();

  @Test
  void absentAndUnrecognizablePayloadsDoNotProduceABatchOrConsumeSequence() {
    assertNull(sync(null));
    assertNull(sync(List.of()));
    assertNull(sync(Arrays.asList(null, "unrecognized", Map.of("buffer", CHANNEL))));
    assertTrue(trace.isEmpty());
    assertTrue(resolved.isEmpty());
    assertEquals("quassel-backlog-sync-1", sync(List.of(11, List.of())).batchId());
  }

  @Test
  void removedNumericBufferRejectsEntireResponseEvenWithAnotherValidBuffer() {
    assertNull(sync(List.of(999, CHANNEL, List.of(message(10, 1, CHANNEL)))));
    assertTrue(resolved.isEmpty());
    assertTrue(trace.isEmpty());
  }

  @Test
  void knownNumericBufferWinsOverLaterDirectBuffer() {
    ChatHistoryBatchReceived batch = sync(List.of(11, OTHER, List.of()));
    assertEquals("#ircafe", batch.target());
    assertEquals(List.of("hint:#ircafe:1"), trace);
    assertTrue(resolved.isEmpty());
  }

  @Test
  void directBufferIsResolvedBeforeDerivingTarget() {
    resolver = info -> OTHER;
    ChatHistoryBatchReceived batch = sync(List.of(CHANNEL, List.of()));
    assertEquals(List.of(CHANNEL), resolved);
    assertEquals("#other", batch.target());
    assertEquals(List.of("hint:#other:2"), trace);
  }

  @Test
  void firstNestedMessageProvidesFallbackBufferAndSenderEvenWhenItIsNotText() {
    BufferInfoValue query = new BufferInfoValue(33, 3, 4, -1, "");
    targets = (info, from) -> from;
    ChatHistoryBatchReceived batch =
        sync(List.of(Map.of("nested", List.of(message(10, 0x20, query)))));
    assertEquals(List.of(query), resolved);
    assertEquals("alice", batch.target());
    assertTrue(batch.entries().isEmpty());
    assertEquals(List.of("hint:alice:3"), trace);
  }

  @Test
  void missingMessageBufferCanBeResolvedToStatus() {
    BufferInfoValue status = new BufferInfoValue(-1, 1, 1, -1, "status");
    resolver = info -> status;
    ChatHistoryBatchReceived batch = sync(List.of(List.of(message(10, 1, null))));
    assertEquals(Arrays.asList((BufferInfoValue) null), resolved);
    assertEquals("status", batch.target());
    assertEquals("status", batch.entries().getFirst().target());
  }

  @Test
  void unresolvedBufferAndBlankTargetSuppressBatchWithoutObservations() {
    resolver = info -> null;
    assertNull(sync(List.of(CHANNEL, List.of(message(10, 1, null)))));
    targets = (info, from) -> "";
    assertNull(sync(List.of(11, List.of(message(10, 1, CHANNEL)))));
    assertTrue(trace.isEmpty());
    targets = (info, from) -> info.bufferName();
    assertEquals("quassel-backlog-sync-1", sync(List.of(11)).batchId());
  }

  @Test
  void knownEmptyResponseStillCompletesHistoryBatchAndObservesTarget() {
    ChatHistoryBatchReceived batch = sync(List.of(11, -1, -1, 50, 0, List.of()));
    assertEquals(NOW, batch.at());
    assertEquals("#ircafe", batch.target());
    assertTrue(batch.entries().isEmpty());
    assertEquals(List.of("hint:#ircafe:1"), trace);
  }

  @Test
  void nonTextResponseStillEmitsEmptyBatchWithoutHistoryObservation() {
    ChatHistoryBatchReceived batch = sync(List.of(11, List.of(message(10, 0x20, CHANNEL))));
    assertTrue(batch.entries().isEmpty());
    assertEquals(List.of("hint:#ircafe:1"), trace);
    assertTrue(observedMessages.isEmpty());
  }

  @Test
  void collectsNestedMapsAndListsInEncounterOrderBeforeSortingEntries() {
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("first", List.of(message(30, 1, CHANNEL), "ignored"));
    nested.put("second", Map.of("messages", List.of(message(10, 2, CHANNEL))));
    ChatHistoryBatchReceived batch =
        sync(List.of(11, List.of(nested, message(20, 4, CHANNEL), message(40, 0x20, CHANNEL))));
    assertEquals(List.of("10", "20", "30"), ids(batch));
    assertEquals(
        List.of(30L, 10L, 20L), observedMessages.stream().map(MessageValue::messageId).toList());
    assertEquals(List.of("hint:#ircafe:1", "history:30", "history:10", "history:20"), trace);
  }

  @Test
  void userTypeWrappersAreNotTreatedAsNativeMessagePayloads() {
    var wrapped = new QuasselCoreDatastreamCodec.UserTypeValue("Message", message(10, 1, CHANNEL));
    ChatHistoryBatchReceived batch = sync(List.of(11, List.of(wrapped)));
    assertTrue(batch.entries().isEmpty());
    assertTrue(observedMessages.isEmpty());
  }

  @Test
  void orderingUsesNumericMessageIdsIncludingLongIdsRatherThanTimestamps() {
    ChatHistoryBatchReceived batch =
        sync(
            List.of(
                11,
                List.of(
                    message(3_000_000_000L, 1, CHANNEL, 1, "latest id"),
                    message(10, 1, CHANNEL, 2, "ten"),
                    message(9, 1, CHANNEL, 3, "nine"))));
    assertEquals(List.of("9", "10", "3000000000"), ids(batch));
  }

  @Test
  void equalIdsKeepEncounterOrderAndDuplicatesRemainForConsumerDedupe() {
    ChatHistoryBatchReceived batch =
        sync(
            List.of(
                11,
                List.of(
                    message(20, 1, CHANNEL, 1, "first duplicate"),
                    message(10, 1, CHANNEL),
                    message(20, 4, CHANNEL, 2, "second duplicate"))));
    assertEquals(List.of("10", "20", "20"), ids(batch));
    assertEquals("first duplicate", batch.entries().get(1).text());
    assertEquals("second duplicate", batch.entries().get(2).text());
    assertEquals(3, observedEntries.size());
  }

  @Test
  void missingIdsSortFirstStablyAndRawIdsStillReachHistoryObserver() {
    ChatHistoryBatchReceived batch =
        sync(
            List.of(
                11,
                List.of(message(5, 1, CHANNEL), message(-4, 1, CHANNEL), message(0, 1, CHANNEL))));
    assertEquals(List.of("", "", "5"), ids(batch));
    assertEquals(
        List.of(5L, -4L, 0L), observedMessages.stream().map(MessageValue::messageId).toList());
  }

  @Test
  void eachEntryUsesItsOwnBufferAndSenderWhileBatchKeepsEnvelopeTarget() {
    targets = (info, from) -> info.bufferName() + "{net:" + info.networkId() + "}-" + from;
    ChatHistoryBatchReceived batch = sync(List.of(11, List.of(message(10, 1, OTHER))));
    assertEquals("#ircafe{net:1}-alice", batch.target());
    assertEquals("#other{net:2}-alice", batch.entries().getFirst().target());
    assertEquals(batch.entries(), observedEntries);
  }

  @Test
  void blankEntryTargetFallsBackToEnvelopeTarget() {
    targets = (info, from) -> CHANNEL.equals(info) ? "#ircafe" : "";
    ChatHistoryBatchReceived batch = sync(List.of(11, List.of(message(10, 1, OTHER))));
    assertEquals("#ircafe", batch.entries().getFirst().target());
  }

  @ParameterizedTest
  @CsvSource({"1,PRIVMSG", "2,NOTICE", "4,ACTION", "3,NOTICE", "7,ACTION", "9,PRIVMSG"})
  void messageTypeFlagsPreserveActionNoticeAndPlainPrecedence(
      int bits, ChatHistoryEntry.Kind kind) {
    var batch = display(message(10, bits, CHANNEL), "", "10");
    assertEquals(kind, batch.entries().getFirst().kind());
  }

  @Test
  void displayFiltersNullAndNonTextMessagesWithoutConsumingSequence() {
    assertNull(display(null, "#ircafe", ""));
    assertNull(display(message(10, 0x20, CHANNEL), "#ircafe", "10"));
    assertEquals("quassel-backlog-10-1", display(message(10, 1, CHANNEL), "", "10").batchId());
  }

  @Test
  void displayUsesResolvedTargetBeforeTrimmedFallbackAndRejectsMissingTarget() {
    assertEquals("#ircafe", display(message(10, 1, CHANNEL), "#fallback", "10").target());
    targets = (info, from) -> "";
    assertEquals("#fallback", display(message(10, 1, CHANNEL), "  #fallback  ", "10").target());
    assertNull(display(message(10, 1, CHANNEL), " ", "10"));
    assertNull(display(message(10, 1, CHANNEL), null, "10"));
  }

  @Test
  void nullSenderAndContentAreEmptyAndRawTaggedTextIsPreserved() {
    var empty = new MessageValue(10, 100, 1, 0x80, CHANNEL, null, null);
    ChatHistoryEntry entry = display(empty, "", "10").entries().getFirst();
    assertEquals("", entry.from());
    assertEquals("", entry.text());
    var tagged = message(11, 1, CHANNEL, 101, "@msgid=x PRIVMSG #ircafe :raw text");
    ChatHistoryEntry taggedEntry = display(tagged, "", "11").entries().getFirst();
    assertEquals(tagged.content(), taggedEntry.text());
    assertTrue(taggedEntry.ircv3Tags().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, 0, 100})
  void entryTimestampUsesWireSecondsOrCurrentTimeAndDisplayKeepsProvidedBatchTime(long seconds) {
    var batch = display(message(10, 1, CHANNEL, seconds, "text"), "", "10");
    assertEquals(NOW.minusSeconds(1), batch.at());
    assertEquals(
        seconds > 0 ? Instant.ofEpochSecond(seconds) : NOW, batch.entries().getFirst().at());
  }

  @Test
  void missingDisplayIdUsesWireTimestampInBatchIdWhileEntryIdRemainsEmpty() {
    var batch = display(message(0, 1, CHANNEL, 100, "text"), "", "");
    assertEquals("quassel-backlog-100-1", batch.batchId());
    assertEquals("", batch.entries().getFirst().messageId());
    assertEquals(
        "quassel-backlog-0-2", display(message(0, 1, CHANNEL, 0, "text"), "", "").batchId());
  }

  @Test
  void syncAndDisplayShareOneSequenceAndSeparateSessionsStartIndependently() {
    assertEquals("quassel-backlog-sync-1", sync(List.of(11)).batchId());
    assertEquals("quassel-backlog-10-2", display(message(10, 1, CHANNEL), "", "10").batchId());
    assertEquals("quassel-backlog-sync-3", sync(List.of(11)).batchId());
    var otherSession = new QuasselCoreBacklogTranslator(() -> NOW);
    assertEquals(
        "quassel-backlog-10-1",
        otherSession.display(NOW, "", message(10, 1, CHANNEL), "10", targets).batchId());
  }

  @Test
  void entriesAreImmutableAndDetachedFromMutablePayloadLists() {
    List<Object> payloadMessages = new ArrayList<>(List.of(message(10, 1, CHANNEL)));
    var batch = sync(List.of(11, payloadMessages));
    payloadMessages.clear();
    assertEquals(List.of("10"), ids(batch));
    assertThrows(UnsupportedOperationException.class, () -> batch.entries().clear());
  }

  @Test
  void historyObserverFailurePropagatesWithoutAllocatingBatchSequence() {
    var failure = new IllegalStateException("observation failed");
    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class,
            () ->
                translator.sync(
                    List.of(11, List.of(message(10, 1, CHANNEL))),
                    known::get,
                    resolver,
                    targets,
                    (target, networkId) -> {},
                    (message, entry) -> {
                      throw failure;
                    })));
    assertEquals("quassel-backlog-sync-1", sync(List.of(11)).batchId());
  }

  private ChatHistoryBatchReceived sync(List<Object> values) {
    return translator.sync(
        values,
        known::get,
        info -> {
          resolved.add(info);
          return resolver.apply(info);
        },
        targets,
        (target, networkId) -> trace.add("hint:" + target + ":" + networkId),
        (message, entry) -> {
          observedMessages.add(message);
          observedEntries.add(entry);
          trace.add("history:" + message.messageId());
        });
  }

  private ChatHistoryBatchReceived display(MessageValue message, String fallback, String id) {
    return translator.display(NOW.minusSeconds(1), fallback, message, id, targets);
  }

  private static List<String> ids(ChatHistoryBatchReceived batch) {
    return batch.entries().stream().map(ChatHistoryEntry::messageId).toList();
  }

  private static MessageValue message(long id, int type, BufferInfoValue buffer) {
    return message(id, type, buffer, 100, "text " + id);
  }

  private static MessageValue message(
      long id, int type, BufferInfoValue buffer, long seconds, String text) {
    return new MessageValue(id, seconds, type, 0x80, buffer, "alice!u@h", text);
  }
}
