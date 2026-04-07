package cafe.woden.ircclient.irc.ircv3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ircv3CapabilityNameResolverAdapterTest {

  @Test
  void catalogResolverNormalizesPluginProvidedAliases() {
    ArrayList<Ircv3ExtensionDefinitionProvider> providers =
        new ArrayList<>(Ircv3ExtensionRegistry.builtInProviders());
    providers.add(new ExampleCapabilityProvider());

    Ircv3CapabilityNameResolverAdapter resolver =
        new Ircv3CapabilityNameResolverAdapter(Ircv3ExtensionCatalog.forProviders(providers));

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
}
