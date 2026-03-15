package cafe.woden.ircclient.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cafe.woden.ircclient.config.IrcProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.Test;

class DeferredConnectSocksSocketFactoryTest {

  @Test
  void deferredTlsSocketUsesSocksProxyAndPreservesRemoteDns() throws Exception {
    try (FakeSocks5Proxy proxy = new FakeSocks5Proxy()) {
      RecordingSslSocketFactory sslFactory = new RecordingSslSocketFactory();
      IrcProperties.Proxy cfg =
          new IrcProperties.Proxy(true, proxy.host(), proxy.port(), "", "", true, 2_000, 3_000);
      DeferredConnectSocksSocketFactory factory =
          new DeferredConnectSocksSocketFactory(cfg, "irc.example.test", 6697, sslFactory);

      try (Socket socket = factory.createSocket()) {
        socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), 6697), 2_000);
      }

      SocksConnectRequest request = proxy.awaitConnectRequest();
      assertEquals(FakeSocks5Proxy.ATYP_DOMAIN, request.addressType());
      assertEquals("irc.example.test", request.host());
      assertEquals(6697, request.port());
      assertNotNull(sslFactory.wrappedSocket);
      assertEquals("irc.example.test", sslFactory.host);
      assertEquals(6697, sslFactory.port);
    }
  }

  private record SocksConnectRequest(int addressType, String host, int port) {}

  private static final class RecordingSslSocketFactory extends SSLSocketFactory {
    private Socket wrappedSocket;
    private String host;
    private int port;

    @Override
    public String[] getDefaultCipherSuites() {
      return new String[0];
    }

    @Override
    public String[] getSupportedCipherSuites() {
      return new String[0];
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) {
      this.wrappedSocket = socket;
      this.host = host;
      this.port = port;
      return socket;
    }

    @Override
    public Socket createSocket() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Socket createSocket(String host, int port) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Socket createSocket(InetAddress host, int port) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Socket createSocket(
        InetAddress address, int port, InetAddress localAddress, int localPort) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class FakeSocks5Proxy implements AutoCloseable {
    private static final int ATYP_IPV4 = 0x01;
    private static final int ATYP_DOMAIN = 0x03;
    private static final int ATYP_IPV6 = 0x04;

    private final ServerSocket serverSocket;
    private final ExecutorService executor;
    private final Future<SocksConnectRequest> requestFuture;

    private FakeSocks5Proxy() throws IOException {
      serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
      executor = Executors.newSingleThreadExecutor();
      requestFuture = executor.submit(this::acceptOnce);
    }

    private String host() {
      return serverSocket.getInetAddress().getHostAddress();
    }

    private int port() {
      return serverSocket.getLocalPort();
    }

    private SocksConnectRequest awaitConnectRequest()
        throws InterruptedException, ExecutionException, TimeoutException {
      return requestFuture.get(5, TimeUnit.SECONDS);
    }

    private SocksConnectRequest acceptOnce() throws IOException {
      try (Socket socket = serverSocket.accept()) {
        socket.setSoTimeout(5_000);
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();

        int version = in.read();
        int methodCount = in.read();
        if (version != 0x05 || methodCount < 0) {
          throw new IOException("invalid SOCKS greeting");
        }
        in.readNBytes(methodCount);
        out.write(new byte[] {0x05, 0x00});
        out.flush();

        int requestVersion = in.read();
        int command = in.read();
        in.read();
        int addressType = in.read();
        if (requestVersion != 0x05 || command != 0x01) {
          throw new IOException("invalid SOCKS connect request");
        }

        String host =
            switch (addressType) {
              case ATYP_DOMAIN -> {
                int length = in.read();
                if (length < 0) {
                  throw new IOException("unexpected end of stream");
                }
                yield new String(in.readNBytes(length), StandardCharsets.US_ASCII);
              }
              case ATYP_IPV4 -> InetAddress.getByAddress(in.readNBytes(4)).getHostAddress();
              case ATYP_IPV6 -> InetAddress.getByAddress(in.readNBytes(16)).getHostAddress();
              default -> throw new IOException("unsupported address type: " + addressType);
            };

        int port = (in.read() << 8) | in.read();

        out.write(new byte[] {0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0, 0});
        out.flush();
        return new SocksConnectRequest(addressType, host, port);
      }
    }

    @Override
    public void close() throws Exception {
      try {
        serverSocket.close();
      } finally {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
      }
    }
  }
}
