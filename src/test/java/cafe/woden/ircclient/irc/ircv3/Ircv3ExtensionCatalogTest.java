package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.InstalledPluginServices;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.util.CompiledPluginJarSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Ircv3ExtensionCatalogTest {

  private static final String PLUGIN_PROVIDER_CLASS = "plugin.ircv3.RuntimeIrcv3ExtensionProvider";

  @TempDir Path tempDir;

  @Test
  void runtimeCatalogLoadsInstalledIrcv3ExtensionProviders() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("example-ircv3-provider.jar"),
        PLUGIN_PROVIDER_CLASS,
        pluginProviderSource(),
        Ircv3ExtensionDefinitionProvider.class.getName(),
        CompiledPluginJarSupport.compatibleManifest("example-ircv3-provider", "1.0.0"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");

    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    Ircv3ExtensionCatalog catalog = new Ircv3ExtensionCatalog(installedPlugins);

    assertTrue(installedPlugins.pluginProblems().isEmpty());
    assertTrue(catalog.providerIds().contains("plugin-example"));
    assertTrue(catalog.requestableCapabilityTokens().contains("draft/example-cap"));
    assertEquals("draft/example-cap", catalog.requestTokenFor("example-cap"));
    assertEquals("example-cap", catalog.preferenceKeyFor("draft/example-cap"));
    assertTrue(
        catalog.visibleFeatures().stream()
            .anyMatch(feature -> "Example feature".equals(feature.label())));
  }

  private static String pluginProviderSource() {
    return """
        package plugin.ircv3;

        import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionDefinitionProvider;
        import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionRegistry;
        import java.util.List;

        public final class RuntimeIrcv3ExtensionProvider
            implements Ircv3ExtensionDefinitionProvider {
          @Override
          public String providerId() {
            return "plugin-example";
          }

          @Override
          public int sortOrder() {
            return 950;
          }

          @Override
          public List<Ircv3ExtensionRegistry.ExtensionDefinition> extensions() {
            return List.of(
                new Ircv3ExtensionRegistry.ExtensionDefinition(
                    "example-cap",
                    Ircv3ExtensionRegistry.ExtensionKind.CAPABILITY,
                    Ircv3ExtensionRegistry.SpecStatus.DRAFT,
                    List.of("draft/example-cap"),
                    "draft/example-cap",
                    "example-cap",
                    new Ircv3ExtensionRegistry.UiMetadata(
                        "Example capability (draft)",
                        Ircv3ExtensionRegistry.UiGroup.OTHER,
                        910,
                        "Adds an example plugin-provided capability.")));
          }

          @Override
          public List<Ircv3ExtensionRegistry.FeatureDefinition> visibleFeatures() {
            return List.of(
                new Ircv3ExtensionRegistry.FeatureDefinition(
                    910,
                    "Example feature",
                    List.of("message-tags"),
                    List.of("example-cap", "draft/example-cap")));
          }
        }
        """;
  }
}
