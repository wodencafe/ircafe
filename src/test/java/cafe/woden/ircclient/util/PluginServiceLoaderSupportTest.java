package cafe.woden.ircclient.util;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.commands.BackendNamedCommandHandler;
import cafe.woden.ircclient.app.commands.ParsedInput;
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

class PluginServiceLoaderSupportTest {

  @TempDir Path tempDir;

  @Test
  void wrapsInvalidProviderConfigurationWithHelpfulMessage() throws Exception {
    Path pluginDir = Files.createDirectories(tempDir.resolve("plugins"));
    writePluginJar(pluginDir.resolve("broken-provider.jar"));

    IllegalStateException error =
        assertThrows(
            IllegalStateException.class,
            () ->
                PluginServiceLoaderSupport.loadInstalledServices(
                    BackendNamedCommandHandler.class,
                    List.of(),
                    pluginDir,
                    PluginServiceLoaderSupportTest.class.getClassLoader(),
                    null));

    assertTrue(error.getMessage().contains(BackendNamedCommandHandler.class.getName()));
    assertTrue(error.getMessage().contains(PrivateBackendNamedCommandHandler.class.getName()));
    assertTrue(error.getMessage().contains("public no-arg constructor"));
  }

  private static void writePluginJar(Path jarPath) throws IOException {
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarPath))) {
      out.putNextEntry(
          new JarEntry("META-INF/services/" + BackendNamedCommandHandler.class.getName()));
      out.write(
          (PrivateBackendNamedCommandHandler.class.getName() + System.lineSeparator())
              .getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }

  private static final class PrivateBackendNamedCommandHandler
      implements BackendNamedCommandHandler {

    @Override
    public Set<String> supportedCommandNames() {
      return Set.of("broken");
    }

    @Override
    public ParsedInput parse(String line, String matchedCommandName) {
      return null;
    }
  }
}
