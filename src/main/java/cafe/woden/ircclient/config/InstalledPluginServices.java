package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.InstalledPluginProblem;
import cafe.woden.ircclient.config.api.InstalledPluginsPort;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.util.InstalledPluginDescriptor;
import cafe.woden.ircclient.util.PluginServiceLoaderSupport;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Shared runtime plugin service registry used by Spring-managed SPI catalogs. */
@Component
@ApplicationLayer
public final class InstalledPluginServices implements InstalledPluginsPort {

  private static final Logger log = LoggerFactory.getLogger(InstalledPluginServices.class);

  private final Path pluginDirectory;
  private final ClassLoader applicationClassLoader;
  private final List<PluginServiceLoaderSupport.PluginClassLoaderHandle> pluginClassLoaderHandles;
  private final List<InstalledPluginDescriptor> installedPlugins;
  private final CopyOnWriteArrayList<InstalledPluginProblem> pluginProblems;

  @Autowired
  public InstalledPluginServices(RuntimeConfigPathPort runtimeConfigPathPort) {
    this(
        PluginServiceLoaderSupport.resolvePluginDirectory(
            runtimeConfigPathPort == null ? null : runtimeConfigPathPort::runtimeConfigPath, log),
        PluginServiceLoaderSupport.defaultApplicationClassLoader(InstalledPluginServices.class));
  }

  static InstalledPluginServices installed(
      Path pluginDirectory, ClassLoader applicationClassLoader) {
    return new InstalledPluginServices(pluginDirectory, applicationClassLoader);
  }

  private InstalledPluginServices(Path pluginDirectory, ClassLoader applicationClassLoader) {
    this.pluginDirectory = pluginDirectory;
    this.applicationClassLoader =
        Objects.requireNonNullElseGet(
            applicationClassLoader,
            () ->
                PluginServiceLoaderSupport.defaultApplicationClassLoader(
                    InstalledPluginServices.class));
    InstalledPluginDiscovery discovery = discoverInstalledPlugins(pluginDirectory);
    this.installedPlugins = discovery.installedPlugins();
    this.pluginProblems = new CopyOnWriteArrayList<>(discovery.pluginProblems());
    this.pluginClassLoaderHandles =
        PluginServiceLoaderSupport.openInstalledPluginClassLoaders(
            pluginDirectory, this.installedPlugins, this.applicationClassLoader, log);
  }

  @Override
  public Path pluginDirectory() {
    return pluginDirectory;
  }

  public ClassLoader applicationClassLoader() {
    return applicationClassLoader;
  }

  @Override
  public List<InstalledPluginDescriptor> installedPlugins() {
    return installedPlugins;
  }

  @Override
  public List<InstalledPluginProblem> pluginProblems() {
    return List.copyOf(pluginProblems);
  }

  public <T> List<T> loadInstalledServices(Class<T> serviceType, List<T> builtInServices) {
    List<T> loadedServices =
        new ArrayList<>(
            PluginServiceLoaderSupport.loadInstalledServices(
                serviceType, builtInServices, applicationClassLoader, null));
    if (pluginClassLoaderHandles.isEmpty()) {
      return List.copyOf(loadedServices);
    }

    LinkedHashSet<String> providerClassNames = new LinkedHashSet<>();
    for (T loadedService : loadedServices) {
      if (loadedService == null) {
        continue;
      }
      providerClassNames.add(loadedService.getClass().getName());
    }

    for (PluginServiceLoaderSupport.PluginClassLoaderHandle handle : pluginClassLoaderHandles) {
      try {
        mergeLoadedServices(
            loadedServices,
            providerClassNames,
            PluginServiceLoaderSupport.loadInstalledServices(
                serviceType, List.of(), null, handle.classLoader()));
      } catch (RuntimeException e) {
        recordPluginProblem(
            handle.descriptor(),
            "Failed to load plugin providers for "
                + Objects.requireNonNull(serviceType).getName()
                + " from plugin '"
                + handle.descriptor().pluginId()
                + "'",
            e);
      }
    }
    return List.copyOf(loadedServices);
  }

  @PreDestroy
  void shutdown() {
    PluginServiceLoaderSupport.closePluginClassLoaders(
        pluginClassLoaderHandles.stream()
            .map(PluginServiceLoaderSupport.PluginClassLoaderHandle::classLoader)
            .toList(),
        log,
        "[ircafe] failed to close shared plugin classloader");
  }

  private InstalledPluginDiscovery discoverInstalledPlugins(Path pluginDirectory) {
    try {
      return new InstalledPluginDiscovery(
          PluginServiceLoaderSupport.discoverInstalledPlugins(pluginDirectory, log), List.of());
    } catch (RuntimeException e) {
      StringBuilder details = new StringBuilder();
      if (pluginDirectory != null) {
        details.append("Plugin directory: ").append(pluginDirectory.toAbsolutePath()).append('\n');
      }
      details.append(Objects.toString(e.getMessage(), e.getClass().getName()));
      log.warn("[ircafe] failed to discover declared plugins from {}", pluginDirectory, e);
      return new InstalledPluginDiscovery(
          List.of(),
          List.of(
              new InstalledPluginProblem(
                  "ERROR", "Failed to discover declared plugin jars.", details.toString())));
    }
  }

  private static <T> void mergeLoadedServices(
      List<T> targetServices, LinkedHashSet<String> providerClassNames, List<T> loadedServices) {
    if (targetServices == null || providerClassNames == null || loadedServices == null) {
      return;
    }
    for (T loadedService : loadedServices) {
      if (loadedService == null) {
        continue;
      }
      String providerClassName = loadedService.getClass().getName();
      if (!providerClassNames.add(providerClassName)) {
        continue;
      }
      targetServices.add(loadedService);
    }
  }

  private void recordPluginProblem(
      InstalledPluginDescriptor descriptor, String summary, RuntimeException error) {
    StringBuilder details = new StringBuilder();
    if (descriptor != null) {
      details
          .append("Plugin id: ")
          .append(descriptor.pluginId())
          .append('\n')
          .append("Plugin version: ")
          .append(descriptor.pluginVersion())
          .append('\n')
          .append("Plugin jar: ")
          .append(descriptor.sourceJar())
          .append('\n');
    }
    String errorMessage = Objects.toString(error == null ? null : error.getMessage(), "").trim();
    if (!errorMessage.isEmpty()) {
      details.append(errorMessage);
    }
    InstalledPluginProblem problem =
        new InstalledPluginProblem(
            "ERROR",
            summary,
            details.isEmpty()
                ? "See application logs for the full plugin loader error."
                : details.toString().trim());
    if (!pluginProblems.contains(problem)) {
      pluginProblems.add(problem);
    }
    log.warn("[ircafe] {}", summary, error);
  }

  private record InstalledPluginDiscovery(
      List<InstalledPluginDescriptor> installedPlugins,
      List<InstalledPluginProblem> pluginProblems) {
    private InstalledPluginDiscovery {
      installedPlugins = List.copyOf(Objects.requireNonNullElse(installedPlugins, List.of()));
      pluginProblems = List.copyOf(Objects.requireNonNullElse(pluginProblems, List.of()));
    }
  }
}
