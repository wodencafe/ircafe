package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.UserTypeValue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreNetworkLifecycleTranslatorTest {
  private final Queue<String> names = new ArrayDeque<>(List.of("First", "Second", "Third"));
  private final List<String> trace = new ArrayList<>();
  private final List<Map<?, ?>> states = new ArrayList<>();
  private final QuasselCoreNetworkLifecycleTranslator translator = newTranslator();

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "2identityCreated(IdentityId)", "2displayMsg(Message)"})
  void unrelatedAndBlankSlotsDoNotObserveOrClaimNames(String slot) {
    translator.handleRpc(slot, List.of(new UserTypeValue("NetworkId", 7)));
    assertTrue(trace.isEmpty());
    assertEquals(3, names.size());
  }

  @Test
  void absentParametersAndInvalidScalarsHaveNoEffects() {
    translator.handleRpc("networkCreated", null);
    translator.handleRpc("networkCreated", List.of());
    translator.handleRpc(
        "networkCreated", Arrays.asList(null, -1, "bad", "", true, new byte[] {7}));
    assertTrue(trace.isEmpty());
    assertEquals(3, names.size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"2networkCreated(NetworkId)", " NETWORKADDED ", "prefixNetworkCreateSuffix"})
  void createLikeSlotsClaimNamesForBareIdsUsingCaseInsensitiveSubstringMatching(String slot) {
    translator.handleRpc(slot, List.of(7));
    assertEquals(List.of("claim:First", "observe:7:First"), trace);
  }

  @Test
  void typedCreatedIdClaimsExactlyOnceWithoutRecursingIntoItsValue() {
    translator.handleRpc("networkCreated", List.of(new UserTypeValue(" NetworkId ", " 7 ")));
    assertEquals(List.of("claim:First", "observe:7:First"), trace);
    assertEquals(2, names.size());
  }

  @Test
  void normalNetworkSlotsObserveBareAndTypedIdsWithoutClaimingNames() {
    translator.handleRpc("networkUpdated", List.of(7, new UserTypeValue("NetworkId", 8)));
    assertEquals(List.of("observe:7:", "observe:8:"), trace);
    assertEquals(3, names.size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "networkRemoved",
        " NETWORKDELETED ",
        "networkRemovedAndCreated",
        "networkAddedThenDeleted"
      })
  void removalTakesPrecedenceAndDoesNotClaimNames(String slot) {
    translator.handleRpc(slot, List.of(7, new UserTypeValue("NetworkId", 8)));
    assertEquals(List.of("forget:7", "forget:8"), trace);
    assertEquals(3, names.size());
  }

  @Test
  void nestedListsKeepEncounterOrderAndMapsDoNotConsumeCreatedNames() {
    Map<String, Object> state = Map.of("networkId", 8, "networkName", "Named");
    translator.handleRpc(
        "networkCreated",
        List.of(List.of(new UserTypeValue("NetworkId", 7), List.of(state, -1, "9")), 0));
    assertEquals(
        List.of(
            "claim:First",
            "observe:7:First",
            "observe:8:Named",
            "state:8",
            "claim:Second",
            "observe:9:Second",
            "claim:Third",
            "observe:0:Third"),
        trace);
    assertSame(state, states.getFirst());
  }

  @Test
  void unknownWrappersRecursivelyExposeTheirValues() {
    translator.handleRpc(
        "networkUpdated",
        List.of(
            new UserTypeValue("Unknown", List.of(new UserTypeValue("Another", 7), "8")),
            new UserTypeValue(null, 9)));
    assertEquals(List.of("observe:7:", "observe:8:", "observe:9:"), trace);
  }

  @Test
  void invalidTypedIdsAreDiscardedIncludingWrappedMapsAndLists() {
    translator.handleRpc(
        "networkCreated",
        Arrays.asList(
            new UserTypeValue("NetworkId", -1),
            new UserTypeValue("NetworkId", "bad"),
            new UserTypeValue("NetworkId", null),
            new UserTypeValue("NetworkId", List.of(7)),
            new UserTypeValue("NetworkId", Map.of("networkId", 8))));
    assertTrue(trace.isEmpty());
    assertEquals(3, names.size());
  }

  @Test
  void userTypeNamesRemainCaseSensitive() {
    Map<String, Object> state = Map.of("networkId", 7);
    translator.handleRpc("networkCreated", List.of(new UserTypeValue("networkid", state)));
    assertEquals(List.of("observe:7:", "state:7"), trace);
    assertEquals(3, names.size());
  }

  @Test
  void networkInfoWrapperPassesOriginalStateAfterNetworkObservation() {
    Map<String, Object> state =
        Map.of("networkId", 7, "networkName", "Example", "capsEnabled", List.of("message-tags"));
    translator.handleRpc("networkCreated", List.of(new UserTypeValue("NetworkInfo", state)));
    assertEquals(List.of("observe:7:Example", "state:7"), trace);
    assertSame(state, states.getFirst());
    assertEquals(3, names.size());
  }

  @Test
  void networkInfoWrapperWithScalarValueFallsBackToIdObservation() {
    translator.handleRpc("networkCreated", List.of(new UserTypeValue("NetworkInfo", 7)));
    assertEquals(List.of("claim:First", "observe:7:First"), trace);
  }

  @ParameterizedTest
  @ValueSource(strings = {"networkName", "NETWORKNAME", "name"})
  void mapNameKeysAreCaseInsensitiveAndValuesAreTrimmed(String key) {
    translator.handleRpc("networkCreated", List.of(Map.of("networkId", 7, key, " Example ")));
    assertEquals(List.of("observe:7:Example", "state:7"), trace);
    assertEquals(3, names.size());
  }

  @Test
  void mapNamePriorityUsesFirstNonblankValue() {
    translator.handleRpc(
        "networkUpdated",
        List.of(Map.of("networkId", 7, "networkName", "Preferred", "name", "Other")));
    translator.handleRpc(
        "networkUpdated", List.of(Map.of("networkId", 8, "networkName", " ", "name", "Fallback")));
    assertEquals(List.of("observe:7:Preferred", "state:7", "observe:8:Fallback", "state:8"), trace);
  }

  @ParameterizedTest
  @ValueSource(strings = {"networkId", "network", "id"})
  void recognizedMapIdKeysAcceptNumericText(String key) {
    translator.handleRpc("networkUpdated", List.of(Map.of(key, " 7 ")));
    assertEquals(List.of("observe:7:", "state:7"), trace);
  }

  @Test
  void mapIdPrioritySkipsInvalidValuesAndAcceptsZero() {
    translator.handleRpc("networkUpdated", List.of(Map.of("networkId", 0, "network", 8, "id", 9)));
    translator.handleRpc("networkUpdated", List.of(Map.of("networkId", -1, "network", 8, "id", 9)));
    assertEquals(List.of("observe:0:", "state:0", "observe:8:", "state:8"), trace);
  }

  @Test
  void unrecognizedMapIdStillReachesStateObserverWithFallbackId() {
    Map<String, Object> state =
        Map.of("NetworkId", new UserTypeValue("NetworkId", 7), "NETWORKNAME", "Example");
    translator.handleRpc("networkUpdated", List.of(state));
    assertEquals(List.of("observe:-1:Example", "state:-1"), trace);
    assertSame(state, states.getFirst());
  }

  @Test
  void mapsAreOpaqueSnapshotsRatherThanRecursiveContainers() {
    Map<String, Object> state =
        Map.of("networkId", 7, "nested", Map.of("networkId", 8), "values", List.of(9));
    translator.handleRpc("networkUpdated", List.of(state));
    assertEquals(List.of("observe:7:", "state:7"), trace);
    assertSame(state, states.getFirst());
  }

  @Test
  void validRemovalMapOnlyForgetsItsIdWithoutObservingStateOrClaimingName() {
    translator.handleRpc(
        "networkRemoved",
        List.of(new UserTypeValue("NetworkInfo", Map.of("networkId", 7, "name", "Example"))));
    assertEquals(List.of("forget:7"), trace);
    assertTrue(states.isEmpty());
    assertEquals(3, names.size());
  }

  @Test
  void invalidRemovalMapPreservesFallbackObservationBehavior() {
    translator.handleRpc("networkRemoved", List.of(Map.of("name", "Example")));
    assertEquals(List.of("observe:-1:Example", "state:-1"), trace);
    assertEquals(3, names.size());
  }

  @Test
  void emptyStateMapStillReachesObservationCallbacks() {
    translator.handleRpc("networkCreated", List.of(Map.of()));
    assertEquals(List.of("observe:-1:", "state:-1"), trace);
    assertEquals(3, names.size());
  }

  @Test
  void numberValuesUseExistingIntegerConversionAndZeroRemainsValid() {
    translator.handleRpc("networkUpdated", List.of(0, 7L, 8.9d, " 9 "));
    assertEquals(List.of("observe:0:", "observe:7:", "observe:8:", "observe:9:"), trace);
  }

  @Test
  void exhaustedNameQueueStillObservesCreatedIds() {
    names.clear();
    translator.handleRpc("networkCreated", List.of(7));
    assertEquals(List.of("claim:", "observe:7:"), trace);
  }

  @Test
  void nameQueueRemainsOwnedBySessionAcrossTranslatorInstances() {
    translator.handleRpc("networkCreated", List.of(7));
    newTranslator().handleRpc("networkCreated", List.of(8));
    assertEquals(
        List.of("claim:First", "observe:7:First", "claim:Second", "observe:8:Second"), trace);
  }

  @Test
  void duplicateCreatedIdsRetainExistingNameConsumptionBehavior() {
    translator.handleRpc("networkCreated", List.of(7, 7));
    assertEquals(
        List.of("claim:First", "observe:7:First", "claim:Second", "observe:7:Second"), trace);
  }

  @Test
  void observationFailurePropagatesAndStopsStateAndRemainingParameters() {
    var failure = new IllegalStateException("observation failed");
    var failing =
        new QuasselCoreNetworkLifecycleTranslator(
            "core",
            () -> "",
            (id, name) -> {
              throw failure;
            },
            id -> trace.add("forget"),
            (id, state) -> trace.add("state"));
    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class,
            () -> failing.handleRpc("networkUpdated", List.of(Map.of("networkId", 7), 8))));
    assertTrue(trace.isEmpty());
  }

  private QuasselCoreNetworkLifecycleTranslator newTranslator() {
    return new QuasselCoreNetworkLifecycleTranslator(
        "core",
        () -> {
          String name = names.isEmpty() ? "" : names.remove();
          trace.add("claim:" + name);
          return name;
        },
        (id, name) -> trace.add("observe:" + id + ":" + name),
        id -> trace.add("forget:" + id),
        (id, state) -> {
          states.add(state);
          trace.add("state:" + id);
        });
  }
}
