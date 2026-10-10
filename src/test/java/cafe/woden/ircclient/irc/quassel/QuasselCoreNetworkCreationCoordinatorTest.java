package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest;
import io.reactivex.rxjava3.core.Completable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreNetworkCreationCoordinatorTest {
  private final QuasselCoreIdentityState identities =
      new QuasselCoreIdentityState("core", 512, sid -> {});
  private final QuasselCoreNetworkCatalog networks = new QuasselCoreNetworkCatalog(512, id -> {});
  private final QuasselCoreObservationMediator observations =
      mock(QuasselCoreObservationMediator.class);
  private final Set<Integer> knownIds = new LinkedHashSet<>(Set.of(1));
  private final List<String> calls = new ArrayList<>();
  private final List<Map<String, Object>> identityPayloads = new ArrayList<>();
  private final List<Create> creates = new ArrayList<>();
  private final IOException failure = new IOException("transport failed");
  private String failingCommand;
  private Consumer<Boolean> onCreate = legacy -> {};
  private Runnable onIdentityCreate = () -> {};
  private BiConsumer<Long, BooleanSupplier> onIdentityWait = (timeout, condition) -> {};
  private BiConsumer<Long, BooleanSupplier> onNetworkWait = (timeout, condition) -> {};
  private final QuasselCoreNetworkCreationCoordinator coordinator =
      new QuasselCoreNetworkCreationCoordinator(
          "core",
          "Client Nick",
          identities,
          networks,
          observations,
          requested -> {
            calls.add("resolve");
            return requested != null
                ? requested
                : identities.hasKnown() ? identities.firstKnownId() : 1;
          },
          () -> knownIds,
          new QuasselCoreNetworkCreationCoordinator.Commands() {
            @Override
            public void createIdentity(Map<String, Object> payload) throws Exception {
              command("identity");
              identityPayloads.add(payload);
              onIdentityCreate.run();
            }

            @Override
            public void createNetwork(
                int identityId, QuasselCoreNetworkCreateRequest request, boolean legacy)
                throws Exception {
              command(legacy ? "legacy" : "modern");
              creates.add(new Create(identityId, request, legacy));
              onCreate.accept(legacy);
            }
          });

  @BeforeEach
  void prepareObservations() {
    when(observations.whenIdentity(eq("core"), anyLong(), any()))
        .thenAnswer(
            invocation -> {
              long timeout = invocation.getArgument(1);
              BooleanSupplier condition = invocation.getArgument(2);
              return Completable.fromAction(
                  () -> {
                    calls.add("identity-wait:" + timeout);
                    onIdentityWait.accept(timeout, condition);
                  });
            });
    when(observations.whenNetwork(eq("core"), anyLong(), any()))
        .thenAnswer(
            invocation -> {
              long timeout = invocation.getArgument(1);
              BooleanSupplier condition = invocation.getArgument(2);
              return Completable.fromAction(
                  () -> {
                    calls.add("network-wait:" + timeout);
                    onNetworkWait.accept(timeout, condition);
                  });
            });
  }

  @Test
  void explicitIdentityBypassesBootstrapAndAutoJoinBypassesLegacyRetry() throws Exception {
    QuasselCoreNetworkCreateRequest request = request(42, List.of("#chat"));
    coordinator.create(request);
    assertEquals(List.of("resolve", "modern"), calls);
    assertEquals(List.of(new Create(42, request, false)), creates);
    assertTrue(identityPayloads.isEmpty());
    verifyNoInteractions(observations);
  }

  @Test
  void knownIdentityNeedsNoBootstrapEvenWithoutUsableIdentityState() throws Exception {
    identities.observe(7, "name");
    coordinator.create(request(null, List.of("#chat")));
    assertEquals(List.of("resolve", "modern"), calls);
    assertEquals(7, creates.getFirst().identityId());
    verifyNoInteractions(observations);
  }

  @Test
  void synchronousIdentityObservationAvoidsWaitAndFeedsResolution() throws Exception {
    onIdentityCreate = () -> identities.observe(7, "created");
    coordinator.create(request(null, List.of("#chat")));
    assertEquals(List.of("identity", "resolve", "modern"), calls);
    assertEquals(7, creates.getFirst().identityId());
    assertEquals(List.of("Client_Nick"), identityPayloads.getFirst().get("nicks"));
    verifyNoInteractions(observations);
  }

  @Test
  void bootstrapWaitsForObservedIdentityBeforeChoosingNetworkIdentity() throws Exception {
    onIdentityWait =
        (timeout, condition) -> {
          assertEquals(1_500L, timeout);
          assertFalse(condition.getAsBoolean());
          identities.observe(0, "invalid");
          assertFalse(condition.getAsBoolean());
          identities.observe(7, "created");
          assertTrue(condition.getAsBoolean());
        };
    coordinator.create(request(null, List.of("#chat")));
    assertEquals(List.of("identity", "identity-wait:1500", "resolve", "modern"), calls);
    assertEquals(7, creates.getFirst().identityId());
  }

  @Test
  void unconfirmedBootstrapContinuesWithFallbackIdentityWithoutRepeatingBootstrap()
      throws Exception {
    coordinator.create(request(null, List.of("#chat")));
    assertEquals(List.of("identity", "identity-wait:1500", "resolve", "modern"), calls);
    assertEquals(1, creates.getFirst().identityId());
    assertEquals(1, identityPayloads.size());
  }

  @Test
  void bootstrapObserverErrorsKeepFallbackIdentitySelection() throws Exception {
    when(observations.whenIdentity(eq("core"), anyLong(), any()))
        .thenReturn(Completable.error(failure));
    coordinator.create(request(null, List.of("#chat")));
    assertEquals(List.of("identity", "resolve", "modern"), calls);
    assertEquals(1, creates.getFirst().identityId());
  }

  @Test
  void newNetworkIdSuppressesRetryEvenBeforeItsNameMatches() throws Exception {
    onCreate =
        legacy -> {
          knownIds.add(9);
          networks.observe(9, "different");
        };
    coordinator.create(request(7, List.of()));
    assertEquals(List.of("resolve", "modern"), calls);
    assertEquals(1, creates.size());
    verifyNoInteractions(observations);
  }

  @Test
  void existingMatchingNamePreservesCurrentRetrySuppression() throws Exception {
    networks.observe(1, " EXAMPLE ");
    coordinator.create(request(7, List.of()));
    assertEquals(List.of("resolve", "modern"), calls);
    verifyNoInteractions(observations);
  }

  @Test
  void observationOfRequestedNameDuringWaitSuppressesLegacyRetry() throws Exception {
    onNetworkWait =
        (timeout, condition) -> {
          assertEquals(1_000L, timeout);
          assertFalse(condition.getAsBoolean());
          networks.observe(1, "different");
          assertFalse(condition.getAsBoolean());
          networks.observe(1, "example");
          assertTrue(condition.getAsBoolean());
        };
    coordinator.create(request(7, List.of()));
    assertEquals(List.of("resolve", "modern", "network-wait:1000"), calls);
  }

  @Test
  void unconfirmedModernCreateRetriesLegacyOnceWithTheSameIdentityAndRequest() throws Exception {
    QuasselCoreNetworkCreateRequest request = request(7, List.of());
    coordinator.create(request);
    assertEquals(
        List.of("resolve", "modern", "network-wait:1000", "legacy", "network-wait:1000"), calls);
    assertEquals(List.of(new Create(7, request, false), new Create(7, request, true)), creates);
  }

  @Test
  void legacyConfirmationStopsAfterOneRetry() throws Exception {
    onNetworkWait =
        (timeout, condition) -> {
          assertFalse(condition.getAsBoolean());
          if (creates.size() == 2) {
            knownIds.add(9);
            networks.observe(9, "example");
          }
        };
    coordinator.create(request(7, List.of()));
    assertEquals(
        List.of("resolve", "modern", "network-wait:1000", "legacy", "network-wait:1000"), calls);
    assertEquals(2, creates.size());
  }

  @Test
  void baselineIncludesNetworksObservedDuringBootstrap() throws Exception {
    onIdentityWait =
        (timeout, condition) -> {
          identities.observe(7, "created");
          knownIds.add(9);
        };
    coordinator.create(request(null, List.of()));
    assertEquals(
        List.of(
            "identity",
            "identity-wait:1500",
            "resolve",
            "modern",
            "network-wait:1000",
            "legacy",
            "network-wait:1000"),
        calls);
  }

  @Test
  void networkObserverErrorsDoNotAddExtraRetriesOrBecomeTransportFailures() throws Exception {
    when(observations.whenNetwork(eq("core"), anyLong(), any()))
        .thenReturn(Completable.error(failure));
    coordinator.create(request(7, List.of()));
    assertEquals(List.of("resolve", "modern", "legacy"), calls);
    verify(observations, times(2)).whenNetwork(eq("core"), eq(1_000L), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"identity", "modern", "legacy"})
  void commandFailuresPropagateWithoutAdditionalCreateAttempts(String command) {
    failingCommand = command;
    Integer identity = "identity".equals(command) ? null : 7;
    assertSame(
        failure,
        assertThrows(IOException.class, () -> coordinator.create(request(identity, List.of()))));
    assertEquals(command, calls.getLast());
    assertEquals(1, calls.stream().filter(command::equals).count());
  }

  private void command(String name) throws Exception {
    calls.add(name);
    if (name.equals(failingCommand)) throw failure;
  }

  private static QuasselCoreNetworkCreateRequest request(Integer identity, List<String> autoJoin) {
    return new QuasselCoreNetworkCreateRequest(
        "example", "irc.example.net", 6697, true, "", true, identity, autoJoin);
  }

  private record Create(int identityId, QuasselCoreNetworkCreateRequest request, boolean legacy) {}
}
