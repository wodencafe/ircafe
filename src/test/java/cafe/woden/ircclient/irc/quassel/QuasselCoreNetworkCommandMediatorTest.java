package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class QuasselCoreNetworkCommandMediatorTest {
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final OutputStream output = mock(OutputStream.class);
  private final List<String> order = new ArrayList<>();
  private final AtomicInteger identityResolutions = new AtomicInteger();
  private IOException writeFailure;
  private final QuasselCoreSession session =
      new QuasselCoreSession(
          "core",
          "alice",
          "host",
          4242,
          (socket, operation) -> {
            order.add("write");
            if (writeFailure != null) throw writeFailure;
            operation.write(codec, output);
          },
          id -> {},
          event -> {});
  private final QuasselCoreNetworkCommandMediator commands =
      new QuasselCoreNetworkCommandMediator(
          session,
          () -> {
            identityResolutions.incrementAndGet();
            return 42;
          },
          (id, name) -> {
            order.add("observed:" + id + ":" + name);
            session.networks.observe(id, name);
          });

  @Test
  void updateInheritsObservedNameIdentityAndEnabledStateAndPublishesAfterSending()
      throws Exception {
    session.socketRef.set(new Socket());
    session.networks.observeState(
        7, Map.of("NetworkName", "Existing", "Identity", 9, "isEnabled", false));
    commands.updateNetwork(7, update("", null, null));
    var payload = updatePayload();
    assertEquals("Existing", payload.get("NetworkName"));
    assertEquals(
        new QuasselCoreDatastreamCodec.UserTypeValue("IdentityId", 9), payload.get("Identity"));
    // Update payloads retain their canonical wire shape (enabled state is not serialized here).
    assertFalse(payload.containsKey("isEnabled"));
    assertEquals(0, identityResolutions.get());
    assertEquals(List.of("write", "observed:7:Existing"), order);
  }

  @Test
  void explicitUpdateValuesTakePrecedenceOverObservedState() throws Exception {
    session.socketRef.set(new Socket());
    session.networks.observeState(7, Map.of("networkName", "Old", "identity", 9));
    commands.updateNetwork(7, update("New", 12, false));
    var payload = updatePayload();
    assertEquals("New", payload.get("NetworkName"));
    assertEquals(
        new QuasselCoreDatastreamCodec.UserTypeValue("IdentityId", 12), payload.get("Identity"));
    assertEquals(0, identityResolutions.get());
    assertEquals("New", session.networks.displayNames().get(7));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void updateUsesCatalogNameOrGeneratedNameAndResolvesMissingIdentity(boolean named)
      throws Exception {
    session.socketRef.set(new Socket());
    if (named) session.networks.observe(7, "Catalog");
    commands.updateNetwork(7, update("", null, null));
    var payload = updatePayload();
    assertEquals(named ? "Catalog" : "network-7", payload.get("NetworkName"));
    assertEquals(
        new QuasselCoreDatastreamCodec.UserTypeValue("IdentityId", 42), payload.get("Identity"));
    assertEquals(1, identityResolutions.get());
  }

  @Test
  void updateDoesNotPublishOrRenameAfterWriteFailure() {
    session.socketRef.set(new Socket());
    session.networks.observe(7, "Original");
    writeFailure = new IOException("failed");
    assertSame(
        writeFailure,
        assertThrows(IOException.class, () -> commands.updateNetwork(7, update("New", 12, true))));
    assertEquals(List.of("write"), order);
    assertEquals("Original", session.networks.displayNames().get(7));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 7})
  void rejectedUpdateDoesNotResolveIdentityOrPublish(int id) {
    var failure =
        assertThrows(
            RuntimeException.class, () -> commands.updateNetwork(id, update("", null, null)));
    assertEquals(
        id < 0 ? "network id is invalid" : "Quassel socket is closed", failure.getMessage());
    assertEquals(0, identityResolutions.get());
    assertTrue(order.isEmpty());
    verifyNoInteractions(codec);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void creationRemembersNameOnlyForSuccessfulModernRequest(boolean legacy) throws Exception {
    session.socketRef.set(new Socket());
    commands.createNetwork(42, create(), legacy);
    var params = paramsCaptor();
    verify(codec)
        .writeSignalProxyRpcCall(
            eq(output),
            eq(legacy ? "2createNetwork(NetworkInfo)" : "2createNetwork(NetworkInfo,QStringList)"),
            params.capture());
    assertEquals(legacy ? 1 : 2, params.getValue().size());
    if (!legacy) assertEquals(List.of("#ircafe"), params.getValue().get(1));
    assertEquals(legacy ? "" : "Libera", session.networks.claimCreatedName());
    assertEquals("", session.networks.claimCreatedName());
    assertTrue(session.networks.displayNames().isEmpty());
  }

  @Test
  void preflightCreationUsesModernSlotAndRemembersName() throws Exception {
    session.socketRef.set(new Socket());
    QuasselCoreNetworkConnectPreflight.Commands preflight = commands;
    preflight.createNetwork(42, create());
    verify(codec)
        .writeSignalProxyRpcCall(
            eq(output), eq("2createNetwork(NetworkInfo,QStringList)"), anyList());
    assertEquals("Libera", session.networks.claimCreatedName());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void failedCreationPreservesEarlierPendingNameWithoutRememberingNewName(boolean legacy) {
    session.socketRef.set(new Socket());
    session.networks.rememberCreatedName("Earlier");
    writeFailure = new IOException("failed");
    assertSame(
        writeFailure,
        assertThrows(IOException.class, () -> commands.createNetwork(42, create(), legacy)));
    assertEquals("Earlier", session.networks.claimCreatedName());
    assertEquals("", session.networks.claimCreatedName());
  }

  @Test
  void closedSocketCreationDoesNotRememberName() {
    assertThrows(IllegalStateException.class, () -> commands.createNetwork(42, create()));
    assertEquals("", session.networks.claimCreatedName());
    assertTrue(order.isEmpty());
  }

  @Test
  void preflightInitUsesNetworkObjectIncludingZero() throws Exception {
    session.socketRef.set(new Socket());
    commands.requestNetworkInitState(0);
    verify(codec).writeSignalProxyInitRequest(output, "Network", "0", List.of());
  }

  @Test
  void invalidOrDisconnectedInitIsANoop() throws Exception {
    session.socketRef.set(new Socket());
    commands.requestNetworkInitState(-1);
    session.socketRef.set(null);
    commands.requestNetworkInitState(7);
    assertTrue(order.isEmpty());
    verifyNoInteractions(codec);
  }

  @Test
  void identityBootstrapUsesExistingNativeIdentitySender() throws Exception {
    session.socketRef.set(new Socket());
    Map<String, Object> payload = Map.of("identityName", "Custom", "nicks", List.of("bob"));
    QuasselCoreNetworkCreationCoordinator.Commands creation = commands;
    creation.createIdentity(payload);
    verify(codec)
        .writeSignalProxyRpcCall(
            output,
            "2createIdentity(Identity,QVariantMap)",
            List.of(new QuasselCoreDatastreamCodec.UserTypeValue("Identity", payload), Map.of()));
    assertTrue(session.identities.knownIds().isEmpty());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> updatePayload() throws Exception {
    var params = paramsCaptor();
    verify(codec)
        .writeSignalProxySync(
            eq(output), eq("Network"), eq("7"), eq("requestSetNetworkInfo"), params.capture());
    return (Map<String, Object>)
        ((QuasselCoreDatastreamCodec.UserTypeValue) params.getValue().getFirst()).value();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ArgumentCaptor<List<Object>> paramsCaptor() {
    return ArgumentCaptor.forClass((Class) List.class);
  }

  private static QuasselCoreNetworkUpdateRequest update(
      String name, Integer identityId, Boolean enabled) {
    return new QuasselCoreNetworkUpdateRequest(
        name, "irc.example", 6697, true, "", true, identityId, enabled);
  }

  private static QuasselCoreNetworkCreateRequest create() {
    return new QuasselCoreNetworkCreateRequest(
        "Libera", "irc.example", 6697, true, "", true, 42, List.of("#ircafe"));
  }
}
