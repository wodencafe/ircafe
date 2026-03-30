package cafe.woden.ircclient.app.api;

import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Immutable lookup of built-in and plugin-provided backend editor profiles. */
public final class BackendEditorProfileCatalog {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();
  private static final BackendEditorProfileCatalog BUILT_INS =
      new BackendEditorProfileCatalog(BuiltInBackendEditorProfiles.all());

  private final Map<String, BackendEditorProfileSpec> profilesByBackendId;

  private BackendEditorProfileCatalog(List<BackendEditorProfileSpec> profiles) {
    LinkedHashMap<String, BackendEditorProfileSpec> indexed = new LinkedHashMap<>();
    for (BackendEditorProfileSpec profile :
        Objects.requireNonNullElse(profiles, List.<BackendEditorProfileSpec>of())) {
      if (profile == null) continue;
      String backendId = normalizeBackendId(profile.backendId());
      if (backendId.isEmpty()) {
        throw new IllegalStateException("Backend editor profile id must not be blank");
      }
      BackendEditorProfileSpec previous = indexed.put(backendId, profile);
      if (previous != null && !previous.backendId().equals(profile.backendId())) {
        throw new IllegalStateException(
            "Duplicate backend editor profile registered for backend id " + backendId);
      }
    }
    if (!indexed.containsKey(defaultBackendId())) {
      throw new IllegalStateException("Missing IRC backend editor profile");
    }
    this.profilesByBackendId = Map.copyOf(indexed);
  }

  public static BackendEditorProfileCatalog builtIns() {
    return BUILT_INS;
  }

  public static BackendEditorProfileCatalog from(AvailableBackendIdsPort backendMetadata) {
    if (backendMetadata == null) {
      return builtIns();
    }
    return fromProfiles(backendMetadata.availableBackendEditorProfiles());
  }

  public static BackendEditorProfileCatalog fromProfiles(List<BackendEditorProfileSpec> profiles) {
    if (profiles == null) {
      return builtIns();
    }
    ArrayList<BackendEditorProfileSpec> merged =
        new ArrayList<>(BuiltInBackendEditorProfiles.all().size() + profiles.size());
    merged.addAll(BuiltInBackendEditorProfiles.all());
    merged.addAll(profiles);
    return new BackendEditorProfileCatalog(merged);
  }

  public List<BackendEditorProfileSpec> profiles() {
    return List.copyOf(profilesByBackendId.values());
  }

  public BackendEditorProfileSpec profileForBackendId(String backendId) {
    String normalized = normalizeBackendId(backendId);
    BackendEditorProfileSpec profile = profilesByBackendId.get(normalized);
    if (profile != null) {
      return profile;
    }
    return profilesByBackendId.get(defaultBackendId());
  }

  public BackendUiMode uiModeForBackendId(String backendId) {
    return profileForBackendId(backendId).uiMode();
  }

  public boolean supportsQuasselCoreCommands(String backendId) {
    return profileForBackendId(backendId).supportsQuasselCoreCommands();
  }

  public String displayName(String backendId) {
    String normalized = normalizeBackendId(backendId);
    BackendEditorProfileSpec profile = profilesByBackendId.get(normalized);
    if (profile != null) {
      return profile.displayName();
    }
    return BACKEND_DESCRIPTORS
        .descriptorForId(normalized)
        .map(descriptor -> Objects.toString(descriptor.displayName(), "").trim())
        .orElse(normalized);
  }

  private static String defaultBackendId() {
    return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC);
  }

  private static String normalizeBackendId(String backendId) {
    String normalized = Objects.toString(backendId, "").trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      return defaultBackendId();
    }
    return BACKEND_DESCRIPTORS.normalizeIdOrDefault(normalized);
  }
}
