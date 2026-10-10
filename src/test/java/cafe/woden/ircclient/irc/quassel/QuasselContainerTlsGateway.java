package cafe.woden.ircclient.irc.quassel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/** Test-owned TLS endpoint forwarding unmodified Quassel frames to a real containerized Core. */
final class QuasselContainerTlsGateway implements AutoCloseable {
  private final SSLServerSocket listener;
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
  private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
  private final String coreHost;
  private final int corePort;

  QuasselContainerTlsGateway(Path directory, String coreHost, int corePort) throws Exception {
    this.coreHost = coreHost;
    this.corePort = corePort;
    Path keyStorePath = directory.resolve("quassel-test.p12");
    String password = "quassel-test-password";
    Process keytool =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair",
                "-alias",
                "quassel-test",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "2",
                "-dname",
                "CN=localhost",
                "-ext",
                "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype",
                "PKCS12",
                "-keystore",
                keyStorePath.toString(),
                "-storepass",
                password,
                "-keypass",
                password,
                "-noprompt")
            .redirectErrorStream(true)
            .start();
    if (!keytool.waitFor(30, TimeUnit.SECONDS)) {
      keytool.destroyForcibly();
      throw new IllegalStateException("Timed out generating the test TLS certificate");
    }
    assertEquals(
        0,
        keytool.exitValue(),
        new String(
            keytool.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (var input = Files.newInputStream(keyStorePath)) {
      keyStore.load(input, password.toCharArray());
    }
    KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keys.init(keyStore, password.toCharArray());
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keys.getKeyManagers(), null, null);
    listener =
        (SSLServerSocket)
            context
                .getServerSocketFactory()
                .createServerSocket(0, 16, InetAddress.getLoopbackAddress());
    workers.submit(this::acceptConnections);
  }

  String host() {
    return listener.getInetAddress().getHostAddress();
  }

  int port() {
    return listener.getLocalPort();
  }

  private void acceptConnections() {
    while (!listener.isClosed()) {
      try {
        SSLSocket client = (SSLSocket) listener.accept();
        connections.add(client);
        workers.submit(() -> forward(client));
      } catch (IOException failure) {
        if (!listener.isClosed())
          throw new IllegalStateException("TLS fixture listener failed", failure);
      }
    }
  }

  private void forward(SSLSocket client) {
    try (client) {
      client.setSoTimeout(10_000);
      client.startHandshake();
      client.setSoTimeout(0);
      try (Socket core = new Socket(coreHost, corePort)) {
        connections.add(core);
        try {
          workers.submit(() -> copy(client, core));
          copy(core, client);
        } finally {
          connections.remove(core);
        }
      }
    } catch (IOException expectedDisconnect) {
      // Includes the deliberate untrusted-certificate rejection and client reconnects.
    } finally {
      connections.remove(client);
    }
  }

  private static void copy(Socket from, Socket to) {
    try {
      from.getInputStream().transferTo(to.getOutputStream());
    } catch (IOException expectedDisconnect) {
      // Closing either endpoint interrupts the other direction too.
    } finally {
      closeSocket(from);
      closeSocket(to);
    }
  }

  private static void closeSocket(Socket socket) {
    try {
      socket.close();
    } catch (IOException ignored) {
    }
  }

  @Override
  public void close() throws Exception {
    listener.close();
    connections.forEach(QuasselContainerTlsGateway::closeSocket);
    workers.shutdownNow();
    assertTrue(
        workers.awaitTermination(10, TimeUnit.SECONDS), "TLS fixture workers must terminate");
    assertTrue(connections.isEmpty(), "TLS fixture must release every connection");
  }
}
