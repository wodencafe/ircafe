package cafe.woden.ircclient.ui.servers;

import cafe.woden.ircclient.app.api.BackendEditorProfileSpec;
import cafe.woden.ircclient.app.api.BuiltInBackendEditorProfiles;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Built-in backend profiles used by {@link ServerEditorDialog}. */
final class ServerEditorBackendProfiles {
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();
  private static final ServerEditorBackendProfiles BUILT_INS =
      new ServerEditorBackendProfiles(profilesFromSpecs(BuiltInBackendEditorProfiles.all()));

  private final List<ServerEditorBackendProfile> profiles;
  private final Map<String, ServerEditorBackendProfile> profilesByBackendId;

  private ServerEditorBackendProfiles(List<ServerEditorBackendProfile> profiles) {
    ArrayList<ServerEditorBackendProfile> ordered = new ArrayList<>();
    LinkedHashMap<String, ServerEditorBackendProfile> indexed = new LinkedHashMap<>();
    for (ServerEditorBackendProfile profile :
        Objects.requireNonNullElse(profiles, List.<ServerEditorBackendProfile>of())) {
      if (profile == null) continue;
      ordered.add(profile);
      ServerEditorBackendProfile previous = indexed.putIfAbsent(profile.backendId(), profile);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate server editor backend profile registered for " + profile.backendId());
      }
    }
    if (!indexed.containsKey(defaultBackendId())) {
      throw new IllegalStateException("Missing IRC server editor backend profile");
    }
    this.profiles = List.copyOf(ordered);
    this.profilesByBackendId = Map.copyOf(indexed);
  }

  static ServerEditorBackendProfiles builtIns() {
    return BUILT_INS;
  }

  static ServerEditorBackendProfiles forAvailableBackendIds(List<String> backendIds) {
    return forAvailableBackends(backendIds, List.of());
  }

  static ServerEditorBackendProfiles forAvailableBackends(
      List<String> backendIds, List<BackendEditorProfileSpec> explicitProfiles) {
    LinkedHashMap<String, ServerEditorBackendProfile> indexed = new LinkedHashMap<>();
    for (ServerEditorBackendProfile profile : BUILT_INS.profiles) {
      indexed.put(profile.backendId(), profile);
    }
    for (BackendEditorProfileSpec profileSpec :
        Objects.requireNonNullElse(explicitProfiles, List.<BackendEditorProfileSpec>of())) {
      if (profileSpec == null) continue;
      indexed.put(profileSpec.backendId(), toProfile(profileSpec));
    }
    for (String backendId : Objects.requireNonNullElse(backendIds, List.<String>of())) {
      String normalized = BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendId);
      if (indexed.containsKey(normalized)) {
        continue;
      }
      indexed.put(normalized, BUILT_INS.fallbackProfile(normalized));
    }
    return new ServerEditorBackendProfiles(new ArrayList<>(indexed.values()));
  }

  List<String> selectableBackendIds(String selectedBackendId) {
    String normalized = BACKEND_DESCRIPTORS.normalizeIdOrDefault(selectedBackendId);
    ArrayList<String> backendIds = new ArrayList<>(profiles.size() + 1);
    for (ServerEditorBackendProfile profile : profiles) {
      backendIds.add(profile.backendId());
    }
    if (!profilesByBackendId.containsKey(normalized)) {
      backendIds.add(normalized);
    }
    return List.copyOf(backendIds);
  }

  String defaultBackendId() {
    return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC);
  }

  ServerEditorBackendProfile profileFor(IrcProperties.Server.Backend backend) {
    return profileForBackendId(BACKEND_DESCRIPTORS.idFor(backend));
  }

  ServerEditorBackendProfile profileForBackendId(String backendId) {
    String normalized = BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendId);
    ServerEditorBackendProfile profile = profilesByBackendId.get(normalized);
    return profile != null ? profile : fallbackProfile(normalized);
  }

  private ServerEditorBackendProfile fallbackProfile(String backendId) {
    ServerEditorBackendProfile defaults = profilesByBackendId.get(defaultBackendId());
    return new ServerEditorBackendProfile(
        backendId,
        fallbackDisplayName(backendId),
        defaults.defaultPlainPort(),
        defaults.defaultTlsPort(),
        defaults.directAuthEnabled(),
        defaults.matrixAuthSupported(),
        defaults.requiresNick(),
        defaults.usesNickAsDefaultLogin(),
        defaults.supportsQuasselCoreCommands(),
        defaults.defaultLoginFallback(),
        defaults.hostLabel(),
        defaults.serverPasswordLabel(),
        defaults.nickLabel(),
        defaults.loginLabel(),
        defaults.realNameLabel(),
        defaults.tlsToggleLabel(),
        defaults.connectionHint(),
        defaults.authDisabledHint(),
        defaults.serverPasswordPlaceholder(),
        defaults.hostPlaceholder(),
        defaults.loginPlaceholder(),
        defaults.nickPlaceholder(),
        defaults.realNamePlaceholder());
  }

  private String fallbackDisplayName(String backendId) {
    return BACKEND_DESCRIPTORS
        .descriptorForId(backendId)
        .map(descriptor -> descriptor.displayName())
        .orElse(backendId);
  }

  private static List<ServerEditorBackendProfile> profilesFromSpecs(
      List<BackendEditorProfileSpec> profileSpecs) {
    ArrayList<ServerEditorBackendProfile> profiles = new ArrayList<>();
    for (BackendEditorProfileSpec profileSpec :
        Objects.requireNonNullElse(profileSpecs, List.<BackendEditorProfileSpec>of())) {
      if (profileSpec == null) continue;
      profiles.add(toProfile(profileSpec));
    }
    return List.copyOf(profiles);
  }

  private static ServerEditorBackendProfile toProfile(BackendEditorProfileSpec profileSpec) {
    return new ServerEditorBackendProfile(
        profileSpec.backendId(),
        profileSpec.displayName(),
        profileSpec.defaultPlainPort(),
        profileSpec.defaultTlsPort(),
        profileSpec.directAuthEnabled(),
        profileSpec.matrixAuthSupported(),
        profileSpec.requiresNick(),
        profileSpec.usesNickAsDefaultLogin(),
        profileSpec.supportsQuasselCoreCommands(),
        profileSpec.defaultLoginFallback(),
        profileSpec.hostLabel(),
        profileSpec.serverPasswordLabel(),
        profileSpec.nickLabel(),
        profileSpec.loginLabel(),
        profileSpec.realNameLabel(),
        profileSpec.tlsToggleLabel(),
        profileSpec.connectionHint(),
        profileSpec.authDisabledHint(),
        profileSpec.serverPasswordPlaceholder(),
        profileSpec.hostPlaceholder(),
        profileSpec.loginPlaceholder(),
        profileSpec.nickPlaceholder(),
        profileSpec.realNamePlaceholder());
  }
}
