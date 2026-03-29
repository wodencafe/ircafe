package cafe.woden.ircclient.app.outbound.upload.spi;

import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Backend translator for semantic /upload command arguments into outbound protocol lines. */
@SecondaryPort
@ApplicationLayer
public interface UploadCommandTranslationHandler {

  @Deprecated(forRemoval = false)
  default IrcProperties.Server.Backend backend() {
    return BackendDescriptorCatalog.builtIns().backendForId(backendId()).orElse(null);
  }

  default String backendId() {
    return "";
  }

  String translateUpload(String target, String msgType, String sourcePath, String displayBody);
}
