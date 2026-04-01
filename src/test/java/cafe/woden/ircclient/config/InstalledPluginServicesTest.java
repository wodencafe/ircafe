package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.commands.BackendNamedCommandHandler;
import cafe.woden.ircclient.app.commands.ParsedInput;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
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

  @TempDir Path tempDir;

  @Test
  void loadsServicesFromPluginsNextToRuntimeConfig() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    writePluginJar(pluginDir.resolve("backendping.jar"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");

    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    try {
      List<BackendNamedCommandHandler> services =
          installedPlugins.loadInstalledServices(BackendNamedCommandHandler.class, List.of());

      assertEquals(runtimeConfigDirectory.resolve("plugins"), installedPlugins.pluginDirectory());
      assertTrue(
          services.stream().anyMatch(PluginProvidedBackendNamedCommandHandler.class::isInstance));
    } finally {
      installedPlugins.shutdown();
    }
  }

  private static void writePluginJar(Path jarPath) throws IOException {
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarPath))) {
      out.putNextEntry(
          new JarEntry("META-INF/services/" + BackendNamedCommandHandler.class.getName()));
      out.write(
          (PluginProvidedBackendNamedCommandHandler.class.getName() + System.lineSeparator())
              .getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }

  public static final class PluginProvidedBackendNamedCommandHandler
      implements BackendNamedCommandHandler {

    @Override
    public Set<String> supportedCommandNames() {
      return Set.of("backendping");
    }

    @Override
    public ParsedInput parse(String line, String matchedCommandName) {
      return new ParsedInput.BackendNamed(matchedCommandName, "");
    }
  }
}
