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
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginServiceLoaderSupportTest {

  private static final String REAL_PLUGIN_PROVIDER_CLASS =
      "plugin.real.ManifestBackendNamedCommandHandler";
  private static final String SECOND_PLUGIN_PROVIDER_CLASS =
      "plugin.real.SecondManifestBackendNamedCommandHandler";

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

  @Test
  void loadsPluginProviderFromJarWithCompatibleManifest() throws Exception {
    Path pluginDir = Files.createDirectories(tempDir.resolve("plugins"));
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("compatible-provider.jar"),
        REAL_PLUGIN_PROVIDER_CLASS,
        pluginProviderSource(REAL_PLUGIN_PROVIDER_CLASS),
        BackendNamedCommandHandler.class.getName(),
        CompiledPluginJarSupport.compatibleManifest("example-plugin", "1.2.3"));

    List<BackendNamedCommandHandler> services =
        PluginServiceLoaderSupport.loadInstalledServices(
                BackendNamedCommandHandler.class,
                List.of(),
                pluginDir,
                PluginServiceLoaderSupportTest.class.getClassLoader(),
                null)
            .services();

    assertTrue(
        services.stream()
            .anyMatch(service -> REAL_PLUGIN_PROVIDER_CLASS.equals(service.getClass().getName())));
  }

  @Test
  void rejectsPluginProviderWithMissingManifestMetadata() throws Exception {
    Path pluginDir = Files.createDirectories(tempDir.resolve("plugins"));
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("missing-manifest.jar"),
        REAL_PLUGIN_PROVIDER_CLASS,
        pluginProviderSource(REAL_PLUGIN_PROVIDER_CLASS),
        BackendNamedCommandHandler.class.getName(),
        Map.of());

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

    assertTrue(error.getMessage().contains(REAL_PLUGIN_PROVIDER_CLASS));
    assertTrue(error.getMessage().contains(PluginServiceLoaderSupport.PLUGIN_ID_ATTRIBUTE));
  }

  @Test
  void rejectsPluginProviderWithUnsupportedApiVersion() throws Exception {
    Path pluginDir = Files.createDirectories(tempDir.resolve("plugins"));
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("unsupported-api.jar"),
        REAL_PLUGIN_PROVIDER_CLASS,
        pluginProviderSource(REAL_PLUGIN_PROVIDER_CLASS),
        BackendNamedCommandHandler.class.getName(),
        Map.of(
            PluginServiceLoaderSupport.PLUGIN_ID_ATTRIBUTE,
            "example-plugin",
            PluginServiceLoaderSupport.PLUGIN_VERSION_ATTRIBUTE,
            "1.2.3",
            PluginServiceLoaderSupport.PLUGIN_API_VERSION_ATTRIBUTE,
            "99"));

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

    assertTrue(error.getMessage().contains("unsupported plugin API version 99"));
    assertTrue(
        error
            .getMessage()
            .contains(Integer.toString(PluginServiceLoaderSupport.SUPPORTED_PLUGIN_API_VERSION)));
  }

  @Test
  void rejectsDuplicatePluginIdsAcrossDifferentPluginJars() throws Exception {
    Path pluginDir = Files.createDirectories(tempDir.resolve("plugins"));
    Map<String, String> duplicateManifest =
        CompiledPluginJarSupport.compatibleManifest("duplicate-plugin", "1.0.0");
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("duplicate-one.jar"),
        REAL_PLUGIN_PROVIDER_CLASS,
        pluginProviderSource(REAL_PLUGIN_PROVIDER_CLASS),
        BackendNamedCommandHandler.class.getName(),
        duplicateManifest);
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("duplicate-two.jar"),
        SECOND_PLUGIN_PROVIDER_CLASS,
        pluginProviderSource(SECOND_PLUGIN_PROVIDER_CLASS),
        BackendNamedCommandHandler.class.getName(),
        duplicateManifest);

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

    assertTrue(error.getMessage().contains("duplicate plugin id 'duplicate-plugin'"));
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

  private static String pluginProviderSource(String providerClassName) {
    int lastDot = providerClassName.lastIndexOf('.');
    String packageName = providerClassName.substring(0, lastDot);
    String simpleName = providerClassName.substring(lastDot + 1);
    return """
        package %s;

        import cafe.woden.ircclient.app.commands.BackendNamedCommandHandler;
        import cafe.woden.ircclient.app.commands.ParsedInput;
        import java.util.Set;

        public final class %s implements BackendNamedCommandHandler {
          @Override
          public Set<String> supportedCommandNames() {
            return Set.of("manifestping");
          }

          @Override
          public ParsedInput parse(String line, String matchedCommandName) {
            return new ParsedInput.BackendNamed(matchedCommandName, "ok");
          }
        }
        """
        .formatted(packageName, simpleName);
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
