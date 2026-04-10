package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTranslationHandler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Strategy registry for backend-specific semantic /upload translators. */
@Component
@ApplicationLayer
@RequiredArgsConstructor
public final class BackendUploadCommandRegistry {
  @NonNull private final BackendExtensionCatalog backendExtensionCatalog;

  @Deprecated(forRemoval = false)
  public UploadCommandTranslationHandler find(
      cafe.woden.ircclient.config.IrcProperties.Server.Backend backend) {
    return backendExtensionCatalog.uploadTranslationHandlerFor(backend);
  }

  public UploadCommandTranslationHandler find(String backendId) {
    return backendExtensionCatalog.uploadTranslationHandlerFor(backendId);
  }
}
