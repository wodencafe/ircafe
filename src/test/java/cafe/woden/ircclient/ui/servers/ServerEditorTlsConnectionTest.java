package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.NetTlsContext;
import cafe.woden.ircclient.net.SelfSignedTlsServer;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServerEditorTlsConnectionTest {
  @BeforeEach
  @AfterEach
  void resetGlobalTlsPolicy() {
    NetTlsContext.configure(false);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void connectionTestHonorsCertificateOptOutForDirectAndSocksConnections(boolean socks)
      throws Exception {
    try (SelfSignedTlsServer endpoint = new SelfSignedTlsServer(socks)) {
      IrcProperties.Proxy cfg =
          new IrcProperties.Proxy(
              socks, endpoint.host(), endpoint.port(), "", "", true, 3_000, 5_000);
      assertThrows(
          SSLHandshakeException.class,
          () -> ServerEditorDialog.testConnect(endpoint.host(), endpoint.port(), true, false, cfg));
      ServerEditorDialog.testConnect(endpoint.host(), endpoint.port(), true, true, cfg);
      assertFalse(NetTlsContext.trustAllCertificates());
    }
  }
}
