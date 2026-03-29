package cafe.woden.ircclient.ui.servers;

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
      new ServerEditorBackendProfiles(List.of(ircProfile(), quasselProfile(), matrixProfile()));

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
    ArrayList<ServerEditorBackendProfile> availableProfiles = new ArrayList<>(BUILT_INS.profiles);
    for (String backendId : Objects.requireNonNullElse(backendIds, List.<String>of())) {
      String normalized = BACKEND_DESCRIPTORS.normalizeIdOrDefault(backendId);
      if (BUILT_INS.profilesByBackendId.containsKey(normalized)) {
        continue;
      }
      availableProfiles.add(BUILT_INS.fallbackProfile(normalized));
    }
    return new ServerEditorBackendProfiles(availableProfiles);
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

  private static ServerEditorBackendProfile ircProfile() {
    return new ServerEditorBackendProfile(
        BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC),
        BACKEND_DESCRIPTORS.displayNameFor(IrcProperties.Server.Backend.IRC),
        6667,
        6697,
        true,
        false,
        true,
        true,
        false,
        "",
        "Host",
        "Server password",
        "Nick",
        "Login/Ident",
        "Real name",
        "Use TLS (SSL)",
        "Direct IRC connection using this profile.",
        "No authentication on connect. Use this for networks that don't require account auth.",
        "(optional)",
        "irc.example.net",
        "ircafe",
        "IRCafeUser",
        "IRCafe User");
  }

  private static ServerEditorBackendProfile quasselProfile() {
    return new ServerEditorBackendProfile(
        BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.QUASSEL_CORE),
        BACKEND_DESCRIPTORS.displayNameFor(IrcProperties.Server.Backend.QUASSEL_CORE),
        4242,
        4243,
        false,
        false,
        false,
        true,
        true,
        "quassel-user",
        "Host",
        "Core password",
        "Default nick",
        "Core username",
        "Core real name",
        "Use TLS (SSL)",
        "Quassel backend logs into Quassel Core here (default ports: 4242 plain, 4243 TLS)."
            + " Core password can be blank before initial setup. SASL/NickServ below are ignored.",
        "Quassel backend does not run direct IRC SASL/NickServ auth from IRCafe."
            + " Configure upstream network auth inside Quassel Core.",
        "(optional until core is configured)",
        "quassel.example.net",
        "quassel-user",
        "display nick (optional)",
        "display name (optional)");
  }

  private static ServerEditorBackendProfile matrixProfile() {
    return new ServerEditorBackendProfile(
        BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.MATRIX),
        BACKEND_DESCRIPTORS.displayNameFor(IrcProperties.Server.Backend.MATRIX),
        80,
        443,
        false,
        true,
        false,
        false,
        false,
        "",
        "Homeserver",
        "Credential",
        "Nick (optional)",
        "User ID (optional)",
        "Display name (optional)",
        "Use TLS (HTTPS)",
        "Matrix backend connects to this homeserver and authenticates with either access token"
            + " or username/password."
            + " Defaults: 443 TLS, 80 plain. SASL/NickServ below are ignored.",
        "Matrix backend authentication is configured here."
            + " IRC SASL/NickServ settings are ignored.",
        "matrix access token / password",
        "https://matrix.example.org",
        "@alice:matrix.example.org",
        "IRCafeUser (optional)",
        "IRCafe User (optional)");
  }
}
