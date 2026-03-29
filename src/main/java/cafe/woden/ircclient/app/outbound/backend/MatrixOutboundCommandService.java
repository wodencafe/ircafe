package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.app.outbound.upload.spi.SemanticUploadCommandHandler;
import cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTranslationHandler;
import cafe.woden.ircclient.model.TargetRef;
import java.util.Objects;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Handles Matrix-specific outbound command logic and semantic /upload translation. */
@Component
@SecondaryAdapter
@ApplicationLayer
@RequiredArgsConstructor
public final class MatrixOutboundCommandService implements SemanticUploadCommandHandler {

  @NonNull private final UiPort ui;
  @NonNull private final OutboundBackendCapabilityPolicy backendCapabilityPolicy;
  @NonNull private final MatrixOutboundCommandSupport matrixCommandSupport;
  @NonNull private final BackendUploadCommandRegistry backendUploadCommandRegistry;

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

    String backendId = backendCapabilityPolicy.backendIdForServer(target.serverId());
    if (!backendCapabilityPolicy.supportsSemanticUpload(target.serverId())) {
      return unsupportedBackend(
          "Server '"
              + target.serverId()
              + "' does not use the Matrix backend. /upload is only available on Matrix-backed servers.");
    }
    UploadCommandTranslationHandler translationHandler =
        backendUploadCommandRegistry.find(backendId);
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
