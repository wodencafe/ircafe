package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles Matrix-specific outbound command logic and semantic /upload translation. */
@Component
@ApplicationLayer
final class MatrixOutboundCommandService implements SemanticUploadCommandHandler {

  private final UiPort ui;
  private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;
  private final MatrixOutboundCommandSupport matrixCommandSupport;
  private final BackendUploadCommandRegistry backendUploadCommandRegistry;

  MatrixOutboundCommandService(
      UiPort ui,
      OutboundBackendCapabilityPolicy backendCapabilityPolicy,
      MatrixOutboundCommandSupport matrixCommandSupport,
      BackendUploadCommandRegistry backendUploadCommandRegistry) {
    this.ui = Objects.requireNonNull(ui, "ui");
    this.backendCapabilityPolicy =
        Objects.requireNonNull(backendCapabilityPolicy, "backendCapabilityPolicy");
    this.matrixCommandSupport =
        Objects.requireNonNull(matrixCommandSupport, "matrixCommandSupport");
    this.backendUploadCommandRegistry =
        Objects.requireNonNull(backendUploadCommandRegistry, "backendUploadCommandRegistry");
  }

  @Override
  public void appendUploadHelp(TargetRef out) {
    matrixCommandSupport.appendUploadHelp(ui, out);
  }

  @Override
  public void appendUploadUsage(TargetRef out) {
    matrixCommandSupport.appendUploadUsage(ui, out);
  }

  @Override
  public UploadPreparation prepareUpload(
      TargetRef target, String msgType, String path, String caption) {
    if (target == null) return usage();

    String normalizedType = matrixCommandSupport.normalizeUploadMsgType(msgType);
    String sourcePath = matrixCommandSupport.normalizeUploadPath(path);
    if (normalizedType.isEmpty() || sourcePath.isEmpty()) {
      return usage();
    }

    IrcProperties.Server.Backend backend =
        backendCapabilityPolicy.backendForServer(target.serverId());
    if (!backendCapabilityPolicy.supportsSemanticUpload(backend)) {
      return unsupportedBackend(
          "Server '"
              + target.serverId()
              + "' does not use the Matrix backend. /upload is only available on Matrix-backed servers.");
    }
    UploadCommandTranslationHandler translationHandler = backendUploadCommandRegistry.find(backend);
    if (translationHandler == null) {
      return unsupportedBackend(
          "Server '"
              + target.serverId()
              + "' does not use the Matrix backend. /upload is only available on Matrix-backed servers.");
    }

    String body = Objects.toString(caption, "").trim();
    String displayBody =
        body.isEmpty() ? matrixCommandSupport.defaultUploadCaption(sourcePath) : body;
    String line =
        translationHandler.translateUpload(
            target.target(), normalizedType, sourcePath, displayBody);
    if (line.isBlank()) {
      return usage();
    }
    return success(line);
  }

  private static UploadPreparation success(String line) {
    return new UploadPreparation(Objects.toString(line, ""), "", false);
  }

  private static UploadPreparation unsupportedBackend(String message) {
    return new UploadPreparation("", Objects.toString(message, ""), false);
  }

  private static UploadPreparation usage() {
    return new UploadPreparation("", "", true);
  }
}
