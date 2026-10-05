package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreBufferCatalogTest {
  private final QuasselCoreBufferCatalog buffers = new QuasselCoreBufferCatalog();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void firstObservationKeepsNativeValuesAndAcceptsBufferZero(boolean sync) {
    var first = new BufferInfoValue(0, 0, 0, 0, " #Room ");
    assertSame(first, update(buffers, first, sync));
    assertSame(first, buffers.get(0));
  }

  @ParameterizedTest
  @CsvSource({"0,0,5,7", "-1,-1,5,7", "-2,-2,-2,-2", "9,8,9,8"})
  void partialMetadataPreservesOnlyTheExistingUnknownSentinelRules(
      int network, int group, int expectedNetwork, int expectedGroup) {
    for (boolean sync : new boolean[] {true, false}) {
      var catalog = new QuasselCoreBufferCatalog();
      catalog.merge(new BufferInfoValue(11, 5, 2, 7, " #Room "));
      assertEquals(
          new BufferInfoValue(11, expectedNetwork, 2, expectedGroup, "#Room"),
          update(catalog, new BufferInfoValue(11, network, 0, group, " "), sync));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void knownUpdatesReplaceMetadataAndTrimTheirName(boolean sync) {
    buffers.merge(new BufferInfoValue(11, 1, 2, 7, "#old"));
    var expected = new BufferInfoValue(11, 2, 4, 8, "new");
    assertEquals(expected, update(buffers, new BufferInfoValue(11, 2, 4, 8, " new "), sync));
    assertEquals(expected, buffers.get(11));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void missingAndEphemeralMetadataDoNotConsumeCapacity(boolean sync) {
    var limited = new QuasselCoreBufferCatalog(1);
    var known = new BufferInfoValue(11, 1, 2, -1, "#known");
    limited.merge(known);
    var ephemeral = new BufferInfoValue(-1, 2, 2, -1, "#new");
    assertSame(ephemeral, update(limited, ephemeral, sync));
    assertNull(update(limited, null, sync));
    assertNull(limited.get(-1));
    assertEquals(List.of(known), List.copyOf(limited.values()));
  }

  @Test
  void preferredNetworkAndTypeOutrankAllOtherNameMatches() {
    buffers.merge(new BufferInfoValue(1, 2, 4, -1, "#room"));
    buffers.merge(new BufferInfoValue(2, 1, 2, -1, "#room"));
    var preferred = new BufferInfoValue(3, 2, 2, -1, "#room");
    buffers.merge(preferred);
    assertEquals(preferred, buffers.findByName("#room", 2, 2));
  }

  @Test
  void preferredNetworkWithAnotherTypeOutranksTypeMatchesOnOtherNetworks() {
    buffers.merge(new BufferInfoValue(1, 1, 2, -1, "#room"));
    var preferred = new BufferInfoValue(2, 2, 4, -1, "#room");
    buffers.merge(preferred);
    assertEquals(preferred, buffers.findByName("#room", 2, 2));
  }

  @Test
  void typeMatchIsPreferredWhenTheRequestedNetworkHasNoMatchingName() {
    buffers.merge(new BufferInfoValue(1, 1, 4, -1, "#room"));
    buffers.merge(new BufferInfoValue(2, 2, 2, -1, "#different"));
    var fallback = new BufferInfoValue(3, 1, 2, -1, "#room");
    buffers.merge(fallback);
    assertEquals(fallback, buffers.findByName("#room", 2, 2));
  }

  @Test
  void anyMatchingNameIsUsedWhenNeitherNetworkNorTypeMatches() {
    var fallback = new BufferInfoValue(1, 1, 4, -1, "#room");
    buffers.merge(fallback);
    assertEquals(fallback, buffers.findByName("#room", 2, 2));
    assertEquals(fallback, buffers.findByName("#room", 0, -1));
  }

  @Test
  void namesAreTrimmedAndCaseInsensitiveAndTypesUseBitMasks() {
    var combined = new BufferInfoValue(11, 1, 6, -1, " #Room ");
    buffers.merge(combined);
    assertEquals(combined, buffers.findByName(" #ROOM ", 4, 1));
    assertNull(buffers.findByName("#other", 2, 1));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void blankNamesDoNotResolveEvenIfTheCatalogContainsAStatusBuffer(String name) {
    buffers.merge(new BufferInfoValue(1, 1, 1, -1, ""));
    assertNull(buffers.findByName(name, 1, 1));
  }

  @Test
  void initialLoadRetainsTheNativeMapKeysAndReplacesRatherThanMerges() {
    var known = new BufferInfoValue(11, 1, 2, 7, "#known");
    buffers.merge(known);
    var replacement = new BufferInfoValue(11, -1, 0, -1, "");
    buffers.loadInitial(Map.of(11, replacement, 99, known));
    assertSame(replacement, buffers.get(11));
    assertSame(known, buffers.get(99));
  }

  @Test
  void valuesAreAReadOnlyLiveViewThroughInsertionRemovalAndReset() {
    var values = buffers.values();
    var known = new BufferInfoValue(11, 1, 2, -1, "#room");
    buffers.merge(known);
    assertEquals(List.of(known), List.copyOf(values));
    assertThrows(UnsupportedOperationException.class, () -> values.remove(known));
    assertThrows(UnsupportedOperationException.class, values::clear);
    buffers.remove(11);
    buffers.remove(11);
    assertTrue(values.isEmpty());
    buffers.loadInitial(Map.of(11, known));
    buffers.clear();
    buffers.clear();
    assertTrue(values.isEmpty());
    buffers.merge(known);
    assertTrue(values.contains(known));
  }

  @Test
  void forgettingANetworkCleansUpEveryMatchingMapKeyAndKeepsOtherNetworks() {
    var first = new BufferInfoValue(11, 1_000, 2, -1, "#one");
    var second = new BufferInfoValue(12, 1_000, 2, -1, "#two");
    var other = new BufferInfoValue(13, 2_000, 2, -1, "#other");
    buffers.loadInitial(Map.of(11, first, 99, second, 13, other));
    var discarded = new ArrayList<Integer>();
    buffers.forgetNetwork(1_000, discarded::add);
    assertEquals(new HashSet<>(List.of(11, 99)), new HashSet<>(discarded));
    assertEquals(List.of(other), List.copyOf(buffers.values()));
    buffers.forgetNetwork(1_000, discarded::add);
    assertEquals(2, discarded.size());
  }

  @Test
  void networkRemovalRetainsMetadataReassignedAfterTheSnapshotAndStillRunsCleanup() {
    buffers.merge(new BufferInfoValue(11, 1, 2, -1, "#one"));
    buffers.merge(new BufferInfoValue(12, 1, 2, -1, "#two"));
    var discarded = new ArrayList<Integer>();
    buffers.forgetNetwork(
        1,
        id -> {
          discarded.add(id);
          if (discarded.size() == 1) {
            assertNull(buffers.get(id));
            int otherId = id == 11 ? 12 : 11;
            buffers.merge(new BufferInfoValue(otherId, 2, 2, -1, "#reassigned"));
          } else {
            assertEquals(2, buffers.get(id).networkId());
          }
        });
    assertEquals(2, discarded.size());
    assertEquals(
        List.of(new BufferInfoValue(discarded.get(1), 2, 2, -1, "#reassigned")),
        List.copyOf(buffers.values()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void bothUpdatePathsRemainBoundedDuringRepeatedInsertion(boolean sync) {
    var limited = new QuasselCoreBufferCatalog(3);
    for (int id = 0; id < 100; id++) {
      update(limited, new BufferInfoValue(id, 1, 2, -1, "#room" + id), sync);
      assertTrue(limited.values().size() <= 3);
    }
    assertEquals(3, limited.values().size());
  }

  @Test
  void initialLoadsAreBoundedAndDefaultCapacityRemains8192() {
    Map<Integer, BufferInfoValue> initial = new LinkedHashMap<>();
    for (int id = 0; id < 9_000; id++)
      initial.put(id, new BufferInfoValue(id, 1, 2, -1, "#room" + id));
    buffers.loadInitial(initial);
    assertEquals(8_192, buffers.values().size());
    var limited = new QuasselCoreBufferCatalog(3);
    limited.loadInitial(initial);
    assertEquals(3, limited.values().size());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void capacityMustBePositive(int capacity) {
    assertThrows(IllegalArgumentException.class, () -> new QuasselCoreBufferCatalog(capacity));
  }

  private static BufferInfoValue update(
      QuasselCoreBufferCatalog catalog, BufferInfoValue incoming, boolean sync) {
    return sync ? catalog.merge(incoming) : catalog.resolve(incoming);
  }
}
