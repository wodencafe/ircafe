package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest;
import io.reactivex.rxjava3.core.Completable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreNetworkConnectPreflightTest {
  private final QuasselCoreNetworkCatalog networks = new QuasselCoreNetworkCatalog(512, id -> {});
  private final QuasselCoreIdentityState identities =
      new QuasselCoreIdentityState("core", 512, id -> {});
  private final QuasselCoreObservationMediator observations =
      mock(QuasselCoreObservationMediator.class);
  private final IntSupplier replacement = mock(IntSupplier.class);
  private final List<String> calls = new ArrayList<>();
  private final List<QuasselCoreNetworkUpdateRequest> updates = new ArrayList<>();
  private final List<QuasselCoreNetworkCreateRequest> creates = new ArrayList<>();
  private BiConsumer<Long, BooleanSupplier> onWait = (timeout, condition) -> {};
  private String failingCommand;
  private final IOException failure = new IOException("transport failed");
  private final QuasselCoreNetworkConnectPreflight preflight =
      new QuasselCoreNetworkConnectPreflight(
          "core",
          networks,
          identities,
          observations,
          replacement,
          () -> Set.of(1),
          new QuasselCoreNetworkConnectPreflight.Commands() {
            @Override
            public void requestNetworkInitState(int id) throws Exception {
              command("init:" + id);
            }

            @Override
            public void updateNetwork(int id, QuasselCoreNetworkUpdateRequest request)
                throws Exception {
              command("update:" + id);
              updates.add(request);
            }

            @Override
            public void createNetwork(int identityId, QuasselCoreNetworkCreateRequest request)
                throws Exception {
              command("create:" + identityId);
              creates.add(request);
            }
          });

  @BeforeEach
  void setupObservation() {
    when(replacement.getAsInt()).thenReturn(7);
    when(observations.whenNetwork(eq("core"), anyLong(), any()))
        .thenAnswer(
            invocation -> {
              long timeout = invocation.getArgument(1);
              BooleanSupplier condition = invocation.getArgument(2);
              return Completable.fromAction(
                  () -> {
                    calls.add("wait:" + timeout);
                    onWait.accept(timeout, condition);
                  });
            });
  }

  @Test
  void usableKnownIdentityConnectsWithoutRepairOrIdentityResolution() throws Exception {
    networkState(1, 7);
    usableIdentity();
    assertTrue(preflight.prepare(1));
    assertTrue(calls.isEmpty());
    verify(replacement, never()).getAsInt();
    verifyNoInteractions(observations);
  }

  static Stream<Arguments> unusableIdentities() {
    return Stream.of(
        Arguments.of(Map.of()),
        Arguments.of(Map.of("identityId", 7, "identityName", "", "nicks", List.of("nick"))),
        Arguments.of(Map.of("identityId", 7, "identityName", "name", "nicks", List.of())));
  }

  @ParameterizedTest
  @MethodSource("unusableIdentities")
  void unusableIdentityIsUpdatedEvenIfReplacementAlreadyMatchesSnapshot(
      Map<String, Object> identity) throws Exception {
    networkState(1, 7);
    identities.observe(7, "known");
    identities.handleSync("7", List.of(identity));
    assertTrue(preflight.prepare(1));
    assertEquals(List.of("update:1", "init:1"), calls);
    assertEquals(7, updates.getFirst().identityId());
    verifyNoInteractions(observations);
  }

  @Test
  void missingEndpointKeepsExistingConnectBehaviorWithoutRepair() throws Exception {
    networks.observeState(1, Map.of("networkName", "named", "identity", 0));
    assertTrue(preflight.prepare(1));
    assertTrue(calls.isEmpty());
    verify(replacement).getAsInt();
  }

  @Test
  void missingReplacementDoesNotUpdateOrFallback() throws Exception {
    networkState(1, 0);
    when(replacement.getAsInt()).thenReturn(-1);
    assertTrue(preflight.prepare(1));
    assertTrue(calls.isEmpty());
  }

  @Test
  void missingSnapshotRefreshWaitsForUsableNetworkState() throws Exception {
    usableIdentity();
    onWait =
        (timeout, condition) -> {
          assertEquals(800L, timeout);
          assertFalse(condition.getAsBoolean());
          networkState(1, 7);
          assertTrue(condition.getAsBoolean());
        };
    assertTrue(preflight.prepare(1));
    assertEquals(List.of("init:1", "wait:800"), calls);
    verify(replacement, never()).getAsInt();
  }

  @Test
  void unavailableSnapshotAfterRefreshTimeoutStillAllowsConnectWithoutHost() throws Exception {
    assertTrue(preflight.prepare(1));
    assertEquals(List.of("init:1", "wait:800"), calls);
  }

  @Test
  void repairRequiresConfirmationForTheRequestedNetworkAndIdentity() throws Exception {
    networkState(1, 0);
    onWait =
        (timeout, condition) -> {
          assertEquals(2_500L, timeout);
          assertFalse(condition.getAsBoolean());
          networkState(2, 7);
          assertFalse(condition.getAsBoolean());
          networkState(1, 8);
          assertFalse(condition.getAsBoolean());
          networkState(1, 7);
          assertTrue(condition.getAsBoolean());
        };
    assertTrue(preflight.prepare(1));
    assertEquals(List.of("update:1", "init:1", "wait:2500"), calls);
    assertEquals(
        new QuasselCoreNetworkUpdateRequest(
            "example", "irc.example.net", 6697, true, "", true, 7, false),
        updates.getFirst());
    assertTrue(creates.isEmpty());
  }

  @Test
  void fallbackRunsOnceAfterUpdateRemainsUnconfirmed() throws Exception {
    networkState(1, 0);
    onWait =
        (timeout, condition) -> {
          assertFalse(condition.getAsBoolean());
          if (timeout == 2_000L) networkState(1, 7);
        };
    assertTrue(preflight.prepare(1));
    assertEquals(
        List.of("update:1", "init:1", "wait:2500", "create:7", "init:1", "wait:2000"), calls);
    assertEquals(
        new QuasselCoreNetworkCreateRequest(
            "example", "irc.example.net", 6697, true, "", true, 7, List.of()),
        creates.getFirst());
  }

  @Test
  void unconfirmedFallbackPreventsConnectAndStopsFurtherAttempts() throws Exception {
    networkState(1, 0);
    assertFalse(preflight.prepare(1));
    assertEquals(
        List.of("update:1", "init:1", "wait:2500", "create:7", "init:1", "wait:2000"), calls);
    assertEquals(1, updates.size());
    assertEquals(1, creates.size());
  }

  @Test
  void observerErrorsRemainBestEffortAndCannotConfirmRepair() throws Exception {
    networkState(1, 0);
    when(observations.whenNetwork(eq("core"), anyLong(), any()))
        .thenReturn(Completable.error(failure));
    assertFalse(preflight.prepare(1));
    assertEquals(List.of("update:1", "init:1", "create:7", "init:1"), calls);
  }

  @ParameterizedTest
  @ValueSource(strings = {"update:1", "init:1", "create:7"})
  void transportFailuresPropagateAndStopSubsequentCommands(String failing) {
    networkState(1, 0);
    failingCommand = failing;
    assertSame(failure, assertThrows(IOException.class, () -> preflight.prepare(1)));
    assertEquals(failing, calls.getLast());
    assertEquals(1, calls.stream().filter(failing::equals).count());
  }

  @Test
  void malformedEndpointIsRejectedBeforeSendingRepair() {
    networks.observeState(
        1,
        Map.of(
            "identity",
            0,
            "ServerList",
            List.of(Map.of("Host", "irc.example.net", "Port", 70_000))));
    assertThrows(IllegalArgumentException.class, () -> preflight.prepare(1));
    assertTrue(calls.isEmpty());
  }

  @Test
  void negativeNetworkIsIgnored() throws Exception {
    assertTrue(preflight.prepare(-1));
    assertTrue(calls.isEmpty());
    verify(replacement, never()).getAsInt();
    verifyNoInteractions(observations);
  }

  @Test
  void missingNetworkNameUsesCatalogDisplayName() throws Exception {
    networks.observe(1, "Catalog Name");
    networks.observeState(
        1,
        Map.of(
            "identity", 0, "ServerList", List.of(Map.of("Host", "irc.example.net", "Port", 6667))));
    onWait = (timeout, condition) -> networkState(1, 7);
    assertTrue(preflight.prepare(1));
    assertEquals("Catalog Name", updates.getFirst().networkName());
    assertEquals(6667, updates.getFirst().serverPort());
    assertFalse(updates.getFirst().useTls());
    assertTrue(updates.getFirst().enabled());
  }

  private void usableIdentity() {
    identities.handleSync(
        "7", List.of(Map.of("identityId", 7, "identityName", "name", "nicks", List.of("nick"))));
  }

  private void networkState(int networkId, int identityId) {
    networks.observeState(
        networkId,
        Map.of(
            "networkName",
            "example",
            "identity",
            identityId,
            "enabled",
            false,
            "ServerList",
            List.of(Map.of("Host", "irc.example.net", "UseSSL", true))));
  }

  private void command(String name) throws Exception {
    calls.add(name);
    if (name.equals(failingCommand)) throw failure;
  }
}
