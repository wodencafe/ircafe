package cafe.woden.ircclient.ui.backend;

import cafe.woden.ircclient.app.api.AvailableBackendIdsPort;
import cafe.woden.ircclient.app.api.BackendEditorProfileSpec;
import cafe.woden.ircclient.app.api.BackendUiMode;
import cafe.woden.ircclient.app.api.BuiltInBackendEditorProfiles;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.irc.backend.IrcBackendModePort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private final Map<String, BackendEditorProfileSpec> backendProfilesById;
  private final BackendUiContext backendUiContext;

  public BackendUiProfileProvider(
      @Qualifier("ircClientService") IrcBackendModePort backendMode,
      AvailableBackendIdsPort backendMetadata) {
    this.backendMode = Objects.requireNonNull(backendMode, "backendMode");
    this.backendMetadata =
        Objects.requireNonNullElseGet(backendMetadata, AvailableBackendIdsPort::builtInsOnly);
    this.backendProfilesById = indexBackendProfiles(this.backendMetadata);
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
    return profileForBackendId(backendIdForServer(sid)).supportsQuasselCoreCommands();
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
    return profileForBackendId(backendIdForServer(serverId)).uiMode();
  }

  private BackendEditorProfileSpec profileForBackendId(String backendId) {
    String normalized = BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendId);
    BackendEditorProfileSpec profile = backendProfilesById.get(normalized);
    if (profile != null) {
      return profile;
    }
    return backendProfilesById.get(BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC));
  }

  private static Map<String, BackendEditorProfileSpec> indexBackendProfiles(
      AvailableBackendIdsPort backendMetadata) {
    LinkedHashMap<String, BackendEditorProfileSpec> indexed = new LinkedHashMap<>();
    for (BackendEditorProfileSpec profile : BuiltInBackendEditorProfiles.all()) {
      indexed.put(profile.backendId(), profile);
    }
    for (BackendEditorProfileSpec profile :
        Objects.requireNonNullElse(
            backendMetadata.availableBackendEditorProfiles(),
            List.<BackendEditorProfileSpec>of())) {
      if (profile == null) continue;
      indexed.put(profile.backendId(), profile);
    }
    return Map.copyOf(indexed);
  }

  private static String normalizeServerId(String serverId) {
    return Objects.toString(serverId, "").trim();
  }
}
