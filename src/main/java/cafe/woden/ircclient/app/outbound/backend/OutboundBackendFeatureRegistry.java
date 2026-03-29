package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.outbound.backend.spi.OutboundBackendFeatureAdapter;
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

/** Registry for backend-specific outbound feature adapters. */
@Component
@ApplicationLayer
public final class OutboundBackendFeatureRegistry {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  private static final OutboundBackendFeatureAdapter DEFAULT_ADAPTER =
      new OutboundBackendFeatureAdapter() {
        @Override
        public String backendId() {
          return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC);
        }
      };

  private final BackendExtensionCatalog backendExtensionCatalog;
  private final Map<String, OutboundBackendFeatureAdapter> adaptersByBackendId;

  @Autowired
  public OutboundBackendFeatureRegistry(BackendExtensionCatalog backendExtensionCatalog) {
    this.backendExtensionCatalog =
        Objects.requireNonNull(backendExtensionCatalog, "backendExtensionCatalog");
    this.adaptersByBackendId = Map.of();
  }

  public OutboundBackendFeatureRegistry(List<OutboundBackendFeatureAdapter> adapters) {
    this(indexAdapters(Objects.requireNonNullElse(adapters, List.of())));
  }

  private OutboundBackendFeatureRegistry(
      Map<String, OutboundBackendFeatureAdapter> adaptersByBackendId) {
    this.backendExtensionCatalog = null;
    this.adaptersByBackendId =
        Map.copyOf(Objects.requireNonNull(adaptersByBackendId, "adaptersByBackendId"));
  }

  private static Map<String, OutboundBackendFeatureAdapter> indexAdapters(
      List<OutboundBackendFeatureAdapter> adapters) {
    LinkedHashMap<String, OutboundBackendFeatureAdapter> map = new LinkedHashMap<>();
    for (OutboundBackendFeatureAdapter adapter : adapters) {
      if (adapter == null) continue;
      String backendId = backendIdOf(adapter);
      if (backendId.isEmpty()) continue;
      OutboundBackendFeatureAdapter previous = map.putIfAbsent(backendId, adapter);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate outbound backend feature adapters registered for backend " + backendId);
      }
    }
    return Map.copyOf(map);
  }

  @Deprecated(forRemoval = false)
  public OutboundBackendFeatureAdapter adapterFor(IrcProperties.Server.Backend backend) {
    return adapterFor(backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend));
  }

  public OutboundBackendFeatureAdapter adapterFor(String backendId) {
    String id = normalizeBackendId(backendId);
    if (id.isEmpty()) return DEFAULT_ADAPTER;
    if (backendExtensionCatalog != null) {
      return backendExtensionCatalog.featureAdapterFor(id);
    }
    return adaptersByBackendId.getOrDefault(id, DEFAULT_ADAPTER);
  }

  private static String normalizeBackendId(String backendId) {
    return Objects.toString(backendId, "").trim().toLowerCase(Locale.ROOT);
  }

  private static String backendIdOf(OutboundBackendFeatureAdapter adapter) {
    String backendId = normalizeBackendId(adapter.backendId());
    if (!backendId.isEmpty()) {
      return backendId;
    }
    IrcProperties.Server.Backend backend = adapter.backend();
    return backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend);
  }
}
