package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.InstalledPluginServices;
import cafe.woden.ircclient.config.api.InstalledPluginProblem;
import cafe.woden.ircclient.config.api.InstalledPluginsPort;
import cafe.woden.ircclient.config.api.RuntimeConfigPathPort;
import cafe.woden.ircclient.util.CompiledPluginJarSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class Ircv3ExtensionCatalogTest {

  private static final String PLUGIN_PROVIDER_CLASS = "plugin.ircv3.RuntimeIrcv3ExtensionProvider";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(Ircv3ExtensionCatalog.class)
          .withBean(InstalledPluginsPort.class, RecordingInstalledPluginsPort::new);

  @TempDir Path tempDir;

  @Test
  void createsCatalogBeanThroughAutowiredInstalledPluginsConstructor() {
    runner.run(
        ctx -> {
          Ircv3ExtensionCatalog catalog = ctx.getBean(Ircv3ExtensionCatalog.class);

          assertNotNull(catalog);
          assertFalse(catalog.providerIds().isEmpty());
        });
  }

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
    assertEquals("draft/example-cap", catalog.normalizeRequestToken("example-cap"));
    assertEquals("example-cap", catalog.normalizePreferenceKey("draft/example-cap"));
    assertTrue(
        catalog.visibleFeatures().stream()
            .anyMatch(feature -> "Example feature".equals(feature.label())));
  }

  @Test
  void conflictingPluginMetadataFallsBackToBuiltInsAndRecordsProblem() {
    RecordingInstalledPluginsPort installedPlugins =
        new RecordingInstalledPluginsPort(
            List.of(
                new Ircv3ExtensionDefinitionProvider() {
                  @Override
                  public String providerId() {
                    return "plugin-conflict";
                  }

                  @Override
                  public int sortOrder() {
                    return 950;
                  }

                  @Override
                  public List<Ircv3ExtensionRegistry.ExtensionDefinition> extensions() {
                    return List.of(
                        Ircv3ExtensionProviderSupport.capability(
                            "plugin-conflict-cap",
                            Ircv3ExtensionRegistry.SpecStatus.DRAFT,
                            "echo-message",
                            "plugin-conflict-cap",
                            "Conflicting capability",
                            Ircv3ExtensionRegistry.UiGroup.OTHER,
                            950,
                            "Conflicting test-only capability."));
                  }
                }));

    Ircv3ExtensionCatalog catalog = new Ircv3ExtensionCatalog(installedPlugins);

    assertFalse(catalog.providerIds().contains("plugin-conflict"));
    assertEquals(
        Ircv3ExtensionRegistry.providerIds(),
        catalog.providerIds(),
        "conflicting plugin metadata should fall back to built-ins");
    assertEquals(1, installedPlugins.pluginProblems().size());
    assertTrue(
        installedPlugins
            .pluginProblems()
            .getFirst()
            .summary()
            .contains("IRCv3 extension metadata"));
    assertTrue(installedPlugins.pluginProblems().getFirst().details().contains("plugin-conflict"));
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

  private static final class RecordingInstalledPluginsPort implements InstalledPluginsPort {
    private final List<Ircv3ExtensionDefinitionProvider> providers;
    private final List<InstalledPluginProblem> problems = new ArrayList<>();

    private RecordingInstalledPluginsPort() {
      this(List.of());
    }

    private RecordingInstalledPluginsPort(List<Ircv3ExtensionDefinitionProvider> providers) {
      this.providers = List.copyOf(providers);
    }

    @Override
    public List<InstalledPluginProblem> pluginProblems() {
      return List.copyOf(problems);
    }

    @Override
    public void recordPluginProblem(InstalledPluginProblem problem) {
      if (problem != null) {
        problems.add(problem);
      }
    }

    @Override
    public <T> List<T> loadInstalledServices(Class<T> serviceType, List<T> builtInServices) {
      ArrayList<T> services = new ArrayList<>(builtInServices);
      if (serviceType == Ircv3ExtensionDefinitionProvider.class) {
        for (Ircv3ExtensionDefinitionProvider provider : providers) {
          services.add(serviceType.cast(provider));
        }
      }
      return List.copyOf(services);
    }
  }
}
