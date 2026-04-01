package cafe.woden.ircclient.config;

import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.util.PluginServiceLoaderSupport;
import jakarta.annotation.PreDestroy;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Shared runtime plugin classloader used by Spring-managed SPI catalogs. */
@Component
@ApplicationLayer
public final class InstalledPluginServices {

  private static final Logger log = LoggerFactory.getLogger(InstalledPluginServices.class);

  private final Path pluginDirectory;
  private final ClassLoader applicationClassLoader;
  private final URLClassLoader pluginClassLoader;

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
    this.pluginClassLoader =
        PluginServiceLoaderSupport.openPluginClassLoader(
            pluginDirectory, this.applicationClassLoader, log);
  }

  public Path pluginDirectory() {
    return pluginDirectory;
  }

  public ClassLoader applicationClassLoader() {
    return applicationClassLoader;
  }

  public <T> List<T> loadInstalledServices(Class<T> serviceType, List<T> builtInServices) {
    return PluginServiceLoaderSupport.loadInstalledServices(
        serviceType, builtInServices, applicationClassLoader, pluginClassLoader);
  }

  @PreDestroy
  void shutdown() {
    PluginServiceLoaderSupport.closePluginClassLoader(
        pluginClassLoader, log, "[ircafe] failed to close shared plugin classloader");
  }
}
