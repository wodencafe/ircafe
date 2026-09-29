package cafe.woden.ircclient.irc.znc;

import static cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures.bouncerDiscoveryPort;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.bouncer.BouncerConnectionPort;
import cafe.woden.ircclient.bouncer.spi.BouncerDiscoveredNetwork;
import cafe.woden.ircclient.bouncer.spi.BuiltInBouncerBackendIds;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.RuntimeConfigStoreTestFixtures;
import cafe.woden.ircclient.config.properties.ZncProperties;
import cafe.woden.ircclient.config.runtime.RuntimeConfigStore;
import cafe.woden.ircclient.config.servers.EphemeralServerRegistry;
import cafe.woden.ircclient.config.servers.ServerRegistry;
import io.reactivex.rxjava3.core.Completable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZncEphemeralNetworkImporterTest {

  @Test
  void upsertsEphemeralServerForDiscoveredNetwork() {
    IrcProperties.Server.Sasl sasl = new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null);
    IrcProperties.Server bouncer =
        IrcPropertiesTestFixtures.serverBuilder("znc")
            .host("bouncer.example")
            .serverPassword("pass")
            .nick("nick")
            .login("user@ircafe")
            .realName("Real")
            .sasl(sasl)
            .build();

    IrcProperties props = IrcPropertiesTestFixtures.properties(bouncer);
    RuntimeConfigStore runtime = RuntimeConfigStoreTestFixtures.inMemoryStore(props);
    ServerRegistry configured =
        new ServerRegistry(props, RuntimeConfigStoreTestFixtures.serverRegistryPort(runtime));
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();
    ZncAutoConnectStore autoConnect =
        new ZncAutoConnectStore(
            new ZncProperties(Map.of(), new ZncProperties.Discovery(true)),
            bouncerDiscoveryPort(runtime));
    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    ZncEphemeralNetworkImporter importer =
        new ZncEphemeralNetworkImporter(
            new ZncBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            bouncerDiscoveryPort(runtime),
            connectionPort);

    importer.onNetworkDiscovered(zncNetwork("znc", "Libera.Chat", true));

    assertTrue(ephemeral.containsId("znc:znc:libera.chat"));
    IrcProperties.Server imported = ephemeral.require("znc:znc:libera.chat");
    assertEquals("user@ircafe/Libera.Chat", imported.login());
    assertEquals("user@ircafe/Libera.Chat", imported.sasl().username());
    assertEquals("znc", ephemeral.originOf(imported.id()).orElseThrow());
  }

  @Test
  void callingTwiceDoesNotCreateDuplicate() {
    IrcProperties.Server.Sasl sasl = new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null);
    IrcProperties.Server bouncer =
        IrcPropertiesTestFixtures.serverBuilder("znc")
            .host("bouncer.example")
            .serverPassword("pass")
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
    ZncAutoConnectStore autoConnect =
        new ZncAutoConnectStore(
            new ZncProperties(Map.of(), new ZncProperties.Discovery(true)),
            bouncerDiscoveryPort(runtime));
    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    ZncEphemeralNetworkImporter importer =
        new ZncEphemeralNetworkImporter(
            new ZncBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            bouncerDiscoveryPort(runtime),
            connectionPort);
    BouncerDiscoveredNetwork net = zncNetwork("znc", "oftc", false);

    importer.onNetworkDiscovered(net);
    importer.onNetworkDiscovered(net);

    assertEquals(1, ephemeral.serverIds().size());
    assertTrue(ephemeral.containsId("znc:znc:oftc"));
  }

  @Test
  void originDisconnectRemovesEphemerals() {
    IrcProperties.Server.Sasl sasl = new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null);
    IrcProperties.Server bouncer =
        IrcPropertiesTestFixtures.serverBuilder("znc")
            .host("bouncer.example")
            .serverPassword("pass")
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
    ZncAutoConnectStore autoConnect =
        new ZncAutoConnectStore(
            new ZncProperties(Map.of(), new ZncProperties.Discovery(true)),
            bouncerDiscoveryPort(runtime));
    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    ZncEphemeralNetworkImporter importer =
        new ZncEphemeralNetworkImporter(
            new ZncBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            bouncerDiscoveryPort(runtime),
            connectionPort);

    importer.onNetworkDiscovered(zncNetwork("znc", "libera", true));
    importer.onNetworkDiscovered(zncNetwork("znc", "oftc", true));

    assertEquals(2, ephemeral.serverIds().size());

    importer.onOriginDisconnected("znc");

    assertEquals(0, ephemeral.serverIds().size());
  }

  @Test
  void importsKnownChannelsIntoEphemeralAutoJoinWhenAutoReattachEnabled() throws Exception {
    IrcProperties.Server.Sasl sasl = new IrcProperties.Server.Sasl(false, "", "", "PLAIN", null);
    IrcProperties.Server bouncer =
        IrcPropertiesTestFixtures.serverBuilder("znc")
            .host("bouncer.example")
            .serverPassword("pass")
            .nick("nick")
            .login("user")
            .realName("Real")
            .sasl(sasl)
            .build();

    IrcProperties props = IrcPropertiesTestFixtures.properties(bouncer);
    Path cfg = Files.createTempFile("ircafe-znc-autojoin-", ".yml");
    RuntimeConfigStore runtime = RuntimeConfigStoreTestFixtures.store(cfg, props);
    runtime.rememberJoinedChannel("znc:znc:libera.chat", "#ircafe");
    runtime.rememberJoinedChannel("znc:znc:libera.chat", "#off");
    runtime.rememberServerTreeChannelAutoReattach("znc:znc:libera.chat", "#off", false);

    ServerRegistry configured =
        new ServerRegistry(props, RuntimeConfigStoreTestFixtures.serverRegistryPort(runtime));
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();
    ZncAutoConnectStore autoConnect =
        new ZncAutoConnectStore(
            new ZncProperties(Map.of(), new ZncProperties.Discovery(true)),
            bouncerDiscoveryPort(runtime));
    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    ZncEphemeralNetworkImporter importer =
        new ZncEphemeralNetworkImporter(
            new ZncBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            bouncerDiscoveryPort(runtime),
            connectionPort);
    importer.onNetworkDiscovered(zncNetwork("znc", "Libera.Chat", true));

    IrcProperties.Server imported = ephemeral.require("znc:znc:libera.chat");
    assertTrue(imported.autoJoin().contains("#ircafe"));
    assertFalse(imported.autoJoin().contains("#off"));
  }

  private static BouncerDiscoveredNetwork zncNetwork(
      String originServerId, String name, Boolean onIrc) {
    Map<String, String> attrs = onIrc == null ? Map.of() : Map.of("onIrc", String.valueOf(onIrc));
    return new BouncerDiscoveredNetwork(
        BuiltInBouncerBackendIds.ZNC, originServerId, name, name, name, attrs);
  }
}
