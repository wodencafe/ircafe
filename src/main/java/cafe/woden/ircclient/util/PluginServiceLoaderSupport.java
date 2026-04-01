package cafe.woden.ircclient.util;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.slf4j.Logger;

/** Shared ServiceLoader support for built-in and plugin-provided SPI implementations. */
public final class PluginServiceLoaderSupport {

  public static final String PLUGIN_ID_ATTRIBUTE = "Ircafe-Plugin-Id";
  public static final String PLUGIN_VERSION_ATTRIBUTE = "Ircafe-Plugin-Version";
  public static final String PLUGIN_API_VERSION_ATTRIBUTE = "Ircafe-Plugin-Api-Version";
  public static final int SUPPORTED_PLUGIN_API_VERSION = 1;

  private PluginServiceLoaderSupport() {}

  public static <T> LoadedServices<T> loadInstalledServices(
      Class<T> serviceType,
      List<T> builtInServices,
      Path pluginDirectory,
      ClassLoader applicationClassLoader,
      Logger log) {
    List<PluginClassLoaderHandle> pluginClassLoaderHandles =
        openInstalledPluginClassLoaders(
            pluginDirectory,
            discoverInstalledPlugins(pluginDirectory, log),
            applicationClassLoader,
            log);
    return new LoadedServices<>(
        loadInstalledServices(
            serviceType,
            builtInServices,
            applicationClassLoader,
            pluginClassLoaderHandles,
            (handle, error) -> {
              if (log != null) {
                log.warn(
                    "[ircafe] failed to load plugin providers for {} from plugin '{}' ({})",
                    serviceType.getName(),
                    handle.descriptor().pluginId(),
                    handle.descriptor().sourceJar(),
                    error);
              }
            }),
        pluginClassLoaderHandles.stream().map(PluginClassLoaderHandle::classLoader).toList());
  }

  public static <T> List<T> loadInstalledServices(
      Class<T> serviceType,
      List<T> builtInServices,
      ClassLoader applicationClassLoader,
      ClassLoader pluginClassLoader) {
    Objects.requireNonNull(serviceType, "serviceType");

    ArrayList<T> loadedServices = new ArrayList<>();
    LinkedHashSet<String> providerClassNames = new LinkedHashSet<>();
    LinkedHashMap<Path, InstalledPluginDescriptor> descriptorsByJar = new LinkedHashMap<>();
    LinkedHashMap<String, Path> pluginIdsByJar = new LinkedHashMap<>();

    loadServices(
        Objects.requireNonNullElse(builtInServices, List.of()), loadedServices, providerClassNames);
    loadServicesFromClassLoader(
        serviceType,
        applicationClassLoader,
        loadedServices,
        providerClassNames,
        descriptorsByJar,
        pluginIdsByJar,
        false);
    loadServicesFromClassLoader(
        serviceType,
        pluginClassLoader,
        loadedServices,
        providerClassNames,
        descriptorsByJar,
        pluginIdsByJar,
        true);

    return List.copyOf(loadedServices);
  }

  public static <T> List<T> loadInstalledServices(
      Class<T> serviceType,
      List<T> builtInServices,
      ClassLoader applicationClassLoader,
      List<PluginClassLoaderHandle> pluginClassLoaderHandles,
      BiConsumer<PluginClassLoaderHandle, RuntimeException> failureHandler) {
    ArrayList<T> loadedServices =
        new ArrayList<>(
            loadInstalledServices(serviceType, builtInServices, applicationClassLoader, null));
    List<PluginClassLoaderHandle> handles =
        List.copyOf(Objects.requireNonNullElse(pluginClassLoaderHandles, List.of()));
    if (handles.isEmpty()) {
      return List.copyOf(loadedServices);
    }

    LinkedHashSet<String> providerClassNames = new LinkedHashSet<>();
    for (T loadedService : loadedServices) {
      if (loadedService == null) {
        continue;
      }
      providerClassNames.add(loadedService.getClass().getName());
    }

    for (PluginClassLoaderHandle handle : handles) {
      if (handle == null || handle.classLoader() == null) {
        continue;
      }
      try {
        loadServices(
            loadInstalledServices(serviceType, List.of(), null, handle.classLoader()),
            loadedServices,
            providerClassNames);
      } catch (RuntimeException e) {
        if (failureHandler != null) {
          failureHandler.accept(handle, e);
        }
      }
    }
    return List.copyOf(loadedServices);
  }

  public static URLClassLoader openPluginClassLoader(
      Path pluginDirectory, ClassLoader applicationClassLoader, Logger log) {
    Path directory = pluginDirectory != null ? pluginDirectory.toAbsolutePath().normalize() : null;
    if (directory == null || !Files.exists(directory)) {
      return null;
    }
    if (!Files.isDirectory(directory)) {
      if (log != null) {
        log.warn("[ircafe] plugin path is not a directory: {}", directory);
      }
      return null;
    }
    List<URL> jarUrls = pluginJarUrls(directory, log);
    if (jarUrls.isEmpty()) {
      return null;
    }
    return URLClassLoader.newInstance(jarUrls.toArray(URL[]::new), applicationClassLoader);
  }

  public static List<PluginClassLoaderHandle> openInstalledPluginClassLoaders(
      Path pluginDirectory,
      List<InstalledPluginDescriptor> installedPlugins,
      ClassLoader applicationClassLoader,
      Logger log) {
    Path directory = pluginDirectory != null ? pluginDirectory.toAbsolutePath().normalize() : null;
    List<InstalledPluginDescriptor> declaredPlugins =
        List.copyOf(Objects.requireNonNullElse(installedPlugins, List.of()));
    if (directory == null
        || declaredPlugins.isEmpty()
        || !Files.exists(directory)
        || !Files.isDirectory(directory)) {
      return List.of();
    }

    List<Path> helperJarPaths = pluginDependencyJarPaths(directory, declaredPlugins, log);
    ArrayList<PluginClassLoaderHandle> handles = new ArrayList<>(declaredPlugins.size());
    for (InstalledPluginDescriptor descriptor : declaredPlugins) {
      if (descriptor == null || descriptor.sourceJar() == null) continue;
      List<URL> jarUrls =
          pluginRuntimeJarUrls(
              descriptor.sourceJar().toAbsolutePath().normalize(), helperJarPaths, log);
      if (jarUrls.isEmpty()) {
        continue;
      }
      handles.add(
          new PluginClassLoaderHandle(
              descriptor,
              URLClassLoader.newInstance(jarUrls.toArray(URL[]::new), applicationClassLoader)));
    }
    return List.copyOf(handles);
  }

  public static List<InstalledPluginDescriptor> discoverInstalledPlugins(
      Path pluginDirectory, Logger log) {
    Path directory = pluginDirectory != null ? pluginDirectory.toAbsolutePath().normalize() : null;
    if (directory == null || !Files.exists(directory) || !Files.isDirectory(directory)) {
      return List.of();
    }
    LinkedHashMap<String, InstalledPluginDescriptor> pluginsById = new LinkedHashMap<>();
    for (Path jarPath : pluginJarPaths(directory, log)) {
      Optional<InstalledPluginDescriptor> descriptor = declaredPluginDescriptor(jarPath);
      if (descriptor.isEmpty()) {
        continue;
      }
      InstalledPluginDescriptor plugin = descriptor.get();
      InstalledPluginDescriptor previous = pluginsById.putIfAbsent(plugin.pluginId(), plugin);
      if (previous != null && !previous.sourceJar().equals(plugin.sourceJar())) {
        throw new IllegalStateException(
            "[ircafe] duplicate plugin id '"
                + plugin.pluginId()
                + "' declared by "
                + previous.sourceJar()
                + " and "
                + plugin.sourceJar());
      }
    }
    return List.copyOf(pluginsById.values());
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

  public static void closePluginClassLoader(
      URLClassLoader pluginClassLoader, Logger log, String failureMessage) {
    closePluginClassLoaders(
        pluginClassLoader == null ? List.of() : List.of(pluginClassLoader), log, failureMessage);
  }

  public static Path resolvePluginDirectory(Supplier<Path> runtimeConfigPathSupplier, Logger log) {
    try {
      Path runtimeConfigPath =
          runtimeConfigPathSupplier != null ? runtimeConfigPathSupplier.get() : null;
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
      Set<String> providerClassNames,
      Map<Path, InstalledPluginDescriptor> descriptorsByJar,
      Map<String, Path> pluginIdsByJar,
      boolean validatePluginMetadata) {
    if (serviceType == null
        || classLoader == null
        || targetServices == null
        || providerClassNames == null
        || descriptorsByJar == null
        || pluginIdsByJar == null) {
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
      if (validatePluginMetadata) {
        validatePluginProviderMetadata(
            serviceType, classLoader, service, descriptorsByJar, pluginIdsByJar);
      }
      String className = service.getClass().getName();
      if (!providerClassNames.add(className)) continue;
      targetServices.add(service);
    }
  }

  private static void validatePluginProviderMetadata(
      Class<?> serviceType,
      ClassLoader requestedClassLoader,
      Object service,
      Map<Path, InstalledPluginDescriptor> descriptorsByJar,
      Map<String, Path> pluginIdsByJar) {
    if (serviceType == null
        || requestedClassLoader == null
        || service == null
        || descriptorsByJar == null
        || pluginIdsByJar == null) {
      return;
    }
    Class<?> providerType = service.getClass();
    if (providerType.getClassLoader() != requestedClassLoader) {
      return;
    }
    Path sourceJar = providerSourceJar(serviceType, providerType);
    InstalledPluginDescriptor descriptor =
        descriptorsByJar.computeIfAbsent(
            sourceJar, path -> readPluginDescriptor(path, serviceType, providerType));
    Path previousJar = pluginIdsByJar.putIfAbsent(descriptor.pluginId(), sourceJar);
    if (previousJar != null && !previousJar.equals(sourceJar)) {
      throw new IllegalStateException(
          "[ircafe] duplicate plugin id '"
              + descriptor.pluginId()
              + "' for service "
              + serviceType.getName()
              + ": "
              + previousJar
              + " and "
              + sourceJar);
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

  private static Path providerSourceJar(Class<?> serviceType, Class<?> providerType) {
    try {
      URL location = providerType.getProtectionDomain().getCodeSource().getLocation();
      if (location == null) {
        throw new IllegalStateException("missing code source");
      }
      Path sourcePath = Path.of(location.toURI()).toAbsolutePath().normalize();
      if (!Files.isRegularFile(sourcePath)
          || !sourcePath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
        throw new IllegalStateException("provider does not originate from a jar file");
      }
      return sourcePath;
    } catch (Exception e) {
      throw new IllegalStateException(
          "[ircafe] plugin provider "
              + providerType.getName()
              + " for service "
              + serviceType.getName()
              + " could not resolve its source jar",
          e);
    }
  }

  private static InstalledPluginDescriptor readPluginDescriptor(
      Path jarPath, Class<?> serviceType, Class<?> providerType) {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      Manifest manifest = jarFile.getManifest();
      Attributes attributes = manifest == null ? new Attributes() : manifest.getMainAttributes();
      String pluginId =
          requiredManifestValue(
              attributes, PLUGIN_ID_ATTRIBUTE, jarPath, serviceType, providerType);
      String pluginVersion =
          firstNonBlank(
              attributes.getValue(PLUGIN_VERSION_ATTRIBUTE),
              attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION));
      if (pluginVersion.isEmpty()) {
        throw missingManifestAttribute(
            PLUGIN_VERSION_ATTRIBUTE + " (or " + Attributes.Name.IMPLEMENTATION_VERSION + ")",
            jarPath,
            serviceType,
            providerType);
      }
      int pluginApiVersion =
          parsePluginApiVersion(
              requiredManifestValue(
                  attributes, PLUGIN_API_VERSION_ATTRIBUTE, jarPath, serviceType, providerType),
              jarPath,
              serviceType,
              providerType);
      if (pluginApiVersion != SUPPORTED_PLUGIN_API_VERSION) {
        throw new IllegalStateException(
            "[ircafe] plugin provider "
                + providerType.getName()
                + " for service "
                + serviceType.getName()
                + " declares unsupported plugin API version "
                + pluginApiVersion
                + " in "
                + jarPath
                + ". This build supports plugin API version "
                + SUPPORTED_PLUGIN_API_VERSION
                + ".");
      }
      return new InstalledPluginDescriptor(pluginId, pluginVersion, pluginApiVersion, jarPath);
    } catch (IllegalStateException e) {
      throw e;
    } catch (IOException e) {
      throw new IllegalStateException(
          "[ircafe] failed to read plugin manifest for provider "
              + providerType.getName()
              + " in "
              + jarPath,
          e);
    }
  }

  private static String requiredManifestValue(
      Attributes attributes,
      String attributeName,
      Path jarPath,
      Class<?> serviceType,
      Class<?> providerType) {
    String value = firstNonBlank(attributes == null ? null : attributes.getValue(attributeName));
    if (!value.isEmpty()) {
      return value;
    }
    throw missingManifestAttribute(attributeName, jarPath, serviceType, providerType);
  }

  private static IllegalStateException missingManifestAttribute(
      String attributeName, Path jarPath, Class<?> serviceType, Class<?> providerType) {
    return new IllegalStateException(
        "[ircafe] plugin provider "
            + providerType.getName()
            + " for service "
            + serviceType.getName()
            + " was loaded from "
            + jarPath
            + " but the jar manifest is missing "
            + attributeName
            + ".");
  }

  private static int parsePluginApiVersion(
      String rawVersion, Path jarPath, Class<?> serviceType, Class<?> providerType) {
    try {
      return Integer.parseInt(rawVersion);
    } catch (NumberFormatException e) {
      throw new IllegalStateException(
          "[ircafe] plugin provider "
              + providerType.getName()
              + " for service "
              + serviceType.getName()
              + " declares non-numeric plugin API version '"
              + rawVersion
              + "' in "
              + jarPath
              + ".",
          e);
    }
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return "";
    }
    for (String value : values) {
      String normalized = Objects.toString(value, "").trim();
      if (!normalized.isEmpty()) {
        return normalized;
      }
    }
    return "";
  }

  private static Optional<InstalledPluginDescriptor> declaredPluginDescriptor(Path jarPath) {
    try (JarFile jarFile = new JarFile(jarPath.toFile())) {
      Manifest manifest = jarFile.getManifest();
      if (manifest == null) {
        return Optional.empty();
      }
      Attributes attributes = manifest.getMainAttributes();
      String pluginId = firstNonBlank(attributes.getValue(PLUGIN_ID_ATTRIBUTE));
      if (pluginId.isEmpty()) {
        return Optional.empty();
      }
      String pluginVersion =
          firstNonBlank(
              attributes.getValue(PLUGIN_VERSION_ATTRIBUTE),
              attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION));
      if (pluginVersion.isEmpty()) {
        throw new IllegalStateException(
            "[ircafe] plugin jar "
                + jarPath
                + " declares "
                + PLUGIN_ID_ATTRIBUTE
                + " but is missing "
                + PLUGIN_VERSION_ATTRIBUTE
                + " (or "
                + Attributes.Name.IMPLEMENTATION_VERSION
                + ").");
      }
      String rawApiVersion = firstNonBlank(attributes.getValue(PLUGIN_API_VERSION_ATTRIBUTE));
      if (rawApiVersion.isEmpty()) {
        throw new IllegalStateException(
            "[ircafe] plugin jar "
                + jarPath
                + " declares "
                + PLUGIN_ID_ATTRIBUTE
                + " but is missing "
                + PLUGIN_API_VERSION_ATTRIBUTE
                + ".");
      }
      int pluginApiVersion;
      try {
        pluginApiVersion = Integer.parseInt(rawApiVersion);
      } catch (NumberFormatException e) {
        throw new IllegalStateException(
            "[ircafe] plugin jar "
                + jarPath
                + " declares non-numeric "
                + PLUGIN_API_VERSION_ATTRIBUTE
                + ".",
            e);
      }
      if (pluginApiVersion != SUPPORTED_PLUGIN_API_VERSION) {
        throw new IllegalStateException(
            "[ircafe] plugin jar "
                + jarPath
                + " declares unsupported plugin API version "
                + pluginApiVersion
                + ". This build supports plugin API version "
                + SUPPORTED_PLUGIN_API_VERSION
                + ".");
      }
      return Optional.of(
          new InstalledPluginDescriptor(pluginId, pluginVersion, pluginApiVersion, jarPath));
    } catch (IllegalStateException e) {
      throw e;
    } catch (IOException e) {
      throw new IllegalStateException("[ircafe] failed to read plugin manifest for " + jarPath, e);
    }
  }

  private static List<URL> pluginJarUrls(Path pluginDirectory, Logger log) {
    ArrayList<URL> jarUrls = new ArrayList<>();
    for (Path jarPath : pluginJarPaths(pluginDirectory, log)) {
      try {
        jarUrls.add(jarPath.toUri().toURL());
      } catch (Exception e) {
        if (log != null) {
          log.warn("[ircafe] failed to resolve plugin jar URL for {}", jarPath, e);
        }
      }
    }
    return List.copyOf(jarUrls);
  }

  private static List<URL> pluginRuntimeJarUrls(
      Path pluginJarPath, List<Path> helperJarPaths, Logger log) {
    ArrayList<URL> jarUrls = new ArrayList<>();
    try {
      jarUrls.add(pluginJarPath.toUri().toURL());
    } catch (Exception e) {
      if (log != null) {
        log.warn("[ircafe] failed to resolve plugin jar URL for {}", pluginJarPath, e);
      }
      return List.of();
    }
    for (Path helperJarPath : Objects.requireNonNullElse(helperJarPaths, List.<Path>of())) {
      if (helperJarPath == null) continue;
      try {
        jarUrls.add(helperJarPath.toUri().toURL());
      } catch (Exception e) {
        if (log != null) {
          log.warn("[ircafe] failed to resolve helper plugin jar URL for {}", helperJarPath, e);
        }
      }
    }
    return List.copyOf(jarUrls);
  }

  private static List<Path> pluginJarPaths(Path pluginDirectory, Logger log) {
    ArrayList<Path> jarPaths = new ArrayList<>();
    try (var stream = Files.list(pluginDirectory)) {
      stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
          .sorted()
          .forEach(jarPaths::add);
    } catch (IOException e) {
      if (log != null) {
        log.warn("[ircafe] failed to scan plugin directory {}", pluginDirectory, e);
      }
    }
    return List.copyOf(jarPaths);
  }

  private static List<Path> pluginDependencyJarPaths(
      Path pluginDirectory, List<InstalledPluginDescriptor> installedPlugins, Logger log) {
    LinkedHashSet<Path> declaredPluginJars = new LinkedHashSet<>();
    for (InstalledPluginDescriptor descriptor :
        Objects.requireNonNullElse(installedPlugins, List.<InstalledPluginDescriptor>of())) {
      if (descriptor == null || descriptor.sourceJar() == null) continue;
      declaredPluginJars.add(descriptor.sourceJar().toAbsolutePath().normalize());
    }

    ArrayList<Path> helperJarPaths = new ArrayList<>();
    for (Path jarPath : pluginJarPaths(pluginDirectory, log)) {
      Path normalizedJarPath = jarPath.toAbsolutePath().normalize();
      if (declaredPluginJars.contains(normalizedJarPath)) {
        continue;
      }
      helperJarPaths.add(normalizedJarPath);
    }
    return List.copyOf(helperJarPaths);
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

  public record PluginClassLoaderHandle(
      InstalledPluginDescriptor descriptor, URLClassLoader classLoader) {}
}
