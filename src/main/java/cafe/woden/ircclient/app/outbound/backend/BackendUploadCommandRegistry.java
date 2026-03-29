package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTranslationHandler;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Strategy registry for backend-specific semantic /upload translators. */
@Component
@ApplicationLayer
public final class BackendUploadCommandRegistry {

  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  private final BackendExtensionCatalog backendExtensionCatalog;
  private final Map<String, UploadCommandTranslationHandler> handlersByBackendId;

  @Autowired
  public BackendUploadCommandRegistry(BackendExtensionCatalog backendExtensionCatalog) {
    this.backendExtensionCatalog =
        Objects.requireNonNull(backendExtensionCatalog, "backendExtensionCatalog");
    this.handlersByBackendId = Map.of();
  }

  public BackendUploadCommandRegistry(List<UploadCommandTranslationHandler> handlers) {
    this(indexHandlers(Objects.requireNonNull(handlers, "handlers")));
  }

  private BackendUploadCommandRegistry(
      Map<String, UploadCommandTranslationHandler> handlersByBackendId) {
    this.backendExtensionCatalog = null;
    this.handlersByBackendId =
        Map.copyOf(Objects.requireNonNull(handlersByBackendId, "handlersByBackendId"));
  }

  private static Map<String, UploadCommandTranslationHandler> indexHandlers(
      List<UploadCommandTranslationHandler> handlers) {
    LinkedHashMap<String, UploadCommandTranslationHandler> map = new LinkedHashMap<>();
    for (UploadCommandTranslationHandler handler : handlers) {
      if (handler == null) continue;
      String backendId = normalizeBackendId(handler.backendId());
      if (backendId.isEmpty()) continue;
      UploadCommandTranslationHandler previous = map.putIfAbsent(backendId, handler);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate upload translation handlers registered for backend " + backendId);
      }
    }
    return Map.copyOf(map);
  }

  public UploadCommandTranslationHandler find(IrcProperties.Server.Backend backend) {
    return find(backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend));
  }

  public UploadCommandTranslationHandler find(String backendId) {
    String id = normalizeBackendId(backendId);
    if (id.isEmpty()) return null;
    if (backendExtensionCatalog != null) {
      return backendExtensionCatalog.uploadTranslationHandlerFor(id);
    }
    return handlersByBackendId.get(id);
  }

  private static String normalizeBackendId(String backendId) {
    return Objects.toString(backendId, "").trim().toLowerCase(Locale.ROOT);
  }
}
