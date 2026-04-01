package cafe.woden.ircclient.app.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class BackendNamedCommandCatalogTest {

  @TempDir Path tempDir;

  @Test
  void loadsServiceProvidersFromPluginDirectoryJar() throws Exception {
    Path pluginDir = Files.createDirectories(tempDir.resolve("plugins"));
    writePluginJar(pluginDir.resolve("backendping.jar"));

    BackendNamedCommandCatalog catalog =
        BackendNamedCommandCatalog.installed(
            pluginDir, BackendNamedCommandCatalogTest.class.getClassLoader());

    ParsedInput parsed = catalog.parse("/backendping hello");

    assertTrue(parsed instanceof ParsedInput.BackendNamed);
    assertEquals("backendping", ((ParsedInput.BackendNamed) parsed).command());
    assertEquals("hello", ((ParsedInput.BackendNamed) parsed).args());
  }

  @Test
  void loadsServiceProvidersFromPluginsNextToRuntimeConfig() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    writePluginJar(pluginDir.resolve("backendping.jar"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");

    BackendNamedCommandCatalog catalog =
        BackendNamedCommandCatalog.installed(
            runtimeConfigPathPort, BackendNamedCommandCatalogTest.class.getClassLoader());

    ParsedInput parsed = catalog.parse("/backendping hello");

    assertEquals(
        runtimeConfigDirectory.resolve("plugins"),
        BackendNamedCommandCatalog.resolvePluginDirectory(runtimeConfigPathPort));
    assertTrue(parsed instanceof ParsedInput.BackendNamed);
    assertEquals("backendping", ((ParsedInput.BackendNamed) parsed).command());
    assertEquals("hello", ((ParsedInput.BackendNamed) parsed).args());
  }

  @Test
  void duplicateParserCommandRegistrationsFailFast() {
    BackendNamedCommandHandler first =
        new BackendNamedCommandHandler() {
          @Override
          public Set<String> supportedCommandNames() {
            return Set.of("backendping");
          }

          @Override
          public ParsedInput parse(String line, String matchedCommandName) {
            return null;
          }
        };
    BackendNamedCommandHandler second =
        new BackendNamedCommandHandler() {
          @Override
          public Set<String> supportedCommandNames() {
            return Set.of("backendping");
          }

          @Override
          public ParsedInput parse(String line, String matchedCommandName) {
            return null;
          }
        };

    assertThrows(
        IllegalStateException.class,
        () -> BackendNamedCommandCatalog.fromHandlers(List.of(first, second)));
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
      return new ParsedInput.BackendNamed(
          matchedCommandName, BackendNamedCommandParser.argAfter(line, "/" + matchedCommandName));
    }

    @Override
    public List<SlashCommandDescriptor> autocompleteCommands() {
      return List.of(new SlashCommandDescriptor("/backendping", "Plugin test command"));
    }
  }
}
