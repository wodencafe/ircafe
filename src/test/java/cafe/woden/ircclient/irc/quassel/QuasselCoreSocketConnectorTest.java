package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.config.IrcPropertiesTestFixtures;
import cafe.woden.ircclient.net.NetTlsContext;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.SelfSignedTlsServer;
import cafe.woden.ircclient.net.ServerProxyResolver;
import java.net.Socket;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuasselCoreSocketConnectorTest {
  @BeforeEach
  @AfterEach
  void resetGlobalTlsPolicy() {
    NetTlsContext.configure(false);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void selfSignedCertificateRequiresOptOutForDirectAndSocksConnections(boolean socks)
      throws Exception {
    try (SelfSignedTlsServer endpoint = new SelfSignedTlsServer(socks)) {
      ServerProxyResolver resolver = mock(ServerProxyResolver.class);
      when(resolver.planForServer("core"))
          .thenReturn(
              socks
                  ? ProxyPlan.from(
                      new IrcProperties.Proxy(
                          true, endpoint.host(), endpoint.port(), "", "", true, 3_000, 5_000))
                  : ProxyPlan.direct());
      QuasselCoreSocketConnector connector = new QuasselCoreSocketConnector(resolver);
      IrcProperties.Server strict =
          IrcPropertiesTestFixtures.serverBuilder("core")
              .host(endpoint.host())
              .port(endpoint.port())
              .backend(IrcProperties.Server.Backend.QUASSEL_CORE)
              .build();
      assertThrows(SSLHandshakeException.class, () -> connector.connect(strict));

      IrcProperties.Server insecure =
          IrcPropertiesTestFixtures.serverBuilder("core")
              .host(endpoint.host())
              .port(endpoint.port())
              .backend(IrcProperties.Server.Backend.QUASSEL_CORE)
              .trustAllCertificates(true)
              .build();
      try (Socket socket = connector.connect(insecure)) {
        socket.getOutputStream().write(42);
        socket.getOutputStream().flush();
        assertEquals(42, socket.getInputStream().read());
      }
    }
  }
}
