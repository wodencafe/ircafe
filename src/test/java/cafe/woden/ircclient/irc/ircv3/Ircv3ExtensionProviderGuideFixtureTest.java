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

class Ircv3ExtensionProviderGuideFixtureTest {

  private static final String GUIDE_PROVIDER_CLASS = "example.ircv3.ExampleIrcv3Provider";

  @TempDir Path tempDir;

  @Test
  void documentedExampleProviderLoadsThroughInstalledPluginCatalog() throws Exception {
    Path runtimeConfigDirectory = Files.createDirectories(tempDir.resolve("config-home/ircafe"));
    Path pluginDir = Files.createDirectories(runtimeConfigDirectory.resolve("plugins"));
    CompiledPluginJarSupport.writePluginJar(
        pluginDir.resolve("ircv3-guide-example.jar"),
        GUIDE_PROVIDER_CLASS,
        guideProviderSource(),
        Ircv3ExtensionDefinitionProvider.class.getName(),
        CompiledPluginJarSupport.compatibleManifest("ircv3-guide-example", "1.0.0"));
    RuntimeConfigPathPort runtimeConfigPathPort =
        () -> runtimeConfigDirectory.resolve("ircafe.yml");

    InstalledPluginServices installedPlugins = new InstalledPluginServices(runtimeConfigPathPort);
    Ircv3ExtensionCatalog catalog = new Ircv3ExtensionCatalog(installedPlugins);

    assertTrue(installedPlugins.pluginProblems().isEmpty());
    assertTrue(catalog.providerIds().contains("example-ircv3"));
    assertTrue(catalog.requestableCapabilityTokens().contains("draft/example-cap"));
    assertEquals("draft/example-cap", catalog.normalizeRequestToken("example-cap"));
    assertEquals("example-cap", catalog.normalizePreferenceKey("draft/example-cap"));
    assertTrue(
        catalog.visibleFeatures().stream()
            .anyMatch(feature -> "Example feature".equals(feature.label())));
  }

  private static String guideProviderSource() {
    return """
        package example.ircv3;

        import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionDefinitionProvider;
        import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionRegistry;
        import java.util.List;

        public final class ExampleIrcv3Provider implements Ircv3ExtensionDefinitionProvider {

          @Override
          public String providerId() {
            return "example-ircv3";
          }

          @Override
          public int sortOrder() {
            return 900;
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
                        900,
                        "Adds example plugin-provided IRCv3 capability metadata.")));
          }

          @Override
          public List<Ircv3ExtensionRegistry.FeatureDefinition> visibleFeatures() {
            return List.of(
                new Ircv3ExtensionRegistry.FeatureDefinition(
                    900,
                    "Example feature",
                    List.of("message-tags"),
                    List.of("example-cap", "draft/example-cap")));
          }
        }
        """;
  }
}
