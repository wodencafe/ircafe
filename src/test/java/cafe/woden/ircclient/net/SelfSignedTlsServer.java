package cafe.woden.ircclient.net;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

/** Local TLS echo fixture, optionally preceded by a SOCKS5 negotiation. Test keys only. */
public final class SelfSignedTlsServer implements AutoCloseable {
  private final SSLContext context;
  private final ServerSocket listener;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Future<?> worker;

  public SelfSignedTlsServer(boolean socks) throws Exception {
    context = serverContext();
    listener = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
    worker =
        executor.submit(
            () -> {
              serve(socks);
              return null;
            });
  }

  public String host() {
    return listener.getInetAddress().getHostAddress();
  }

  public int port() {
    return listener.getLocalPort();
  }

  private void serve(boolean socks) throws IOException {
    while (!listener.isClosed()) {
      try (Socket tcp = listener.accept()) {
        tcp.setSoTimeout(5_000);
        if (socks) negotiateSocks(tcp);
        try (SSLSocket tls =
            (SSLSocket) context.getSocketFactory().createSocket(tcp, host(), port(), true)) {
          tls.setUseClientMode(false);
          tls.setSoTimeout(5_000);
          tls.startHandshake();
          int value = tls.getInputStream().read();
          if (value >= 0) {
            tls.getOutputStream().write(value);
            tls.getOutputStream().flush();
          }
        }
      } catch (SSLException | SocketException ignored) {
        // Certificate rejection can surface as a TLS alert or a closed TCP connection.
      } catch (IOException ex) {
        if (!listener.isClosed()) throw ex;
      }
    }
  }

  private static void negotiateSocks(Socket socket) throws IOException {
    DataInputStream in = new DataInputStream(socket.getInputStream());
    var out = socket.getOutputStream();
    if (in.readUnsignedByte() != 5) throw new IOException("Expected SOCKS5 greeting");
    in.skipNBytes(in.readUnsignedByte());
    out.write(new byte[] {5, 0});
    out.flush();
    if (in.readUnsignedByte() != 5 || in.readUnsignedByte() != 1) {
      throw new IOException("Expected SOCKS5 CONNECT");
    }
    in.readUnsignedByte();
    int addressLength =
        switch (in.readUnsignedByte()) {
          case 1 -> 4;
          case 3 -> in.readUnsignedByte();
          case 4 -> 16;
          default -> throw new IOException("Unsupported SOCKS5 address type");
        };
    in.skipNBytes(addressLength);
    in.readUnsignedShort();
    out.write(new byte[] {5, 0, 0, 1, 127, 0, 0, 1, 0, 0});
    out.flush();
  }

  private static SSLContext serverContext() throws Exception {
    Certificate certificate;
    try (var input =
        Objects.requireNonNull(
            SelfSignedTlsServer.class.getResourceAsStream("/tls/self-signed-server-cert.pem"))) {
      certificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
    }
    String pem;
    try (var input =
        Objects.requireNonNull(
            SelfSignedTlsServer.class.getResourceAsStream("/tls/self-signed-server-key.pem"))) {
      pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
    }
    byte[] keyBytes =
        Base64.getMimeDecoder()
            .decode(
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", ""));
    var key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    char[] password = "test-only".toCharArray();
    KeyStore keys = KeyStore.getInstance("PKCS12");
    keys.load(null, null);
    keys.setKeyEntry("server", key, password, new Certificate[] {certificate});
    KeyManagerFactory managers =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    managers.init(keys, password);
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(managers.getKeyManagers(), null, null);
    return context;
  }

  @Override
  public void close() throws Exception {
    try {
      listener.close();
      worker.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}
