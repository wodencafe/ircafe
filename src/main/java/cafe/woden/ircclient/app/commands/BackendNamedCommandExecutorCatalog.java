package cafe.woden.ircclient.app.commands;

import cafe.woden.ircclient.config.InstalledPluginServices;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.util.PluginServiceLoaderSupport;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import jakarta.annotation.PreDestroy;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jmolecules.architecture.layered.ApplicationLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Registry for backend named command execution contributions from built-ins and plugins. */
@Component
@ApplicationLayer
public final class BackendNamedCommandExecutorCatalog {

  private static final Logger log =
      LoggerFactory.getLogger(BackendNamedCommandExecutorCatalog.class);

  private final Map<String, BackendNamedCommandExecutor> executionHandlersByCommandName;
  private final List<URLClassLoader> pluginClassLoaders;

  @Autowired
  public BackendNamedCommandExecutorCatalog(
      InstalledPluginServices installedPluginServices,
      List<BackendNamedCommandExecutor> builtInExecutors) {
    this(
        loadInstalledCatalogState(
            List.copyOf(Objects.requireNonNullElse(builtInExecutors, List.of())),
            installedPluginServices));
  }

  public BackendNamedCommandExecutorCatalog(
      RuntimeConfigPathPort runtimeConfigPathPort,
      List<BackendNamedCommandExecutor> builtInExecutors) {
    this(
        loadInstalledCatalogState(
            List.copyOf(Objects.requireNonNullElse(builtInExecutors, List.of())),
            PluginServiceLoaderSupport.resolvePluginDirectory(
                runtimeConfigPathPort == null ? null : runtimeConfigPathPort::runtimeConfigPath,
                log),
            PluginServiceLoaderSupport.defaultApplicationClassLoader(
                BackendNamedCommandExecutorCatalog.class)));
  }

  public static BackendNamedCommandExecutorCatalog empty() {
    return fromExecutors(List.of());
  }

  public static BackendNamedCommandExecutorCatalog installed() {
    return installed(
        PluginServiceLoaderSupport.resolvePluginDirectory(null, log),
        PluginServiceLoaderSupport.defaultApplicationClassLoader(
            BackendNamedCommandExecutorCatalog.class));
  }

  public static BackendNamedCommandExecutorCatalog fromExecutors(
      List<BackendNamedCommandExecutor> executors) {
    return new BackendNamedCommandExecutorCatalog(
        List.copyOf(Objects.requireNonNull(executors, "executors")), List.of());
  }

  static BackendNamedCommandExecutorCatalog installed(
      Path pluginDirectory, ClassLoader applicationClassLoader) {
    return new BackendNamedCommandExecutorCatalog(
        loadInstalledCatalogState(List.of(), pluginDirectory, applicationClassLoader));
  }

  private BackendNamedCommandExecutorCatalog(LoadedCatalogState state) {
    this(Objects.requireNonNull(state, "state").executors(), state.pluginClassLoaders());
  }

  private BackendNamedCommandExecutorCatalog(
      List<BackendNamedCommandExecutor> executors, List<URLClassLoader> pluginClassLoaders) {
    this.executionHandlersByCommandName = indexExecutionHandlersByCommandName(executors);
    this.pluginClassLoaders =
        List.copyOf(Objects.requireNonNull(pluginClassLoaders, "pluginClassLoaders"));
  }

  @PreDestroy
  void shutdown() {
    PluginServiceLoaderSupport.closePluginClassLoaders(
        pluginClassLoaders, log, "[ircafe] failed to close backend execution plugin classloader");
  }

  public boolean handle(
      BackendNamedCommandExecutionContext context,
      CompositeDisposable disposables,
      ParsedInput.BackendNamed command) {
    if (context == null || disposables == null || command == null) return false;
    BackendNamedCommandExecutor executor =
        executionHandlersByCommandName.get(
            BackendNamedCommandRegistrationSupport.normalizeCommandName(command.command()));
    if (executor == null) return false;
    return executor.handle(context, disposables, command);
  }

  private static Map<String, BackendNamedCommandExecutor> indexExecutionHandlersByCommandName(
      List<BackendNamedCommandExecutor> executors) {
    LinkedHashMap<String, BackendNamedCommandExecutor> index = new LinkedHashMap<>();
    for (BackendNamedCommandExecutor executor :
        Objects.requireNonNullElse(executors, List.<BackendNamedCommandExecutor>of())) {
      if (executor == null) continue;
      Set<String> commandNames =
          Objects.requireNonNullElse(executor.handledCommandNames(), Set.<String>of());
      for (String commandName : commandNames) {
        String normalized =
            BackendNamedCommandRegistrationSupport.normalizeCommandName(commandName);
        if (normalized.isEmpty()) continue;
        if (BackendNamedCommandRegistrationSupport.isReservedCommandName(normalized)) {
          throw new IllegalStateException(
              "Backend named execution command '"
                  + normalized
                  + "' collides with a reserved built-in command");
        }
        BackendNamedCommandExecutor previous = index.putIfAbsent(normalized, executor);
        if (previous != null && previous != executor) {
          throw new IllegalStateException(
              "Duplicate backend named execution handler registered for command '"
                  + normalized
                  + "'");
        }
      }
    }
    return Map.copyOf(index);
  }

  private static LoadedCatalogState loadInstalledCatalogState(
      List<BackendNamedCommandExecutor> builtInExecutors,
      Path pluginDirectory,
      ClassLoader applicationClassLoader) {
    PluginServiceLoaderSupport.LoadedServices<BackendNamedCommandExecutor> loadedServices =
        PluginServiceLoaderSupport.loadInstalledServices(
            BackendNamedCommandExecutor.class,
            builtInExecutors,
            pluginDirectory,
            applicationClassLoader,
            log);
    return new LoadedCatalogState(loadedServices.services(), loadedServices.pluginClassLoaders());
  }

  private static LoadedCatalogState loadInstalledCatalogState(
      List<BackendNamedCommandExecutor> builtInExecutors,
      InstalledPluginServices installedPluginServices) {
    InstalledPluginServices pluginServices =
        Objects.requireNonNull(installedPluginServices, "installedPluginServices");
    return new LoadedCatalogState(
        pluginServices.loadInstalledServices(BackendNamedCommandExecutor.class, builtInExecutors),
        List.of());
  }

  private record LoadedCatalogState(
      List<BackendNamedCommandExecutor> executors, List<URLClassLoader> pluginClassLoaders) {}
}
