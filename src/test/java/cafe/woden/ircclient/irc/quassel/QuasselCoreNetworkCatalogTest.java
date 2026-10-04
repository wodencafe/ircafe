package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.irc.quassel.QuasselCoreAuthHandshake.AuthResult;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.UserTypeValue;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkSummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class QuasselCoreNetworkCatalogTest {
  private final List<Integer> observedIdentities = new ArrayList<>();
  private final QuasselCoreNetworkCatalog networks =
      new QuasselCoreNetworkCatalog(8, observedIdentities::add);

  @Test
  void routingTokensHandleDuplicateNamesAndRepeatedObservations() {
    networks.observe(1, " Cafe Net ");
    networks.observe(2, "Cafe Net");
    networks.observe(2, "");
    assertEquals("Cafe Net", networks.displayNames().get(1));
    assertEquals("cafe-net", networks.token(1));
    assertEquals("cafe-net-2", networks.token(2));
    assertEquals(1, networks.idForToken("cafe-net"));
    assertEquals(2, networks.idForToken("cafe-net-2"));
    assertEquals(2, networks.resolve("CAFE-NET-2"));
  }

  @Test
  void renamedNetworksReleaseTheirOldRoutingToken() {
    networks.observe(4, "Old Name");
    networks.observe(4, "New Name");
    assertNull(networks.idForToken("old-name"));
    assertEquals(-1, networks.resolve("Old Name"));
    assertEquals(4, networks.resolve("New Name"));
    assertEquals(4, networks.resolve("NEW---NAME"));
    assertEquals("new-name", networks.token(4));
  }

  @Test
  void fallbackNamesAndTokensIncludeZeroAndIgnoreInvalidIds() {
    networks.observe(-1, "Invalid");
    networks.observe(0, "");
    networks.observe(7, "---");
    assertEquals(Map.of(0, "network-0", 7, "---"), networks.displayNames());
    assertEquals("network-0", networks.token(0));
    assertEquals("id-7", networks.token(7));
    assertEquals(7, networks.resolve("id-7"));
  }

  @Test
  void resolutionPreservesNumericIdsAndFindsNamesInStateOnlySnapshots() {
    networks.observeState(5, Map.of("name", "State Only"));
    assertEquals(99, networks.resolve("Network/99"));
    assertEquals(0, networks.resolve("0"));
    assertEquals(5, networks.resolve("state only"));
    assertEquals(-1, networks.resolve("missing"));
    assertEquals(
        "network id/name is required",
        assertThrows(IllegalArgumentException.class, () -> networks.resolve(" ")).getMessage());
  }

  @Test
  void knownIdsCombineAuthenticationBuffersObservedNamesAndState() {
    networks.observe(2, "Observed");
    networks.observeState(4, Map.of("connected", true));
    var auth = auth(7, List.of(7, 3, -1));
    var buffers = List.of(buffer(9), buffer(7), buffer(-1));
    var known = networks.knownIds(auth, buffers);
    assertEquals(Set.of(7, 3, 9, 2, 4), known);
    assertEquals(List.of(7, 3, 9), new ArrayList<>(known).subList(0, 3));
    known.clear();
    assertEquals(5, networks.knownIds(auth, buffers).size());
  }

  @Test
  void removedNetworksStayHiddenDespiteStaleAuthenticatedIdsAndBuffers() {
    var auth = auth(3, List.of(3));
    var buffers = List.of(buffer(3));
    networks.observe(3, "Cafe");
    networks.observeState(3, Map.of("identity", 2, "connected", true));
    networks.forget(3);
    assertTrue(networks.knownIds(auth, buffers).isEmpty());
    assertTrue(networks.snapshot(auth, buffers).isEmpty());
    assertTrue(networks.displayNames().isEmpty());
    assertTrue(networks.stateIds().isEmpty());
    assertTrue(networks.tokenIds().isEmpty());
    assertNull(networks.idForToken("cafe"));
    assertFalse(networks.isObservedName("Cafe"));

    networks.observe(3, "Recreated");
    assertEquals(Set.of(3), networks.knownIds(auth, buffers));
    assertEquals(3, networks.resolve("recreated"));
  }

  @Test
  void preferredNetworkUsesAuthenticationThenFallsBackToRemainingKnownIds() {
    var auth = auth(9, List.of(3, 9));
    var buffers = List.of(buffer(7));
    assertEquals(9, networks.primaryNetworkId(auth, buffers));
    assertEquals(9, networks.firstKnownNetworkId(auth, buffers));
    networks.forget(9);
    assertEquals(3, networks.primaryNetworkId(auth, buffers));
    assertEquals(3, networks.firstKnownNetworkId(auth, buffers));
    networks.forget(3);
    assertEquals(7, networks.primaryNetworkId(auth, buffers));
    assertEquals(7, networks.firstKnownNetworkId(auth, buffers));
    assertEquals(-1, networks.primaryNetworkId(null, List.of()));
    assertEquals(-1, networks.firstKnownNetworkId(null, List.of()));
  }

  @Test
  void partialStateUpdatesMergeWithoutMutatingEarlierSnapshots() {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put(" networkName ", "Cafe");
    first.put("identity", new UserTypeValue("IdentityId", 2));
    first.put("connected", true);
    var stored = networks.observeState(3, first);
    first.put("connected", false);
    var updated = networks.observeState(3, Map.of("connected", false));
    assertEquals(true, stored.get("connected"));
    assertEquals(false, updated.get("connected"));
    assertEquals("Cafe", updated.get("networkName"));
    assertEquals(List.of(2, 2), observedIdentities);
    assertThrows(UnsupportedOperationException.class, () -> updated.put("name", "changed"));
  }

  @Test
  void invalidStatePayloadsDoNotRetainStateOrObserveIdentities() {
    assertNull(networks.observeState(-1, Map.of("identity", 2)));
    assertNull(networks.observeState(3, null));
    assertNull(networks.observeState(3, Map.of()));
    assertNull(networks.observeState(3, Map.of(" ", 2)));
    networks.observeState(3, Map.of("identity", 0));
    assertEquals(Set.of(3), networks.stateIds());
    assertTrue(observedIdentities.isEmpty());
  }

  @Test
  void snapshotsAreSortedImmutableAndKeepTheirOriginalState() {
    networks.observe(9, "Observed Name");
    networks.observeState(
        9,
        Map.of(
            "networkName",
            "Core Name",
            "identity",
            2,
            "connected",
            true,
            "enabled",
            false,
            "ServerList",
            List.of(
                new UserTypeValue(
                    "Network::Server",
                    Map.of("Host", "irc.example", "Port", 7000, "UseSSL", true)))));
    var snapshot = networks.snapshot(auth(9, List.of(9, 3)), List.of());
    assertEquals(
        List.of(3, 9), snapshot.stream().map(QuasselCoreNetworkSummary::networkId).toList());
    var summary = snapshot.get(1);
    assertEquals("Core Name", summary.networkName());
    assertTrue(summary.connected());
    assertFalse(summary.enabled());
    assertEquals(2, summary.identityId());
    assertEquals("irc.example", summary.serverHost());
    assertEquals(7000, summary.serverPort());
    assertTrue(summary.useTls());
    assertEquals("network-3", snapshot.getFirst().networkName());
    assertTrue(snapshot.getFirst().enabled());
    networks.observeState(9, Map.of("connected", false));
    assertEquals(true, summary.rawState().get("connected"));
    assertThrows(UnsupportedOperationException.class, snapshot::clear);
    assertThrows(UnsupportedOperationException.class, () -> summary.rawState().clear());
  }

  @Test
  void snapshotReadsNeitherCreateRoutingMetadataNorPublishIdentityObservations() {
    var auth = auth(9, List.of(9));
    for (int i = 0; i < 3; i++) {
      assertEquals(1, networks.snapshot(auth, List.of()).size());
    }
    assertTrue(networks.displayNames().isEmpty());
    assertEquals("", networks.token(9));
    assertTrue(observedIdentities.isEmpty());
    networks.forget(9);
    assertTrue(networks.snapshot(auth, List.of()).isEmpty());
    assertTrue(networks.knownIds(auth, List.of()).isEmpty());
  }

  @Test
  void metadataMapsStayCappedAndTheirReadViewsCannotBeModified() {
    for (int id = 1; id <= 50; id++) {
      networks.observe(id, "Network " + id);
      networks.observeState(id, Map.of("networkName", "Network " + id));
    }
    assertEquals(8, networks.displayNames().size());
    assertEquals(8, networks.tokenIds().size());
    assertEquals(8, networks.stateIds().size());
    int indexed = 0;
    for (int id = 1; id <= 50; id++) {
      if (networks.idForToken("network-" + id) != null) indexed++;
    }
    assertEquals(8, indexed);
    assertThrows(UnsupportedOperationException.class, () -> networks.displayNames().clear());
    assertThrows(UnsupportedOperationException.class, () -> networks.tokenIds().clear());
    assertThrows(UnsupportedOperationException.class, () -> networks.stateIds().clear());
    assertThrows(UnsupportedOperationException.class, () -> networks.states().clear());
  }

  @Test
  void identityObserverSeesStoredStateBeforeCachePruning() {
    var owner = new AtomicReference<QuasselCoreNetworkCatalog>();
    List<Set<Integer>> idsWhenObserved = new ArrayList<>();
    var catalog =
        new QuasselCoreNetworkCatalog(
            1,
            identityId -> {
              assertEquals(2, identityId);
              assertEquals(2, owner.get().state(9).get("identity"));
              idsWhenObserved.add(Set.copyOf(owner.get().stateIds()));
            });
    owner.set(catalog);
    catalog.observeState(3, Map.of("networkName", "First"));
    catalog.observeState(9, Map.of("identity", 2));
    assertEquals(List.of(Set.of(3, 9)), idsWhenObserved);
    assertEquals(1, catalog.stateIds().size());
  }

  @Test
  void metadataCleanupKeepsRemovalTrackingUntilTheNextAuthenticationReset() {
    var auth = auth(9, List.of(9));
    networks.observe(3, "Cafe");
    networks.observeState(3, Map.of("networkName", "Core Name"));
    assertTrue(networks.isObservedName(" cafe "));
    assertTrue(networks.isObservedName("CORE NAME"));
    assertFalse(networks.isObservedName(" "));
    networks.forget(9);
    networks.clearMetadata();
    assertTrue(networks.displayNames().isEmpty());
    assertTrue(networks.stateIds().isEmpty());
    assertTrue(networks.tokenIds().isEmpty());
    assertTrue(networks.knownIds(auth, List.of()).isEmpty());
    networks.reset();
    assertEquals(Set.of(9), networks.knownIds(auth, List.of()));
  }

  @Test
  void pendingCreateNamesAreClaimedInOrderAndBlankRequestsAreIgnored() {
    var catalog = new QuasselCoreNetworkCatalog(8, observedIdentities::add, () -> 1_000L);
    catalog.rememberCreatedName(null);
    catalog.rememberCreatedName(" ");
    catalog.rememberCreatedName(" First ");
    catalog.rememberCreatedName("Second");
    assertEquals("First", catalog.claimCreatedName());
    assertEquals("Second", catalog.claimCreatedName());
    assertEquals("", catalog.claimCreatedName());
    assertTrue(observedIdentities.isEmpty());
  }

  @Test
  void pendingCreateNamesDropOldestRequestsWhenTheQueueReachesItsLimit() {
    var catalog = new QuasselCoreNetworkCatalog(8, ignored -> {}, () -> 1_000L);
    for (int i = 0; i < 40; i++) catalog.rememberCreatedName("Network " + i);
    List<String> claimed = new ArrayList<>();
    for (String name = catalog.claimCreatedName();
        !name.isEmpty();
        name = catalog.claimCreatedName()) {
      claimed.add(name);
    }
    assertEquals(32, claimed.size());
    assertEquals("Network 8", claimed.getFirst());
    assertEquals("Network 39", claimed.getLast());
  }

  @Test
  void pendingCreateNamesExpireAfterTwoMinutesWhileTheExactBoundaryRemainsValid() {
    AtomicLong clock = new AtomicLong(1_000L);
    var catalog = new QuasselCoreNetworkCatalog(8, ignored -> {}, clock::get);
    catalog.rememberCreatedName("Boundary");
    clock.addAndGet(TimeUnit.MINUTES.toMillis(2));
    assertEquals("Boundary", catalog.claimCreatedName());
    catalog.rememberCreatedName("Expired");
    clock.addAndGet(TimeUnit.MINUTES.toMillis(2) + 1);
    assertEquals("", catalog.claimCreatedName());
  }

  @Test
  void aFreshCreateRequestPrunesExpiredNamesBeforeTheyCanBeAttachedToItsReply() {
    AtomicLong clock = new AtomicLong(1_000L);
    var catalog = new QuasselCoreNetworkCatalog(8, ignored -> {}, clock::get);
    catalog.rememberCreatedName("Stale");
    clock.addAndGet(TimeUnit.MINUTES.toMillis(2) + 1);
    catalog.rememberCreatedName("Fresh");
    assertEquals("Fresh", catalog.claimCreatedName());
    assertEquals("", catalog.claimCreatedName());
  }

  @Test
  void sessionCleanupAndAuthenticationResetReleasePendingCreationNames() {
    networks.rememberCreatedName("Disconnected request");
    networks.clearMetadata();
    assertEquals("", networks.claimCreatedName());
    networks.rememberCreatedName("Old session request");
    networks.reset();
    assertEquals("", networks.claimCreatedName());
  }

  private static AuthResult auth(int primary, List<Integer> ids) {
    return new AuthResult("core", primary, ids, Map.of());
  }

  private static BufferInfoValue buffer(int networkId) {
    return new BufferInfoValue(networkId, networkId, 2, -1, "#cafe");
  }
}
