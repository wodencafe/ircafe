package cafe.woden.ircclient.irc.soju;

import cafe.woden.ircclient.config.EphemeralServerRegistry;
import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.config.ServerRegistry;
import cafe.woden.ircclient.config.SojuProperties;
import cafe.woden.ircclient.irc.IrcClientService;
import io.reactivex.rxjava3.core.Completable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

class SojuEphemeralNetworkImporterTest {

  @Test
  void upsertsEphemeralServerForDiscoveredNetwork() {
    IrcProperties.Server.Sasl sasl = new IrcProperties.Server.Sasl(true, "zimmerdon", "pw", "PLAIN", null);
    IrcProperties.Server bouncer = new IrcProperties.Server(
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
        null);

    IrcProperties props = new IrcProperties(null, List.of(bouncer));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", props);
    ServerRegistry configured = new ServerRegistry(props, runtime);
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();

    SojuAutoConnectStore autoConnect = new SojuAutoConnectStore(
        new SojuProperties(Map.of(), new SojuProperties.Discovery(true)),
        runtime
    );
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer = new SojuEphemeralNetworkImporter(configured, ephemeral, autoConnect, irc);

    SojuNetwork net = new SojuNetwork("soju", "123", "libera", Map.of("name", "libera"));
    importer.onNetworkDiscovered(net);

    assertTrue(ephemeral.containsId("soju:soju:123"));
    IrcProperties.Server imported = ephemeral.require("soju:soju:123");
    assertEquals("zimmerdon/libera@ircafe", imported.login());
    assertEquals("zimmerdon/libera@ircafe", imported.sasl().username());
    assertEquals("soju", ephemeral.originOf(imported.id()).orElseThrow());
  }

  @Test
  void callingTwiceDoesNotCreateDuplicate() {
    IrcProperties.Server.Sasl sasl = new IrcProperties.Server.Sasl(true, "user", "pw", "PLAIN", null);
    IrcProperties.Server bouncer = new IrcProperties.Server(
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
        null);

    IrcProperties props = new IrcProperties(null, List.of(bouncer));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", props);
    ServerRegistry configured = new ServerRegistry(props, runtime);
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();

    SojuAutoConnectStore autoConnect = new SojuAutoConnectStore(
        new SojuProperties(Map.of(), new SojuProperties.Discovery(true)),
        runtime
    );
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer = new SojuEphemeralNetworkImporter(configured, ephemeral, autoConnect, irc);
    SojuNetwork net = new SojuNetwork("soju", "9", "oftc", Map.of("name", "oftc"));

    importer.onNetworkDiscovered(net);
    importer.onNetworkDiscovered(net);

    assertEquals(1, ephemeral.serverIds().size());
    assertTrue(ephemeral.containsId("soju:soju:9"));
  }

  @Test
  void enabledRuleTriggersAutoConnectOnce() {
    IrcProperties.Server.Sasl sasl = new IrcProperties.Server.Sasl(true, "user", "pw", "PLAIN", null);
    IrcProperties.Server bouncer = new IrcProperties.Server(
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
        null);

    IrcProperties props = new IrcProperties(null, List.of(bouncer));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", props);
    ServerRegistry configured = new ServerRegistry(props, runtime);
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();

    // Persisted rule: auto-connect the 'libera' network on bouncer server id 'soju'.
    SojuAutoConnectStore autoConnect = new SojuAutoConnectStore(
        new SojuProperties(
            Map.of("soju", Map.of("libera", true)),
            new SojuProperties.Discovery(true)
        ),
        runtime
    );

    IrcClientService irc = mock(IrcClientService.class);
    when(irc.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer = new SojuEphemeralNetworkImporter(configured, ephemeral, autoConnect, irc);
    SojuNetwork net = new SojuNetwork("soju", "123", "libera", Map.of("name", "libera"));

    importer.onNetworkDiscovered(net);
    importer.onNetworkDiscovered(net);

    verify(irc, times(1)).connect("soju:soju:123");
  }


  @Test
  void originDisconnectRemovesEphemerals() {
    IrcProperties.Server.Sasl sasl = new IrcProperties.Server.Sasl(true, "user", "pw", "PLAIN", null);
    IrcProperties.Server bouncer = new IrcProperties.Server(
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
        null);

    IrcProperties props = new IrcProperties(null, List.of(bouncer));
    RuntimeConfigStore runtime = new RuntimeConfigStore(" ", props);
    ServerRegistry configured = new ServerRegistry(props, runtime);
    EphemeralServerRegistry ephemeral = new EphemeralServerRegistry();

    SojuAutoConnectStore autoConnect = new SojuAutoConnectStore(
        new SojuProperties(Map.of(), new SojuProperties.Discovery(true)),
        runtime
    );
    IrcClientService irc = mock(IrcClientService.class);
    when(irc.connect(anyString())).thenReturn(Completable.complete());

    SojuEphemeralNetworkImporter importer = new SojuEphemeralNetworkImporter(configured, ephemeral, autoConnect, irc);

    importer.onNetworkDiscovered(new SojuNetwork("soju", "1", "libera", Map.of("name", "libera")));
    importer.onNetworkDiscovered(new SojuNetwork("soju", "2", "oftc", Map.of("name", "oftc")));

    assertEquals(2, ephemeral.serverIds().size());

    importer.onOriginDisconnected("soju");

    assertEquals(0, ephemeral.serverIds().size());
  }
}
