package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.config.IrcProperties;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

@Component
@ApplicationLayer
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixUploadCommandTranslationHandler implements UploadCommandTranslationHandler {
  @NonNull private final MatrixOutboundCommandSupport matrixCommandSupport;

  @Override
  public IrcProperties.Server.Backend backend() {
    return IrcProperties.Server.Backend.MATRIX;
  }

  @Override
  public String translateUpload(
      String target, String msgType, String sourcePath, String displayBody) {
    return matrixCommandSupport.buildUploadPrivmsg(target, msgType, sourcePath, displayBody);
  }
}
