package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.outbound.backend.spi.OutboundBackendFeatureAdapter;
import cafe.woden.ircclient.config.IrcProperties;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

@Component
@SecondaryAdapter
@ApplicationLayer
public final class MatrixOutboundBackendFeatureAdapter implements OutboundBackendFeatureAdapter {

  @Override
  public IrcProperties.Server.Backend backend() {
    return IrcProperties.Server.Backend.MATRIX;
  }

  @Override
  public boolean supportsSemanticUpload() {
    return true;
  }
}
