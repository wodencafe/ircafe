package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.commands.BackendNamedCommandHandler;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.util.CompiledPluginJarSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
      assertTrue(
          services.stream()
              .anyMatch(
                  service -> REAL_PLUGIN_PROVIDER_CLASS.equals(service.getClass().getName())));
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
}
