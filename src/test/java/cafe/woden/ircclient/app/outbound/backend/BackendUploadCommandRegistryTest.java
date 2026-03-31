package cafe.woden.ircclient.app.outbound.backend;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTranslationHandler;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackendUploadCommandRegistryTest {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

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

  @Test
  void findsHandlerByCustomBackendId() {
    UploadCommandTranslationHandler pluginHandler = pluginHandler();
    BackendUploadCommandRegistry registry =
        new BackendUploadCommandRegistry(List.of(pluginHandler));

    assertSame(pluginHandler, registry.find("plugin"));
  }

  private static UploadCommandTranslationHandler handlerFor(IrcProperties.Server.Backend backend) {
    return new UploadCommandTranslationHandler() {
      @Override
      public String backendId() {
        return BACKEND_DESCRIPTORS.idFor(backend);
      }

      @Override
      public String translateUpload(
          String target, String msgType, String sourcePath, String displayBody) {
        return target + "|" + msgType + "|" + sourcePath + "|" + displayBody;
      }
    };
  }

  private static UploadCommandTranslationHandler pluginHandler() {
    return new UploadCommandTranslationHandler() {
      @Override
      public String backendId() {
        return "plugin";
      }

      @Override
      public String translateUpload(
          String target, String msgType, String sourcePath, String displayBody) {
        return target + "|" + msgType + "|" + sourcePath + "|" + displayBody;
      }
    };
  }
}
