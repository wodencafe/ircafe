package cafe.woden.ircclient.irc.ircv3;

import cafe.woden.ircclient.config.api.InstalledPluginsPort;
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

  private static Ircv3ExtensionRegistry.Snapshot buildSnapshot(
      InstalledPluginsPort installedPlugins) {
    if (installedPlugins == null) {
      return Ircv3ExtensionRegistry.snapshot();
    }
    return Ircv3ExtensionRegistry.snapshotForProviders(
        installedPlugins.loadInstalledServices(
            Ircv3ExtensionDefinitionProvider.class, Ircv3ExtensionRegistry.builtInProviders()));
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
