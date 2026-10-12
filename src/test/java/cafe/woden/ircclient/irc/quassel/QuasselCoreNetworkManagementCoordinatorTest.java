package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.irc.backend.BackendNotAvailableException;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.BufferInfoValue;
import cafe.woden.ircclient.irc.quassel.QuasselCoreDatastreamCodec.UserTypeValue;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest;
import io.reactivex.rxjava3.core.Completable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreNetworkManagementCoordinatorTest {
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final QuasselCoreObservationMediator observations =
      mock(QuasselCoreObservationMediator.class);
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();
  private final List<Command> commands = new ArrayList<>();
  private final List<String> trace = new ArrayList<>();
  private IOException writeFailure;
  private Consumer<Command> onCommand = ignored -> {};
  private final QuasselCoreSession session = session("core");
  private final QuasselCoreStateObservationCoordinator stateObservations =
      new QuasselCoreStateObservationCoordinator(
          mock(QuasselCoreTargetResolver.class), event -> trace.add(event.source()));
  private final QuasselCoreNetworkManagementCoordinator management =
      new QuasselCoreNetworkManagementCoordinator(observations, stateObservations);

  @BeforeEach
  void configureTransport() throws Exception {
    session.socketRef.set(new Socket());
    doAnswer(
            call -> {
              command(
                  new Command(
                      "sync", call.getArgument(2), call.getArgument(3), call.getArgument(4)));
              return null;
            })
        .when(codec)
        .writeSignalProxySync(eq(output), eq("Network"), anyString(), anyString(), anyList());
    doAnswer(
            call -> {
              command(
                  new Command(
                      "init", call.getArgument(2), call.getArgument(1), call.getArgument(3)));
              return null;
            })
        .when(codec)
        .writeSignalProxyInitRequest(eq(output), anyString(), anyString(), anyList());
    doAnswer(
            call -> {
              command(new Command("rpc", "", call.getArgument(1), call.getArgument(2)));
              return null;
            })
        .when(codec)
        .writeSignalProxyRpcCall(eq(output), anyString(), anyList());
    when(observations.whenNetwork(anyString(), anyLong(), any()))
        .thenReturn(Completable.complete());
    when(observations.whenIdentity(anyString(), anyLong(), any()))
        .thenReturn(Completable.complete());
  }

  @ParameterizedTest
  @CsvSource({
    "connect, 7", "connect, Home Network", "connect, home-network", "connect, HOME NETWORK",
    "disconnect, 7", "disconnect, Home Network", "disconnect, home-network",
        "disconnect, HOME NETWORK"
  })
  void networkNamesAndTokensRouteThroughExistingSyncCommands(String operation, String token)
      throws Exception {
    session.networks.observe(7, "Home Network");
    session.networks.observeState(7, Map.of("identity", 5));
    usableIdentity(5);
    operate(operation, " " + token + " ");
    assertEquals(
        List.of(
            new Command(
                "sync",
                "7",
                operation.equals("connect") ? "requestConnect" : "requestDisconnect",
                List.of())),
        commands);
    assertEquals(1, trace.size());
    verifyNoInteractions(observations);
  }

  @ParameterizedTest
  @ValueSource(strings = {"connect", "disconnect", "update", "remove"})
  void unknownAndBlankNetworkTokensFailWithOperationContextBeforeSending(String operation) {
    var failure =
        assertThrows(BackendNotAvailableException.class, () -> operate(operation, " missing "));
    assertEquals("quassel-core", failure.backendId());
    assertEquals("core", failure.serverId());
    assertEquals("quassel " + operation + " network", failure.operation());
    assertEquals("unknown Quassel network 'missing' (run /quasselnet list)", failure.detail());
    assertEquals(
        "network id/name is required",
        assertThrows(IllegalArgumentException.class, () -> operate(operation, " ")).getMessage());
    assertTrue(commands.isEmpty());
    assertTrue(trace.isEmpty());
    verifyNoInteractions(observations);
  }

  @Test
  void numericNetworkZeroCanDisconnectWithoutObservedMetadata() throws Exception {
    management.disconnect(session, "0");
    assertEquals(List.of(new Command("sync", "0", "requestDisconnect", List.of())), commands);
    assertTrue(stateObservations.snapshot(session).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void connectWaitsForConfirmedIdentityRepairAndSuppressesUnconfirmedConnect(boolean confirmed)
      throws Exception {
    session.networks.observe(7, "Home");
    session.networks.observeState(
        7,
        Map.of("identity", 0, "ServerList", List.of(Map.of("Host", "irc.example", "Port", 6697))));
    session.identities.observe(5, "Replacement");
    onCommand =
        command -> {
          if (confirmed && command.slot().equals("requestSetNetworkInfo")) {
            session.networks.observeState(7, Map.of("identity", 5));
          }
        };
    management.connect(session, "Home");
    assertEquals("requestSetNetworkInfo", commands.getFirst().slot());
    assertEquals(5, identityId(commands.getFirst()));
    assertEquals(
        confirmed
            ? List.of("requestSetNetworkInfo", "Network", "requestConnect")
            : List.of(
                "requestSetNetworkInfo",
                "Network",
                "2createNetwork(NetworkInfo,QStringList)",
                "Network"),
        commands.stream().map(Command::slot).toList());
    assertEquals("sync:7:requestSetNetworkInfo", trace.getFirst());
    assertEquals("observe-known-network", trace.get(1));
    if (confirmed) verifyNoInteractions(observations);
    else verify(observations, times(2)).whenNetwork(eq("core"), anyLong(), any());
  }

  @Test
  void missingNetworkSnapshotRefreshesBeforeConnect() throws Exception {
    session.networks.observe(7, "Home");
    usableIdentity(5);
    onCommand =
        command -> {
          if (command.kind().equals("init")) {
            session.networks.observeState(7, Map.of("identity", 5));
          }
        };
    management.connect(session, "7");
    assertEquals(
        List.of(
            new Command("init", "7", "Network", List.of()),
            new Command("sync", "7", "requestConnect", List.of())),
        commands);
    verifyNoInteractions(observations);
  }

  @Test
  void createNormalizesRequestAndUsesExplicitIdentityWithoutBootstrapOrLegacyRetry()
      throws Exception {
    session.identities.observe(3, "Earlier");
    management.create(session, create(" New Network ", 12));
    assertEquals(1, commands.size());
    Command created = commands.getFirst();
    assertEquals("2createNetwork(NetworkInfo,QStringList)", created.slot());
    assertEquals("New Network", payload(created).get("NetworkName"));
    assertEquals(12, identityId(created));
    assertEquals(List.of("#room"), created.params().get(1));
    assertEquals("New Network", session.networks.claimCreatedName());
    assertTrue(stateObservations.snapshot(session).isEmpty());
    verifyNoInteractions(observations);
  }

  @Test
  void createBootstrapUsesIdentityObservedByCoreBeforeNetworkCreation() throws Exception {
    onCommand =
        command -> {
          if (command.slot().equals("2createIdentity(Identity,QVariantMap)")) {
            session.identities.observe(19, "Created");
          }
        };
    management.create(session, create("New", null));
    assertEquals(
        List.of("2createIdentity(Identity,QVariantMap)", "2createNetwork(NetworkInfo,QStringList)"),
        commands.stream().map(Command::slot).toList());
    assertEquals(19, identityId(commands.getLast()));
    verifyNoInteractions(observations);
  }

  @ParameterizedTest
  @CsvSource({"known,3", "primary,9", "secondary,11", "default,1"})
  void updateIdentityFallbackUsesLiveCatalogPrecedence(String source, int identity)
      throws Exception {
    session.authResult.set(
        new QuasselCoreAuthHandshake.AuthResult("core", 7, List.of(7, 8), Map.of()));
    session.networks.observe(7, "Primary");
    session.networks.observe(8, "Secondary");
    switch (source) {
      case "known" -> {
        session.networks.observeState(7, Map.of("identity", 9));
        session.identities.observe(3, "Lowest");
      }
      case "primary" -> {
        session.networks.observeState(7, Map.of("identity", 9));
        session.networks.observeState(8, Map.of("identity", 11));
        session.identities.clear();
      }
      case "secondary" -> {
        session.networks.observeState(8, Map.of("identity", 11));
        session.identities.clear();
      }
      case "default" -> {}
      default -> fail("Unknown fixture source");
    }
    management.update(session, "99", update("", null));
    assertEquals(identity, identityId(commands.getFirst()));
    assertEquals("network-99", payload(commands.getFirst()).get("NetworkName"));
    assertEquals(List.of("sync:99:requestSetNetworkInfo", "observe-known-network"), trace);
    verifyNoInteractions(observations);
  }

  @Test
  void updatePreservesKnownNameAndPrefersExplicitIdentityBeforePublishingRename() throws Exception {
    session.networks.observe(7, "Original");
    session.networks.observeState(7, Map.of("identity", 9));
    management.update(session, "Original", update(" Renamed ", 12));
    assertEquals(12, identityId(commands.getFirst()));
    assertEquals("Renamed", payload(commands.getFirst()).get("NetworkName"));
    assertEquals("Renamed", session.networks.displayNames().get(7));
    assertEquals(List.of("sync:7:requestSetNetworkInfo", "observe-known-network"), trace);
  }

  @Test
  void removeWritesBeforeClearingNetworkBuffersAndPendingMarkers() throws Exception {
    session.networks.observe(7, "Home");
    session.buffers.merge(new BufferInfoValue(11, 7, 2, -1, "#room"));
    session.pendingReadMarkers.defer(11, 101);
    onCommand =
        command -> {
          assertEquals("Home", session.networks.displayNames().get(7));
          assertNotNull(session.buffers.get(11));
        };
    management.remove(session, "Home");
    assertEquals(
        List.of(
            new Command(
                "rpc",
                "",
                "2removeNetwork(NetworkId)",
                List.of(new UserTypeValue("NetworkId", 7)))),
        commands);
    assertTrue(stateObservations.snapshot(session).isEmpty());
    assertNull(session.buffers.get(11));
    assertNull(session.pendingReadMarkers.takeBufferForMessage(101));
    assertEquals(List.of("rpc::2removeNetwork(NetworkId)", "forget-known-network"), trace);
  }

  @ParameterizedTest
  @ValueSource(strings = {"connect", "disconnect", "create", "update", "remove", "init"})
  void writeFailuresPropagateWithoutOptimisticObservations(String operation) {
    session.networks.observe(7, "Home");
    session.networks.observeState(7, Map.of("identity", 5));
    usableIdentity(5);
    session.buffers.merge(new BufferInfoValue(11, 7, 2, -1, "#room"));
    writeFailure = new IOException("write failed");
    assertSame(writeFailure, assertThrows(IOException.class, () -> operate(operation, "7")));
    assertEquals("Home", session.networks.displayNames().get(7));
    assertNotNull(session.buffers.get(11));
    assertEquals("", session.networks.claimCreatedName());
    assertTrue(commands.isEmpty());
    assertTrue(trace.isEmpty());
    verifyNoInteractions(observations);
  }

  @Test
  void malformedRequestsAndUnknownUpdateTargetKeepValidationOrder() {
    assertEquals(
        "request",
        assertThrows(NullPointerException.class, () -> management.create(session, null))
            .getMessage());
    assertEquals(
        "network name is required",
        assertThrows(
                IllegalArgumentException.class, () -> management.create(session, create("", 12)))
            .getMessage());
    assertThrows(
        BackendNotAvailableException.class, () -> management.update(session, "unknown", null));
    assertEquals(
        "request",
        assertThrows(NullPointerException.class, () -> management.update(session, "7", null))
            .getMessage());
    assertTrue(commands.isEmpty());
    assertTrue(trace.isEmpty());
    verifyNoInteractions(observations);
  }

  @Test
  void sharedCoordinatorResolvesIdentityAgainstEachSessionAtCallTime() throws Exception {
    var other = session("other");
    other.socketRef.set(new Socket());
    session.identities.observe(3, "First");
    other.identities.observe(12, "Second");
    management.create(session, create("First", null));
    management.create(other, create("Second", null));
    session.identities.clear();
    session.identities.observe(19, "New");
    management.create(session, create("Third", null));
    assertEquals(List.of(3, 12, 19), commands.stream().map(this::identityId).toList());
    assertEquals("First", session.networks.claimCreatedName());
    assertEquals("Third", session.networks.claimCreatedName());
    assertEquals("Second", other.networks.claimCreatedName());
  }

  @Test
  void invalidAndDisconnectedInitRequestsRemainNoOpsIncludingNetworkZero() throws Exception {
    management.requestNetworkInitState(null, 7);
    management.requestNetworkInitState(session, -1);
    session.socketRef.set(null);
    management.requestNetworkInitState(session, 7);
    assertTrue(commands.isEmpty());
    session.socketRef.set(new Socket());
    management.requestNetworkInitState(session, 0);
    assertEquals(List.of(new Command("init", "0", "Network", List.of())), commands);
    verifyNoInteractions(observations);
  }

  private void operate(String operation, String token) throws Exception {
    switch (operation) {
      case "connect" -> management.connect(session, token);
      case "disconnect" -> management.disconnect(session, token);
      case "create" -> management.create(session, create("New", 12));
      case "update" -> management.update(session, token, update("Renamed", 12));
      case "remove" -> management.remove(session, token);
      case "init" -> management.requestNetworkInitState(session, Integer.parseInt(token));
      default -> fail("Unknown operation");
    }
  }

  private QuasselCoreSession session(String serverId) {
    return new QuasselCoreSession(
        serverId,
        "alice",
        "core.example",
        4242,
        (socket, operation) -> {
          if (writeFailure != null) throw writeFailure;
          operation.write(codec, output);
        },
        ignored -> {},
        ignored -> {});
  }

  private void command(Command command) {
    commands.add(command);
    trace.add(command.kind() + ":" + command.object() + ":" + command.slot());
    onCommand.accept(command);
  }

  private void usableIdentity(int id) {
    session.identities.handleSync(
        Integer.toString(id),
        List.of(Map.of("identityId", id, "identityName", "Usable", "nicks", List.of("alice"))));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> payload(Command command) {
    return (Map<String, Object>) ((UserTypeValue) command.params().getFirst()).value();
  }

  private int identityId(Command command) {
    return ((Number) ((UserTypeValue) payload(command).get("Identity")).value()).intValue();
  }

  private static QuasselCoreNetworkCreateRequest create(String name, Integer identity) {
    return new QuasselCoreNetworkCreateRequest(
        name, " irc.example ", 0, true, "", true, identity, List.of(" #room "));
  }

  private static QuasselCoreNetworkUpdateRequest update(String name, Integer identity) {
    return new QuasselCoreNetworkUpdateRequest(
        name, " irc.example ", 0, true, "", true, identity, null);
  }

  private record Command(String kind, String object, String slot, List<Object> params) {}
}
