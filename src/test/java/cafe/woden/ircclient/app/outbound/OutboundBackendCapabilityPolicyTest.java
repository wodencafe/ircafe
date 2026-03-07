package cafe.woden.ircclient.app.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.ServerCatalog;
import cafe.woden.ircclient.irc.IrcBackendAvailabilityPort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutboundBackendCapabilityPolicyTest {

  private final ServerCatalog serverCatalog = mock(ServerCatalog.class);
  private final IrcBackendAvailabilityPort backendAvailability =
      mock(IrcBackendAvailabilityPort.class);
  private final CommandTargetPolicy commandTargetPolicy = new CommandTargetPolicy(serverCatalog);
  private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry =
      new OutboundBackendFeatureRegistry(
          List.of(
              new MatrixOutboundBackendFeatureAdapter(),
              new QuasselOutboundBackendFeatureAdapter()));
  private final OutboundBackendCapabilityPolicy policy =
      new OutboundBackendCapabilityPolicy(
          commandTargetPolicy, outboundBackendFeatureRegistry, backendAvailability);

  @Test
  void supportsMatrixSemanticUploadViaBackendAdapter() {
    when(serverCatalog.find("matrix"))
        .thenReturn(Optional.of(server("matrix", IrcProperties.Server.Backend.MATRIX)));

    assertTrue(policy.supportsSemanticUpload("matrix"));
    assertFalse(policy.supportsQuasselCoreCommands("matrix"));
  }

  @Test
  void supportsQuasselCommandsViaBackendAdapter() {
    when(serverCatalog.find("quassel"))
        .thenReturn(Optional.of(server("quassel", IrcProperties.Server.Backend.QUASSEL_CORE)));

    assertTrue(policy.supportsQuasselCoreCommands("quassel"));
    assertFalse(policy.supportsSemanticUpload("quassel"));
  }

  @Test
  void featureUnavailableMessageUsesBackendReasonWhenPresent() {
    when(backendAvailability.backendAvailabilityReason("matrix"))
        .thenReturn("Matrix backend is not available yet");

    assertEquals(
        "Matrix backend is not available yet.",
        policy.featureUnavailableMessage("matrix", "fallback"));
    assertEquals(
        "Matrix backend is not available yet",
        policy.unavailableReasonForHelp("matrix", "fallback"));
  }

  @Test
  void featureUnavailableMessageFallsBackWhenNoBackendReason() {
    when(backendAvailability.backendAvailabilityReason("libera")).thenReturn("");

    assertEquals(
        "MONITOR capability is unavailable on this server.",
        policy.featureUnavailableMessage(
            "libera", "MONITOR capability is unavailable on this server."));
    assertEquals(
        "requires negotiated read-marker or draft/read-marker",
        policy.unavailableReasonForHelp(
            "libera", "requires negotiated read-marker or draft/read-marker"));
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
}
