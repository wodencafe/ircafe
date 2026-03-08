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

class MatrixLoginClientTest {

  private final ServerProxyResolver proxyResolver = mock(ServerProxyResolver.class);
  private final MatrixLoginClient loginClient = new MatrixLoginClient(proxyResolver);

  @Test
  void loginWithPasswordPostsExpectedPayloadAndReturnsToken() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(
            200,
            "{\"access_token\":\"secret-token\",\"user_id\":\"@alice:matrix.example.org\"}")) {
      MatrixLoginClient.LoginResult result =
          loginClient.loginWithPassword(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "alice",
              "matrix-password");

      assertTrue(result.authenticated());
      assertEquals("@alice:matrix.example.org", result.userId());
      assertEquals("secret-token", result.accessToken());
      assertEquals("POST", server.lastMethod().get());
      assertEquals("/_matrix/client/v3/login", server.lastPath().get());
      assertEquals(
          "{\"type\":\"m.login.password\",\"identifier\":{\"type\":\"m.id.user\",\"user\":\"alice\"},\"password\":\"matrix-password\"}",
          server.lastBody().get());
    }
  }

  @Test
  void loginWithPasswordRejectsBlankUsername() {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    MatrixLoginClient.LoginResult result =
        loginClient.loginWithPassword(
            "matrix",
            serverConfig("matrix", "matrix.example.org", 8448, true),
            " ",
            "matrix-password");

    assertFalse(result.authenticated());
    assertEquals("username is blank", result.detail());
  }

  @Test
  void loginWithPasswordReportsHttpFailure() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server = TestServer.start(403, "{\"errcode\":\"M_FORBIDDEN\"}")) {
      MatrixLoginClient.LoginResult result =
          loginClient.loginWithPassword(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "alice",
              "matrix-password");

      assertFalse(result.authenticated());
      assertEquals("HTTP 403 from login endpoint", result.detail());
    }
  }

  @Test
  void loginWithPasswordReportsFailureWhenAccessTokenMissing() throws Exception {
    when(proxyResolver.planForServer("matrix")).thenReturn(directPlan());
    try (TestServer server =
        TestServer.start(200, "{\"user_id\":\"@alice:matrix.example.org\"}")) {
      MatrixLoginClient.LoginResult result =
          loginClient.loginWithPassword(
              "matrix",
              serverConfig("matrix", "127.0.0.1", server.port(), false),
              "alice",
              "matrix-password");

      assertFalse(result.authenticated());
      assertEquals("login response did not include access_token", result.detail());
    }
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
      AtomicReference<String> lastBody)
      implements AutoCloseable {

    static TestServer start(int statusCode, String body) throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      AtomicReference<String> method = new AtomicReference<>("");
      AtomicReference<String> path = new AtomicReference<>("");
      AtomicReference<String> requestBody = new AtomicReference<>("");
      byte[] payload = body.getBytes(StandardCharsets.UTF_8);
      server.createContext(
          "/_matrix/client/v3/login",
          exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getRawPath());
            requestBody.set(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, statusCode, payload);
          });
      server.start();
      return new TestServer(server, server.getAddress().getPort(), method, path, requestBody);
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
