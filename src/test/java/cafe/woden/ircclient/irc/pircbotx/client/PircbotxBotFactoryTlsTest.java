package cafe.woden.ircclient.irc.pircbotx.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.config.properties.SojuProperties;
import cafe.woden.ircclient.irc.ircv3.Ircv3ExtensionCatalog;
import cafe.woden.ircclient.irc.ircv3.Ircv3RuntimeTestFixtures;
import cafe.woden.ircclient.net.NetTlsContext;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.SelfSignedTlsServer;
import cafe.woden.ircclient.net.ServerProxyResolver;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.pircbotx.hooks.ListenerAdapter;

class PircbotxBotFactoryTlsTest {
  @BeforeEach
  @AfterEach
  void resetGlobalTlsPolicy() {
    NetTlsContext.configure(false);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void selfSignedCertificateRequiresPerServerOptOutForDirectAndSocksConnections(boolean socks)
      throws Exception {
    try (SelfSignedTlsServer endpoint = new SelfSignedTlsServer(socks)) {
      ProxyPlan plan =
          socks
              ? ProxyPlan.from(
                  new IrcProperties.Proxy(
                      true, endpoint.host(), endpoint.port(), "", "", true, 3_000, 5_000))
              : ProxyPlan.direct();
      ServerProxyResolver resolver = mock(ServerProxyResolver.class);
      when(resolver.planForServer("znc")).thenReturn(plan);
      PircbotxBotFactory factory =
          new PircbotxBotFactory(
              resolver,
              new SojuProperties(Map.of(), null),
              null,
              Ircv3ExtensionCatalog.builtInCatalog(),
              Ircv3RuntimeTestFixtures.catalogs());

      assertThrows(SSLHandshakeException.class, () -> exchange(factory, endpoint, false));
      assertEquals(42, exchange(factory, endpoint, true));
      assertFalse(NetTlsContext.trustAllCertificates());
      // Enabling one server must not weaken the next connection's certificate checks.
      assertThrows(SSLHandshakeException.class, () -> exchange(factory, endpoint, false));
    }
  }

  private static int exchange(
      PircbotxBotFactory factory, SelfSignedTlsServer endpoint, boolean trustAllCertificates)
      throws Exception {
    IrcProperties.Server server =
        IrcPropertiesTestFixtures.serverBuilder("znc")
            .host(endpoint.host())
            .port(endpoint.port())
            .trustAllCertificates(trustAllCertificates)
            .build();
    var bot = factory.build(server, "IRCafe test", new ListenerAdapter() {});
    var config = bot.getConfiguration();
    try (Socket socket = config.getSocketFactory().createSocket()) {
      socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), 3_000);
      socket.setSoTimeout(5_000);
      socket.getOutputStream().write(42);
      socket.getOutputStream().flush();
      return socket.getInputStream().read();
    } finally {
      config.getListenerManager().shutdown(bot);
    }
  }
}
