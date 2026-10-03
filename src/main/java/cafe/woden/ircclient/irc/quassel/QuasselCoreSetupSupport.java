package cafe.woden.ircclient.irc.quassel;

import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.firstNonBlankMapValue;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.mapValueIgnoreCase;
import static cafe.woden.ircclient.irc.quassel.QuasselCoreVariantSupport.normalizeObjectMap;

import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreSetupPrompt;
import cafe.woden.ircclient.irc.quassel.control.QuasselCoreControlPort.QuasselCoreSetupRequest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Interprets core setup options and normalizes user-provided setup values. */
final class QuasselCoreSetupSupport {
  private QuasselCoreSetupSupport() {}

  private static final List<String> DEFAULT_SETUP_STORAGE_BACKENDS = List.of("SQLite");

  private static final List<String> DEFAULT_SETUP_AUTHENTICATORS = List.of("Database");

  static QuasselCoreSetupPrompt buildSetupPrompt(
      String serverId, String detail, Map<String, Object> setupFields) {
    Map<String, Object> rawFields = normalizeObjectMap(setupFields);
    List<String> storageBackends =
        extractSetupOptions(rawFields, "StorageBackends", "BackendInfo", "Backends");
    if (storageBackends.isEmpty()) {
      storageBackends = DEFAULT_SETUP_STORAGE_BACKENDS;
    }
    List<String> authenticators =
        extractSetupOptions(rawFields, "Authenticators", "AuthenticatorInfo");
    if (authenticators.isEmpty()) {
      authenticators = DEFAULT_SETUP_AUTHENTICATORS;
    }
    return new QuasselCoreSetupPrompt(
        serverId, Objects.toString(detail, "").trim(), storageBackends, authenticators, rawFields);
  }

  static QuasselCoreSetupRequest normalizeSetupRequest(
      QuasselCoreSetupPrompt prompt, QuasselCoreSetupRequest request) {
    String adminUser = Objects.toString(request.adminUser(), "").trim();
    if (adminUser.isEmpty()) {
      throw new IllegalArgumentException("admin user is required");
    }
    String adminPassword = Objects.toString(request.adminPassword(), "");
    if (adminPassword.isBlank()) {
      throw new IllegalArgumentException("admin password is required");
    }

    List<String> storageOptions =
        prompt == null || prompt.storageBackends() == null ? List.of() : prompt.storageBackends();
    String storageBackend =
        selectSetupOption(
            request.storageBackend(),
            storageOptions,
            DEFAULT_SETUP_STORAGE_BACKENDS.isEmpty() ? "" : DEFAULT_SETUP_STORAGE_BACKENDS.get(0));

    List<String> authOptions =
        prompt == null || prompt.authenticators() == null ? List.of() : prompt.authenticators();
    String authenticator =
        selectSetupOption(
            request.authenticator(),
            authOptions,
            DEFAULT_SETUP_AUTHENTICATORS.isEmpty() ? "" : DEFAULT_SETUP_AUTHENTICATORS.get(0));

    Map<String, Object> storageSetupData = normalizeObjectMap(request.storageSetupData());
    Map<String, Object> authSetupData = normalizeObjectMap(request.authSetupData());

    return new QuasselCoreSetupRequest(
        adminUser, adminPassword, storageBackend, authenticator, storageSetupData, authSetupData);
  }

  private static String selectSetupOption(
      String requested, List<String> preferredOptions, String fallback) {
    String explicit = Objects.toString(requested, "").trim();
    if (!explicit.isEmpty()) return explicit;
    if (preferredOptions != null) {
      for (String candidate : preferredOptions) {
        String c = Objects.toString(candidate, "").trim();
        if (!c.isEmpty()) return c;
      }
    }
    String dflt = Objects.toString(fallback, "").trim();
    if (!dflt.isEmpty()) return dflt;
    throw new IllegalArgumentException("setup option is required");
  }

  private static List<String> extractSetupOptions(
      Map<String, Object> setupFields, String... candidateKeys) {
    if (setupFields == null || setupFields.isEmpty() || candidateKeys == null) return List.of();
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String key : candidateKeys) {
      Object raw = mapValueIgnoreCase(setupFields, key);
      collectSetupOptions(raw, out);
    }
    if (out.isEmpty()) return List.of();
    return List.copyOf(out);
  }

  private static void collectSetupOptions(Object raw, LinkedHashSet<String> out) {
    if (raw == null || out == null) return;
    if (raw instanceof List<?> list) {
      for (Object value : list) {
        collectSetupOptions(value, out);
      }
      return;
    }
    if (raw instanceof Map<?, ?> map) {
      // BackendInfo/AuthenticatorInfo entries are often nested maps with identifier/name keys.
      String token =
          firstNonBlankMapValue(
              map,
              "Backend",
              "BackendId",
              "StorageBackend",
              "StorageBackends",
              "Storage",
              "StorageId",
              "Authenticator",
              "AuthenticatorId",
              "AuthBackend",
              "AuthBackendId",
              "Identifier",
              "Id",
              "Key",
              "Name",
              "DisplayName",
              "Value");
      if (!token.isEmpty()) {
        out.add(token);
      }
      collectSetupOptions(mapValueIgnoreCase(map, "Backends"), out);
      collectSetupOptions(mapValueIgnoreCase(map, "StorageBackends"), out);
      collectSetupOptions(mapValueIgnoreCase(map, "Authenticators"), out);
      collectSetupOptions(mapValueIgnoreCase(map, "BackendInfo"), out);
      collectSetupOptions(mapValueIgnoreCase(map, "AuthenticatorInfo"), out);
      return;
    }
    String token = Objects.toString(raw, "").trim();
    if (!token.isEmpty()) out.add(token);
  }
}
