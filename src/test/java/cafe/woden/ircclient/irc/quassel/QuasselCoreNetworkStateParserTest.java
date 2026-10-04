package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.UserTypeValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreNetworkStateParser.NetworkServerEndpoint;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuasselCoreNetworkStateParserTest {
  @Test
  void explicitConnectionFlagsTakePrecedenceOverConnectionState() {
    assertFalse(
        QuasselCoreNetworkStateParser.parseNetworkConnected(
            Map.of("ISCONNECTED", false, "connectionState", 2)));
    assertTrue(QuasselCoreNetworkStateParser.parseNetworkConnected(Map.of("connected", "yes")));
    assertTrue(QuasselCoreNetworkStateParser.parseNetworkConnected(Map.of("state", 1)));
    assertFalse(QuasselCoreNetworkStateParser.parseNetworkConnected(null));
  }

  @Test
  void enabledStateUsesExistingAliasesAndDefaultsToEnabled() {
    assertFalse(
        QuasselCoreNetworkStateParser.parseNetworkEnabled(
            Map.of("isEnabled", false, "enabled", true)));
    assertFalse(QuasselCoreNetworkStateParser.parseNetworkEnabled(Map.of("ISINITIALIZED", 0)));
    assertTrue(QuasselCoreNetworkStateParser.parseNetworkEnabled(Map.of()));
  }

  @Test
  void identityReferencesAcceptQtUserTypesAndPositiveNumericIds() {
    assertEquals(
        9,
        QuasselCoreNetworkStateParser.parseNetworkIdentityId(
            Map.of("Identity", new UserTypeValue("IdentityId", 9))));
    assertEquals(
        8, QuasselCoreNetworkStateParser.parseNetworkIdentityId(Map.of("identityId", "8")));
    assertEquals(-1, QuasselCoreNetworkStateParser.parseNetworkIdentityId(Map.of("identity", 0)));
  }

  @Test
  void firstUsableServerIsSelectedFromTypedAndUntypedEntries() {
    var state =
        Map.of(
            "ServerList",
            List.of(
                Map.of("Port", 6667),
                new UserTypeValue("Network::Server", Map.of("Host", "irc.example", "UseSSL", true)),
                Map.of("host", "later.example", "port", 7000)));
    assertEquals(
        new NetworkServerEndpoint("irc.example", 6697, true),
        QuasselCoreNetworkStateParser.parsePrimaryNetworkServer(state));
  }

  @Test
  void serverListAndDirectSnapshotAliasesPreserveConfiguredPorts() {
    assertEquals(
        new NetworkServerEndpoint("irc.example", 7000, true),
        QuasselCoreNetworkStateParser.parsePrimaryNetworkServer(
            Map.of(
                "servers",
                new UserTypeValue(
                    "Network::Server",
                    Map.of("SERVER", "irc.example", "PORT", "7000", "SSL", "yes")))));
    assertEquals(
        new NetworkServerEndpoint("plain.example", 6667, false),
        QuasselCoreNetworkStateParser.parsePrimaryNetworkServer(
            Map.of("hostname", "plain.example")));
    assertEquals(
        new NetworkServerEndpoint("", 0, false),
        QuasselCoreNetworkStateParser.parsePrimaryNetworkServer(Map.of()));
  }

  @Test
  void networkIdAliasesKeepExistingPrecedenceAndUseObjectFallbackWhenMissing() {
    assertEquals(
        0,
        QuasselCoreNetworkStateParser.networkIdFromStateMap(
            Map.of("networkId", 0, "network", 2, "id", 3), 9));
    assertEquals(
        2, QuasselCoreNetworkStateParser.networkIdFromStateMap(Map.of("network", "2", "id", 3), 9));
    assertEquals(
        9, QuasselCoreNetworkStateParser.networkIdFromStateMap(Map.of("name", "network"), 9));
  }

  @ParameterizedTest
  @CsvSource({"1,1", "Network/12,12", "Network/not-a-number,-1", "Network/,-1"})
  void objectNamesSupportNumericLeafIds(String name, int id) {
    assertEquals(id, QuasselCoreNetworkStateParser.parseNetworkId(name));
  }

  @Test
  void flatQtKeyValuePayloadsDecodeByteArrayKeysAndRequireEnoughPairs() {
    List<Object> values =
        List.of(
            "NetworkName".getBytes(StandardCharsets.UTF_8),
            "cafe",
            "identity",
            1,
            "myNick",
            "alice",
            "ServerList",
            List.of(),
            "connected",
            false,
            "isEnabled",
            true);
    var flattened = QuasselCoreNetworkStateParser.flattenNetworkStateFromKeyValueParams(values);
    assertEquals("cafe", flattened.get("NetworkName"));
    assertEquals(false, flattened.get("connected"));
    assertThrows(UnsupportedOperationException.class, () -> flattened.put("extra", true));
    assertTrue(
        QuasselCoreNetworkStateParser.flattenNetworkStateFromKeyValueParams(values.subList(0, 10))
            .isEmpty());
    assertTrue(
        QuasselCoreNetworkStateParser.flattenNetworkStateFromKeyValueParams(values.subList(0, 11))
            .isEmpty());
  }

  @Test
  void nestedStateCandidatesPreserveDepthFirstObservationOrder() {
    Map<String, Object> child = Map.of("networkId", 2);
    Map<String, Object> parent =
        Map.of("networkId", 1, "nested", new UserTypeValue("NetworkInfo", child));
    List<Map<?, ?>> candidates = new ArrayList<>();
    QuasselCoreNetworkStateParser.collectPotentialNetworkStateMaps(List.of(parent), candidates);
    assertEquals(List.of(parent, child), candidates);
  }

  @Test
  void connectPreflightAcceptsAHostIdentityOrNameAndRejectsEmptySnapshots() {
    assertTrue(
        QuasselCoreNetworkStateParser.networkStateLooksUsableForConnect(
            Map.of("host", "irc.example")));
    assertTrue(
        QuasselCoreNetworkStateParser.networkStateLooksUsableForConnect(Map.of("identity", 1)));
    assertTrue(
        QuasselCoreNetworkStateParser.networkStateLooksUsableForConnect(
            Map.of("networkName", "cafe")));
    assertFalse(QuasselCoreNetworkStateParser.networkStateLooksUsableForConnect(Map.of()));
    assertFalse(
        QuasselCoreNetworkStateParser.networkStateLooksUsableForConnect(Map.of("identity", 0)));
  }
}
