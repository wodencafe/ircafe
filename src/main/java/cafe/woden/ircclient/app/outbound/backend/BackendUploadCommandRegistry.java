package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTranslationHandler;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Strategy registry for backend-specific semantic /upload translators. */
@Component
@ApplicationLayer
public final class BackendUploadCommandRegistry {

  private final Map<IrcProperties.Server.Backend, UploadCommandTranslationHandler>
      handlersByBackend;

  public BackendUploadCommandRegistry(List<UploadCommandTranslationHandler> handlers) {
    LinkedHashMap<IrcProperties.Server.Backend, UploadCommandTranslationHandler> map =
        new LinkedHashMap<>();
    for (UploadCommandTranslationHandler handler : Objects.requireNonNull(handlers, "handlers")) {
      if (handler == null) continue;
      IrcProperties.Server.Backend backend = handler.backend();
      if (backend == null) continue;
      UploadCommandTranslationHandler previous = map.putIfAbsent(backend, handler);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate upload translation handlers registered for backend " + backend);
      }
    }
    this.handlersByBackend = Map.copyOf(map);
  }

  public UploadCommandTranslationHandler find(IrcProperties.Server.Backend backend) {
    if (backend == null) return null;
    return handlersByBackend.get(backend);
  }
}
