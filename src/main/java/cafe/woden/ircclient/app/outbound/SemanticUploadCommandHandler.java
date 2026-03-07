package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.model.TargetRef;

/** Backend-specific translator/validator for semantic /upload commands. */
interface SemanticUploadCommandHandler {

  void appendUploadHelp(TargetRef out);

  void appendUploadUsage(TargetRef out);

  UploadPreparation prepareUpload(TargetRef target, String msgType, String path, String caption);

  record UploadPreparation(String line, String statusMessage, boolean showUsage) {}
}
