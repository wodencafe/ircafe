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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class QuasselCoreNetworkCommandSenderTest {
  private final QuasselCoreDatastreamCodec codec = mock(QuasselCoreDatastreamCodec.class);
  private final OutputStream output = mock(OutputStream.class);
  private final Socket originalSocket = new Socket();
  private final AtomicReference<Socket> socket = new AtomicReference<>(originalSocket);
  private final List<Socket> writes = new ArrayList<>();
  private final QuasselCoreNetworkCommandSender commands =
      new QuasselCoreNetworkCommandSender(
          "core",
          "alice",
          socket::get,
          (captured, operation) -> {
            writes.add(captured);
            operation.write(codec, output);
          });

  @Test
  void networkSyncAndRpcKeepTheirDistinctAddressing() throws Exception {
    commands.syncNetwork(0, " requestConnect ");
    commands.rpcNetwork(7, " 2custom(NetworkId) ");
    commands.removeNetwork(7);
    verify(codec).writeSignalProxySync(output, "Network", "0", "requestConnect", List.of());
    verify(codec).writeSignalProxyRpcCall(output, "2custom(NetworkId)", List.of(7));
    verify(codec)
        .writeSignalProxyRpcCall(
            output,
            "2removeNetwork(NetworkId)",
            List.of(new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", 7)));
  }

  @Test
  void globalInitAllowsAnEmptyObjectName() throws Exception {
    commands.requestInit(" BufferSyncer ", " ");
    verify(codec).writeSignalProxyInitRequest(output, "BufferSyncer", "", List.of());
  }

  @Test
  void initWithMissingClassOrClosedSocketIsANoop() throws Exception {
    commands.requestInit(null, "1");
    commands.requestInit(" ", "1");
    socket.set(null);
    commands.requestInit("Network", "1");
    assertTrue(writes.isEmpty());
    verifyNoInteractions(codec);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void identityCreationKeepsDefaultAndExplicitPayloads(boolean defaults) throws Exception {
    Map<String, Object> explicit = Map.of("identityName", "custom", "nicks", List.of("bob"));
    commands.createIdentity(defaults ? Map.of() : explicit);
    var params = rpcParams("2createIdentity(Identity,QVariantMap)");
    var payload = typedMap(params.getFirst(), "Identity");
    if (defaults) {
      assertEquals("alice", payload.get("identityName"));
      assertEquals(List.of("alice"), payload.get("nicks"));
    } else {
      assertSame(explicit, payload);
    }
    assertEquals(Map.of(), params.get(1));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void createKeepsAliasesAndOptionalAutoJoinAndCapturesSocketBeforeDiagnostics(boolean modern)
      throws Exception {
    String slot =
        modern ? "2createNetwork(NetworkInfo,QStringList)" : "2createNetwork(NetworkInfo)";
    var request = createRequest();
    List<Map<String, Object>> diagnosticPayloads = new ArrayList<>();
    commands.createNetwork(
        42,
        request,
        " " + slot + " ",
        modern,
        (normalizedSlot, payload) -> {
          assertEquals(slot, normalizedSlot);
          assertTrue(writes.isEmpty());
          diagnosticPayloads.add(payload);
          socket.set(null);
        });
    assertEquals(List.of(originalSocket), writes);
    var params = rpcParams(slot);
    assertEquals(modern ? 2 : 1, params.size());
    var payload = typedMap(params.getFirst(), "NetworkInfo");
    assertSame(diagnosticPayloads.getFirst(), payload);
    assertEquals("Libera", payload.get("NetworkName"));
    assertEquals("Libera", payload.get("networkName"));
    assertEquals(42, payload.get("identityId"));
    assertEquals(
        new QuasselCoreDatastreamCodec.UserTypeValue("IdentityId", 42), payload.get("Identity"));
    if (modern) assertEquals(List.of("#ircafe"), params.get(1));
  }

  @Test
  void updateResolvesStateAfterSocketValidationAndKeepsCanonicalPayload() throws Exception {
    var request = updateRequest();
    String name =
        commands.updateNetwork(
            7,
            request,
            () -> {
              assertTrue(writes.isEmpty());
              socket.set(null);
              return new QuasselCoreNetworkCommandSender.Update("Existing", 42, false);
            });
    assertEquals("Existing", name);
    assertEquals(List.of(originalSocket), writes);
    var params = paramsCaptor();
    verify(codec)
        .writeSignalProxySync(
            eq(output), eq("Network"), eq("7"), eq("requestSetNetworkInfo"), params.capture());
    var payload = typedMap(params.getValue().getFirst(), "NetworkInfo");
    assertEquals("Existing", payload.get("NetworkName"));
    assertFalse(payload.containsKey("networkName"));
    assertEquals(
        new QuasselCoreDatastreamCodec.UserTypeValue("IdentityId", 42), payload.get("Identity"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"sync", "rpc", "create", "update", "identity", "remove"})
  void closedSocketRejectsBeforeSlotPayloadOrStateResolution(String operation) {
    socket.set(null);
    var failure = assertThrows(IllegalStateException.class, () -> invoke(operation, 7, " "));
    assertEquals("Quassel socket is closed", failure.getMessage());
    assertTrue(writes.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"sync", "rpc", "update"})
  void invalidNetworkRejectsBeforeSocketLookup(String operation) {
    socket.set(null);
    var failure = assertThrows(IllegalArgumentException.class, () -> invoke(operation, -1, " "));
    assertEquals("network id is invalid", failure.getMessage());
    assertTrue(writes.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"sync", "rpc", "create"})
  void blankSlotRejectsBeforePayloadConstruction(String operation) {
    var failure = assertThrows(IllegalArgumentException.class, () -> invoke(operation, 7, " "));
    String message =
        switch (operation) {
          case "rpc" -> "rpc slot name is blank";
          case "create" -> "create-network rpc slot is blank";
          default -> "slot name is blank";
        };
    assertEquals(message, failure.getMessage());
    assertTrue(writes.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"create", "update", "identity", "remove"})
  void writeFailurePropagatesWithoutRetry(String operation) throws Exception {
    IOException failure = new IOException("partial write");
    doThrow(failure).when(codec).writeSignalProxyRpcCall(any(), anyString(), anyList());
    doThrow(failure)
        .when(codec)
        .writeSignalProxySync(any(), anyString(), anyString(), anyString(), anyList());
    assertSame(
        failure,
        assertThrows(
            IOException.class,
            () -> {
              switch (operation) {
                case "create" ->
                    commands.createNetwork(
                        42, createRequest(), "create", true, (slot, payload) -> {});
                case "update" ->
                    commands.updateNetwork(
                        7,
                        updateRequest(),
                        () -> new QuasselCoreNetworkCommandSender.Update("Existing", 42, true));
                case "identity" -> commands.createIdentity(null);
                case "remove" -> commands.removeNetwork(7);
                default -> throw new AssertionError(operation);
              }
            }));
    assertEquals(List.of(originalSocket), writes);
  }

  private void invoke(String operation, int id, String slot) throws Exception {
    switch (operation) {
      case "sync" -> commands.syncNetwork(id, slot);
      case "rpc" -> commands.rpcNetwork(id, slot);
      case "create" ->
          commands.createNetwork(
              42, null, slot, true, (normalized, payload) -> fail("diagnostics must not run"));
      case "update" ->
          commands.updateNetwork(
              id,
              null,
              () -> {
                fail("state resolution must not run");
                return null;
              });
      case "identity" -> commands.createIdentity(null);
      case "remove" -> commands.removeNetwork(id);
      default -> throw new AssertionError(operation);
    }
  }

  private List<Object> rpcParams(String slot) throws Exception {
    var params = paramsCaptor();
    verify(codec).writeSignalProxyRpcCall(eq(output), eq(slot), params.capture());
    return params.getValue();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static ArgumentCaptor<List<Object>> paramsCaptor() {
    return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> typedMap(Object raw, String type) {
    var value = assertInstanceOf(QuasselCoreDatastreamCodec.UserTypeValue.class, raw);
    assertEquals(type, value.typeName());
    return (Map<String, Object>) value.value();
  }

  private static QuasselCoreNetworkCreateRequest createRequest() {
    return new QuasselCoreNetworkCreateRequest(
        "Libera", "irc.libera.test", 6697, true, "secret", true, 42, List.of("#ircafe"));
  }

  private static QuasselCoreNetworkUpdateRequest updateRequest() {
    return new QuasselCoreNetworkUpdateRequest(
        "", "irc.update.test", 6667, false, "", true, null, null);
  }
}
