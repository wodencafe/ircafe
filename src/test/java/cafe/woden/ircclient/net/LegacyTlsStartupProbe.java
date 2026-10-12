package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.StartupTlsSettings;
import java.net.Socket;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Runs in a fresh JVM so cached JSSE policy and test ordering cannot mask startup regressions. */
public final class LegacyTlsStartupProbe {
  private LegacyTlsStartupProbe() {}

  public static void main(String[] args) throws Exception {
    // Make the baseline deterministic even if the build machine customizes Java's policy.
    Security.setProperty("jdk.tls.disabledAlgorithms", "TLS_RSA_*, RC4, SSLv3");
    boolean compatibility =
        LegacyTlsStartupPolicy.configureFromArguments(
            args, StartupTlsSettings.readAllowLegacyRsa(args));
    SSLSocketFactory strict = (SSLSocketFactory) SSLSocketFactory.getDefault();
    try (SSLSocket socket = (SSLSocket) strict.createSocket()) {
      boolean rsaEnabled =
          Arrays.stream(socket.getEnabledCipherSuites()).anyMatch(s -> s.startsWith("TLS_RSA_"));
      if (rsaEnabled != compatibility) throw new AssertionError("Unexpected RSA cipher policy");
    }
    if (!"RC4, SSLv3"
        .equals(
            LegacyTlsStartupPolicy.withoutLegacyRsaRestriction(
                Security.getProperty("jdk.tls.disabledAlgorithms")))) {
      throw new AssertionError("Other algorithm restrictions changed");
    }
    if (!compatibility) return;

    try (SelfSignedTlsServer server =
        new SelfSignedTlsServer(false, "TLS_RSA_WITH_AES_256_GCM_SHA384")) {
      try {
        exchange(strict, server);
        throw new AssertionError("Certificate validation was bypassed");
      } catch (SSLHandshakeException expected) {
        // Allowing RSA must not implicitly accept the fixture's self-signed certificate.
      }
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(
          null,
          new TrustManager[] {
            new X509TrustManager() {
              @Override
              public void checkClientTrusted(X509Certificate[] chain, String authType) {}

              @Override
              public void checkServerTrusted(X509Certificate[] chain, String authType) {}

              @Override
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }
            }
          },
          null);
      exchange(context.getSocketFactory(), server);
    }
  }

  private static void exchange(SSLSocketFactory factory, SelfSignedTlsServer server)
      throws Exception {
    try (Socket tcp = new Socket()) {
      tcp.connect(new java.net.InetSocketAddress(server.host(), server.port()), 5_000);
      try (SSLSocket tls =
          (SSLSocket) factory.createSocket(tcp, server.host(), server.port(), true)) {
        tls.setSoTimeout(5_000);
        tls.startHandshake();
        if (!"TLS_RSA_WITH_AES_256_GCM_SHA384".equals(tls.getSession().getCipherSuite())) {
          throw new AssertionError("Expected legacy RSA cipher");
        }
        tls.getOutputStream().write(42);
        tls.getOutputStream().flush();
        if (tls.getInputStream().read() != 42) throw new AssertionError("TLS exchange failed");
      }
    }
  }
}
