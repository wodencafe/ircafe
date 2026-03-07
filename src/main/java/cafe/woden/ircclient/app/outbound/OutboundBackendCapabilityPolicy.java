package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Backend capability facade used by outbound command services. */
@Component
final class OutboundBackendCapabilityPolicy {

  private final CommandTargetPolicy commandTargetPolicy;
  private final OutboundBackendFeatureRegistry outboundBackendFeatureRegistry;

  OutboundBackendCapabilityPolicy(
      CommandTargetPolicy commandTargetPolicy,
      OutboundBackendFeatureRegistry outboundBackendFeatureRegistry) {
    this.commandTargetPolicy = Objects.requireNonNull(commandTargetPolicy, "commandTargetPolicy");
    this.outboundBackendFeatureRegistry =
        Objects.requireNonNull(outboundBackendFeatureRegistry, "outboundBackendFeatureRegistry");
  }

  IrcProperties.Server.Backend backendForServer(String serverId) {
    return commandTargetPolicy.backendForServer(serverId);
  }

  boolean supportsSemanticUpload(String serverId) {
    return supportsSemanticUpload(backendForServer(serverId));
  }

  boolean supportsSemanticUpload(IrcProperties.Server.Backend backend) {
    return outboundBackendFeatureRegistry.adapterFor(backend).supportsSemanticUpload();
  }

  boolean supportsQuasselCoreCommands(String serverId) {
    return supportsQuasselCoreCommands(backendForServer(serverId));
  }

  boolean supportsQuasselCoreCommands(IrcProperties.Server.Backend backend) {
    return outboundBackendFeatureRegistry.adapterFor(backend).supportsQuasselCoreCommands();
  }
}
