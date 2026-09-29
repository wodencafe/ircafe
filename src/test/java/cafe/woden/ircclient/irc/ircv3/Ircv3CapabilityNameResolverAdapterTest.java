package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.RuntimeConfigPathAdapter;
import cafe.woden.ircclient.config.plugins.InstalledPluginServices;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3ExtensionContribution;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3ExtensionProvider;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3SpecStatus;
import cafe.woden.ircclient.irc.ircv3.spi.Ircv3UiGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class Ircv3CapabilityNameResolverAdapterTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withPropertyValues("ircafe.runtime-config=")
          .withUserConfiguration(
              RuntimeConfigStore.class,
              RuntimeConfigPathAdapter.class,
              InstalledPluginServices.class,
              Ircv3ExtensionCatalog.class,
              Ircv3CapabilityNameResolverAdapter.class)
          .withBean(IrcProperties.class, IrcPropertiesTestFixtures::properties);

  @Test
  void springContextCreatesResolverCatalogAndInstalledPluginsWithoutCycle() {
    runner.run(
        ctx -> {
          assertNotNull(ctx.getBean(RuntimeConfigStore.class));
          assertNotNull(ctx.getBean(InstalledPluginServices.class));
          assertNotNull(ctx.getBean(Ircv3ExtensionCatalog.class));
          assertNotNull(ctx.getBean(Ircv3CapabilityNameResolverAdapter.class));
        });
  }

  @Test
  void catalogResolverNormalizesPluginProvidedAliases() {
    ArrayList<Ircv3ExtensionProvider> providers =
        new ArrayList<>(Ircv3ExtensionRegistry.defaultProviders());
    providers.add(new ExampleCapabilityProvider());

    Ircv3CapabilityNameResolverAdapter resolver =
        new Ircv3CapabilityNameResolverAdapter(
            catalogProvider(Ircv3ExtensionCatalog.forProviders(providers)));

    assertEquals("draft/plugin-example-cap", resolver.normalizeRequestToken("plugin/example-cap"));
    assertEquals("plugin-example-cap", resolver.normalizePreferenceKey("plugin/example-cap"));
    assertEquals(
        "draft/plugin-example-cap", resolver.normalizeRequestToken("draft/plugin-example-cap"));
    assertEquals("custom/example-cap", resolver.normalizeRequestToken("Custom/Example-Cap"));
    assertEquals("custom/example-cap", resolver.normalizePreferenceKey("Custom/Example-Cap"));
    assertEquals("", resolver.normalizeRequestToken("typing"));
  }

  private static final class ExampleCapabilityProvider implements Ircv3ExtensionProvider {

    @Override
    public String providerId() {
      return "plugin-example-capability";
    }

    @Override
    public int sortOrder() {
      return 940;
    }

    @Override
    public List<Ircv3ExtensionContribution> extensions() {
      return List.of(
          Ircv3TestExtensionContributions.capability(
              "plugin-example-cap",
              Ircv3SpecStatus.DRAFT,
              "draft/plugin-example-cap",
              "plugin-example-cap",
              "Plugin example capability",
              Ircv3UiGroup.OTHER,
              940,
              "Test-only plugin-provided capability alias mapping.",
              "plugin/example-cap",
              "draft/plugin-example-cap"));
    }
  }

  private static ObjectProvider<Ircv3ExtensionCatalog> catalogProvider(
      Ircv3ExtensionCatalog catalog) {
    return new ObjectProvider<>() {
      @Override
      public Ircv3ExtensionCatalog getIfAvailable(Supplier<Ircv3ExtensionCatalog> defaultSupplier) {
        return catalog;
      }
    };
  }
}
