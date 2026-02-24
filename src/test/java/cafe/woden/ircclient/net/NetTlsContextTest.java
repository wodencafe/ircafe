package cafe.woden.ircclient.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NetTlsContextTest {

  @BeforeEach
  void setUp() {
    NetTlsContext.configure(false);
  }

  @AfterEach
  void tearDown() {
    NetTlsContext.configure(false);
  }

  @Test
  void trustAllModeSwitchesVerifierAndCachesSslFactory() {
    HostnameVerifier strictVerifier = NetTlsContext.hostnameVerifier();
    assertFalse(NetTlsContext.trustAllCertificates());

    NetTlsContext.configure(true);

    assertTrue(NetTlsContext.trustAllCertificates());
    HostnameVerifier trustAllVerifier = NetTlsContext.hostnameVerifier();
    assertNotSame(strictVerifier, trustAllVerifier);
    assertTrue(trustAllVerifier.verify("anything.invalid", null));

    SSLSocketFactory first = NetTlsContext.sslSocketFactory();
    SSLSocketFactory second = NetTlsContext.sslSocketFactory();
    assertNotNull(first);
    assertSame(first, second);
  }

  @Test
  void normalizeAndConfigureHandleNullAsSafeDefault() {
    assertFalse(NetTlsContext.normalize(null).trustAllCertificates());

    NetTlsContext.configure((IrcProperties.Client.Tls) null);

    assertFalse(NetTlsContext.trustAllCertificates());
  }
}
