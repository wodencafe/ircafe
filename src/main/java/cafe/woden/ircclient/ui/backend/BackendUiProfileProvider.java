package cafe.woden.ircclient.ui.backend;

import cafe.woden.ircclient.app.api.AvailableBackendIdsPort;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.irc.backend.IrcBackendModePort;
import java.util.List;
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
  private final BackendUiContext backendUiContext;

  public BackendUiProfileProvider(
      @Qualifier("ircClientService") IrcBackendModePort backendMode,
      AvailableBackendIdsPort backendMetadata) {
    this.backendMode = Objects.requireNonNull(backendMode, "backendMode");
    this.backendMetadata =
        Objects.requireNonNullElseGet(
            backendMetadata, BackendUiProfileProvider::defaultBackendMetadata);
    this.backendUiContext = BackendUiContext.fromBackendModePort(backendMode);
  }

  BackendUiProfileProvider(IrcBackendModePort backendMode) {
    this(backendMode, defaultBackendMetadata());
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
    return backendMode.supportsQuasselCoreCommands(sid);
  }

  public String backendDisplayNameForServer(String serverId) {
    String sid = normalizeServerId(serverId);
    if (sid.isEmpty()) return "";
    return backendDisplayName(backendMode.backendIdForServer(sid));
  }

  public String backendDisplayName(String backendId) {
    String normalized = BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendId);
    String displayName =
        Objects.toString(backendMetadata.backendDisplayName(normalized), "").trim();
    return displayName.isEmpty() ? normalized : displayName;
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }

  private static AvailableBackendIdsPort defaultBackendMetadata() {
    return new AvailableBackendIdsPort() {
      @Override
      public List<String> availableBackendIds() {
        return List.of();
      }

      @Override
      public String backendDisplayName(String backendId) {
        String normalized = BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendId);
        return BACKEND_DESCRIPTORS
            .descriptorForId(normalized)
            .map(descriptor -> Objects.toString(descriptor.displayName(), "").trim())
            .orElse(normalized);
      }
    };
  }
}
