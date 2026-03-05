package cafe.woden.ircclient.irc.soju;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import cafe.woden.ircclient.bouncer.BouncerConnectionPort;
import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.config.SojuProperties;
import io.reactivex.rxjava3.core.Completable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SojuEphemeralNetworkImporterTest {

  @Test
  void upsertsEphemeralServerForDiscoveredNetwork() {
    IrcProperties.Server.Sasl sasl =
        new IrcProperties.Server.Sasl(true, "zimmerdon", "pw", "PLAIN", null);
    IrcProperties.Server bouncer =
        new IrcProperties.Server(
            "soju",
            "bouncer.example",
            6697,
            true,
            "",
            "zimmedon",
            "zimmerdon",
            "Real",
            sasl,
            List.of(),
            List.of(),
            null);

    IrcProperties props = new IrcProperties(null, List.of(bouncer));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", props);
    ServerRegistry configured = new ServerRegistry(props, runtime);
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();

    SojuAutoConnectStore autoConnect =
        new SojuAutoConnectStore(
            new SojuProperties(Map.of(), new SojuProperties.Discovery(true)), runtime);
    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer =
        new SojuEphemeralNetworkImporter(
            new SojuBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            runtime,
            connectionPort);

    SojuNetwork net = new SojuNetwork("soju", "123", "libera", Map.of("name", "libera"));
    importer.onSojuNetworkDiscovered(net);

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
        new IrcProperties.Server(
            "soju",
            "bouncer.example",
            6697,
            true,
            "",
            "nick",
            "user",
            "Real",
            sasl,
            List.of(),
            List.of(),
            null);

    IrcProperties props = new IrcProperties(null, List.of(bouncer));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", props);
    ServerRegistry configured = new ServerRegistry(props, runtime);
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();

    SojuAutoConnectStore autoConnect =
        new SojuAutoConnectStore(
            new SojuProperties(Map.of(), new SojuProperties.Discovery(true)), runtime);
    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer =
        new SojuEphemeralNetworkImporter(
            new SojuBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            runtime,
            connectionPort);
    SojuNetwork net = new SojuNetwork("soju", "9", "oftc", Map.of("name", "oftc"));

    importer.onSojuNetworkDiscovered(net);
    importer.onSojuNetworkDiscovered(net);

    assertEquals(1, ephemeral.serverIds().size());
    assertTrue(ephemeral.containsId("soju:soju:9"));
  }

  @Test
  void enabledRuleTriggersAutoConnectOnce() {
    IrcProperties.Server.Sasl sasl =
        new IrcProperties.Server.Sasl(true, "user", "pw", "PLAIN", null);
    IrcProperties.Server bouncer =
        new IrcProperties.Server(
            "soju",
            "bouncer.example",
            6697,
            true,
            "",
            "nick",
            "user",
            "Real",
            sasl,
            List.of(),
            List.of(),
            null);

    IrcProperties props = new IrcProperties(null, List.of(bouncer));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", props);
    ServerRegistry configured = new ServerRegistry(props, runtime);
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();

    // Persisted rule: auto-connect the 'libera' network on bouncer server id 'soju'.
    SojuAutoConnectStore autoConnect =
        new SojuAutoConnectStore(
            new SojuProperties(
                Map.of("soju", Map.of("libera", true)), new SojuProperties.Discovery(true)),
            runtime);

    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer =
        new SojuEphemeralNetworkImporter(
            new SojuBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            runtime,
            connectionPort);
    SojuNetwork net = new SojuNetwork("soju", "123", "libera", Map.of("name", "libera"));

    importer.onSojuNetworkDiscovered(net);
    importer.onSojuNetworkDiscovered(net);

    verify(connectionPort, times(1)).connect("soju:soju:123");
  }

  @Test
  void importsKnownChannelsIntoEphemeralAutoJoinWhenAutoReattachEnabled() throws Exception {
    IrcProperties.Server.Sasl sasl =
        new IrcProperties.Server.Sasl(true, "user", "pw", "PLAIN", null);
    IrcProperties.Server bouncer =
        new IrcProperties.Server(
            "soju",
            "bouncer.example",
            6697,
            true,
            "",
            "nick",
            "user",
            "Real",
            sasl,
            List.of(),
            List.of(),
            null);

    IrcProperties props = new IrcProperties(null, List.of(bouncer));
    Path cfg = Files.createTempFile("ircafe-soju-autojoin-", ".yml");
    RuntimeConfigStore runtime = new RuntimeConfigStore(cfg.toString(), props);
    runtime.rememberJoinedChannel("soju:soju:123", "#ircafe");
    runtime.rememberJoinedChannel("soju:soju:123", "#off");
    runtime.rememberServerTreeChannelAutoReattach("soju:soju:123", "#off", false);

    ServerRegistry configured = new ServerRegistry(props, runtime);
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();
    SojuAutoConnectStore autoConnect =
        new SojuAutoConnectStore(
            new SojuProperties(Map.of(), new SojuProperties.Discovery(true)), runtime);
    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer =
        new SojuEphemeralNetworkImporter(
            new SojuBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            runtime,
            connectionPort);
    importer.onSojuNetworkDiscovered(
        new SojuNetwork("soju", "123", "libera", Map.of("name", "libera")));

    IrcProperties.Server imported = ephemeral.require("soju:soju:123");
    assertTrue(imported.autoJoin().contains("#ircafe"));
    assertFalse(imported.autoJoin().contains("#off"));
  }

  @Test
  void originDisconnectRemovesEphemerals() {
    IrcProperties.Server.Sasl sasl =
        new IrcProperties.Server.Sasl(true, "user", "pw", "PLAIN", null);
    IrcProperties.Server bouncer =
        new IrcProperties.Server(
            "soju",
            "bouncer.example",
            6697,
            true,
            "",
            "nick",
            "user",
            "Real",
            sasl,
            List.of(),
            List.of(),
            null);

    IrcProperties props = new IrcProperties(null, List.of(bouncer));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", props);
    ServerRegistry configured = new ServerRegistry(props, runtime);
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();

    SojuAutoConnectStore autoConnect =
        new SojuAutoConnectStore(
            new SojuProperties(Map.of(), new SojuProperties.Discovery(true)), runtime);
    BouncerConnectionPort connectionPort = mock(BouncerConnectionPort.class);
    when(connectionPort.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer =
        new SojuEphemeralNetworkImporter(
            new SojuBouncerNetworkMappingStrategy(),
            configured,
            ephemeral,
            autoConnect,
            runtime,
            connectionPort);

    importer.onSojuNetworkDiscovered(
        new SojuNetwork("soju", "1", "libera", Map.of("name", "libera")));
    importer.onSojuNetworkDiscovered(new SojuNetwork("soju", "2", "oftc", Map.of("name", "oftc")));

    assertEquals(2, ephemeral.serverIds().size());

    importer.onOriginDisconnected("soju");

    assertEquals(0, ephemeral.serverIds().size());
  }
}
