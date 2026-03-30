package cafe.woden.ircclient.ui.backend;

import cafe.woden.ircclient.app.api.AvailableBackendIdsPort;
import cafe.woden.ircclient.app.api.BackendEditorProfileCatalog;
import cafe.woden.ircclient.app.api.BackendUiMode;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.irc.backend.IrcBackendModePort;
import java.util.Objects;
import org.jmolecules.architecture.layered.InterfaceLayer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Central provider for backend-aware UI profiles scoped by server id. */
@Component
@InterfaceLayer
public class BackendUiProfileProvider {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  private final IrcBackendModePort backendMode;
  private final AvailableBackendIdsPort backendMetadata;
  private final BackendEditorProfileCatalog backendProfiles;
  private final BackendUiContext backendUiContext;

  public BackendUiProfileProvider(
      @Qualifier("ircClientService") IrcBackendModePort backendMode,
      AvailableBackendIdsPort backendMetadata) {
    this.backendMode = Objects.requireNonNull(backendMode, "backendMode");
    this.backendMetadata =
        Objects.requireNonNullElseGet(backendMetadata, AvailableBackendIdsPort::builtInsOnly);
    this.backendProfiles = BackendEditorProfileCatalog.from(this.backendMetadata);
    this.backendUiContext = BackendUiContext.fromBackendUiModeResolver(this::uiModeForServer);
  }

  BackendUiProfileProvider(IrcBackendModePort backendMode) {
    this(backendMode, AvailableBackendIdsPort.builtInsOnly());
  }

  public BackendUiContext backendUiContext() {
    return backendUiContext;
  }

  public BackendUiProfile profileForServer(String serverId) {
    return new BackendUiProfile(normalizeServerId(serverId), backendUiContext);
  }

  public boolean supportsQuasselCoreCommands(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return false;
    return backendProfiles.supportsQuasselCoreCommands(backendIdForServer(sid));
  }

  public String backendIdForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return "";
    return BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendMode.backendIdForServer(sid));
  }

  public String backendDisplayNameForServer(String serverId) {
    String backendId = backendIdForServer(serverId);
    if (backendId.isEmpty()) return "";
    return backendDisplayName(backendId);
  }

  public String backendDisplayName(String backendId) {
    String normalized = BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendId);
    String displayName =
        Objects.toString(backendMetadata.backendDisplayName(normalized), "").trim();
    return displayName.isEmpty() ? normalized : displayName;
  }

  private BackendUiMode uiModeForServer(String serverId) {
    return backendProfiles.uiModeForBackendId(backendIdForServer(serverId));
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
