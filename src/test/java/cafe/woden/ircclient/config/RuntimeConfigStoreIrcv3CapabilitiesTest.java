package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.api.Ircv3CapabilityNameResolverPort;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreIrcv3CapabilitiesTest {

  @TempDir Path tempDir;

  @Test
  void capabilityOverridesDefaultToEnabledWhenUnset() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    assertTrue(store.isIrcv3CapabilityEnabled("typing", true));
    assertTrue(store.readIrcv3Capabilities().isEmpty());
  }

  @Test
  void capabilityOverrideCanBeDisabledAndThenResetToDefault() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    store.rememberIrcv3CapabilityEnabled("typing", false);
    assertFalse(store.isIrcv3CapabilityEnabled("typing", true));
    assertTrue(Boolean.FALSE.equals(store.readIrcv3Capabilities().get("typing")));

    // Re-enabling removes the explicit override so default behavior applies again.
    store.rememberIrcv3CapabilityEnabled("typing", true);
    assertTrue(store.isIrcv3CapabilityEnabled("typing", true));
    assertFalse(store.readIrcv3Capabilities().containsKey("typing"));
  }

  @Test
  void draftCapabilityOverridesReuseExistingCanonicalPreferenceKeys() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    store.rememberIrcv3CapabilityEnabled("chathistory", false);

    assertFalse(store.isIrcv3CapabilityEnabled("draft/chathistory", true));
    assertFalse(store.isIrcv3CapabilityEnabled("chathistory", true));
    assertTrue(Boolean.FALSE.equals(store.readIrcv3Capabilities().get("chathistory")));
  }

  @Test
  void runtimeResolverCanCanonicalizePluginProvidedCapabilityAliases() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));
    store.setIrcv3CapabilityNameResolver(
        new Ircv3CapabilityNameResolverPort() {
          @Override
          public String normalizePreferenceKey(String capability) {
            return switch (String.valueOf(capability).trim().toLowerCase(Locale.ROOT)) {
              case "plugin/example-cap", "draft/plugin-example-cap" -> "plugin-example-cap";
              default -> Ircv3CapabilityNameResolverPort.super.normalizePreferenceKey(capability);
            };
          }
        });

    store.rememberIrcv3CapabilityEnabled("draft/plugin-example-cap", false);

    assertFalse(store.isIrcv3CapabilityEnabled("plugin/example-cap", true));
    assertTrue(Boolean.FALSE.equals(store.readIrcv3Capabilities().get("plugin-example-cap")));
  }
}
