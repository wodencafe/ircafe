package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.model.TargetRef;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Backend-specific translator/validator for semantic /upload commands. */
@SecondaryPort
@ApplicationLayer
public interface SemanticUploadCommandHandler {

  void appendUploadHelp(TargetRef out);

  void appendUploadUsage(TargetRef out);

  UploadPreparation prepareUpload(TargetRef target, String msgType, String path, String caption);

  record UploadPreparation(String line, String statusMessage, boolean showUsage) {}
}
