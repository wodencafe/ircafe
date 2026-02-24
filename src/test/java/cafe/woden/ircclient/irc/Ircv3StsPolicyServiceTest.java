package cafe.woden.ircclient.irc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.IrcProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Ircv3StsPolicyServiceTest {

  @TempDir
  Path tempDir;

  @Test
  void secureStsPolicyUpgradesFutureConnectionsToTlsAndPolicyPort() {
    Ircv3StsPolicyService svc = new Ircv3StsPolicyService();
    IrcProperties.Server configured = server("irc.example.net", 6667, false);

    svc.observeFromCapList("libera", configured.host(), true, "sts=duration=86400,port=6697,preload");
    IrcProperties.Server effective = svc.applyPolicy(configured);

    assertTrue(effective.tls());
    assertEquals(6697, effective.port());
    assertTrue(svc.activePolicyForHost(configured.host()).isPresent());
  }

  @Test
  void insecureConnectionDoesNotLearnStsPolicy() {
    Ircv3StsPolicyService svc = new Ircv3StsPolicyService();
    IrcProperties.Server configured = server("irc.example.net", 6667, false);

    svc.observeFromCapList("libera", configured.host(), false, "sts=duration=86400,port=6697");
    IrcProperties.Server effective = svc.applyPolicy(configured);

    assertFalse(effective.tls());
    assertEquals(6667, effective.port());
    assertTrue(svc.activePolicyForHost(configured.host()).isEmpty());
  }

  @Test
  void durationZeroClearsExistingPolicy() {
    Ircv3StsPolicyService svc = new Ircv3StsPolicyService();
    IrcProperties.Server configured = server("irc.example.net", 6667, false);

    svc.observeFromCapList("libera", configured.host(), true, "sts=duration=86400,port=6697");
    assertTrue(svc.activePolicyForHost(configured.host()).isPresent());

    svc.observeFromCapList("libera", configured.host(), true, "sts=duration=0");
    assertTrue(svc.activePolicyForHost(configured.host()).isEmpty());
    IrcProperties.Server effective = svc.applyPolicy(configured);
    assertFalse(effective.tls());
    assertEquals(6667, effective.port());
  }

  @Test
  void learnedPolicyPersistsToRuntimeConfigAndReloads() {
    RuntimeConfigStore store = new RuntimeConfigStore(
        tempDir.resolve("ircafe.yml").toString(),
        new IrcProperties(null, List.of()));
    IrcProperties.Server configured = server("irc.example.net", 6667, false);

    Ircv3StsPolicyService writer = new Ircv3StsPolicyService(store);
    writer.observeFromCapList("libera", configured.host(), true, "sts=duration=86400,port=6697,preload");
    assertTrue(store.readIrcv3StsPolicies().containsKey("irc.example.net"));

    Ircv3StsPolicyService reader = new Ircv3StsPolicyService(store);
    IrcProperties.Server effective = reader.applyPolicy(configured);
    assertTrue(effective.tls());
    assertEquals(6697, effective.port());
  }

  @Test
  void durationZeroAlsoRemovesPersistedPolicy() {
    RuntimeConfigStore store = new RuntimeConfigStore(
        tempDir.resolve("ircafe.yml").toString(),
        new IrcProperties(null, List.of()));
    IrcProperties.Server configured = server("irc.example.net", 6667, false);
    Ircv3StsPolicyService svc = new Ircv3StsPolicyService(store);

    svc.observeFromCapList("libera", configured.host(), true, "sts=duration=86400,port=6697");
    assertTrue(store.readIrcv3StsPolicies().containsKey("irc.example.net"));

    svc.observeFromCapList("libera", configured.host(), true, "sts=duration=0");
    assertTrue(store.readIrcv3StsPolicies().isEmpty());
  }

  private static IrcProperties.Server server(String host, int port, boolean tls) {
    return new IrcProperties.Server(
        "libera",
        host,
        port,
        tls,
        "",
        "IRCafeUser",
        "ircafe",
        "IRCafe User",
        new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null),
        List.of(),
        List.of(),
        null);
  }
}
