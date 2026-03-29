package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.outbound.backend.spi.BackendExtension;
import cafe.woden.ircclient.app.outbound.backend.spi.OutboundBackendFeatureAdapter;
import cafe.woden.ircclient.app.outbound.mutation.MessageMutationOutboundCommands;
import cafe.woden.ircclient.config.IrcProperties;
import com.google.auto.service.AutoService;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Built-in backend extension for the standard IRC transport. */
@Component
@SecondaryAdapter
@ApplicationLayer
@AutoService(BackendExtension.class)
public final class IrcBackendExtension implements BackendExtension {

  private static final MessageMutationOutboundCommands MESSAGE_MUTATION_COMMANDS =
      new IrcMessageMutationOutboundCommands();

  private static final OutboundBackendFeatureAdapter FEATURE_ADAPTER =
      new OutboundBackendFeatureAdapter() {
        @Override
        public IrcProperties.Server.Backend backend() {
          return IrcProperties.Server.Backend.IRC;
        }
      };

  @Override
  public IrcProperties.Server.Backend backend() {
    return IrcProperties.Server.Backend.IRC;
  }

  @Override
  public OutboundBackendFeatureAdapter featureAdapter() {
    return FEATURE_ADAPTER;
  }

  @Override
  public MessageMutationOutboundCommands messageMutationOutboundCommands() {
    return MESSAGE_MUTATION_COMMANDS;
  }
}
