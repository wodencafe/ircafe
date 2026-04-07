package cafe.woden.ircclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionRegistry;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;

class Ircv3CapabilityNameSupportTest {

  @Test
  void requestTokenNormalizationMatchesRegistryForBuiltInExtensions() {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    Ircv3ExtensionRegistry.all().forEach(definition -> names.addAll(definition.allNames()));

    for (String name : names) {
      assertEquals(
          Ircv3ExtensionRegistry.requestTokenFor(name),
          Ircv3CapabilityNameSupport.normalizeRequestToken(name),
          () -> "request token mismatch for " + name);
    }
  }

  @Test
  void preferenceKeyNormalizationMatchesRegistryForBuiltInExtensions() {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    Ircv3ExtensionRegistry.all().forEach(definition -> names.addAll(definition.allNames()));

    for (String name : names) {
      assertEquals(
          Ircv3ExtensionRegistry.preferenceKeyFor(name),
          Ircv3CapabilityNameSupport.normalizePreferenceKey(name),
          () -> "preference key mismatch for " + name);
    }
  }

  @Test
  void unknownCapabilitiesPassThroughAndBlankValuesAreRejected() {
    assertEquals(
        "example/custom-cap",
        Ircv3CapabilityNameSupport.normalizeRequestToken("Example/Custom-Cap"));
    assertEquals(
        "example/custom-cap",
        Ircv3CapabilityNameSupport.normalizePreferenceKey("Example/Custom-Cap"));
    assertEquals("", Ircv3CapabilityNameSupport.normalizeRequestToken("  "));
    assertEquals(null, Ircv3CapabilityNameSupport.normalizePreferenceKey("  "));
  }
}
