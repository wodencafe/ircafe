package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.app.api.AvailableBackendIdsPort;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.stereotype.Component;

/** Supplies the server editor with all backend ids known at startup, including plugin backends. */
@Component
@InterfaceLayer
final class ServerEditorBackendProfilesProvider {
  private final ServerEditorBackendProfiles backendProfiles;

  ServerEditorBackendProfilesProvider(AvailableBackendIdsPort availableBackendIdsPort) {
    this.backendProfiles =
        ServerEditorBackendProfiles.forAvailableBackends(
            availableBackendIdsPort.availableBackendIds(),
            availableBackendIdsPort.availableBackendEditorProfiles());
  }

  ServerEditorBackendProfiles backendProfiles() {
    return backendProfiles;
  }
}
