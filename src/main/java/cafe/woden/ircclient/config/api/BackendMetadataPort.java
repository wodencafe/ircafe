package cafe.woden.ircclient.config.api;

import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import java.util.Locale;
import java.util.Objects;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Resolves backend display metadata for both built-in and plugin-provided backend ids. */
@SecondaryPort
@ApplicationLayer
public interface BackendMetadataPort {
  BackendDescriptorCatalog BACKEND_DESCRIPTORS = BackendDescriptorCatalog.builtIns();

  BackendMetadataPort BUILT_INS_ONLY = new BackendMetadataPort() {};

  static BackendMetadataPort builtInsOnly() {
    return BUILT_INS_ONLY;
  }

  default String backendDisplayName(String backendId) {
    String normalized = normalizeBackendId(backendId);
    if (normalized.isEmpty()) return "";
    return BACKEND_DESCRIPTORS
        .descriptorForId(normalized)
        .map(descriptor -> Objects.toString(descriptor.displayName(), "").trim())
        .orElse(normalized);
  }

  default String backendDisplayLabel(String backendId) {
    String displayName = backendDisplayName(backendId);
    if (displayName.isEmpty()) return "";
    return displayName.toLowerCase(Locale.ROOT).endsWith("backend")
        ? displayName
        : displayName + " backend";
  }

  private static String normalizeBackendId(String backendId) {
    String normalized = Objects.toString(backendId, "").trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) return "";
    return BACKEND_DESCRIPTORS
        .descriptorForId(normalized)
        .map(descriptor -> Objects.toString(descriptor.id(), "").trim())
        .orElse(normalized);
  }
}
