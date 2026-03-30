package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Lists backend ids available to the app at startup, including installed plugins. */
@SecondaryPort
@ApplicationLayer
public interface AvailableBackendIdsPort {
  BackendDescriptorCatalog BACKEND_DESCRIPTORS = BackendDescriptorCatalog.builtIns();

  AvailableBackendIdsPort BUILT_INS_ONLY =
      new AvailableBackendIdsPort() {
        @Override
        public List<String> availableBackendIds() {
          return List.of();
        }
      };

  List<String> availableBackendIds();

  static AvailableBackendIdsPort builtInsOnly() {
    return BUILT_INS_ONLY;
  }

  default List<BackendEditorProfileSpec> availableBackendEditorProfiles() {
    return List.of();
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
