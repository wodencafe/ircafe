package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkCreateRequest;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreNetworkUpdateRequest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuasselCoreNetworkRequestsTest {
  @Test
  void normalizesCreateWithoutTrimmingPasswordOrDiscardingChannelKeys() {
    QuasselCoreNetworkCreateRequest result =
        QuasselCoreNetworkRequests.normalizeCreateRequest(
            new QuasselCoreNetworkCreateRequest(
                " Libera ",
                " irc.libera.chat ",
                0,
                true,
                " secret ",
                true,
                null,
                Arrays.asList(" #room key ", " ", null)));
    assertEquals("Libera", result.networkName());
    assertEquals("irc.libera.chat", result.serverHost());
    assertEquals(6697, result.serverPort());
    assertEquals(" secret ", result.serverPassword());
    assertEquals(List.of("#room key"), result.autoJoinChannels());
    assertThrows(
        UnsupportedOperationException.class, () -> result.autoJoinChannels().add("#other"));
  }

  @Test
  void permitsUnnamedUpdateAndDefaultsPlaintextPort() {
    QuasselCoreNetworkUpdateRequest result =
        QuasselCoreNetworkRequests.normalizeUpdateRequest(
            new QuasselCoreNetworkUpdateRequest(
                null, " irc.example.test ", -1, false, null, false, 2, false));
    assertEquals("", result.networkName());
    assertEquals(6667, result.serverPort());
    assertEquals("", result.serverPassword());
    assertEquals(false, result.enabled());
  }

  @Test
  void buildsTypedCreatePayloadWithLegacyAliasesAndServerSettings() {
    QuasselCoreNetworkCreateRequest request =
        new QuasselCoreNetworkCreateRequest(
            "Libera", "irc.libera.chat", 6697, true, "secret", true, 2, List.of());
    Map<String, Object> payload =
        QuasselCoreNetworkRequests.networkInfoPayload(-1, 2, request, true, true);
    assertEquals(
        new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", -1), payload.get("NetworkId"));
    assertEquals(
        new QuasselCoreDatastreamCodec.UserTypeValue("IdentityId", 2), payload.get("Identity"));
    assertEquals(2, payload.get("identityId"));
    assertEquals("Libera", payload.get("networkName"));
    assertArrayEquals(
        "UTF-8".getBytes(StandardCharsets.UTF_8), (byte[]) payload.get("CodecForServer"));
    QuasselCoreDatastreamCodec.UserTypeValue server =
        assertInstanceOf(
            QuasselCoreDatastreamCodec.UserTypeValue.class,
            ((List<?>) payload.get("ServerList")).getFirst());
    assertEquals("Network::Server", server.typeName());
    Map<?, ?> fields = assertInstanceOf(Map.class, server.value());
    assertEquals("irc.libera.chat", fields.get("Host"));
    assertEquals("secret", fields.get("Password"));
    assertEquals(true, fields.get("SslVerify"));
    assertEquals(fields.get("Host"), fields.get("hostname"));
    assertThrows(UnsupportedOperationException.class, () -> payload.put("NetworkName", "other"));
  }

  @Test
  void updatePayloadKeepsTypedKeysWithoutCreateOnlyAliases() {
    QuasselCoreNetworkUpdateRequest request =
        new QuasselCoreNetworkUpdateRequest(
            "renamed", "irc.example.test", 6667, false, "", true, 3, true);
    Map<String, Object> payload =
        QuasselCoreNetworkRequests.networkInfoUpdatePayload(7, 3, "renamed", request, true);
    assertEquals(
        new QuasselCoreDatastreamCodec.UserTypeValue("NetworkId", 7), payload.get("NetworkId"));
    assertEquals("renamed", payload.get("NetworkName"));
    assertFalse(payload.containsKey("networkName"));
    assertFalse(payload.containsKey("isInitialized"));
  }

  @Test
  void rejectsInvalidNetworkParametersBeforePayloadConstruction() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            QuasselCoreNetworkRequests.normalizeCreateRequest(
                new QuasselCoreNetworkCreateRequest(
                    " ", "host", 6667, false, "", true, null, List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            QuasselCoreNetworkRequests.normalizeUpdateRequest(
                new QuasselCoreNetworkUpdateRequest(
                    "name", "host\nJOIN #room", 6667, false, "", true, null, null)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            QuasselCoreNetworkRequests.normalizeUpdateRequest(
                new QuasselCoreNetworkUpdateRequest(
                    "name", "host", 65536, false, "", true, null, null)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            QuasselCoreNetworkRequests.normalizeCreateRequest(
                new QuasselCoreNetworkCreateRequest(
                    "name", "host", 6667, false, "", true, 0, List.of())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            QuasselCoreNetworkRequests.normalizeCreateRequest(
                new QuasselCoreNetworkCreateRequest(
                    "name", "host", 6667, false, "", true, null, List.of("#room\r/NICK other"))));
  }
}
