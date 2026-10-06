package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreBufferSyncerParser.parseReadMarkers;
import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.quassel.QuasselCoreBufferSyncerParser.ReadMarkerUpdate;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreBufferSyncerParserTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "setMarkerLine(BufferId,MsgId)",
        "setLastSeenMsg",
        " REQUESTSETLASTSEENMSG(BufferId,MsgId) "
      })
  void slotUpdatesUseTheirBufferAndMessageIds(String slot) {
    assertEquals(List.of(new ReadMarkerUpdate(11, 42)), parseReadMarkers(slot, List.of(11, 42)));
  }

  @Test
  void bufferZeroAndLargeNativeMessageIdsRemainValid() {
    assertEquals(
        List.of(new ReadMarkerUpdate(0, Long.MAX_VALUE)),
        parseReadMarkers("setMarkerLine", List.of("0", Long.toString(Long.MAX_VALUE))));
  }

  @ParameterizedTest
  @CsvSource({"-1,42", "11,0", "11,-1", "invalid,42", "11,invalid"})
  void invalidSlotIdsAreIgnoredWithoutInterpretingExtraSnapshotData(String bufferId, String msgId) {
    assertTrue(
        parseReadMarkers(
                "setMarkerLine", List.of(bufferId, msgId, Map.of("bufferId", 12, "markerLine", 80)))
            .isEmpty());
  }

  @Test
  void missingSlotIdsAndEmptyPayloadsDoNotProduceUpdates() {
    assertTrue(parseReadMarkers("setLastSeenMsg", List.of(11)).isEmpty());
    assertTrue(parseReadMarkers("setLastSeenMsg", List.of()).isEmpty());
    assertTrue(parseReadMarkers(null, null).isEmpty());
    assertTrue(parseReadMarkers(null, List.of()).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"MarkerLines", "LastSeenMsg", "LastSeenMsgs"})
  void byteArrayPropertyNamesDecodeAlternatingPairsAndDeduplicateExactUpdates(String property) {
    assertEquals(
        List.of(
            new ReadMarkerUpdate(11, 42),
            new ReadMarkerUpdate(12, 80),
            new ReadMarkerUpdate(11, 43)),
        parseReadMarkers(
            "",
            List.of(
                (" " + property.toUpperCase(java.util.Locale.ROOT) + " ")
                    .getBytes(StandardCharsets.UTF_8),
                List.of(11, 42, 12, 80, 11, 42, 11, 43, "unpaired"))));
  }

  @ParameterizedTest
  @CsvSource({"bufferId,markerLine", "BUFFER_ID,messageID", "buffer,LastSeen", "ID,lastSeenMsg"})
  void nestedSnapshotsAcceptExistingCaseInsensitiveIdAliases(String bufferKey, String msgKey) {
    assertEquals(
        List.of(new ReadMarkerUpdate(11, 42)),
        parseReadMarkers(null, List.of(List.of(Map.of(bufferKey, "11", msgKey, "42")))));
  }

  @Test
  void snapshotAliasesKeepTheirFirstUsableIdPrecedence() {
    assertEquals(
        List.of(new ReadMarkerUpdate(0, 42)),
        parseReadMarkers(
            "",
            List.of(
                Map.of(
                    "bufferId", 0,
                    "buffer", 11,
                    "markerLine", 42,
                    "msgId", 80))));
    assertEquals(
        List.of(new ReadMarkerUpdate(11, 80)),
        parseReadMarkers(
            "",
            List.of(
                Map.of(
                    "bufferId", -1,
                    "buffer", 11,
                    "markerLine", 0,
                    "msgId", 80))));
  }

  @Test
  void nestedMapsAndListsKeepObservationOrderAndDeduplicateAcrossRepresentations() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    Map<Object, Object> markerIds = new LinkedHashMap<>();
    markerIds.put("11", 42L);
    markerIds.put(12, "80");
    snapshot.put("bufferId", 13);
    snapshot.put("msgId", 90);
    snapshot.put("MarkerLines", markerIds);
    snapshot.put("LastSeenMsgs", List.of(11, 42, 11, 43, 12, 80));
    snapshot.put(
        "nested",
        List.of(Map.of("BUFFER", 14, "messageID", 100), Map.of("id", 13, "markerline", 90)));
    assertEquals(
        List.of(
            new ReadMarkerUpdate(13, 90),
            new ReadMarkerUpdate(11, 42),
            new ReadMarkerUpdate(12, 80),
            new ReadMarkerUpdate(11, 43),
            new ReadMarkerUpdate(14, 100)),
        parseReadMarkers("", List.of(snapshot)));
  }

  @Test
  void flatPropertiesAreObservedBeforeNestedMapsRegardlessOfTheirPositionInThePayload() {
    assertEquals(
        List.of(
            new ReadMarkerUpdate(11, 42),
            new ReadMarkerUpdate(12, 80),
            new ReadMarkerUpdate(9, 20)),
        parseReadMarkers(
            "",
            List.of(
                Map.of("bufferId", 9, "msgId", 20),
                "unrelated",
                "MarkerLines",
                List.of(11, 42, 12, 80),
                Map.of("buffer", 11, "markerLine", 42))));
  }

  @Test
  void malformedPairsAndUnrelatedPropertiesDoNotBecomeReadMarkers() {
    assertEquals(
        List.of(new ReadMarkerUpdate(12, 80)),
        parseReadMarkers(
            "",
            List.of(
                "MarkerLines",
                Arrays.asList(-1, 42, 11, 0, "invalid", 50, 13, null, 12, 80, 14),
                "BufferActivity",
                List.of(15, 90))));
    assertTrue(parseReadMarkers("", List.of("BufferActivity", List.of(11, 42))).isEmpty());
    assertTrue(parseReadMarkers("", List.of(Map.of("id", 11), Map.of("msgId", 42))).isEmpty());
  }

  @Test
  void decodedUpdatesAreImmutableAndIndependentOfTheSourceSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>(Map.of("bufferId", 11, "markerLine", 42));
    List<ReadMarkerUpdate> updates = parseReadMarkers("", List.of(snapshot));
    snapshot.put("markerLine", 80);
    assertEquals(List.of(new ReadMarkerUpdate(11, 42)), updates);
    assertThrows(
        UnsupportedOperationException.class, () -> updates.add(new ReadMarkerUpdate(12, 90)));
  }
}
