package cafe.woden.ircclient.irc.ircv3;

import static cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures.ircv3StsPolicyPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

class Ircv3StsPolicyServiceTest {

  @TempDir Path tempDir;

  @Test
  void exposesOnlyExplicitRuntimeConstructor() {
    Constructor<?>[] constructors = Ircv3StsPolicyService.class.getConstructors();
    assertEquals(1, constructors.length);
    Constructor<?> constructor = constructors[0];
    assertTrue(constructor.isAnnotationPresent(Autowired.class));
    assertEquals(2, constructor.getParameterCount());
    assertTrue(
        Arrays.asList(constructor.getParameterTypes())
            .contains(Ircv3InboundCommandSignalRuntimeCatalog.class));
  }

  @Test
  void secureStsPolicyUpgradesFutureConnectionsToTlsAndPolicyPort() {
    Ircv3StsPolicyService svc = Ircv3RuntimeTestFixtures.stsPolicyService();
    IrcProperties.Server configured = server("irc.example.net", 6667, false);

    svc.observeFromCapList(
        "libera", configured.host(), true, "sts=duration=86400,port=6697,preload");
    IrcProperties.Server effective = svc.applyPolicy(configured);

    assertTrue(effective.tls());
    assertEquals(6697, effective.port());
    assertTrue(svc.activePolicyForHost(configured.host()).isPresent());
  }

  @Test
  void insecureConnectionDoesNotLearnStsPolicy() {
    Ircv3StsPolicyService svc = Ircv3RuntimeTestFixtures.stsPolicyService();
    IrcProperties.Server configured = server("irc.example.net", 6667, false);

    svc.observeFromCapList("libera", configured.host(), false, "sts=duration=86400,port=6697");
    IrcProperties.Server effective = svc.applyPolicy(configured);

    assertFalse(effective.tls());
    assertEquals(6667, effective.port());
    assertTrue(svc.activePolicyForHost(configured.host()).isEmpty());
  }

  @Test
  void durationZeroClearsExistingPolicy() {
    Ircv3StsPolicyService svc = Ircv3RuntimeTestFixtures.stsPolicyService();
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
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));
    IrcProperties.Server configured = server("irc.example.net", 6667, false);

    Ircv3StsPolicyService writer =
        Ircv3RuntimeTestFixtures.stsPolicyService(ircv3StsPolicyPort(store));
    writer.observeFromCapList(
        "libera", configured.host(), true, "sts=duration=86400,port=6697,preload");
    assertTrue(store.readIrcv3StsPolicies().containsKey("irc.example.net"));

    Ircv3StsPolicyService reader =
        Ircv3RuntimeTestFixtures.stsPolicyService(ircv3StsPolicyPort(store));
    IrcProperties.Server effective = reader.applyPolicy(configured);
    assertTrue(effective.tls());
    assertEquals(6697, effective.port());
  }

  @Test
  void durationZeroAlsoRemovesPersistedPolicy() {
    RuntimeConfigStore store = RuntimeConfigStoreTestFixtures.store(tempDir.resolve("ircafe.yml"));
    IrcProperties.Server configured = server("irc.example.net", 6667, false);
    Ircv3StsPolicyService svc =
        Ircv3RuntimeTestFixtures.stsPolicyService(ircv3StsPolicyPort(store));

    svc.observeFromCapList("libera", configured.host(), true, "sts=duration=86400,port=6697");
    assertTrue(store.readIrcv3StsPolicies().containsKey("irc.example.net"));

    svc.observeFromCapList("libera", configured.host(), true, "sts=duration=0");
    assertTrue(store.readIrcv3StsPolicies().isEmpty());
  }

  private static IrcProperties.Server server(String host, int port, boolean tls) {
    return IrcPropertiesTestFixtures.serverBuilder("libera")
        .host(host)
        .port(port)
        .tls(tls)
        .nick("IRCafeUser")
        .build();
  }
}
