package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
final class MatrixUploadCommandTranslationHandler implements UploadCommandTranslationHandler {
  private final MatrixOutboundCommandSupport matrixCommandSupport;

  MatrixUploadCommandTranslationHandler(MatrixOutboundCommandSupport matrixCommandSupport) {
    this.matrixCommandSupport =
        Objects.requireNonNull(matrixCommandSupport, "matrixCommandSupport");
  }

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
