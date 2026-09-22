package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.*;

import cafe.woden.ircclient.config.servers.ServerRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class RuntimeConfigFirstRunSetupAdapterTest {
  @TempDir Path temp;

  @Test
  void skippingPreservesDefaultsAndSurvivesRestart() throws Exception {
    var path = temp.resolve("ircafe.yml");
    var defaults = IrcPropertiesTestFixtures.properties(IrcPropertiesTestFixtures.server("libera"));
    var store = new RuntimeConfigStore(path.toString(), defaults);
    var registry = new ServerRegistry(defaults, new RuntimeConfigServerRegistryAdapter(store));
    var setup = new RuntimeConfigFirstRunSetupAdapter(store, registry);
    assertTrue(Files.exists(path), "normal startup already creates the config");
    assertTrue(setup.shouldOfferSetup());

    setup.finishSetup(null);

    assertEquals(defaults.servers(), registry.servers());
    assertFalse(setup.shouldOfferSetup());
    var restarted = new RuntimeConfigStore(path.toString(), defaults);
    assertTrue(restarted.readFirstRunSetupDismissed());
    assertFalse(new RuntimeConfigFirstRunSetupAdapter(restarted, registry).shouldOfferSetup());
  }

  @Test
  void existingProfilesIncludingEmptyServerListsNeverPromptOrGetReplaced() throws Exception {
    var path = temp.resolve("ircafe.yml");
    Files.writeString(path, "irc:\n  servers: []\nircafe:\n  ui:\n    theme: custom\n");
    var store = RuntimeConfigStoreTestFixtures.store(path);
    var registry =
        new ServerRegistry(
            IrcPropertiesTestFixtures.properties(), new RuntimeConfigServerRegistryAdapter(store));
    var setup = new RuntimeConfigFirstRunSetupAdapter(store, registry);
    String before = Files.readString(path);

    assertFalse(setup.shouldOfferSetup());
    setup.finishSetup(IrcPropertiesTestFixtures.server("new"));

    assertEquals(before, Files.readString(path));
    assertTrue(registry.servers().isEmpty());
  }

  @Test
  void completedSetupPersistsServerAccountChannelsAndKeepsOtherPreferences() throws Exception {
    var path = temp.resolve("ircafe.yml");
    var defaults = IrcPropertiesTestFixtures.properties(IrcPropertiesTestFixtures.server("libera"));
    var store = new RuntimeConfigStore(path.toString(), defaults);
    var registry = new ServerRegistry(defaults, new RuntimeConfigServerRegistryAdapter(store));
    var setup = new RuntimeConfigFirstRunSetupAdapter(store, registry);
    store.rememberUiSettings("dark", "Dialog", 14);
    var server =
        IrcPropertiesTestFixtures.serverBuilder("custom")
            .host("irc.example.org")
            .sasl(new IrcProperties.Server.Sasl(true, "account", "secret", "PLAIN", true))
            .autoJoin(List.of("#one", "#two"))
            .build();

    setup.finishSetup(server);

    assertEquals(List.of(server), registry.servers());
    assertFalse(setup.shouldOfferSetup());
    Map<?, ?> yaml = new Yaml().load(Files.readString(path));
    var persisted = (Map<?, ?>) ((List<?>) ((Map<?, ?>) yaml.get("irc")).get("servers")).getFirst();
    assertEquals("custom", persisted.get("id"));
    assertEquals("irc.example.org", persisted.get("host"));
    assertEquals(List.of("#one", "#two"), persisted.get("autoJoin"));
    assertEquals("secret", ((Map<?, ?>) persisted.get("sasl")).get("password"));
    assertTrue(Files.readString(path).contains("dark"));
    assertTrue(store.readFirstRunSetupDismissed());
  }
}
