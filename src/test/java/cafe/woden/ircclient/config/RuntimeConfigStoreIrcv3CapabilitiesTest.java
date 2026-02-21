package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreIrcv3CapabilitiesTest {

  @TempDir
  Path tempDir;

  @Test
  void capabilityOverridesDefaultToEnabledWhenUnset() {
    RuntimeConfigStore store = new RuntimeConfigStore(
        tempDir.resolve("ircafe.yml").toString(),
        new IrcProperties(null, List.of()));

    assertTrue(store.isIrcv3CapabilityEnabled("typing", true));
    assertTrue(store.readIrcv3Capabilities().isEmpty());
  }

  @Test
  void capabilityOverrideCanBeDisabledAndThenResetToDefault() {
    RuntimeConfigStore store = new RuntimeConfigStore(
        tempDir.resolve("ircafe.yml").toString(),
        new IrcProperties(null, List.of()));

    store.rememberIrcv3CapabilityEnabled("typing", false);
    assertFalse(store.isIrcv3CapabilityEnabled("typing", true));
    assertTrue(Boolean.FALSE.equals(store.readIrcv3Capabilities().get("typing")));

    // Re-enabling removes the explicit override so default behavior applies again.
    store.rememberIrcv3CapabilityEnabled("typing", true);
    assertTrue(store.isIrcv3CapabilityEnabled("typing", true));
    assertFalse(store.readIrcv3Capabilities().containsKey("typing"));
  }
}
