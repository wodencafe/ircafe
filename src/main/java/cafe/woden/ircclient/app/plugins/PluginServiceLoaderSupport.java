package cafe.woden.ircclient.app.plugins;

import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import org.slf4j.Logger;

/** Shared ServiceLoader support for built-in and plugin-provided SPI implementations. */
public final class PluginServiceLoaderSupport {

  private PluginServiceLoaderSupport() {}

  public static <T> LoadedServices<T> loadInstalledServices(
      Class<T> serviceType,
      List<T> builtInServices,
      Path pluginDirectory,
      ClassLoader applicationClassLoader,
      Logger log) {
    Objects.requireNonNull(serviceType, "serviceType");

    ArrayList<T> loadedServices = new ArrayList<>();
    LinkedHashSet<String> providerClassNames = new LinkedHashSet<>();
    ArrayList<URLClassLoader> pluginClassLoaders = new ArrayList<>();

    loadServices(
        Objects.requireNonNullElse(builtInServices, List.of()), loadedServices, providerClassNames);
    loadServicesFromClassLoader(
        serviceType, applicationClassLoader, loadedServices, providerClassNames);

    Path directory = pluginDirectory != null ? pluginDirectory.toAbsolutePath().normalize() : null;
    if (directory != null && Files.exists(directory)) {
      if (!Files.isDirectory(directory)) {
        if (log != null) {
          log.warn("[ircafe] plugin path is not a directory: {}", directory);
        }
      } else {
        List<URL> jarUrls = pluginJarUrls(directory, log);
        if (!jarUrls.isEmpty()) {
          URLClassLoader pluginClassLoader =
              URLClassLoader.newInstance(jarUrls.toArray(URL[]::new), applicationClassLoader);
          pluginClassLoaders.add(pluginClassLoader);
          loadServicesFromClassLoader(
              serviceType, pluginClassLoader, loadedServices, providerClassNames);
        }
      }
    }

    return new LoadedServices<>(List.copyOf(loadedServices), List.copyOf(pluginClassLoaders));
  }

  public static void closePluginClassLoaders(
      List<URLClassLoader> pluginClassLoaders, Logger log, String failureMessage) {
    if (pluginClassLoaders == null || pluginClassLoaders.isEmpty()) {
      return;
    }
    for (URLClassLoader pluginClassLoader : pluginClassLoaders) {
      if (pluginClassLoader == null) continue;
      try {
        pluginClassLoader.close();
      } catch (IOException e) {
        if (log != null) {
          log.debug(failureMessage, e);
        }
      }
    }
  }

  public static Path resolvePluginDirectory(
      RuntimeConfigPathPort runtimeConfigPathPort, Logger log) {
    try {
      Path runtimeConfigPath =
          runtimeConfigPathPort != null ? runtimeConfigPathPort.runtimeConfigPath() : null;
      Path runtimeConfigDirectory =
          runtimeConfigPath != null ? runtimeConfigPath.getParent() : null;
      if (runtimeConfigDirectory != null) {
        return runtimeConfigDirectory.resolve("plugins").normalize();
      }
    } catch (Exception e) {
      if (log != null) {
        log.warn("[ircafe] failed to resolve plugin directory from runtime config path", e);
      }
    }
    return defaultPluginDirectory();
  }

  public static ClassLoader defaultApplicationClassLoader(Class<?> anchorType) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    return classLoader != null
        ? classLoader
        : Objects.requireNonNull(anchorType, "anchorType").getClassLoader();
  }

  private static <T> void loadServices(
      List<T> services, List<T> targetServices, Set<String> providerClassNames) {
    if (services == null
        || services.isEmpty()
        || targetServices == null
        || providerClassNames == null) {
      return;
    }
    for (T service : services) {
      if (service == null) continue;
      String className = service.getClass().getName();
      if (!providerClassNames.add(className)) continue;
      targetServices.add(service);
    }
  }

  private static <T> void loadServicesFromClassLoader(
      Class<T> serviceType,
      ClassLoader classLoader,
      List<T> targetServices,
      Set<String> providerClassNames) {
    if (serviceType == null
        || classLoader == null
        || targetServices == null
        || providerClassNames == null) {
      return;
    }
    ServiceLoader<T> loader = ServiceLoader.load(serviceType, classLoader);
    var iterator = loader.iterator();
    while (true) {
      final boolean hasNext;
      try {
        hasNext = iterator.hasNext();
      } catch (ServiceConfigurationError e) {
        throw invalidProviderConfiguration(serviceType, classLoader, e);
      }
      if (!hasNext) {
        return;
      }
      final T service;
      try {
        service = iterator.next();
      } catch (ServiceConfigurationError e) {
        throw invalidProviderConfiguration(serviceType, classLoader, e);
      }
      if (service == null) continue;
      String className = service.getClass().getName();
      if (!providerClassNames.add(className)) continue;
      targetServices.add(service);
    }
  }

  private static IllegalStateException invalidProviderConfiguration(
      Class<?> serviceType, ClassLoader classLoader, ServiceConfigurationError cause) {
    String reason = Objects.toString(cause.getMessage(), "").trim();
    String message =
        "[ircafe] failed to load ServiceLoader provider for "
            + serviceType.getName()
            + " from "
            + classLoaderDescription(classLoader);
    if (!reason.isEmpty()) {
      message += ": " + reason;
    }
    message +=
        ". Providers loaded via ServiceLoader must be public and expose a public no-arg constructor.";
    return new IllegalStateException(message, cause);
  }

  private static String classLoaderDescription(ClassLoader classLoader) {
    if (classLoader == null) {
      return "<null classloader>";
    }
    return classLoader.getClass().getName();
  }

  private static List<URL> pluginJarUrls(Path pluginDirectory, Logger log) {
    ArrayList<URL> jarUrls = new ArrayList<>();
    try (var stream = Files.list(pluginDirectory)) {
      stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
          .sorted()
          .forEach(
              path -> {
                try {
                  jarUrls.add(path.toUri().toURL());
                } catch (Exception e) {
                  if (log != null) {
                    log.warn("[ircafe] failed to resolve plugin jar URL for {}", path, e);
                  }
                }
              });
    } catch (IOException e) {
      if (log != null) {
        log.warn("[ircafe] failed to scan plugin directory {}", pluginDirectory, e);
      }
    }
    return List.copyOf(jarUrls);
  }

  private static Path defaultPluginDirectory() {
    String xdgConfigHome = Objects.toString(System.getenv("XDG_CONFIG_HOME"), "").trim();
    if (!xdgConfigHome.isEmpty()) {
      return Path.of(xdgConfigHome, "ircafe", "plugins");
    }
    String userHome = Objects.toString(System.getProperty("user.home"), "").trim();
    if (!userHome.isEmpty()) {
      return Path.of(userHome, ".config", "ircafe", "plugins");
    }
    return Path.of(System.getProperty("java.io.tmpdir"), "ircafe", "plugins");
  }

  public record LoadedServices<T>(List<T> services, List<URLClassLoader> pluginClassLoaders) {}
}
