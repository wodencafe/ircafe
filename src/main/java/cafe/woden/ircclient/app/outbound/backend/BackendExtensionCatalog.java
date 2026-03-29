package cafe.woden.ircclient.app.outbound.backend;

import cafe.woden.ircclient.app.api.AvailableBackendIdsPort;
import cafe.woden.ircclient.app.api.BackendEditorProfileSpec;
import cafe.woden.ircclient.app.outbound.backend.spi.BackendExtension;
import cafe.woden.ircclient.app.outbound.backend.spi.OutboundBackendFeatureAdapter;
import cafe.woden.ircclient.app.outbound.mutation.MessageMutationOutboundCommands;
import cafe.woden.ircclient.app.outbound.upload.spi.UploadCommandTranslationHandler;
import cafe.woden.ircclient.app.plugins.PluginServiceLoaderSupport;
import cafe.woden.ircclient.config.BackendDescriptorCatalog;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import jakarta.annotation.PreDestroy;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Registry for backend extension bundles from built-ins and ServiceLoader plugins. */
@Component
@ApplicationLayer
public final class BackendExtensionCatalog implements AvailableBackendIdsPort {

  private static final Logger log = LoggerFactory.getLogger(BackendExtensionCatalog.class);
  private static final BackendDescriptorCatalog BACKEND_DESCRIPTORS =
      BackendDescriptorCatalog.builtIns();

  private static final MessageMutationOutboundCommands DEFAULT_MESSAGE_MUTATION_COMMANDS =
      new IrcMessageMutationOutboundCommands();

  private static final OutboundBackendFeatureAdapter DEFAULT_FEATURE_ADAPTER =
      new OutboundBackendFeatureAdapter() {
        @Override
        public String backendId() {
          return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC);
        }
      };

  private final Map<String, BackendExtension> extensionsByBackendId;
  private final List<URLClassLoader> pluginClassLoaders;

  @Autowired
  public BackendExtensionCatalog(
      RuntimeConfigPathPort runtimeConfigPathPort, List<BackendExtension> builtInExtensions) {
    this(
        loadInstalledCatalogState(
            List.copyOf(Objects.requireNonNullElse(builtInExtensions, List.of())),
            PluginServiceLoaderSupport.resolvePluginDirectory(runtimeConfigPathPort, log),
            PluginServiceLoaderSupport.defaultApplicationClassLoader(
                BackendExtensionCatalog.class)));
  }

  public BackendExtensionCatalog() {
    this(
        loadInstalledCatalogState(
            List.of(),
            PluginServiceLoaderSupport.resolvePluginDirectory(null, log),
            PluginServiceLoaderSupport.defaultApplicationClassLoader(
                BackendExtensionCatalog.class)));
  }

  BackendExtensionCatalog(List<BackendExtension> extensions) {
    this(List.copyOf(Objects.requireNonNull(extensions, "extensions")), List.of());
  }

  BackendExtensionCatalog(Path pluginDirectory) {
    this(
        loadInstalledCatalogState(
            List.of(),
            pluginDirectory,
            PluginServiceLoaderSupport.defaultApplicationClassLoader(
                BackendExtensionCatalog.class)));
  }

  BackendExtensionCatalog(Path pluginDirectory, ClassLoader applicationClassLoader) {
    this(loadInstalledCatalogState(List.of(), pluginDirectory, applicationClassLoader));
  }

  private BackendExtensionCatalog(LoadedCatalogState state) {
    this(Objects.requireNonNull(state, "state").extensions(), state.pluginClassLoaders());
  }

  private BackendExtensionCatalog(
      List<BackendExtension> extensions, List<URLClassLoader> pluginClassLoaders) {
    this.extensionsByBackendId = indexExtensionsByBackendId(extensions);
    this.pluginClassLoaders =
        List.copyOf(Objects.requireNonNull(pluginClassLoaders, "pluginClassLoaders"));
  }

  @PreDestroy
  void shutdown() {
    PluginServiceLoaderSupport.closePluginClassLoaders(
        pluginClassLoaders, log, "[ircafe] failed to close backend extension plugin classloader");
  }

  @Deprecated(forRemoval = false)
  public BackendExtension extensionFor(IrcProperties.Server.Backend backend) {
    return extensionFor(backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend));
  }

  public BackendExtension extensionFor(String backendId) {
    String id = normalizeBackendId(backendId);
    if (id.isEmpty()) {
      return defaultExtension();
    }
    return extensionsByBackendId.getOrDefault(id, defaultExtension());
  }

  @Deprecated(forRemoval = false)
  public OutboundBackendFeatureAdapter featureAdapterFor(IrcProperties.Server.Backend backend) {
    return featureAdapterFor(backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend));
  }

  public OutboundBackendFeatureAdapter featureAdapterFor(String backendId) {
    OutboundBackendFeatureAdapter featureAdapter = extensionFor(backendId).featureAdapter();
    return featureAdapter != null ? featureAdapter : DEFAULT_FEATURE_ADAPTER;
  }

  @Deprecated(forRemoval = false)
  public MessageMutationOutboundCommands messageMutationCommandsFor(
      IrcProperties.Server.Backend backend) {
    return messageMutationCommandsFor(backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend));
  }

  public MessageMutationOutboundCommands messageMutationCommandsFor(String backendId) {
    MessageMutationOutboundCommands commands =
        extensionFor(backendId).messageMutationOutboundCommands();
    return commands != null ? commands : DEFAULT_MESSAGE_MUTATION_COMMANDS;
  }

  @Deprecated(forRemoval = false)
  public UploadCommandTranslationHandler uploadTranslationHandlerFor(
      IrcProperties.Server.Backend backend) {
    return uploadTranslationHandlerFor(backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend));
  }

  public UploadCommandTranslationHandler uploadTranslationHandlerFor(String backendId) {
    return extensionFor(backendId).uploadCommandTranslationHandler();
  }

  @Override
  public List<String> availableBackendIds() {
    return List.copyOf(extensionsByBackendId.keySet());
  }

  @Override
  public List<BackendEditorProfileSpec> availableBackendEditorProfiles() {
    ArrayList<BackendEditorProfileSpec> profiles = new ArrayList<>(extensionsByBackendId.size());
    for (BackendExtension extension : extensionsByBackendId.values()) {
      if (extension == null || extension.editorProfile() == null) continue;
      profiles.add(extension.editorProfile());
    }
    return List.copyOf(profiles);
  }

  @Override
  public String backendDisplayName(String backendId) {
    String normalized = normalizeBackendId(backendId);
    if (normalized.isEmpty()) return "";
    BackendExtension extension = extensionsByBackendId.get(normalized);
    if (extension != null && extension.editorProfile() != null) {
      String displayName = Objects.toString(extension.editorProfile().displayName(), "").trim();
      if (!displayName.isEmpty()) {
        return displayName;
      }
    }
    return BACKEND_DESCRIPTORS
        .descriptorForId(normalized)
        .map(descriptor -> Objects.toString(descriptor.displayName(), "").trim())
        .orElse(normalized);
  }

  private static LoadedCatalogState loadInstalledCatalogState(
      List<BackendExtension> builtInExtensions,
      Path pluginDirectory,
      ClassLoader applicationClassLoader) {
    PluginServiceLoaderSupport.LoadedServices<BackendExtension> loadedServices =
        PluginServiceLoaderSupport.loadInstalledServices(
            BackendExtension.class,
            builtInExtensions,
            pluginDirectory,
            applicationClassLoader,
            log);
    return new LoadedCatalogState(loadedServices.services(), loadedServices.pluginClassLoaders());
  }

  private static Map<String, BackendExtension> indexExtensionsByBackendId(
      List<BackendExtension> extensions) {
    LinkedHashMap<String, BackendExtension> index = new LinkedHashMap<>();
    for (BackendExtension extension :
        Objects.requireNonNullElse(extensions, List.<BackendExtension>of())) {
      if (extension == null) continue;
      String backendId = backendIdOf(extension);
      if (backendId.isEmpty()) {
        throw new IllegalStateException(
            "Backend extension reported blank backend id: " + extension.getClass().getName());
      }
      validateContributionBackend(
          backendId, extension.featureAdapter(), "feature adapter", extension);
      validateContributionBackend(
          backendId,
          extension.messageMutationOutboundCommands(),
          "message mutation commands",
          extension);
      validateContributionBackend(
          backendId,
          extension.uploadCommandTranslationHandler(),
          "upload translation handler",
          extension);
      validateContributionBackend(
          backendId, extension.editorProfile(), "editor profile", extension);
      BackendExtension previous = index.putIfAbsent(backendId, extension);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate backend extension registered for backend id "
                + backendId
                + ": "
                + previous.getClass().getName()
                + ", "
                + extension.getClass().getName());
      }
    }
    return Map.copyOf(index);
  }

  private static void validateContributionBackend(
      String backendId,
      OutboundBackendFeatureAdapter featureAdapter,
      String contributionType,
      BackendExtension extension) {
    if (featureAdapter == null) return;
    validateContributionBackend(
        backendId,
        backendIdOf(featureAdapter),
        contributionType,
        extension,
        featureAdapter.getClass());
  }

  private static void validateContributionBackend(
      String backendId,
      MessageMutationOutboundCommands commands,
      String contributionType,
      BackendExtension extension) {
    if (commands == null) return;
    validateContributionBackend(
        backendId, backendIdOf(commands), contributionType, extension, commands.getClass());
  }

  private static void validateContributionBackend(
      String backendId,
      UploadCommandTranslationHandler translationHandler,
      String contributionType,
      BackendExtension extension) {
    if (translationHandler == null) return;
    validateContributionBackend(
        backendId,
        backendIdOf(translationHandler),
        contributionType,
        extension,
        translationHandler.getClass());
  }

  private static void validateContributionBackend(
      String backendId,
      BackendEditorProfileSpec editorProfile,
      String contributionType,
      BackendExtension extension) {
    if (editorProfile == null) return;
    validateContributionBackend(
        backendId,
        normalizeBackendId(editorProfile.backendId()),
        contributionType,
        extension,
        editorProfile.getClass());
  }

  private static void validateContributionBackend(
      String extensionBackendId,
      String contributionBackendId,
      String contributionType,
      BackendExtension extension,
      Class<?> contributionClass) {
    String normalizedContributionBackendId = normalizeBackendId(contributionBackendId);
    if (normalizedContributionBackendId.isEmpty()
        || normalizedContributionBackendId.equals(extensionBackendId)) {
      return;
    }
    throw new IllegalStateException(
        "Backend extension '"
            + extension.getClass().getName()
            + "' registered "
            + contributionType
            + " for "
            + normalizedContributionBackendId
            + " but extension backend is "
            + extensionBackendId
            + " ("
            + contributionClass.getName()
            + ")");
  }

  private static BackendExtension defaultExtension() {
    return new BackendExtension() {
      @Override
      public String backendId() {
        return BACKEND_DESCRIPTORS.idFor(IrcProperties.Server.Backend.IRC);
      }

      @Override
      public OutboundBackendFeatureAdapter featureAdapter() {
        return DEFAULT_FEATURE_ADAPTER;
      }

      @Override
      public MessageMutationOutboundCommands messageMutationOutboundCommands() {
        return DEFAULT_MESSAGE_MUTATION_COMMANDS;
      }
    };
  }

  private static String normalizeBackendId(String backendId) {
    return Objects.toString(backendId, "").trim().toLowerCase(Locale.ROOT);
  }

  private static String backendIdOf(BackendExtension extension) {
    String backendId = normalizeBackendId(extension.backendId());
    if (!backendId.isEmpty()) {
      return backendId;
    }
    IrcProperties.Server.Backend backend = extension.backend();
    return backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend);
  }

  private static String backendIdOf(OutboundBackendFeatureAdapter featureAdapter) {
    String backendId = normalizeBackendId(featureAdapter.backendId());
    if (!backendId.isEmpty()) {
      return backendId;
    }
    IrcProperties.Server.Backend backend = featureAdapter.backend();
    return backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend);
  }

  private static String backendIdOf(MessageMutationOutboundCommands commands) {
    String backendId = normalizeBackendId(commands.backendId());
    if (!backendId.isEmpty()) {
      return backendId;
    }
    IrcProperties.Server.Backend backend = commands.backend();
    return backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend);
  }

  private static String backendIdOf(UploadCommandTranslationHandler translationHandler) {
    String backendId = normalizeBackendId(translationHandler.backendId());
    if (!backendId.isEmpty()) {
      return backendId;
    }
    IrcProperties.Server.Backend backend = translationHandler.backend();
    return backend == null ? "" : BACKEND_DESCRIPTORS.idFor(backend);
  }

  private record LoadedCatalogState(
      List<BackendExtension> extensions, List<URLClassLoader> pluginClassLoaders) {}
}
