package cafe.woden.ircclient.irc.soju;

import static cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures.bouncerDiscoveryPort;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.bouncer.BouncerConnectionPort;
import cafe.woden.ircclient.bouncer.spi.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.bouncer.spi.BuiltInBouncerBackendIds;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures;
import cafe.woden.ircclient.config.properties.SojuProperties;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.config.servers.EphemeralServerRegistry;
import cafe.woden.ircclient.config.servers.ServerRegistry;
import io.reactivex.rxjava3.core.Completable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SojuEphemeralNetworkImporterTest {

  @Test
  void upsertsEphemeralServerForDiscoveredNetwork() {
    IrcProperties.Server.Sasl sasl =
        new IrcProperties.Server.Sasl(true, "zimmerdon", "pw", "PLAIN", null);
    IrcProperties.Server bouncer =
        IrcPropertiesTestFixtures.serverBuilder("soju")
            .host("bouncer.example")
            .nick("zimmedon")
            .login("zimmerdon")
            .realName("Real")
            .sasl(sasl)
            .build();

    IrcProperties props = IrcPropertiesTestFixtures.properties(bouncer);
    RuntimeConfigStore runtime = RuntimeConfigStoreTestFixtures.inMemoryStore(props);
    ServerRegistry configured =
        new ServerRegistry(props, RuntimeConfigStoreTestFixtures.serverRegistryPort(runtime));
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();

    SojuAutoConnectStore autoConnect =
        new SojuAutoConnectStore(
            new SojuProperties(Map.of(), new SojuProperties.Discovery(true)),
            bouncerDiscoveryPort(runtime));
    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer =
        new SojuEphemeralNetworkImporter(
            new SojuBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            bouncerDiscoveryPort(runtime),
            connectionPort);

    BouncerDiscoveredNetwork net = sojuNetwork("soju", "123", "libera");
    importer.onNetworkDiscovered(net);

    assertTrue(ephemeral.containsId("soju:soju:123"));
    IrcProperties.Server imported = ephemeral.require("soju:soju:123");
    assertEquals("zimmerdon/libera@ircafe", imported.login());
    assertEquals("zimmerdon/libera@ircafe", imported.sasl().username());
    assertEquals("soju", ephemeral.originOf(imported.id()).orElseThrow());
  }

  @Test
  void callingTwiceDoesNotCreateDuplicate() {
    IrcProperties.Server.Sasl sasl =
        new IrcProperties.Server.Sasl(true, "user", "pw", "PLAIN", null);
    IrcProperties.Server bouncer =
        IrcPropertiesTestFixtures.serverBuilder("soju")
            .host("bouncer.example")
            .nick("nick")
            .login("user")
            .realName("Real")
            .sasl(sasl)
            .build();

    IrcProperties props = IrcPropertiesTestFixtures.properties(bouncer);
    RuntimeConfigStore runtime = RuntimeConfigStoreTestFixtures.inMemoryStore(props);
    ServerRegistry configured =
        new ServerRegistry(props, RuntimeConfigStoreTestFixtures.serverRegistryPort(runtime));
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();

    SojuAutoConnectStore autoConnect =
        new SojuAutoConnectStore(
            new SojuProperties(Map.of(), new SojuProperties.Discovery(true)),
            bouncerDiscoveryPort(runtime));
    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer =
        new SojuEphemeralNetworkImporter(
            new SojuBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            bouncerDiscoveryPort(runtime),
            connectionPort);
    BouncerDiscoveredNetwork net = sojuNetwork("soju", "9", "oftc");

    importer.onNetworkDiscovered(net);
    importer.onNetworkDiscovered(net);

    assertEquals(1, ephemeral.serverIds().size());
    assertTrue(ephemeral.containsId("soju:soju:9"));
  }

  @Test
  void enabledRuleTriggersAutoConnectOnce() {
    IrcProperties.Server.Sasl sasl =
        new IrcProperties.Server.Sasl(true, "user", "pw", "PLAIN", null);
    IrcProperties.Server bouncer =
        IrcPropertiesTestFixtures.serverBuilder("soju")
            .host("bouncer.example")
            .nick("nick")
            .login("user")
            .realName("Real")
            .sasl(sasl)
            .build();

    IrcProperties props = IrcPropertiesTestFixtures.properties(bouncer);
    RuntimeConfigStore runtime = RuntimeConfigStoreTestFixtures.inMemoryStore(props);
    ServerRegistry configured =
        new ServerRegistry(props, RuntimeConfigStoreTestFixtures.serverRegistryPort(runtime));
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();

    // Persisted rule: auto-connect the 'libera' network on bouncer server id 'soju'.
    SojuAutoConnectStore autoConnect =
        new SojuAutoConnectStore(
            new SojuProperties(
                Map.of("soju", Map.of("libera", true)), new SojuProperties.Discovery(true)),
            bouncerDiscoveryPort(runtime));

    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer =
        new SojuEphemeralNetworkImporter(
            new SojuBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            bouncerDiscoveryPort(runtime),
            connectionPort);
    BouncerDiscoveredNetwork net = sojuNetwork("soju", "123", "libera");

    importer.onNetworkDiscovered(net);
    importer.onNetworkDiscovered(net);

    verify(connectionPort, times(1)).connect("soju:soju:123");
  }

  @Test
  void importsKnownChannelsIntoEphemeralAutoJoinWhenAutoReattachEnabled() throws Exception {
    IrcProperties.Server.Sasl sasl =
        new IrcProperties.Server.Sasl(true, "user", "pw", "PLAIN", null);
    IrcProperties.Server bouncer =
        IrcPropertiesTestFixtures.serverBuilder("soju")
            .host("bouncer.example")
            .nick("nick")
            .login("user")
            .realName("Real")
            .sasl(sasl)
            .build();

    IrcProperties props = IrcPropertiesTestFixtures.properties(bouncer);
    Path cfg = Files.createTempFile("ircafe-soju-autojoin-", ".yml");
    RuntimeConfigStore runtime = RuntimeConfigStoreTestFixtures.store(cfg, props);
    runtime.rememberJoinedChannel("soju:soju:123", "#ircafe");
    runtime.rememberJoinedChannel("soju:soju:123", "#off");
    runtime.rememberServerTreeChannelAutoReattach("soju:soju:123", "#off", false);

    ServerRegistry configured =
        new ServerRegistry(props, RuntimeConfigStoreTestFixtures.serverRegistryPort(runtime));
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();
    SojuAutoConnectStore autoConnect =
        new SojuAutoConnectStore(
            new SojuProperties(Map.of(), new SojuProperties.Discovery(true)),
            bouncerDiscoveryPort(runtime));
    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer =
        new SojuEphemeralNetworkImporter(
            new SojuBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            bouncerDiscoveryPort(runtime),
            connectionPort);
    importer.onNetworkDiscovered(sojuNetwork("soju", "123", "libera"));

    IrcProperties.Server imported = ephemeral.require("soju:soju:123");
    assertTrue(imported.autoJoin().contains("#ircafe"));
    assertFalse(imported.autoJoin().contains("#off"));
  }

  @Test
  void originDisconnectRemovesEphemerals() {
    IrcProperties.Server.Sasl sasl =
        new IrcProperties.Server.Sasl(true, "user", "pw", "PLAIN", null);
    IrcProperties.Server bouncer =
        IrcPropertiesTestFixtures.serverBuilder("soju")
            .host("bouncer.example")
            .nick("nick")
            .login("user")
            .realName("Real")
            .sasl(sasl)
            .build();

    IrcProperties props = IrcPropertiesTestFixtures.properties(bouncer);
    RuntimeConfigStore runtime = RuntimeConfigStoreTestFixtures.inMemoryStore(props);
    ServerRegistry configured =
        new ServerRegistry(props, RuntimeConfigStoreTestFixtures.serverRegistryPort(runtime));
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();

    SojuAutoConnectStore autoConnect =
        new SojuAutoConnectStore(
            new SojuProperties(Map.of(), new SojuProperties.Discovery(true)),
            bouncerDiscoveryPort(runtime));
    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer =
        new SojuEphemeralNetworkImporter(
            new SojuBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            bouncerDiscoveryPort(runtime),
            connectionPort);

    importer.onNetworkDiscovered(sojuNetwork("soju", "1", "libera"));
    importer.onNetworkDiscovered(sojuNetwork("soju", "2", "oftc"));

    assertEquals(2, ephemeral.serverIds().size());

    importer.onOriginDisconnected("soju");

    assertEquals(0, ephemeral.serverIds().size());
  }

  private static BouncerDiscoveredNetwork sojuNetwork(
      String originServerId, String networkId, String name) {
    return new BouncerDiscoveredNetwork(
        BuiltInBouncerBackendIds.SOJU, originServerId, networkId, name, name, Map.of("name", name));
  }
}
