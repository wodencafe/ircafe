package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.outbound.UploadCommandTranslationHandler;
import cafe.woden.ircclient.config.IrcProperties;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.SecondaryAdapter;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

@Component
@SecondaryAdapter
@ApplicationLayer
@RequiredArgsConstructor
public final class MatrixUploadCommandTranslationHandler
    implements UploadCommandTranslationHandler {
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
