package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.commands.BackendNamedCommandHandler;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.util.CompiledPluginJarSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstalledPluginServicesTest {

  private static final String REAL_PLUGIN_PROVIDER_CLASS =
      "plugin.installed.RuntimeBackendNamedCommandHandler";

  @TempDir Path tempDir;

  @Test
  void loadsServicesFromPluginsNextToRuntimeConfig() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("backendping.jar"),
        REAL_PLUGIN_PROVIDER_CLASS,
        pluginProviderSource(),
        BackendNamedCommandHandler.class.getName(),
        CompiledPluginJarSupport.compatibleManifest("installed-plugin", "1.0.0"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");

    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    try {
      List<BackendNamedCommandHandler> services =
          installedPlugins.loadInstalledServices(BackendNamedCommandHandler.class, List.of());

      assertEquals(runtimeConfigDirectory.resolve("plugins"), installedPlugins.pluginDirectory());
      assertEquals(1, installedPlugins.installedPlugins().size());
      assertEquals("installed-plugin", installedPlugins.installedPlugins().getFirst().pluginId());
      assertTrue(installedPlugins.pluginProblems().isEmpty());
      assertTrue(
          services.stream()
              .anyMatch(
                  service -> REAL_PLUGIN_PROVIDER_CLASS.equals(service.getClass().getName())));
    } finally {
      installedPlugins.shutdown();
    }
  }

  @Test
  void keepsHealthyPluginProvidersWhenAnotherPluginProviderIsInvalid() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("broken-plugins/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("healthy-provider.jar"),
        REAL_PLUGIN_PROVIDER_CLASS,
        pluginProviderSource(),
        BackendNamedCommandHandler.class.getName(),
        CompiledPluginJarSupport.compatibleManifest("healthy-plugin", "1.0.0"));
    writeBrokenProviderJar(pluginDir.resolve("broken-provider.jar"), "broken-plugin");
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");

    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    try {
      BackendNamedCommandHandler builtInHandler = new BuiltInBackendNamedCommandHandler();

      List<BackendNamedCommandHandler> services =
          installedPlugins.loadInstalledServices(
              BackendNamedCommandHandler.class, List.of(builtInHandler));

      assertTrue(services.contains(builtInHandler));
      assertEquals(2, installedPlugins.installedPlugins().size());
      assertTrue(
          services.stream()
              .anyMatch(
                  service -> REAL_PLUGIN_PROVIDER_CLASS.equals(service.getClass().getName())));
      assertTrue(
          services.stream()
              .noneMatch(
                  service ->
                      "plugin.installed.MissingBackendNamedCommandHandler"
                          .equals(service.getClass().getName())));
      assertEquals(1, installedPlugins.pluginProblems().size());
      assertTrue(installedPlugins.pluginProblems().getFirst().summary().contains("broken-plugin"));
      assertTrue(
          installedPlugins.pluginProblems().getFirst().details().contains("broken-provider.jar"));
    } finally {
      installedPlugins.shutdown();
    }
  }

  private static String pluginProviderSource() {
    return """
        package plugin.installed;

        import cafe.woden.ircclient.app.commands.BackendNamedCommandHandler;
        import cafe.woden.ircclient.app.commands.ParsedInput;
        import java.util.Set;

        public final class RuntimeBackendNamedCommandHandler implements BackendNamedCommandHandler {
          @Override
          public Set<String> supportedCommandNames() {
            return Set.of("backendping");
          }

          @Override
          public ParsedInput parse(String line, String matchedCommandName) {
            return new ParsedInput.BackendNamed(matchedCommandName, "");
          }
        }
        """;
  }

  private static void writeBrokenProviderJar(Path jarPath, String pluginId) throws IOException {
    var manifest = new java.util.jar.Manifest();
    var attributes = manifest.getMainAttributes();
    attributes.put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
    for (var entry : CompiledPluginJarSupport.compatibleManifest(pluginId, "1.0.0").entrySet()) {
      attributes.putValue(entry.getKey(), entry.getValue());
    }
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
      out.putNextEntry(
          new JarEntry("META-INF/services/" + BackendNamedCommandHandler.class.getName()));
      out.write(
          "plugin.installed.MissingBackendNamedCommandHandler\n".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }

  private static final class BuiltInBackendNamedCommandHandler
      implements BackendNamedCommandHandler {

    @Override
    public Set<String> supportedCommandNames() {
      return Set.of("built-in");
    }

    @Override
    public cafe.woden.ircclient.app.commands.ParsedInput parse(
        String line, String matchedCommandName) {
      return new cafe.woden.ircclient.app.commands.ParsedInput.BackendNamed("built-in", "");
    }
  }
}
