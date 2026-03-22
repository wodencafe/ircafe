package cafe.woden.ircclient.app.outbound.upload.spi;

import cafe.woden.ircclient.config.IrcProperties;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Backend translator for semantic /upload command arguments into outbound protocol lines. */
@SecondaryPort
@ApplicationLayer
public interface UploadCommandTranslationHandler {

  IrcProperties.Server.Backend backend();

  String translateUpload(String target, String msgType, String sourcePath, String displayBody);
}
