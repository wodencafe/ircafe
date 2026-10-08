package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.UserTypeValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreNetworkSyncTranslatorTest {
  private final List<String> trace = new ArrayList<>();
  private final List<Map<?, ?>> snapshots = new ArrayList<>();
  private final List<Map<?, ?>> states = new ArrayList<>();
  private final QuasselCoreNetworkSyncTranslator translator =
      new QuasselCoreNetworkSyncTranslator(
          "core",
          (id, name) -> trace.add("network:" + id + ":" + name),
          (id, state) -> {
            trace.add("snapshot:" + id);
            snapshots.add(state);
          },
          (id, state) -> {
            trace.add("state:" + id);
            states.add(state);
          },
          (id, nick) -> trace.add("nick:" + id + ":" + nick));

  @Test
  void scalarNameUsesFirstParameterAndObservesNameBeforeSnapshot() {
    translator.handleProperty("network/7", "setNetworkName", List.of(" Example ", "ignored"));
    assertEquals(List.of("network:7:Example", "snapshot:7"), trace);
    assertEquals(List.of(Map.of("networkName", "Example")), snapshots);
    assertTrue(states.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "yes", "on", "1"})
  void scalarConnectedAcceptsBooleanAliasesWithoutFullStateObservation(String value) {
    translator.handleProperty("0", "setConnected", List.of(value));
    assertEquals(List.of("snapshot:0"), trace);
    assertEquals(List.of(Map.of("isConnected", true)), snapshots);
    assertTrue(states.isEmpty());
  }

  @Test
  void scalarConnectedAndConnectionStatePreserveFalseAndZero() {
    translator.handleProperty("7", "setConnected", List.of(false));
    translator.handleProperty("7", "setConnectionState", List.of(" 0 "));
    assertEquals(List.of("snapshot:7", "snapshot:7"), trace);
    assertEquals(List.of(Map.of("isConnected", false), Map.of("connectionState", 0)), snapshots);
  }

  @Test
  void scalarNickIsPassedToSessionObserverWithoutTrimmingOrStateObservation() {
    translator.handleProperty("7", "setMyNick", List.of(" next "));
    translator.handleProperty("7", "setMyNick", Arrays.asList((Object) null));
    assertEquals(List.of("nick:7: next ", "nick:7:"), trace);
    assertTrue(snapshots.isEmpty());
    assertTrue(states.isEmpty());
  }

  @Test
  void malformedScalarValuesAndMissingParametersHaveNoEffects() {
    translator.handleProperty("7", "setNetworkName", List.of(" "));
    translator.handleProperty("7", "setConnected", List.of("maybe"));
    translator.handleProperty("7", "setConnectionState", List.of(-1));
    translator.handleProperty("7", "setConnectionState", List.of("invalid"));
    translator.handleProperty("7", "setMyNick", null);
    translator.handleProperty("7", "setMyNick", List.of());
    assertTrue(trace.isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"global", "network/not-an-id", "-1"})
  void invalidObjectIdsDoNotApplyScalarProperties(String objectName) {
    translator.handleProperty(objectName, "setNetworkName", List.of("Example"));
    assertTrue(trace.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"setnetworkname", "SETNETWORKNAME", "setNetworkName(QString)", "sync"})
  void scalarSlotsRemainExactAndCaseSensitive(String slot) {
    translator.handleProperty("7", slot, List.of("Example"));
    assertTrue(trace.isEmpty());
  }

  @Test
  void directNetworkMapsObserveStateBeforeTrimmedNickAndIgnoreWrappedOrNestedMaps() {
    Map<String, Object> first =
        Map.of("networkId", 8, "networkName", " First ", "myNick", " next ");
    Map<String, Object> second = Map.of("name", "Second", "myNick", " ");
    translator.observeNetworkState(
        "network/7",
        List.of(first, List.of(first), new UserTypeValue("NetworkInfo", first), second));
    assertEquals(
        List.of("network:8:First", "state:8", "nick:8:next", "network:7:Second", "state:7"), trace);
    assertSame(first, states.getFirst());
    assertSame(second, states.getLast());
  }

  @Test
  void directNetworkInfoMapsObserveStateWithoutUpdatingNickOrTraversingNestedValues() {
    Map<String, Object> nested = Map.of("networkId", 8, "networkName", "Nested");
    Map<String, Object> direct =
        Map.of("networkName", "Info", "myNick", "ignored", "nested", nested);
    translator.observeNetworkInfo("7", List.of(direct, new UserTypeValue("NetworkInfo", nested)));
    assertEquals(List.of("network:7:Info", "state:7"), trace);
    assertSame(direct, states.getFirst());
  }

  @Test
  void directMapNameAndNickKeysRemainCaseSensitiveIncludingEmptyMaps() {
    translator.observeNetworkState(
        "7", List.of(Map.of("NetworkName", "ignored", "MyNick", "ignored")));
    translator.observeNetworkInfo("global", List.of(Map.of()));
    assertEquals(List.of("network:7:", "state:7", "network:-1:", "state:-1"), trace);
  }

  @Test
  void missingDirectAndFallbackPayloadsHaveNoEffects() {
    translator.observeNetworkState("7", null);
    translator.observeNetworkState("7", List.of());
    translator.observeNetworkInfo("7", null);
    translator.observeNetworkInfo("7", List.of());
    translator.observeUnknownState("Network", "7", "sync", null);
    translator.observeUnknownState("Network", "7", "sync", List.of());
    assertTrue(trace.isEmpty());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"Identity", "CoreInfo"})
  void unrelatedClassesDoNotTraverseFallbackMaps(String className) {
    translator.observeUnknownState(className, "7", "sync", List.of(Map.of("networkId", 8)));
    assertTrue(trace.isEmpty());
  }

  @Test
  void recursiveFallbackKeepsMapThenNestedEncounterOrderAndInheritsObjectId() {
    Map<String, Object> inherited = Map.of("arbitrary", "value");
    Map<String, Object> explicit =
        Map.of("networkId", 8, "NETWORKNAME", " Nested ", "myNick", "ignored");
    Map<String, Object> parent = new LinkedHashMap<>();
    parent.put("networkName", "Parent");
    parent.put("children", List.of(inherited, new UserTypeValue("NetworkInfo", explicit)));
    translator.observeUnknownState(" CUSTOMNETWORKSTATE ", "object/7", "sync", List.of(parent));
    assertEquals(
        List.of(
            "network:7:Parent", "state:7", "network:7:", "state:7", "network:8:Nested", "state:8"),
        trace);
    assertEquals(List.of(parent, inherited, explicit), states);
    assertSame(parent, states.getFirst());
  }

  @Test
  void fallbackRequiresNetworkEvidenceWhenObjectIdIsUnknownAndIgnoresEmptyMaps() {
    translator.observeUnknownState(
        "NetworkConfig",
        "global",
        "sync",
        List.of(
            Map.of(),
            Map.of("arbitrary", "value"),
            Map.of("NETWORKNAME", "Named"),
            Map.of("SERVERLIST", List.of()),
            Map.of("networkId", 0)));
    assertEquals(
        List.of("network:-1:Named", "state:-1", "network:-1:", "state:-1", "network:0:", "state:0"),
        trace);
  }

  @Test
  void flatKeyValueStatePrecedesNestedMapsWithoutUpdatingNick() {
    Map<String, Object> nested = Map.of("networkId", 8, "networkName", "Nested");
    translator.observeUnknownState(
        "Network",
        "7",
        "init",
        List.of(
            "networkName",
            "Flat",
            "myNick",
            "ignored",
            "isConnected",
            true,
            "identity",
            1,
            "capsEnabled",
            List.of("message-tags"),
            "nested",
            nested));
    assertEquals(List.of("network:7:Flat", "state:7", "network:8:Nested", "state:8"), trace);
    assertEquals("ignored", states.getFirst().get("myNick"));
    assertSame(nested, states.getLast());
  }

  @Test
  void shortKeyValuePayloadsAreNotMistakenForSnapshots() {
    translator.observeUnknownState(
        "Network", "7", "init", List.of("networkName", "Flat", "myNick", "ignored"));
    assertTrue(trace.isEmpty());
  }

  @Test
  void networkDirectAndRecursivePassesRemainSeparateAndRepeatTopLevelObservation() {
    Map<String, Object> state = Map.of("networkName", "Example", "myNick", "next");
    translator.observeNetworkState("7", List.of(state));
    translator.observeUnknownState("Network", "7", "sync", List.of(state));
    assertEquals(
        List.of("network:7:Example", "state:7", "nick:7:next", "network:7:Example", "state:7"),
        trace);
    assertSame(states.getFirst(), states.getLast());
  }
}
