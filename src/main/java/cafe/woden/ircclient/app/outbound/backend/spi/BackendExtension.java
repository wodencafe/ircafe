package cafe.woden.ircclient.app.outbound.backend.spi;

import cafe.woden.ircclient.app.outbound.mutation.MessageMutationOutboundCommands;
import cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTranslationHandler;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** ServiceLoader-backed backend extension bundle for backend-specific outbound behavior. */
@SecondaryPort
@ApplicationLayer
public interface BackendExtension {

  @Deprecated(forRemoval = false)
  default IrcProperties.Server.Backend backend() {
    return BackendDescriptorCatalog.builtIns().backendForId(backendId()).orElse(null);
  }

  default String backendId() {
    return "";
  }

  default OutboundBackendFeatureAdapter featureAdapter() {
    return null;
  }

  default MessageMutationOutboundCommands messageMutationOutboundCommands() {
    return null;
  }

  default UploadCommandTranslationHandler uploadCommandTranslationHandler() {
    return null;
  }
}
