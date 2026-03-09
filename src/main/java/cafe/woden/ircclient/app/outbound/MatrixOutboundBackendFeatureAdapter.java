package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.config.IrcProperties;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

@Component
@ApplicationLayer
final class MatrixOutboundBackendFeatureAdapter implements OutboundBackendFeatureAdapter {

  @Override
  public IrcProperties.Server.Backend backend() {
    return IrcProperties.Server.Backend.MATRIX;
  }

  @Override
  public boolean supportsSemanticUpload() {
    return true;
  }
}
