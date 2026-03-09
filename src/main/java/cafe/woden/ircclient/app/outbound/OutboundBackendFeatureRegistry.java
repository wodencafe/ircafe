package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.config.IrcProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.springframework.stereotype.Component;

/** Registry for backend-specific outbound feature adapters. */
@Component
@ApplicationLayer
final class OutboundBackendFeatureRegistry {

  private static final OutboundBackendFeatureAdapter DEFAULT_ADAPTER =
      new OutboundBackendFeatureAdapter() {
        @Override
        public IrcProperties.Server.Backend backend() {
          return IrcProperties.Server.Backend.IRC;
        }
      };

  private final Map<IrcProperties.Server.Backend, OutboundBackendFeatureAdapter> adaptersByBackend;

  OutboundBackendFeatureRegistry(List<OutboundBackendFeatureAdapter> adapters) {
    LinkedHashMap<IrcProperties.Server.Backend, OutboundBackendFeatureAdapter> map =
        new LinkedHashMap<>();
    if (adapters != null) {
      for (OutboundBackendFeatureAdapter adapter : adapters) {
        if (adapter == null) continue;
        IrcProperties.Server.Backend backend = adapter.backend();
        if (backend == null) continue;
        OutboundBackendFeatureAdapter previous = map.putIfAbsent(backend, adapter);
        if (previous != null) {
          throw new IllegalStateException(
              "Duplicate outbound backend feature adapters registered for backend " + backend);
        }
      }
    }
    this.adaptersByBackend = Map.copyOf(map);
  }

  OutboundBackendFeatureAdapter adapterFor(IrcProperties.Server.Backend backend) {
    if (backend == null) return DEFAULT_ADAPTER;
    return adaptersByBackend.getOrDefault(backend, DEFAULT_ADAPTER);
  }
}
