package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.config.IrcProperties;
import org.springframework.stereotype.Component;

@Component
final class QuasselOutboundBackendFeatureAdapter implements OutboundBackendFeatureAdapter {

  @Override
  public IrcProperties.Server.Backend backend() {
    return IrcProperties.Server.Backend.QUASSEL_CORE;
  }

  @Override
  public boolean supportsQuasselCoreCommands() {
    return true;
  }
}
