package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreGenericBouncerSettingsTest {

  @TempDir Path tempDir;

  @Test
  void genericBouncerSettingsDefaultToProvidedFallbacksWhenUnset() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    assertEquals("{base}/{network}", store.readGenericBouncerLoginTemplate("{base}/{network}"));
    assertTrue(store.readGenericBouncerPreferLoginHint(true));
    assertFalse(store.readGenericBouncerPreferLoginHint(false));
  }

  @Test
  void genericBouncerLoginTemplateCanBePersistedAndResetToDefault() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    store.rememberGenericBouncerLoginTemplate("{base}|{network}");
    assertEquals("{base}|{network}", store.readGenericBouncerLoginTemplate("{base}/{network}"));

    store.rememberGenericBouncerLoginTemplate("   ");
    assertEquals("{base}/{network}", store.readGenericBouncerLoginTemplate("{base}/{network}"));
  }

  @Test
  void genericBouncerPreferLoginHintCanBePersisted() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    store.rememberGenericBouncerPreferLoginHint(false);
    assertFalse(store.readGenericBouncerPreferLoginHint(true));

    store.rememberGenericBouncerPreferLoginHint(true);
    assertTrue(store.readGenericBouncerPreferLoginHint(false));
  }

  @Test
  void genericBouncerAutoConnectRulesCanBePersistedAndRemoved() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    assertTrue(store.readGenericBouncerAutoConnectRules().isEmpty());

    store.rememberGenericBouncerAutoConnectNetwork("bouncer-1", "Libera", true);
    assertEquals(
        Map.of("bouncer-1", Map.of("Libera", true)), store.readGenericBouncerAutoConnectRules());

    // Remove case-insensitively against existing key.
    store.rememberGenericBouncerAutoConnectNetwork("bouncer-1", "libera", false);
    assertTrue(store.readGenericBouncerAutoConnectRules().isEmpty());
  }

  @Test
  void removingGenericAutoConnectRulesKeepsGenericSettingsSection() {
    RuntimeConfigStore store =
        new RuntimeConfigStore(
            tempDir.resolve("ircafe.yml").toString(), new IrcProperties(null, List.of()));

    store.rememberGenericBouncerPreferLoginHint(true);
    store.rememberGenericBouncerAutoConnectNetwork("bouncer-1", "Libera", true);
    store.rememberGenericBouncerAutoConnectNetwork("bouncer-1", "Libera", false);

    assertTrue(store.readGenericBouncerAutoConnectRules().isEmpty());
    assertTrue(store.readGenericBouncerPreferLoginHint(false));
  }
}
