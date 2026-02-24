package cafe.woden.ircclient.net;

import cafe.woden.ircclient.config.IrcProperties;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Holds the current TLS settings in a globally accessible place.
 *
 * <p>This is used to provide an "insecure / trust all certificates" mode for both IRC-over-TLS and
 * HTTPS fetching (link previews, embedded images, etc).
 *
 * <p><b>WARNING:</b> Enabling trust-all disables certificate validation and (for HTTPS) hostname
 * checks. This makes man-in-the-middle attacks trivial. Only enable if you fully understand the
 * risks.
 */
public final class NetTlsContext {

  private static final IrcProperties.Client.Tls DEFAULT = new IrcProperties.Client.Tls(false);

  private static volatile IrcProperties.Client.Tls settings = DEFAULT;

  private static final SSLSocketFactory DEFAULT_SSL =
      (SSLSocketFactory) SSLSocketFactory.getDefault();

  private static final HostnameVerifier DEFAULT_HOSTNAME_VERIFIER =
      HttpsURLConnection.getDefaultHostnameVerifier();

  private static final HostnameVerifier TRUST_ALL_HOSTNAME_VERIFIER =
      new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
          return true;
        }
      };

  private static final AtomicReference<SSLSocketFactory> TRUST_ALL_SSL = new AtomicReference<>();

  private NetTlsContext() {}

  public static void configure(IrcProperties.Client.Tls cfg) {
    cfg = normalize(cfg);
    settings = cfg;
  }

  public static void configure(boolean trustAllCertificates) {
    configure(new IrcProperties.Client.Tls(trustAllCertificates));
  }

  public static IrcProperties.Client.Tls normalize(IrcProperties.Client.Tls cfg) {
    return (cfg == null) ? DEFAULT : cfg;
  }

  /** Current TLS settings (never null). */
  public static IrcProperties.Client.Tls settings() {
    return settings;
  }

  public static boolean trustAllCertificates() {
    IrcProperties.Client.Tls s = settings;
    return s != null && s.trustAllCertificates();
  }

  /**
   * Returns the {@link SSLSocketFactory} to use for outbound TLS sockets. When trust-all is
   * enabled, this factory does not validate certificates.
   */
  public static SSLSocketFactory sslSocketFactory() {
    if (!trustAllCertificates()) return DEFAULT_SSL;

    SSLSocketFactory existing = TRUST_ALL_SSL.get();
    if (existing != null) return existing;

    SSLSocketFactory created = buildTrustAllSslFactory();
    // If creation failed for some reason, fall back to default rather than crashing.
    if (created == null) created = DEFAULT_SSL;

    TRUST_ALL_SSL.compareAndSet(null, created);
    return TRUST_ALL_SSL.get();
  }

  /**
   * Returns the {@link HostnameVerifier} to use for HTTPS connections. When trust-all is enabled,
   * this verifier accepts any hostname.
   */
  public static HostnameVerifier hostnameVerifier() {
    return trustAllCertificates() ? TRUST_ALL_HOSTNAME_VERIFIER : DEFAULT_HOSTNAME_VERIFIER;
  }

  private static SSLSocketFactory buildTrustAllSslFactory() {
    try {
      TrustManager[] trustAll =
          new TrustManager[] {
            new X509TrustManager() {
              @Override
              public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // trust all
              }

              @Override
              public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // trust all
              }

              @Override
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }
            }
          };

      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, trustAll, new SecureRandom());
      return Objects.requireNonNull(ctx.getSocketFactory());
    } catch (Exception e) {
      // Degrade gracefully: caller will fall back to DEFAULT_SSL.
      return null;
    }
  }
}
