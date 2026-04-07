package cafe.woden.ircclient.irc.ircv3;

import cafe.woden.ircclient.config.api.InstalledPluginProblem;
import cafe.woden.ircclient.config.api.InstalledPluginsPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Runtime IRCv3 metadata catalog backed by built-ins plus installed plugin providers. */
@Component
@InfrastructureLayer
public final class Ircv3ExtensionCatalog {

  private static final Logger log = LoggerFactory.getLogger(Ircv3ExtensionCatalog.class);
  private static final List<String> BUILT_IN_PROVIDER_IDS = Ircv3ExtensionRegistry.providerIds();

  private final Ircv3ExtensionRegistry.Snapshot snapshot;

  public Ircv3ExtensionCatalog(InstalledPluginsPort installedPlugins) {
    this(buildSnapshot(installedPlugins));
  }

  private Ircv3ExtensionCatalog(Ircv3ExtensionRegistry.Snapshot snapshot) {
    this.snapshot = Objects.requireNonNullElseGet(snapshot, Ircv3ExtensionRegistry::snapshot);
    logLoadedProviders();
  }

  public static Ircv3ExtensionCatalog builtInCatalog() {
    return new Ircv3ExtensionCatalog(Ircv3ExtensionRegistry.snapshot());
  }

  public static Ircv3ExtensionCatalog forProviders(
      List<Ircv3ExtensionDefinitionProvider> providers) {
    return new Ircv3ExtensionCatalog(Ircv3ExtensionRegistry.snapshotForProviders(providers));
  }

  public Ircv3ExtensionRegistry.Snapshot snapshot() {
    return snapshot;
  }

  public List<Ircv3ExtensionRegistry.ExtensionDefinition> all() {
    return snapshot.all();
  }

  public List<String> providerIds() {
    return snapshot.providerIds();
  }

  public Optional<Ircv3ExtensionRegistry.ExtensionDefinition> find(String name) {
    return snapshot.find(name);
  }

  public List<Ircv3ExtensionRegistry.ExtensionDefinition> requestableCapabilities() {
    return snapshot.requestableCapabilities();
  }

  public List<String> requestableCapabilityTokens() {
    return snapshot.requestableCapabilityTokens();
  }

  public List<Ircv3ExtensionRegistry.FeatureDefinition> visibleFeatures() {
    return snapshot.visibleFeatures();
  }

  public String requestTokenFor(String name) {
    return snapshot.requestTokenFor(name);
  }

  public String preferenceKeyFor(String name) {
    return snapshot.preferenceKeyFor(name);
  }

  public String normalizeRequestToken(String name) {
    return snapshot.normalizeRequestToken(name);
  }

  public String normalizePreferenceKey(String name) {
    return snapshot.normalizePreferenceKey(name);
  }

  private static Ircv3ExtensionRegistry.Snapshot buildSnapshot(
      InstalledPluginsPort installedPlugins) {
    if (installedPlugins == null) {
      return Ircv3ExtensionRegistry.snapshot();
    }
    List<Ircv3ExtensionDefinitionProvider> providers =
        installedPlugins.loadInstalledServices(
            Ircv3ExtensionDefinitionProvider.class, Ircv3ExtensionRegistry.builtInProviders());
    try {
      return Ircv3ExtensionRegistry.snapshotForProviders(providers);
    } catch (RuntimeException error) {
      InstalledPluginProblem problem =
          new InstalledPluginProblem(
              "ERROR",
              "Failed to load IRCv3 extension metadata from installed plugins.",
              buildConflictDetails(providers, error));
      installedPlugins.recordPluginProblem(problem);
      log.warn("[ircafe] {}", problem.summary(), error);
      return Ircv3ExtensionRegistry.snapshot();
    }
  }

  private static String buildConflictDetails(
      List<Ircv3ExtensionDefinitionProvider> providers, RuntimeException error) {
    ArrayList<String> pluginProviderIds = new ArrayList<>();
    List<Ircv3ExtensionDefinitionProvider> safeProviders =
        providers != null ? providers : List.of();
    for (Ircv3ExtensionDefinitionProvider provider : safeProviders) {
      if (provider == null) {
        continue;
      }
      String providerId = Objects.toString(provider.providerId(), "").trim();
      if (!providerId.isEmpty() && !BUILT_IN_PROVIDER_IDS.contains(providerId)) {
        pluginProviderIds.add(providerId);
      }
    }

    StringBuilder details = new StringBuilder();
    if (!pluginProviderIds.isEmpty()) {
      details.append("Plugin IRCv3 providers: ").append(pluginProviderIds).append('\n');
    }
    String message = Objects.toString(error == null ? null : error.getMessage(), "").trim();
    if (!message.isEmpty()) {
      details.append(message);
    } else if (error != null) {
      details.append(error.getClass().getName());
    }
    return details.toString().trim();
  }

  private void logLoadedProviders() {
    List<String> providerIds = snapshot.providerIds();
    List<String> pluginProviderIds =
        providerIds.stream()
            .filter(providerId -> !BUILT_IN_PROVIDER_IDS.contains(providerId))
            .toList();
    if (!pluginProviderIds.isEmpty()) {
      log.info(
          "[ircafe] loaded {} plugin IRCv3 extension provider(s): {}",
          pluginProviderIds.size(),
          pluginProviderIds);
      return;
    }
    if (log.isDebugEnabled()) {
      log.debug("[ircafe] loaded IRCv3 extension providers: {}", providerIds);
    }
  }
}
