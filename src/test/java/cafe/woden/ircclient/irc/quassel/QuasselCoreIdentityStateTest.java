package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.UserTypeValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class QuasselCoreIdentityStateTest {
  private final List<String> observations = new ArrayList<>();
  private final QuasselCoreIdentityState identities =
      new QuasselCoreIdentityState("core", 8, observations::add);

  @Test
  void initDataUsesTypedIdentityIdBeforeObjectIdAndRetainsImmutableNormalizedState() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("identityId", new UserTypeValue("IdentityId", 9));
    payload.put("identityName", " Cafe ");
    payload.put(" nicks ", List.of("alice"));
    identities.handleSync("Identity/4", List.of(payload));
    payload.put("identityName", "changed");

    assertEquals(Set.of(9), identities.knownIds());
    assertEquals("Cafe", identities.names().get(9));
    assertEquals(List.of("alice"), identities.state(9).get("nicks"));
    assertEquals(" Cafe ", identities.state(9).get("identityName"));
    assertEquals(List.of("core"), observations);
    assertThrows(UnsupportedOperationException.class, () -> identities.state(9).put("nick", "bob"));
    assertThrows(UnsupportedOperationException.class, () -> identities.knownIds().clear());
    assertThrows(UnsupportedOperationException.class, () -> identities.names().clear());
    assertThrows(UnsupportedOperationException.class, () -> identities.stateIds().clear());
  }

  @Test
  void objectIdSuppliesMissingIdentityIdAndLaterSnapshotsReplaceEarlierState() {
    identities.handleSync(
        "Identity/4", List.of(Map.of("identityName", "Cafe", "nicks", List.of("alice"))));
    identities.handleSync("Identity/4", List.of(Map.of("identityName", "Updated")));

    assertEquals(Set.of(4), identities.knownIds());
    assertEquals(Map.of("identityName", "Updated"), identities.state(4));
    assertEquals("Updated", identities.names().get(4));
    assertEquals(List.of("core", "core"), observations);
  }

  @Test
  void nestedRpcCreationAndTypedDeletionUpdateAllIdentityCollections() {
    identities.handleRpc(
        "2identityCreated(Identity)",
        List.of(List.of(new UserTypeValue("Identity", identity(3, "Cafe")))));
    assertTrue(identities.isKnown(3));
    assertEquals("Cafe", identities.state(3).get("identityName"));

    identities.handleRpc(
        "2identityRemoved(IdentityId)", List.of(new UserTypeValue("IdentityId", 3)));
    assertFalse(identities.hasKnown());
    assertTrue(identities.names().isEmpty());
    assertTrue(identities.stateIds().isEmpty());
    assertEquals(List.of("core"), observations);
  }

  @Test
  void mapDeletionAndUnrelatedRpcPayloadsKeepExistingDispatchRules() {
    identities.handleRpc("2identityCreated(Identity)", List.of(identity(3, "Cafe")));
    identities.handleRpc("2networkCreated(NetworkId)", List.of(new UserTypeValue("IdentityId", 6)));
    identities.handleRpc("2identityCreated(IdentityId)", List.of(7));
    identities.handleRpc("2identityDeleted(Identity)", List.of(identity(3, "Cafe")));
    assertFalse(identities.hasKnown());
    assertTrue(identities.stateIds().isEmpty());
    assertEquals(List.of("core"), observations);
  }

  @Test
  void coreInfoDiscoversNestedIdentitiesWithoutInterpretingUnrelatedMetadata() {
    identities.handleCoreInfoSync(
        "global",
        "init",
        List.of(
            Map.of(
                "IDENTITIES",
                List.of(
                    Map.of("unrelated", "metadata"),
                    new UserTypeValue("Identity", identity(5, "Cafe")),
                    new UserTypeValue("IdentityId", 2)))));
    assertEquals(Set.of(2, 5), identities.knownIds());
    assertEquals(Set.of(5), identities.stateIds());
    assertEquals(2, identities.firstKnownId());
    assertEquals(List.of("core", "core"), observations);
  }

  @Test
  void unknownSyncSupportsObjectFallbackAndNestedIdentityWrappers() {
    identities.observeUnknownState(
        List.of(Map.of("awayNick", "alice_away")), 6, "IdentityManager", "6", "init");
    identities.observeUnknownState(
        new UserTypeValue("Identity", identity(2, "Cafe")),
        -1,
        "IdentityManager",
        "global",
        "init");
    assertEquals(Set.of(2, 6), identities.knownIds());
    assertEquals("alice_away", identities.state(6).get("awayNick"));
    assertEquals(2, identities.firstKnownId());
  }

  @Test
  void initialSessionIdentitiesUseCatalogIdsAndIdentityNameAliases() {
    identities.initialize(
        Map.of(
            3,
            Map.of("IDENTITYNAME", " Cafe "),
            2,
            Map.of("name", "Generic"),
            -1,
            Map.of("identityName", "Ignored")));
    assertEquals(Set.of(2, 3), identities.knownIds());
    assertEquals("Cafe", identities.names().get(3));
    assertEquals("", identities.names().get(2));
    assertEquals("Generic", identities.state(2).get("name"));
    assertEquals(2, identities.firstKnownId());
    assertEquals(2, observations.size());
  }

  @Test
  void nonpositiveIdsDoNotBecomeKnownOrPublishObservations() {
    identities.observe(-1, "Invalid");
    identities.observe(0, "Zero");
    identities.handleSync("0", List.of(Map.of("identityName", "Zero")));
    identities.handleSync("invalid", List.of(Map.of("identityName", "Invalid")));
    assertFalse(identities.hasKnown());
    assertEquals(-1, identities.firstKnownId());
    assertEquals(Set.of(0), identities.stateIds());
    assertTrue(observations.isEmpty());
  }

  @Test
  void repeatedObservationsNotifyWaitersAndBlankNamesPreserveKnownNames() {
    identities.observe(2, "Cafe");
    identities.observe(2, "Cafe");
    identities.observe(2, " ");
    assertEquals("Cafe", identities.names().get(2));
    assertEquals(List.of("core", "core", "core"), observations);
  }

  @Test
  void stateAndNamedMetadataStayCappedWhileKnownMembershipRemainsComplete() {
    for (int id = 1; id <= 50; id++) {
      identities.handleSync(Integer.toString(id), List.of(identity(id, "Identity " + id)));
    }
    assertEquals(8, identities.stateIds().size());
    assertEquals(8, identities.names().size());
    assertEquals(50, identities.knownIds().size());
    assertEquals(1, identities.firstKnownId());
  }

  @Test
  void clearReleasesIdentityStateAndSupportsSubsequentSessionObservations() {
    identities.initialize(Map.of(3, identity(3, "Cafe")));
    identities.clear();
    assertFalse(identities.hasKnown());
    assertTrue(identities.stateIds().isEmpty());
    assertTrue(identities.names().isEmpty());
    assertTrue(identities.state(3).isEmpty());
    identities.observe(5, "New session");
    assertEquals(Set.of(5), identities.knownIds());
  }

  @Test
  void observationTimingKeepsKnownMembershipVisibleBeforeSnapshotReplacement() {
    var owner = new AtomicReference<QuasselCoreIdentityState>();
    List<Map<String, Object>> statesWhenObserved = new ArrayList<>();
    QuasselCoreIdentityState state =
        new QuasselCoreIdentityState(
            "core",
            8,
            sid -> {
              assertEquals("core", sid);
              assertTrue(owner.get().isKnown(3));
              statesWhenObserved.add(owner.get().state(3));
            });
    owner.set(state);
    state.handleSync("3", List.of(identity(3, "First")));
    state.handleSync("3", List.of(identity(3, "Second")));
    assertEquals(List.of(Map.of(), identity(3, "First")), statesWhenObserved);
    assertEquals(identity(3, "Second"), state.state(3));
  }

  @Test
  void connectPreflightRequiresPositiveIdNameAndAtLeastOneUsableNick() {
    assertTrue(QuasselCoreIdentityState.looksUsable(identity(3, "Cafe")));
    assertTrue(
        QuasselCoreIdentityState.looksUsable(
            Map.of(
                "identityId",
                new UserTypeValue("IdentityId", 2),
                "name",
                "Cafe",
                "Nick",
                "alice")));
    assertFalse(
        QuasselCoreIdentityState.looksUsable(
            Map.of("identityId", 0, "identityName", "Cafe", "nick", "alice")));
    assertFalse(
        QuasselCoreIdentityState.looksUsable(
            Map.of("identityId", 3, "identityName", "Cafe", "nicks", List.of("", " "))));
    assertFalse(
        QuasselCoreIdentityState.looksUsable(Map.of("identityId", 3, "nicks", List.of("alice"))));
  }

  private static Map<String, Object> identity(int id, String name) {
    return Map.of("identityId", id, "identityName", name, "nicks", List.of("alice"));
  }
}
