package cafe.woden.ircclient.app.outbound.backend;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTranslationHandler;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackendUploadCommandRegistryTest {

  @Test
  void findsHandlerByBackend() {
    UploadCommandTranslationHandler matrix = handlerFor(IrcProperties.Server.Backend.MATRIX);
    BackendUploadCommandRegistry registry = new BackendUploadCommandRegistry(List.of(matrix));

    assertSame(matrix, registry.find(IrcProperties.Server.Backend.MATRIX));
    assertNull(registry.find(IrcProperties.Server.Backend.IRC));
  }

  @Test
  void duplicateBackendHandlersAreRejected() {
    UploadCommandTranslationHandler first = handlerFor(IrcProperties.Server.Backend.MATRIX);
    UploadCommandTranslationHandler second = handlerFor(IrcProperties.Server.Backend.MATRIX);

    assertThrows(
        IllegalStateException.class,
        () -> new BackendUploadCommandRegistry(List.of(first, second)));
  }

  private static UploadCommandTranslationHandler handlerFor(IrcProperties.Server.Backend backend) {
    return new UploadCommandTranslationHandler() {
      @Override
      public IrcProperties.Server.Backend backend() {
        return backend;
      }

      @Override
      public String translateUpload(
          String target, String msgType, String sourcePath, String displayBody) {
        return target + "|" + msgType + "|" + sourcePath + "|" + displayBody;
      }
    };
  }
}
