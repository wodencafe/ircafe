package cafe.woden.ircclient.app.outbound.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.app.outbound.backend.spi.OutboundBackendFeatureAdapter;
import cafe.woden.ircclient.app.outbound.support.CommandTargetPolicy;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.backend.IrcBackendAvailabilityPort;
import cafe.woden.ircclient.irc.backend.IrcBackendClientService;
import cafe.woden.ircclient.irc.port.IrcNegotiatedFeaturePort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutboundBackendCapabilityPolicyTest {

  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final IrcBackendClientService irc = mock(IrcBackendClientService.class);
  private final IrcBackendAvailabilityPort backendAvailability = irc;
  private final CommandTargetPolicy commandTargetPolicy = new CommandTargetPolicy(serverCatalog);
  private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry =
      new OutboundBackendFeatureRegistry(
          List.of(
              new MatrixOutboundBackendFeatureAdapter(),
              new QuasselOutboundBackendFeatureAdapter()));
  private final OutboundBackendCapabilityPolicy policy =
      new OutboundBackendCapabilityPolicy(
          commandTargetPolicy,
          outboundBackendFeatureRegistry,
          IrcNegotiatedFeaturePort.from(irc),
          backendAvailability);

  @Test
  void supportsMatrixSemanticUploadViaBackendAdapter() {
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(server("matrix", IrcProperties.Server.Backend.MATRIX)));
    when(irc.isDraftReplyAvailable("matrix")).thenReturn(true);
    when(irc.isMultilineAvailable("matrix")).thenReturn(true);

    assertTrue(policy.supportsDraftReply("matrix"));
    assertTrue(policy.supportsMultiline("matrix"));
    assertTrue(policy.supportsSemanticUpload("matrix"));
    assertTrue(policy.persistsJoinedChannelsLocally("matrix"));
    assertFalse(policy.supportsQuasselCoreCommands("matrix"));
    assertTrue(policy.supportsSemanticUploadByBackendId("matrix"));
  }

  @Test
  void supportsQuasselCommandsViaBackendAdapter() {
    when(serverCatalog.find("quassel"))
        .thenReturn(Optional.of(server("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));
    when(irc.isReadMarkerAvailable("quassel")).thenReturn(true);
    when(irc.isLabeledResponseAvailable("quassel")).thenReturn(true);

    assertTrue(policy.supportsReadMarker("quassel"));
    assertTrue(policy.supportsLabeledResponse("quassel"));
    assertTrue(policy.supportsQuasselCoreCommands("quassel"));
    assertFalse(policy.persistsJoinedChannelsLocally("quassel"));
    assertFalse(policy.supportsSemanticUpload("quassel"));
    assertTrue(policy.supportsQuasselCoreCommandsByBackendId("quassel-core"));
    assertFalse(policy.persistsJoinedChannelsLocallyByBackendId("quassel-core"));
  }

  @Test
  void featureUnavailableMessageUsesBackendReasonWhenPresent() {
    when(irc.backendAvailabilityReason("matrix")).thenReturn("Matrix backend is not available yet");

    assertEquals(
        "Matrix backend is not available yet.",
        policy.featureUnavailableMessage("matrix", "fallback"));
    assertEquals(
        "Matrix backend is not available yet",
        policy.unavailableReasonForHelp("matrix", "fallback"));
  }

  @Test
  void featureUnavailableMessageFallsBackWhenNoBackendReason() {
    when(irc.backendAvailabilityReason("libera")).thenReturn("");

    assertEquals(
        "MONITOR capability is unavailable on this server.",
        policy.featureUnavailableMessage(
            "libera", "MONITOR capability is unavailable on this server."));
    assertEquals(
        "requires negotiated read-marker or draft/read-marker",
        policy.unavailableReasonForHelp(
            "libera", "requires negotiated read-marker or draft/read-marker"));
  }

  @Test
  void supportsCustomBackendIdsViaAdapterRegistry() {
    when(serverCatalog.find("plugin")).thenReturn(Optional.of(server("plugin", "plugin-backend")));

    OutboundBackendFeatureRegistry registry =
        new OutboundBackendFeatureRegistry(List.of(new PluginBackendFeatureAdapter()));
    OutboundBackendCapabilityPolicy customPolicy =
        new OutboundBackendCapabilityPolicy(
            commandTargetPolicy, registry, IrcNegotiatedFeaturePort.from(irc), backendAvailability);

    assertTrue(customPolicy.supportsSemanticUpload("plugin"));
    assertTrue(customPolicy.supportsSemanticUploadByBackendId("plugin-backend"));
    assertFalse(customPolicy.supportsQuasselCoreCommandsByBackendId("plugin-backend"));
  }

  private static IrcProperties.Server server(String id, IrcProperties.Server.Backend backend) {
    return new IrcProperties.Server(
        id,
        "core.example.net",
        4242,
        false,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        null,
        List.of(),
        List.of(),
        null,
        backend);
  }

  private static IrcProperties.Server server(String id, String backendId) {
    return new IrcProperties.Server(
        id,
        "plugin.example.net",
        9000,
        false,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        null,
        List.of(),
        List.of(),
        null,
        backendId);
  }

  private static final class PluginBackendFeatureAdapter implements OutboundBackendFeatureAdapter {
    @Override
    public String backendId() {
      return "plugin-backend";
    }

    @Override
    public boolean supportsSemanticUpload() {
      return true;
    }
  }
}
