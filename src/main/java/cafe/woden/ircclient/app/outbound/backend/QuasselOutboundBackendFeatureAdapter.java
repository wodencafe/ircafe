package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.outbound.backend.spi.OutboundBackendFeatureAdapter;
import cafe.woden.ircclient.config.IrcProperties;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

@Component
@SecondaryAdapter
@ApplicationLayer
public final class QuasselOutboundBackendFeatureAdapter implements OutboundBackendFeatureAdapter {

  @Override
  public IrcProperties.Server.Backend backend() {
    return IrcProperties.Server.Backend.QUASSEL_CORE;
  }

  @Override
  public boolean supportsQuasselCoreCommands() {
    return true;
  }
}
