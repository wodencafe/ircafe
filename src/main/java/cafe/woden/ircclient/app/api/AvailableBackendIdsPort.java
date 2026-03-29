package cafe.woden.ircclient.app.api;

import java.util.List;
import java.util.Objects;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Lists backend ids available to the app at startup, including installed plugins. */
@SecondaryPort
@ApplicationLayer
public interface AvailableBackendIdsPort {
  List<String> availableBackendIds();

  default List<BackendEditorProfileSpec> availableBackendEditorProfiles() {
    return List.of();
  }

  default String backendDisplayName(String backendId) {
    return Objects.toString(backendId, "").trim();
  }
}
