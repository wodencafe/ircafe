package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.config.api.BackendMetadataPort;
import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;
import org.jmolecules.architecture.layered.ApplicationLayer;

/** Lists backend ids available to the app at startup, including installed plugins. */
@SecondaryPort
@ApplicationLayer
public interface AvailableBackendIdsPort extends BackendMetadataPort {

  List<String> availableBackendIds();

  static AvailableBackendIdsPort builtInsOnly() {
    return new AvailableBackendIdsPort() {
      @Override
      public List<String> availableBackendIds() {
        return List.of();
      }
    };
  }

  default List<BackendEditorProfileSpec> availableBackendEditorProfiles() {
    return List.of();
  }
}
