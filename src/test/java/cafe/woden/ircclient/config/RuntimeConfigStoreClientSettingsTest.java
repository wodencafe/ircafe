package cafe.woden.ircclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigStoreClientSettingsTest {

  @TempDir Path tempDir;

  @Test
  void persistsFloodPreferencesAndReloadsThemThroughSpringBinding() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    Files.writeString(cfg, "irc:\n  client:\n    floodProtection:\n      futureOption: keep\n");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);
    IrcProperties.FloodProtection settings = new IrcProperties.FloodProtection(false, 2200, 8000);
    store.rememberClientFloodProtection(settings);
    org.springframework.core.env.StandardEnvironment env =
        new org.springframework.core.env.StandardEnvironment();
    var source =
        new org.springframework.boot.env.YamlPropertySourceLoader()
            .load("test", new org.springframework.core.io.FileSystemResource(cfg))
            .getFirst();
    env.getPropertySources().addFirst(source);
    IrcProperties rebound =
        org.springframework.boot.context.properties.bind.Binder.get(env)
            .bind("irc", IrcProperties.class)
            .get();
    assertEquals(settings, rebound.client().floodProtection());
    assertTrue(Files.readString(cfg).contains("futureOption: keep"));
  }

  @Test
  void persistsNormalizedClientHeartbeatAndProxySettings() throws Exception {
    Path cfg = tempDir.resolve("ircafe.yml");
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(cfg);

    store.rememberClientHeartbeat(new IrcProperties.Heartbeat(false, 500, 2_000));
    store.rememberClientProxy(
        new IrcProperties.Proxy(false, " proxy.example ", -1, " alice ", null, false, 0, -1));

    String yaml = Files.readString(cfg);
    assertTrue(yaml.contains("heartbeat:"));
    assertTrue(yaml.contains("enabled: false"));
    assertTrue(yaml.contains("checkPeriodMs: 1000"));
    assertTrue(yaml.contains("timeoutMs: 2000"));
    assertTrue(yaml.contains("proxy:"));
    assertTrue(yaml.contains("host: proxy.example"));
    assertTrue(yaml.contains("port: 0"));
    assertTrue(yaml.contains("username: alice"));
    assertTrue(yaml.contains("password: ''") || yaml.contains("password: \"\""));
    assertTrue(yaml.contains("remoteDns: false"));
    assertTrue(yaml.contains("connectTimeoutMs: 20000"));
    assertTrue(yaml.contains("readTimeoutMs: 30000"));
  }
}
