package cafe.woden.ircclient.irc.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cafe.woden.ircclient.config.IrcProperties;
import cafe.woden.ircclient.net.ProxyPlan;
import cafe.woden.ircclient.net.ServerProxyResolver;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MatrixDisplayNameClientTest {

  private final ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
  private final MatrixDisplayNameClient displayNameClient =
      new MatrixDisplayNameClient(proxyResolver);

  @Test
  void setDisplayNameSendsPutRequestWithDisplayNamePayload() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(200, "{}")) {
      MatrixDisplayNameClient.UpdateResult result =
          displayNameClient.setDisplayName(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "@alice:matrix.example.org",
              "Alice");

      assertTrue(result.updated());
      assertEquals("Alice", result.displayName());
      assertEquals("PUT", server.lastMethod().get());
      assertEquals(
          "/_matrix/client/v3/profile/@alice:matrix.example.org/displayname",
          server.lastPath().get());
      assertEquals("Bearer secret-token", server.lastAuthorizationHeader().get());
      assertEquals("{\"displayname\":\"Alice\"}", server.lastBody().get());
    }
  }

  @Test
  void setDisplayNameReportsHttpFailure() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(403, "{\"errcode\":\"M_FORBIDDEN\"}")) {
      MatrixDisplayNameClient.UpdateResult result =
          displayNameClient.setDisplayName(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "secret-token",
              "@alice:matrix.example.org",
              "Alice");

      assertFalse(result.updated());
      assertEquals("HTTP 403 from displayname endpoint", result.detail());
    }
  }

  @Test
  void setDisplayNameRejectsBlankToken() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixDisplayNameClient.UpdateResult result =
        displayNameClient.setDisplayName(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            " ",
            "@alice:matrix.example.org",
            "Alice");

    assertFalse(result.updated());
    assertEquals("access token is blank", result.detail());
  }

  @Test
  void setDisplayNameRejectsBlankDisplayName() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixDisplayNameClient.UpdateResult result =
        displayNameClient.setDisplayName(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            "secret-token",
            "@alice:matrix.example.org",
            "   ");

    assertFalse(result.updated());
    assertEquals("display name is blank", result.detail());
  }

  private static ProxyPlan directPlan() {
    IrcProperties.Proxy cfg = new IrcProperties.Proxy(false, "", 0, "", "", true, 3_000, 3_000);
    return new ProxyPlan(cfg, Proxy.NO_PROXY, 3_000, 3_000);
  }

  private static IrcProperties.Server serverConfig(String id, String host, int port, boolean tls) {
    return new IrcProperties.Server(
        id,
        host,
        port,
        tls,
        "",
        "ircafe",
        "ircafe",
        "IRCafe User",
        null,
        null,
        List.of(),
        List.of(),
        null,
        IrcProperties.Server.Backend.MATRIX);
  }

  private record TestServer(
      HttpServer server,
      int port,
      AtomicReference<String> lastMethod,
      AtomicReference<String> lastPath,
      AtomicReference<String> lastBody,
      AtomicReference<String> lastAuthorizationHeader)
      implements AutoCloseable {

    static TestServer start(int statusCode, String body) throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      AtomicReference<String> method = new AtomicReference<>("");
      AtomicReference<String> path = new AtomicReference<>("");
      AtomicReference<String> requestBody = new AtomicReference<>("");
      AtomicReference<String> auth = new AtomicReference<>("");
      byte[] payload = body.getBytes(StandardCharsets.UTF_8);
      server.createContext(
          "/_matrix/client/v3/profile/",
          exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getRawPath());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, statusCode, payload);
          });
      server.start();
      return new TestServer(server, server.getAddress().getPort(), method, path, requestBody, auth);
    }

    @Override
    public void close() {
      server.stop(0);
    }

    private static void reply(HttpExchange exchange, int statusCode, byte[] payload)
        throws IOException {
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(statusCode, payload.length);
      exchange.getResponseBody().write(payload);
      exchange.close();
    }
  }
}
