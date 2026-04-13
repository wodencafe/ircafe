package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cafe.woden.ircclient.config.InstalledPluginServices;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
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
              InstalledPluginServices.class,
              Ircv3ExtensionCatalog.class,
              Ircv3CapabilityNameResolverAdapter.class)
          .withBean(IrcProperties.class, () -> new IrcProperties(null, List.of()));

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
    ArrayList<Ircv3ExtensionDefinitionProvider> providers =
        new ArrayList<>(Ircv3ExtensionRegistry.builtInProviders());
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

  private static final class ExampleCapabilityProvider implements Ircv3ExtensionDefinitionProvider {

    @Override
    public String providerId() {
      return "plugin-example-capability";
    }

    @Override
    public int sortOrder() {
      return 940;
    }

    @Override
    public List<Ircv3ExtensionRegistry.ExtensionDefinition> extensions() {
      return List.of(
          Ircv3ExtensionProviderSupport.capability(
              "plugin-example-cap",
              Ircv3ExtensionRegistry.SpecStatus.DRAFT,
              "draft/plugin-example-cap",
              "plugin-example-cap",
              "Plugin example capability",
              Ircv3ExtensionRegistry.UiGroup.OTHER,
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
